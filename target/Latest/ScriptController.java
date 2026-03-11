package com.company.ruleengine.service;

import com.company.ruleengine.model.Rule;
import com.company.ruleengine.parser.RuleNormalizer;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.*;

@Service
public class RuleLoader {

    public List<Rule> load(MultipartFile file) throws Exception {

        List<Rule> rules = new ArrayList<>();

        BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(file.getInputStream())
                );

        String line;
        int id = 1;

        while((line = reader.readLine()) != null){

            if(line.trim().isEmpty()) continue;

            Rule r = new Rule();

            r.setRuleId(id++);
            r.setOriginalExpression(line.trim());
            r.setNormalizedExpression(
                    RuleNormalizer.normalize(line.trim())
            );

            rules.add(r);
        }

        return rules;
    }
}

--------

package com.company.ruleengine.controller;

import com.company.ruleengine.model.Rule;
import com.company.ruleengine.service.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/rules")
public class RuleController {

    @Autowired
    RuleLoader loader;

    @Autowired
    RuleCompiler compiler;

    private List<Rule> cachedRules;

    @PostMapping("/upload")
    public List<Rule> upload(
            @RequestParam("file") MultipartFile file
    ) throws Exception {

        cachedRules = loader.load(file);

        compiler.compile(cachedRules);

        return cachedRules;
    }
}

------




package ruleengine.model;

public class Condition {

    private String field;
    private String operator;
    private String value;

    public Condition(String field,String operator,String value){
        this.field = field;
        this.operator = operator;
        this.value = value;
    }

    public String getField(){ return field; }

    public String getOperator(){ return operator; }

    public String getValue(){ return value; }

}

-—---------

    
package ruleengine.model;

import java.io.Serializable;
import java.util.List;

public class Rule {

    private int ruleId;

    private String originalExpression;

    private String normalizedExpression;

    private Serializable compiledExpression;

    private boolean valid = true;

    private String error;

    private List<Condition> conditions;

    public int getRuleId(){ return ruleId; }

    public void setRuleId(int ruleId){ this.ruleId = ruleId; }

    public String getOriginalExpression(){ return originalExpression; }

    public void setOriginalExpression(String originalExpression){
        this.originalExpression = originalExpression;
    }

    public String getNormalizedExpression(){ return normalizedExpression; }

    public void setNormalizedExpression(String normalizedExpression){
        this.normalizedExpression = normalizedExpression;
    }

    public Serializable getCompiledExpression(){ return compiledExpression; }

    public void setCompiledExpression(Serializable compiledExpression){
        this.compiledExpression = compiledExpression;
    }

    public boolean isValid(){ return valid; }

    public void setValid(boolean valid){ this.valid = valid; }

    public String getError(){ return error; }

    public void setError(String error){ this.error = error; }

    public List<Condition> getConditions(){ return conditions; }

    public void setConditions(List<Condition> conditions){
        this.conditions = conditions;
    }
}

--------------

package ruleengine.model;

public class RuleResult {

    private int ruleId;

    private String status;

    private String error;

    public int getRuleId(){ return ruleId; }

    public void setRuleId(int ruleId){ this.ruleId = ruleId; }

    public String getStatus(){ return status; }

    public void setStatus(String status){ this.status = status; }

    public String getError(){ return error; }

    public void setError(String error){ this.error = error; }

}


------------

package ruleengine.parser;

public class RuleNormalizer {

    public static String normalize(String rule){

        if(rule == null) return null;

        String r = rule;

        r = r.replaceAll("\\['","");

        r = r.replaceAll("'\\]","");

        r = r.replaceAll("\\]\\['",".");

        return r;
    }

}


-----------
package ruleengine.parser;

import ruleengine.model.Condition;

import java.util.*;
import java.util.regex.*;

public class ConditionExtractor {

    private static final Pattern PATTERN =
            Pattern.compile("([a-zA-Z0-9_.]+)\\s*(==|>|<|>=|<=)\\s*'?([^'&|]+)'?");

    public static List<Condition> extract(String expression){

        List<Condition> conditions = new ArrayList<>();

        Matcher m = PATTERN.matcher(expression);

        while(m.find()){

            conditions.add(
                    new Condition(
                            m.group(1),
                            m.group(2),
                            m.group(3)
                    )
            );
        }

        return conditions;
    }

}



-----------

package ruleengine.service;

import ruleengine.model.Rule;

import org.mvel2.MVEL;

import java.util.List;

public class RuleCompiler {

    public void compile(List<Rule> rules){

        for(Rule r : rules){

            try{

                r.setCompiledExpression(
                        MVEL.compileExpression(
                                r.getNormalizedExpression()
                        )
                );

            }catch(Exception e){

                r.setValid(false);

                r.setError(e.getMessage());
            }
        }

    }

}

