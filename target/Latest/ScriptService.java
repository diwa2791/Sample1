
{
  "transaction-entry": [
    {
      "transaction-external-sys-key": "TXN_001",
      "amount": 1000,
      "currency": "HKD",
      "narratives": {
        "narration1": "Payment A",
        "narration2": "Invoice 123"
      },
      "charges": [
        {
          "type": "SERVICE",
          "value": 10
        }
      ]
    },
    {
      "transaction-external-sys-key": "TXN_002",
      "amount": 500,
      "currency": "USD",
      "status": "PENDING"
    }
  ]
}






{
  "transaction-entry": [
    {
      "transaction-external-sys-key": "TXN_001",
      "amount": 1200,
      "currency": "HKD",
      "narratives": {
        "narration1": "Payment A",
        "narration2": "Invoice 999"
      },
      "charges": [
        {
          "type": "SERVICE",
          "value": 15
        }
      ]
    },
    {
      "transaction-external-sys-key": "TXN_002",
      "amount": 500,
      "currency": "EUR",
      "status": "COMPLETED"
    }
  ]
}






<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<title>Advanced JSON Diff Viewer</title>

<style>

body{
    background:#0f172a;
    color:#e2e8f0;
    font-family:monospace;
    padding:20px;
}

textarea{
    width:100%;
    height:160px;
    background:#020617;
    color:white;
    border:1px solid #334155;
    margin-bottom:10px;
    padding:10px;
}

button{
    padding:8px 16px;
    background:#2563eb;
    border:none;
    color:white;
    cursor:pointer;
    border-radius:4px;
}

input{
    width:100%;
    padding:8px;
    margin:10px 0;
    background:#020617;
    border:1px solid #334155;
    color:white;
}

.card{
    border:1px solid #334155;
    margin-top:15px;
    border-radius:6px;
}

.card-header{
    background:#1e293b;
    padding:10px;
    font-weight:bold;
    cursor:pointer;
}

.card-body{
    display:none;
    padding:10px;
    overflow-x:auto;
}

.node{
    margin-left:18px;
}

.toggle{
    cursor:pointer;
    color:#60a5fa;
}

.key{ color:#93c5fd; }
.value{ color:#e2e8f0; }

.changed{ background:rgba(255,80,80,.18); }
.match{ background:rgba(255,255,0,.25); }

</style>
</head>

<body>

<h2>JSON Deep Comparator</h2>

<textarea id="json1" placeholder="Paste JSON 1"></textarea>
<textarea id="json2" placeholder="Paste JSON 2"></textarea>

<button onclick="compare()">Compare</button>

<input id="searchBox" placeholder="Search key or value..." oninput="searchTree()"/>

<div id="output"></div>

<script>

let allNodes = [];

function compare(){

    const out=document.getElementById("output");
    out.innerHTML="";
    allNodes=[];

    let j1,j2;

    try{
        j1=JSON.parse(json1.value);
        j2=JSON.parse(json2.value);
    }catch(e){
        alert("Invalid JSON");
        return;
    }

    const arr1=j1["transaction-entry"]||[];
    const arr2=j2["transaction-entry"]||[];

    const map2={};
    arr2.forEach(t=>map2[t["transaction-external-sys-key"]]=t);

    arr1.forEach(t1=>{
        const key=t1["transaction-external-sys-key"];
        const t2=map2[key];

        const card=document.createElement("div");
        card.className="card";

        const header=document.createElement("div");
        header.className="card-header";
        header.textContent=key;

        const body=document.createElement("div");
        body.className="card-body";

        header.onclick=()=>{
            body.style.display=
                body.style.display==="block"?"none":"block";
        };

        body.appendChild(buildNode(t1,t2));

        card.appendChild(header);
        card.appendChild(body);
        out.appendChild(card);
    });
}

function buildNode(a,b){

    const container=document.createElement("div");

    if(isPrimitive(a)&&isPrimitive(b)){
        const div=document.createElement("div");
        div.className="node";
        div.innerHTML=`${a} | ${b}`;
        if(a!==b) div.classList.add("changed");

        allNodes.push(div);
        return div;
    }

    const keys=new Set([
        ...Object.keys(a||{}),
        ...Object.keys(b||{})
    ]);

    keys.forEach(key=>{

        const v1=a? a[key]:undefined;
        const v2=b? b[key]:undefined;

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
            label.className="key";
            label.textContent=key;

            const child=buildNode(v1,v2);
            child.style.display="none";

            toggle.onclick=()=>{
                const open=child.style.display==="block";
                child.style.display=open?"none":"block";
                toggle.textContent=open?"[+] ":"[-] ";
            };

            row.append(toggle,label,child);

        }else{

            row.innerHTML=`
                <span class="key">${key}:</span>
                <span class="value">${JSON.stringify(v1)}</span>
                |
                <span class="value">${JSON.stringify(v2)}</span>
            `;

            if(v1!==v2)
                row.classList.add("changed");
        }

        allNodes.push(row);
        container.appendChild(row);
    });

    return container;
}

function isPrimitive(v){
    return v===null || typeof v!=="object";
}

//
// 🔎 SEARCH ENGINE
//
function searchTree(){

    const term=document.getElementById("searchBox")
        .value.toLowerCase();

    allNodes.forEach(n=>{
        n.classList.remove("match");

        if(!term){
            n.style.display="";
            return;
        }

        const text=n.textContent.toLowerCase();

        if(text.includes(term)){
            n.classList.add("match");
            n.style.display="";
            expandParents(n);
        }else{
            n.style.display="none";
        }
    });
}

//
// auto expand parents when match found
//
function expandParents(el){

    let parent=el.parentElement;

    while(parent){
        if(parent.classList.contains("card-body"))
            parent.style.display="block";

        if(parent.previousSibling &&
           parent.previousSibling.classList?.contains("toggle")){
            parent.style.display="block";
        }

        parent=parent.parentElement;
    }
}

</script>

</body>
</html>
