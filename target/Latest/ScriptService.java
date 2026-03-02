
public List<DiffNode> compare(JsonNode a, JsonNode b, String rootPath) {

    List<DiffNode> diffs = new ArrayList<>();

    JsonNode nodeA = a;
    JsonNode nodeB = b;

    if (rootPath != null && !rootPath.isBlank()) {

        // remove leading slash if present
        String normalized = rootPath.startsWith("/")
                ? rootPath.substring(1)
                : rootPath;

        nodeA = a.at("/" + normalized);
        nodeB = b.at("/" + normalized);

        if (nodeA.isMissingNode() && nodeB.isMissingNode()) {
            return diffs; // nothing to compare
        }

        compareRecursive("/" + normalized, nodeA, nodeB, diffs);
    }
    else {
        compareRecursive("", a, b, diffs);
    }

    return diffs;
}

---------

  compare(a, b, "/data/customerInfo");







private void checkDuplicateTransactionExternalKey(String path,
                                                  JsonNode arrayNode,
                                                  List<DiffNode> diffs) {

    // Apply only for transaction-entry array
    if (!path.endsWith("/transaction-entry")) return;

    Map<String, List<Integer>> keyIndexMap = new HashMap<>();

    for (int i = 0; i < arrayNode.size(); i++) {

        JsonNode element = arrayNode.get(i);

        if (!element.isObject()) continue;

        JsonNode keyNode = element.get("transaction-external-sys-key");

        if (keyNode == null || keyNode.isNull()) continue;

        String keyValue = keyNode.asText();

        keyIndexMap
                .computeIfAbsent(keyValue, k -> new ArrayList<>())
                .add(i);
    }

    for (Map.Entry<String, List<Integer>> entry : keyIndexMap.entrySet()) {

        if (entry.getValue().size() > 1) {

            for (Integer index : entry.getValue()) {

                String duplicatePath = path + "[" + index + "]"
                        + "/transaction-external-sys-key";

                diffs.add(new DiffNode(
                        duplicatePath,
                        entry.getKey(),
                        entry.getKey(),
                        "DUPLICATE_TXN_KEY"
                ));
            }
        }
    }
}

------
if (a != null && a.isArray()) {
    checkDuplicateTransactionExternalKey(path, a, diffs);
}

if (b != null && b.isArray()) {
    checkDuplicateTransactionExternalKey(path, b, diffs);
}

------

    if (diff.type === "DUPLICATE_TXN_KEY") {
    row.classList.add("duplicate");
}

.duplicate {
    background-color: rgba(255, 140, 0, 0.15);
    border-left: 4px solid orange;
}

    