-------------
package ruleengine.engine;

import ruleengine.model.*;

import java.util.*;

public class RuleIndex {

    private Map<String,List<Rule>> fieldIndex = new HashMap<>();

    public void build(List<Rule> rules){

        for(Rule r : rules){

            if(r.getConditions() == null) continue;

            for(Condition c : r.getConditions()){

                fieldIndex
                        .computeIfAbsent(
                                c.getField(),
                                k -> new ArrayList<>()
                        )
                        .add(r);
            }
        }
    }

    public Set<Rule> findCandidates(Map<String,Object> input){

        Set<Rule> candidates = new HashSet<>();

        for(String field : input.keySet()){

            List<Rule> rules = fieldIndex.get(field);

            if(rules != null){

                candidates.addAll(rules);
            }
        }

        return candidates;
    }

}

-------

package ruleengine.engine;

import ruleengine.model.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.mvel2.MVEL;

import java.util.*;

public class RuleEngine {

    public List<RuleResult> evaluate(
            String json,
            Set<Rule> rules
    ) throws Exception{

        ObjectMapper mapper = new ObjectMapper();

        Map<String,Object> context =
                mapper.readValue(json, Map.class);

        List<RuleResult> results = new ArrayList<>();

        for(Rule r : rules){

            RuleResult rr = new RuleResult();

            rr.setRuleId(r.getRuleId());

            if(!r.isValid()){

                rr.setStatus("INVALID");

                rr.setError(r.getError());

                results.add(rr);

                continue;
            }

            try{

                Boolean pass =
                        (Boolean) MVEL.executeExpression(
                                r.getCompiledExpression(),
                                context
                        );

                rr.setStatus(pass ? "PASS" : "FAIL");

            }catch(Exception e){

                rr.setStatus("ERROR");

                rr.setError(e.getMessage());
            }

            results.add(rr);
        }

        return results;
    }

}

-----------

<!DOCTYPE html>
<html>
<head>

<title>Rule Engine Debugger</title>

<style>

body{
font-family:Arial;
margin:40px;
background:#f5f6fa;
}

textarea{
width:100%;
height:200px;
padding:10px;
font-family:monospace;
}

button{
padding:10px 20px;
margin-top:10px;
cursor:pointer;
}

table{
border-collapse:collapse;
width:100%;
margin-top:20px;
background:white;
}

th,td{
border:1px solid #ddd;
padding:10px;
}

th{
background:#2f3640;
color:white;
}

.pass{
background:#d4edda;
}

.fail{
background:#f8d7da;
}

.invalid{
background:#fff3cd;
}

</style>

</head>

<body>

<h2>Rule Engine Debugger</h2>

<h3>Upload Rule File</h3>

<input type="file" id="ruleFile">

<button onclick="uploadRules()">Upload</button>

<div id="ruleStatus"></div>

<h3>Paste JSON Input</h3>

<textarea id="jsonInput" placeholder="Paste JSON here"></textarea>

<button onclick="evaluateRules()">Evaluate Rules</button>

<table id="resultTable">

<thead>
<tr>
<th>Rule ID</th>
<th>Status</th>
<th>Error</th>
</tr>
</thead>

<tbody></tbody>

</table>

<script>

let uploadedRules=[]

async function uploadRules(){

let file=document.getElementById("ruleFile").files[0]

let formData=new FormData()

formData.append("file",file)

let res=await fetch("/rules/upload",{
method:"POST",
body:formData
})

let data=await res.json()

uploadedRules=data

let statusHTML="<h4>Rules Loaded</h4>"

data.forEach(r=>{

if(r.valid){

statusHTML+="Rule "+r.ruleId+" ✔ VALID<br>"

}else{

statusHTML+="Rule "+r.ruleId+" ❌ INVALID : "+r.error+"<br>"

}

})

document.getElementById("ruleStatus").innerHTML=statusHTML

}

async function evaluateRules(){

let json=document.getElementById("jsonInput").value

let res=await fetch("/rules/evaluate",{

method:"POST",

headers:{
"Content-Type":"application/json"
},

body:json

})

let results=await res.json()

let table=document.querySelector("#resultTable tbody")

table.innerHTML=""

results.forEach(r=>{

let row=document.createElement("tr")

row.innerHTML=

"<td>"+r.ruleId+"</td>"+
"<td>"+r.status+"</td>"+
"<td>"+(r.error || "")+"</td>"

if(r.status==="PASS") row.className="pass"

if(r.status==="FAIL") row.className="fail"

if(r.status==="INVALID") row.className="invalid"

table.appendChild(row)

})

}

</script>

</body>
</html>


