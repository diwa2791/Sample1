
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
    padding:8px 14px;
    margin-right:6px;
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
    position:relative;
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

.changed{
    background:rgba(255,80,80,.18);
}

.match{
    background:rgba(255,255,0,.25);
}

.diff-badge{
    float:right;
    padding:2px 8px;
    border-radius:12px;
    font-size:12px;
    font-weight:bold;
}

.badge-red{
    background:rgba(255,80,80,.25);
    color:#ff8080;
}

.badge-green{
    background:rgba(80,255,120,.25);
    color:#7CFC9C;
}

</style>
</head>

<body>

<h2>JSON Deep Comparator</h2>

<textarea id="json1" placeholder="Paste JSON 1"></textarea>
<textarea id="json2" placeholder="Paste JSON 2"></textarea>

<button onclick="compare()">Compare</button>
<button onclick="jumpDiff(-1)">Prev Diff</button>
<button onclick="jumpDiff(1)">Next Diff</button>

<input id="searchBox" placeholder="Search key or value..." oninput="searchTree()"/>

<div id="output"></div>

<script>

let allNodes=[];
let diffNodes=[];
let currentDiffIndex=-1;

function compare(){

    const out=document.getElementById("output");
    out.innerHTML="";
    allNodes=[];
    diffNodes=[];
    currentDiffIndex=-1;

    let j1,j2;

    try{
        j1=JSON.parse(json1.value);
        j2=JSON.parse(json2.value);
    }catch(e){
        alert("Invalid JSON");
        return;
    }

    const arr1=Array.isArray(j1)? j1 : j1["transaction-entry"]||[];
    const arr2=Array.isArray(j2)? j2 : j2["transaction-entry"]||[];

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

        const startDiffIndex=diffNodes.length;

        const tree=buildNode(t1,t2);
        body.appendChild(tree);

        const endDiffIndex=diffNodes.length;
        const diffCount=endDiffIndex-startDiffIndex;

        if(diffCount>0){
            header.classList.add("changed");
        }

        const badge=document.createElement("span");
        badge.className="diff-badge " +
            (diffCount>0?"badge-red":"badge-green");

        badge.textContent=diffCount+" diff"+(diffCount!==1?"s":"");
        header.appendChild(badge);

        card.appendChild(header);
        card.appendChild(body);
        out.appendChild(card);
    });
}

function markParentsAsChanged(el){

    let parent=el.parentElement;

    while(parent){

        if(parent.classList?.contains("node") ||
           parent.classList?.contains("card-header")){
            parent.classList.add("changed");
        }

        parent=parent.parentElement;
    }
}

function buildNode(a,b){

    const container=document.createElement("div");

    if(isPrimitive(a)&&isPrimitive(b)){

        const div=document.createElement("div");
        div.className="node";
        div.textContent=`${a} | ${b}`;

        if(a!==b){
            div.classList.add("changed");
            diffNodes.push(div);
            markParentsAsChanged(div);
        }

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

            if(v1!==v2){
                row.classList.add("changed");
                diffNodes.push(row);
                markParentsAsChanged(row);
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
