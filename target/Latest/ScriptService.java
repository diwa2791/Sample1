<div class="mt-3 text-center">
    <form method="post" th:action="@{/compare}">
        <input type="hidden" th:field="*{tableName}" />
        <input type="hidden" name="modPage" th:value="${modPage - 1}" />
        <button class="btn btn-light btn-sm"
                th:if="${modPage > 0}">
            Previous
        </button>
    </form>

    <span class="mx-3">
        Page <span th:text="${modPage + 1}"></span>
    </span>

    <form method="post" th:action="@{/compare}">
        <input type="hidden" th:field="*{tableName}" />
        <input type="hidden" name="modPage" th:value="${modPage + 1}" />
        <button class="btn btn-light btn-sm"
                th:if="${(modPage + 1) * pageSize < diff.modifiedRows.size()}">
            Next
        </button>
    </form>
</div>





    <th:block th:each="row,iterStat :
    ${#lists.subList(
        diff.modifiedRows,
        modPage * pageSize,
        T(java.lang.Math).min((modPage + 1) * pageSize, diff.modifiedRows.size())
    )}">
