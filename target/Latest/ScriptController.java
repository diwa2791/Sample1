function buildExpandHtml(data) {

    let html = `
        <table class="inner-table">
            <thead>
                <tr>
                    <th>Column</th>
                    <th>Test</th>
                    <th>Prod</th>
                </tr>
            </thead>
            <tbody>
    `;

    const status = data.status || '';

    // =========================
    // MODIFIED ROW
    // =========================
    if (status === "DIFFERENT") {

        const cols = data.changedColumns || [];

        cols.forEach(col => {
            html += `
                <tr>
                    <td>${col.columnName ?? ''}</td>
                    <td class="test-value">${col.testHighlighted ?? ''}</td>
                    <td class="prod-value">${col.prodHighlighted ?? ''}</td>
                </tr>
            `;
        });

        if (cols.length === 0) {
            html += `
                <tr>
                    <td colspan="3" style="text-align:center;opacity:0.7">
                        No differences found
                    </td>
                </tr>
            `;
        }
    }

    // =========================
    // ONLY IN PROD
    // =========================
    else if (status === "ONLY_IN_PROD") {

        const row = data.prodRow || {};

        Object.keys(row).forEach(key => {
            html += `
                <tr>
                    <td>${key}</td>
                    <td></td>
                    <td class="prod-value">${row[key] ?? ''}</td>
                </tr>
            `;
        });
    }

    // =========================
    // ONLY IN TEST
    // =========================
    else if (status === "ONLY_IN_TEST") {

        const row = data.testRow || {};

        Object.keys(row).forEach(key => {
            html += `
                <tr>
                    <td>${key}</td>
                    <td class="test-value">${row[key] ?? ''}</td>
                    <td></td>
                </tr>
            `;
        });
    }

    html += `
            </tbody>
        </table>
    `;

    return html;
}
