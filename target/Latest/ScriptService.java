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

<div class="container mt-5">
    <div class="glass-card">

        <h3 class="mb-4">TABLE COMPARISON</h3>

        <!-- FORM -->
        <form th:action="@{/compare}" th:object="${request}" method="post">
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

        <!-- TABS -->
        <div th:if="${diff != null}">

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

                <!-- MODIFIED TAB -->
                <div class="tab-pane fade show active" id="modifiedTab">

                    <table>
                        <thead>
                        <tr>
                            <th>Primary Key</th>
                            <th>Changed Columns</th>
                        </tr>
                        </thead>
                        <tbody>
                        <tr th:each="row : ${diff.modifiedRows}">
                            <td th:text="${#strings.listJoin(row.primaryKey.values(),' | ')}"></td>
                            <td th:text="${row.changedColumns.size()}"></td>
                        </tr>
                        </tbody>
                    </table>

                </div>

                <!-- ONLY TEST TAB -->
                <div class="tab-pane fade" id="onlyTestTab">

                    <table>
                        <thead>
                        <tr>
                            <th>Primary Key</th>
                        </tr>
                        </thead>
                        <tbody>
                        <tr th:each="row : ${diff.onlyInTestRows}">
                            <td th:text="${#strings.listJoin(row.primaryKey.values(),' | ')}"></td>
                        </tr>
                        </tbody>
                    </table>

                </div>

                <!-- ONLY PROD TAB -->
                <div class="tab-pane fade" id="onlyProdTab">

                    <table>
                        <thead>
                        <tr>
                            <th>Primary Key</th>
                        </tr>
                        </thead>
                        <tbody>
                        <tr th:each="row : ${diff.onlyInProdRows}">
                            <td th:text="${#strings.listJoin(row.primaryKey.values(),' | ')}"></td>
                        </tr>
                        </tbody>
                    </table>

                </div>

            </div>

        </div>

    </div>
</div>

</body>
</html>
