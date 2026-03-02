function hasDiff(node) {
    if (!node) return false;

    if (node.__diff === true) return true;

    if (node.children) {
        for (const c of node.children) {
            if (hasDiff(c)) return true;
        }
    }
    return false;
}


-------

function buildTree(a, b) {

    const container = document.createElement("div");

    // ---- PRIMITIVE COMPARISON ----
    if (isPrimitive(a) && isPrimitive(b)) {

        const node = document.createElement("div");
        node.className = "node";

        const changed = JSON.stringify(a) !== JSON.stringify(b);
        node.textContent = a + " | " + b;

        node.__diff = changed;

        if (changed) {
            node.classList.add("changed");
            diffNodes.push(node);
        }

        allNodes.push(node);
        return node;
    }

    // ---- OBJECT / ARRAY HANDLING ----
    const keys = new Set([
        ...Object.keys(a || {}),
        ...Object.keys(b || {})
    ]);

    keys.forEach(key => {

        const v1 = a ? a[key] : undefined;
        const v2 = b ? b[key] : undefined;

        const row = document.createElement("div");
        row.className = "node";

        const isObj =
            (v1 !== null && typeof v1 === "object") ||
            (v2 !== null && typeof v2 === "object");

        if (isObj) {

            // ---- RECURSIVE CHILD ----
            const child = buildTree(v1, v2);
            const childHasDiff = hasDiff(child);

            // If no diff inside this branch → skip entirely
            if (!childHasDiff) return;

            row.__diff = true;

            const toggle = document.createElement("span");
            toggle.className = "toggle";
            toggle.textContent = "[+] ";

            const label = document.createElement("span");
            label.textContent = key;

            child.style.display = "none";

            toggle.onclick = () => {
                const open = child.style.display === "block";
                child.style.display = open ? "none" : "block";
                toggle.textContent = open ? "[+] " : "[-] ";
            };

            row.append(toggle, label, child);
        }
        else {
            // ---- LEAF NODE ----
            const changed = JSON.stringify(v1) !== JSON.stringify(v2);

            if (!changed) return;   // 🚨 prune equal leaf

            row.__diff = true;
            row.classList.add("changed");
            diffNodes.push(row);

            row.innerHTML =
                "<b>" + key + "</b>: " +
                JSON.stringify(v1) +
                " | " +
                JSON.stringify(v2);
        }

        allNodes.push(row);
        container.appendChild(row);
    });

    return container;
}
