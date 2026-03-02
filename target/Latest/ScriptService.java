package com.example.batchcompare;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.*;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@SpringBootApplication
public class BatchCompareApplication {

    public static void main(String[] args) {
        SpringApplication.run(BatchCompareApplication.class, args);
    }

    // ===============================
    // DATA MODELS
    // ===============================

    static class DiffNode {
        public String path;
        public String value1;
        public String value2;
        public String type;

        public DiffNode(String path, String value1, String value2, String type) {
            this.path = path;
            this.value1 = value1;
            this.value2 = value2;
            this.type = type;
        }
    }

    static class UetrResult {
        public String uetr;
        public JsonNode prodPayload;
        public JsonNode apiPayload;
        public List<DiffNode> diffs = new ArrayList<>();
    }

    static class IdResult {
        public String id;
        public List<UetrResult> uetrResults = new ArrayList<>();
    }

    static class BatchResult {
        public String batchId;
        public Map<String, IdResult> idResults = new LinkedHashMap<>();
    }

    // ===============================
    // BASELINE LOADER
    // ===============================

    @Service
    static class BaselineLoaderService {

        private final ObjectMapper mapper = new ObjectMapper();
        private final Map<String, JsonNode> baselineStore = new ConcurrentHashMap<>();

        public void loadFolder(String folder) throws Exception {

            Files.walk(Paths.get(folder))
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            JsonNode node = mapper.readTree(path.toFile());
                            String id = node.get("id").asText();
                            baselineStore.put(id, node);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        }

        public JsonNode get(String id) {
            return baselineStore.get(id);
        }
    }

    // ===============================
    // API FETCH
    // ===============================

    @Service
    static class ApiFetchService {

        private final RestTemplate restTemplate = new RestTemplate();
        private final ObjectMapper mapper = new ObjectMapper();

        public JsonNode fetch(String id) throws Exception {

            String url = "http://localhost:8081/api/" + id;
            String response = restTemplate.getForObject(url, String.class);
            return mapper.readTree(response);
        }
    }

    // ===============================
    // JSON DIFF ENGINE
    // ===============================

    @Service
    static class JsonDiffService {

        public List<DiffNode> compare(JsonNode a, JsonNode b) {

            List<DiffNode> diffs = new ArrayList<>();
            compareRecursive("", a, b, diffs);
            return diffs;
        }

        private void compareRecursive(String path, JsonNode a, JsonNode b, List<DiffNode> diffs) {

            if (a == null && b != null) {
                diffs.add(new DiffNode(path, null, b.toString(), "ADDED"));
                return;
            }

            if (a != null && b == null) {
                diffs.add(new DiffNode(path, a.toString(), null, "REMOVED"));
                return;
            }

            if (a.isValueNode() && b.isValueNode()) {
                if (!a.equals(b)) {
                    diffs.add(new DiffNode(path, a.toString(), b.toString(), "CHANGED"));
                }
                return;
            }

            Set<String> fields = new HashSet<>();
            a.fieldNames().forEachRemaining(fields::add);
            b.fieldNames().forEachRemaining(fields::add);

            for (String field : fields) {
                compareRecursive(
                        path + "/" + field,
                        a.get(field),
                        b.get(field),
                        diffs
                );
            }
        }
    }

    // ===============================
    // BATCH SERVICE
    // ===============================

    @Service
    static class BatchCompareService {

        private final BaselineLoaderService baseline;
        private final ApiFetchService api;
        private final JsonDiffService diff;

        private final Map<String, BatchResult> batchStore = new ConcurrentHashMap<>();

        public BatchCompareService(
                BaselineLoaderService baseline,
                ApiFetchService api,
                JsonDiffService diff) {
            this.baseline = baseline;
            this.api = api;
            this.diff = diff;
        }

        public String run(List<String> ids) throws Exception {

            String batchId = UUID.randomUUID().toString();
            BatchResult batch = new BatchResult();
            batch.batchId = batchId;

            for (String id : ids) {

                JsonNode prod = baseline.get(id);
                JsonNode apiResp = api.fetch(id);

                List<DiffNode> diffs = diff.compare(prod, apiResp);

                IdResult idResult = new IdResult();
                idResult.id = id;

                UetrResult u = new UetrResult();
                u.uetr = prod.has("uetr") ? prod.get("uetr").asText() : "MAIN";
                u.prodPayload = prod;
                u.apiPayload = apiResp;
                u.diffs = diffs;

                idResult.uetrResults.add(u);
                batch.idResults.put(id, idResult);
            }

            batchStore.put(batchId, batch);
            return batchId;
        }

        public BatchResult get(String batchId) {
            return batchStore.get(batchId);
        }
    }

    // ===============================
    // CONTROLLER
    // ===============================

    @RestController
    @RequestMapping("/compare")
    static class CompareController {

