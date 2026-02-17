<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <title>Table Comparison</title>

    <link rel="stylesheet"
          href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css">

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/js/bootstrap.bundle.min.js"></script>

    <style>
        body {
            margin: 0;
            min-height: 100vh;
            background: linear-gradient(135deg, #0f2027, #2c5364, #00c9a7);
            font-family: "Segoe UI", sans-serif;
            color: white;
        }

        .glass-card {
            background: rgba(255,255,255,0.08);
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
            padding: 10px;
            border-bottom: 1px solid rgba(255,255,255,0.1);
        }

        .old-value { color: #ff6b6b; font-weight: bold; }
        .new-value { color: #4cd137; font-weight: bold; }

        .expand-btn {
            background: rgba(255,255,255,0.2);
            border: none;
            color: white;
            padding: 5px 10px;
            border-radius: 6px;
        }

        .expand-btn:hover {
            background: rgba(255,255,255,0.35);
        }

        /* Spinner Overlay */
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

        .nav-tabs .nav-link {
            color: white;
        }

        .nav-tabs .nav-link.active {
            background: rgba(255,255,255,0.2);
            border: none;
            color: white;
        }

    </style>
</head>

<body>

<!-- SPINNER -->
<div id="loadingOverlay">
    <div class="spinner-border text-light"></div>
</div>

<div class="container mt-5">
    <div class="glass-card">

        <h3 class="mb-4">TABLE COMPARISON</h3>

        <!-- FORM -->
        <form th:action="@{/compare}"
              th:object="${request}"
              method="post"
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
        <div th:if="${diff != null}" class="mb-3">
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

        <div th:if="${diff != null}">

            <!-- TABS -->
            <ul class="nav nav-tabs mb-3">
                <li class="nav-item">
                    <button class="nav-link active"
                            data-bs-toggle="tab"
                            data-bs-target="#modifiedTab">
                        Modified
                    </button>
                </li>
                <li class="nav-item">
                    <button class="nav-link"
                            data-bs-toggle="tab"
                            data-bs-target="#onlyTestTab">
                        Only in Test
                    </button>
                </li>
                <li class="nav-item">
                    <button class="nav-link"
                            data-bs-toggle="tab"
                            data-bs-target="#onlyProdTab">
                        Only in Prod
                    </button>
                </li>
            </ul>

            <div class="tab-content">

                <!-- ================= MODIFIED TAB ================= -->
                <div class="tab-pane fade show active" id="modifiedTab">

                    <table>
                        <thead>
                        <tr>
                            <th>Primary Key</th>
                            <th>Changed Columns</th>
                            <th>Action</th>
                        </tr>
                        </thead>
                        <tbody>

                        <th:block th:each="row,iterStat : ${diff.modifiedRows}">

                            <tr>
                                <td th:text="${#strings.listJoin(row.primaryKey.values(),' | ')}"></td>
                                <td th:text="${row.changedColumns.size()}"></td>
                                <td>
                                    <button type="button"
                                            class="expand-btn"
                                            th:attr="onclick='toggleDetails(\'mod-' + ${iterStat.index} + '\')'">
                                        Expand
                                    </button>
                                </td>
                            </tr>

                            <tr th:attr="id='mod-' + ${iterStat.index}"
                                style="display:none;">
                                <td colspan="3">
                                    <div class="glass-card mt-2">
                                        <table>
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

                        </th:block>

                        </tbody>
                    </table>
                </div>

                <!-- ================= ONLY IN TEST TAB ================= -->
                <div class="tab-pane fade" id="onlyTestTab">

                    <table>
                        <thead>
                        <tr>
                            <th>Primary Key</th>
                            <th>Action</th>
                        </tr>
                        </thead>
                        <tbody>

                        <th:block th:each="row,iterStat : ${diff.onlyInTestRows}">

                            <tr>
                                <td th:text="${#strings.listJoin(row.primaryKey.values(),' | ')}"></td>
                                <td>
                                    <button type="button"
                                            class="expand-btn"
                                            th:attr="onclick='toggleDetails(\'test-' + ${iterStat.index} + '\')'">
                                        Expand
                                    </button>
                                </td>
                            </tr>

                            <tr th:attr="id='test-' + ${iterStat.index}"
                                style="display:none;">
                                <td colspan="2">
                                    <div class="glass-card mt-2">
                                        <table>
                                            <thead>
                                            <tr>
                                                <th>Column</th>
                                                <th>Value</th>
                                            </tr>
                                            </thead>
                                            <tbody>
                                            <tr th:each="entry : ${row.testRow}">
                                                <td th:text="${entry.key}"></td>
                                                <td th:text="${entry.value}"></td>
                                            </tr>
                                            </tbody>
                                        </table>
                                    </div>
                                </td>
                            </tr>

                        </th:block>

                        </tbody>
                    </table>
                </div>

                <!-- ================= ONLY IN PROD TAB ================= -->
                <div class="tab-pane fade" id="onlyProdTab">

                    <table>
                        <thead>
                        <tr>
                            <th>Primary Key</th>
                            <th>Action</th>
                        </tr>
                        </thead>
                        <tbody>

                        <th:block th:each="row,iterStat : ${diff.onlyInProdRows}">

                            <tr>
                                <td th:text="${#strings.listJoin(row.primaryKey.values(),' | ')}"></td>
                                <td>
                                    <button type="button"
                                            class="expand-btn"
                                            th:attr="onclick='toggleDetails(\'prod-' + ${iterStat.index} + '\')'">
                                        Expand
                                    </button>
                                </td>
                            </tr>

                            <tr th:attr="id='prod-' + ${iterStat.index}"
                                style="display:none;">
                                <td colspan="2">
                                    <div class="glass-card mt-2">
                                        <table>
                                            <thead>
                                            <tr>
                                                <th>Column</th>
                                                <th>Value</th>
                                            </tr>
                                            </thead>
                                            <tbody>
                                            <tr th:each="entry : ${row.prodRow}">
                                                <td th:text="${entry.key}"></td>
                                                <td th:text="${entry.value}"></td>
                                            </tr>
                                            </tbody>
                                        </table>
                                    </div>
                                </td>
                            </tr>

                        </th:block>

                        </tbody>
                    </table>

                </div>

            </div>
        </div>

    </div>
</div>

<script>
function showLoading() {
    document.getElementById("loadingOverlay").style.display = "flex";
}

function toggleDetails(id) {
    let row = document.getElementById(id);
    row.style.display = (row.style.display === "none") ? "table-row" : "none";
}
</script>

</body>
</html>
