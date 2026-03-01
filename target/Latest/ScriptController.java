function buildExpandHtml(data) {

    const status = data.status || '';

    let html = `<div class="expand-scroll">`;

    // ========================
    // MODIFIED (keep existing)
    // ========================
    if (status === "DIFFERENT") {

        html += `
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

        html += `</tbody></table>`;
    }

    // ========================
    // ONLY IN PROD
    // ========================
    else if (status === "ONLY_IN_PROD") {

        const row = data.prodRow || {};
        const keys = Object.keys(row);

        html += `<table class="inner-table">`;

        // Header row (column names)
        html += `<tr>`;
        keys.forEach(k => {
            html += `<th>${k}</th>`;
        });
        html += `</tr>`;

        // Value row
        html += `<tr>`;
        keys.forEach(k => {
            html += `<td class="prod-value">${row[k] ?? ''}</td>`;
        });
        html += `</tr>`;

        html += `</table>`;
    }

    // ========================
    // ONLY IN TEST
    // ========================
    else if (status === "ONLY_IN_TEST") {

        const row = data.testRow || {};
        const keys = Object.keys(row);

        html += `<table class="inner-table">`;

        html += `<tr>`;
        keys.forEach(k => {
            html += `<th>${k}</th>`;
        });
        html += `</tr>`;

        html += `<tr>`;
        keys.forEach(k => {
            html += `<td class="test-value">${row[k] ?? ''}</td>`;
        });
        html += `</tr>`;

        html += `</table>`;
    }

    html += `</div>`;

    return html;
}

-------
.expand-scroll {
    overflow-x: auto;
    overflow-y: hidden;
    max-width: 100%;
    padding: 6px 0;
}

.expand-scroll table {
    min-width: max-content;
    border-collapse: collapse;
}

.inner-table th,
.inner-table td {
    padding: 6px 10px;
    white-space: nowrap;   /* prevents breaking */
}

.inner-table td {
    max-width: 600px;
    overflow: hidden;
    text-overflow: ellipsis;
}

.inner-table td:hover {
    white-space: normal;
}
    