        private final BaselineLoaderService loader;
        private final BatchCompareService batch;

        public CompareController(
                BaselineLoaderService loader,
                BatchCompareService batch) {
            this.loader = loader;
            this.batch = batch;
        }

        @PostMapping("/baseline/load")
        public String load() throws Exception {
            loader.loadFolder("prod/input");
            return "Baseline loaded";
        }

        @PostMapping("/run")
        public String run(@RequestBody String ids) throws Exception {
            List<String> idList = Arrays.asList(ids.split(","));
            return batch.run(idList);
        }

        @GetMapping("/batch/{batchId}")
        public BatchResult result(@PathVariable String batchId) {
            return batch.get(batchId);
        }

        // ===============================
        // SIMPLE UI PAGE
        // ===============================

        @GetMapping("/ui")
        public String ui() {
            return """
<!DOCTYPE html>
<html>
<head>
<title>Batch JSON Compare</title>
<style>
body{font-family:Arial;background:#0f172a;color:white;padding:20px;}
button{padding:6px 10px;margin:4px;}
.card{border:1px solid #334155;margin-top:10px;padding:10px;}
.header{cursor:pointer;font-weight:bold;}
.changed{color:#ff8080;}
</style>
</head>
<body>

<h2>Batch Compare UI</h2>

<input id="ids" placeholder="Enter IDs comma separated"/>
<button onclick="run()">Run</button>

<div id="output"></div>

<script>

async function run(){
    const ids=document.getElementById("ids").value;
    const batchId=await fetch("/compare/run",{method:"POST",body:ids}).then(r=>r.text());
    const data=await fetch("/compare/batch/"+batchId).then(r=>r.json());
    render(data);
}

function render(data){

    const out=document.getElementById("output");
    out.innerHTML="";

    Object.values(data.idResults).forEach(idRes=>{

        const div=document.createElement("div");
        div.className="card";

        div.innerHTML="<div class='header'>"+idRes.id+"</div>";

        idRes.uetrResults.forEach(u=>{

            const udiv=document.createElement("div");
            udiv.innerHTML="<b>UETR:</b> "+u.uetr+
            " | Diffs: <span class='changed'>"+u.diffs.length+"</span>";

            div.appendChild(udiv);
        });

        out.appendChild(div);
    });
}

</script>

</body>
</html>
""";
        }
    }
}



