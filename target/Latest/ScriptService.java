<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <title>Table Comparison</title>

    <link rel="stylesheet"
          href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css">

    <style>
        body {
            margin: 0;
            min-height: 100vh;
            background: linear-gradient(135deg, #0f2027, #2c5364, #00c9a7);
            font-family: "Segoe UI", sans-serif;
            color: white;
        }

        .glass-card {
            background: rgba(255, 255, 255, 0.08);
            backdrop-filter: blur(12px);
            border-radius: 12px;
            padding: 25px;
            box-shadow: 0 8px 32px rgba(0,0,0,0.3);
        }

        table {
            width: 100%;
            border-collapse: collapse;
            color: white;
        }

        thead {
            background: rgba(255,255,255,0.15);
            position: sticky;
            top: 0;
        }

        th, td {
            padding: 12px;
            border-bottom: 1px solid rgba(255,255,255,0.1);
        }

        .modified-row { background: rgba(255,193,7,0.15); }

        .old-value { color: #ff6b6b; font-weight: bold; }
        .new-value { color: #4cd137; font-weight: bold; }

        #loadingOverlay {
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: rgba(0,0,0,0.5);
            display: none;
            align-items: center;
            justify-content: center;
            z-index: 9999;
        }

        .spinner-border {
            width: 4rem;
            height: 4rem;
        }
    </style>
</head>

<body>

<div id="loadingOverlay">
    <div class="spinner-border text-light"></div>
</div>

<div class="container mt-5">
    <div class="glass-card">

        <h3 class="mb-4">TABLE COMPARISON</h3>

        <!-- FORM -->
        <form th:action="@{/compare}" th:object="${request}" method="post"
              onsubmit="showLoading()">

            <div class="row mb-4">
                <div class="col-md-6">
                    <input type="text"
                           th:field="*{tableName}"
                           class="form-control"
                           placeholder="Enter table name"
                           style="background:rgba(255,255,255,0.15); color:white; border:none;">
                </div>
                <div class="col-md-2">
                    <button type="submit"
                            class="btn btn-light w-100">
                        Compare
                    </button>
                </div>
            </div>
        </form>

        <!-- SUMMARY -->
        <div th:if="${diff != null}" class="mb-4">
            <span class="badge bg-warning me-2">
                Modified: <span th:text="${diff.summary.different}"></span>
            </span>
            <span class="badge bg-danger me-2">
                Only in Test: <span th:text="${diff.summary.onlyInTest}"></span>
            </span>
            <span class="badge bg-primary">
                Only in Prod: <span th:text="${diff.summary.onlyInProd}"></span>
            </span>
        </div>

        <!-- MODIFIED TABLE -->
        <div th:if="${diff != null}">
            <table>
                <thead>
                <tr>
                    <th>Primary Key</th>
                    <th>Changed Columns</th>
                    <th>Action</th>
                </tr>
                </thead>
                <tbody>
                <tr th:each="row,iterStat : ${diff.modifiedRows}"
                    class="modified-row">

                    <td th:text="${#strings.listJoin(row.primaryKey.values(),' | ')}"></td>
                    <td th:text="${row.changedColumns.size()}"></td>
                    <td>
                        <button type="button"
                                class="btn btn-sm btn-light"
                                th:attr="onclick='toggleDetails(' + ${iterStat.index} + ')'">
                            Expand
                        </button>
                    </td>
                </tr>

                <!-- Detail Row -->
                <tr th:each="row,iterStat : ${diff.modifiedRows}"
                    th:attr="id='detail-' + ${iterStat.index}"
                    style="display:none;">

                    <td colspan="3">
                        <div class="glass-card mt-2">
                            <strong>Column Differences</strong>
                            <table class="mt-3">
                                <thead>
                                <tr>
                                    <th>Column</th>
                                    <th>Test</th>
                                    <th>Prod</th>
                                </tr>
                                </thead>
                                <tbody>
                                <tr th:each="col : ${row.changedColumns}">
                                    <td th:text="${col.columnName}"></td>
                                    <td class="old-value"
                                        th:text="${col.testValue}"></td>
                                    <td class="new-value"
                                        th:text="${col.prodValue}"></td>
                                </tr>
                                </tbody>
                            </table>
                        </div>
                    </td>
                </tr>

                </tbody>
            </table>
        </div>

    </div>
</div>

<script>
function showLoading() {
    document.getElementById("loadingOverlay").style.display = "flex";
}

function toggleDetails(index) {
    let row = document.getElementById("detail-" + index);
    if (row.style.display === "none") {
        row.style.display = "table-row";
    } else {
        row.style.display = "none";
    }
}
</script>

</body>
</html>
