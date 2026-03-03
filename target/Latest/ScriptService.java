
function buildTree(a, b, diffs = [], path = "")

  const childPath = path ? path + "/" + key : "/" + key;

const child = buildTree(
    v1,
    v2,
    diffs,
    childPath
);

---------

  const changed = JSON.stringify(v1) !== JSON.stringify(v2);

const currentPath = path ? path + "/" + key : "/" + key;

// 🔥 Check duplicate diff from backend
const isDuplicate = diffs.some(d =>
    d.type === "DUPLICATE_TXN_KEY" &&
    d.path === currentPath
);

// If nothing changed AND not duplicate → prune
if (!changed && !isDuplicate) return;

row.__diff = true;

if (changed) {
    row.classList.add("changed");
    diffNodes.push(row);
}

if (isDuplicate) {
    row.classList.add("duplicate");
}

row.innerHTML =
    "<b>" + key + "</b>: " +
    JSON.stringify(v1) +
    " ⟷ " +
    JSON.stringify(v2);


--------
if (isPrimitive(a) && isPrimitive(b)) {

    const node = document.createElement("div");
    node.className = "node";

    const changed = JSON.stringify(a) !== JSON.stringify(b);
    const currentPath = path;

    const isDuplicate = diffs.some(d =>
        d.type === "DUPLICATE_TXN_KEY" &&
        d.path === currentPath
    );

    if (!changed && !isDuplicate) return null;

    node.textContent = a + " ⟷ " + b;

    if (changed) {
        node.classList.add("changed");
        diffNodes.push(node);
    }

    if (isDuplicate) {
        node.classList.add("duplicate");
    }

    allNodes.push(node);
    return node;
}

-------

  const tree = buildTree(
    u.prodPayload,
    u.apiPayload,
    u.diffs,
    ""
);

------

  
.duplicate {
    background: rgba(255,165,0,0.18);
    border-left: 4px solid orange;
}