-----------------------
@GetMapping("/ui")
public String ui() {
    return """
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<title>Batch Deep JSON Compare</title>

<style>
body{
    margin:0;
    font-family:Segoe UI,Arial;
    background:linear-gradient(135deg,#0f172a,#1e293b);
    color:#e2e8f0;
    padding:30px;
}

h2{margin-bottom:20px;}

.input-bar{
    display:flex;
    gap:10px;
    margin-bottom:15px;
}

input{
    flex:1;
    padding:10px;
    background:rgba(255,255,255,0.05);
    border:1px solid rgba(255,255,255,0.1);
    color:white;
    border-radius:6px;
}

button{
    padding:8px 14px;
    background:#3b82f6;
    border:none;
    color:white;
    border-radius:6px;
    cursor:pointer;
}

.card{
    backdrop-filter:blur(12px);
    background:rgba(255,255,255,0.05);
    border:1px solid rgba(255,255,255,0.1);
    border-radius:10px;
    margin-bottom:15px;
    overflow:hidden;
}

.card-header{
    padding:14px;
    cursor:pointer;
    font-weight:600;
    display:flex;
    justify-content:space-between;
}

.card-body{
    display:none;
    padding:15px;
    border-top:1px solid rgba(255,255,255,0.1);
}

.badge{
    padding:4px 10px;
    border-radius:20px;
    font-size:12px;
    font-weight:600;
}

.badge-red{
    background:rgba(255,80,80,0.2);
    color:#ff8080;
}

.badge-green{
    background:rgba(80,255,120,0.2);
    color:#7CFC9C;
}

.node{
    margin-left:18px;
    font-size:13px;
}

.toggle{
    cursor:pointer;
    color:#60a5fa;
}

.changed{
    background:rgba(255,80,80,.18);
}

.match{
    background:rgba(255,255,0,.25);
}

.search-box{
    margin:10px 0;
}
</style>
</head>

<body>

<h2>Batch Deep JSON Comparator</h2>

<div class="input-bar">
    <input id="ids" placeholder="Enter IDs comma separated">
    <button onclick="runBatch()">Run</button>
    <button onclick="jumpDiff(-1)">Prev Diff</button>
    <button onclick="jumpDiff(1)">Next Diff</button>
</div>

<input id="searchBox" class="search-box"
       placeholder="Search within diffs..."
       oninput="searchTree()">

<div id="output"></div>

<script>

let allNodes=[];
let diffNodes=[];
let currentDiffIndex=-1;

async function runBatch(){

    const ids=document.getElementById("ids").value.trim();
    if(!ids) return;

    allNodes=[];
    diffNodes=[];
    currentDiffIndex=-1;

    const batchId=await fetch("/compare/run",{
        method:"POST",
        body:ids
    }).then(r=>r.text());

    const data=await fetch("/compare/batch/"+batchId)
        .then(r=>r.json());

    render(data);
}

function render(data){

    const out=document.getElementById("output");
    out.innerHTML="";

    Object.values(data.idResults).forEach(idRes=>{

        const totalDiff=idRes.uetrResults
            .reduce((sum,u)=>sum+u.diffs.length,0);

        const card=document.createElement("div");
        card.className="card";

        const header=document.createElement("div");
        header.className="card-header";
        header.innerHTML=
            "<span>"+idRes.id+"</span>"+
            "<span class='badge "+
            (totalDiff>0?"badge-red":"badge-green")+
            "'>"+totalDiff+" diffs</span>";

        const body=document.createElement("div");
        body.className="card-body";

        header.onclick=()=>{
            body.style.display=
                body.style.display==="block"?"none":"block";
        };

        idRes.uetrResults.forEach(u=>{
            const uDiv=document.createElement("div");
            uDiv.style.marginTop="10px";

            const uHeader=document.createElement("div");
            uHeader.innerHTML="<b>UETR:</b> "+u.uetr+
            " | <span class='badge "+
            (u.diffs.length>0?"badge-red":"badge-green")+
            "'>"+u.diffs.length+" diffs</span>";

            uDiv.appendChild(uHeader);

            const tree=buildTree(u.prodPayload,u.apiPayload);
            uDiv.appendChild(tree);

            body.appendChild(uDiv);
        });

        card.appendChild(header);
        card.appendChild(body);
        out.appendChild(card);
    });
}

function buildTree(a,b){

    const container=document.createElement("div");

    if(isPrimitive(a)&&isPrimitive(b)){
        const node=document.createElement("div");
        node.className="node";
        node.textContent=a+" | "+b;

        if(JSON.stringify(a)!==JSON.stringify(b)){
            node.classList.add("changed");
            diffNodes.push(node);
        }

        allNodes.push(node);
        return node;
    }

    const keys=new Set([
        ...Object.keys(a||{}),
        ...Object.keys(b||{})
    ]);

    keys.forEach(key=>{

        const v1=a?a[key]:undefined;
        const v2=b?b[key]:undefined;

        const row=document.createElement("div");
        row.className="node";

        const isObj=
            typeof v1==="object" ||
            typeof v2==="object";

        if(isObj && v1 && v2){

            const toggle=document.createElement("span");
            toggle.className="toggle";
            toggle.textContent="[+] ";

            const label=document.createElement("span");
            label.textContent=key;

            const child=buildTree(v1,v2);
            child.style.display="none";

            toggle.onclick=()=>{
                const open=child.style.display==="block";
                child.style.display=open?"none":"block";
                toggle.textContent=open?"[+] ":"[-] ";
            };

            row.append(toggle,label,child);

        }else{

            row.innerHTML=
                "<b>"+key+":</b> "+
                JSON.stringify(v1)+" | "+
                JSON.stringify(v2);

            if(JSON.stringify(v1)!==JSON.stringify(v2)){
                row.classList.add("changed");
                diffNodes.push(row);
            }
        }

        allNodes.push(row);
        container.appendChild(row);
    });

    return container;
}

function isPrimitive(v){
    return v===null || typeof v!=="object";
}

function searchTree(){

    const term=searchBox.value.toLowerCase();

    allNodes.forEach(n=>{
        n.classList.remove("match");

        if(!term){
            n.style.display="";
            return;
        }

        if(n.textContent.toLowerCase().includes(term)){
            n.classList.add("match");
            n.style.display="";
            expandParents(n);
        }else{
            n.style.display="none";
        }
    });
}

function expandParents(el){
    let parent=el.parentElement;
    while(parent){
        if(parent.classList.contains("card-body"))
            parent.style.display="block";
        parent=parent.parentElement;
    }
}

function jumpDiff(direction){

    if(diffNodes.length===0) return;

    currentDiffIndex+=direction;

    if(currentDiffIndex>=diffNodes.length)
        currentDiffIndex=0;

    if(currentDiffIndex<0)
        currentDiffIndex=diffNodes.length-1;

    const target=diffNodes[currentDiffIndex];
    expandParents(target);

    target.scrollIntoView({
        behavior:"smooth",
        block:"center"
    });

    diffNodes.forEach(d=>d.style.outline="none");
    target.style.outline="2px solid yellow";
}

</script>

</body>
</html>
""";
}
