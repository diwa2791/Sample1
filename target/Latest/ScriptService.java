@GetMapping("/export")
public void export(
        @RequestParam String cacheId,
        @RequestParam(defaultValue = "ALL") String type,
        HttpServletResponse response) throws Exception {

    response.setBufferSize(8192);
    response.setContentType("text/csv");
    response.setHeader("Content-Disposition",
            "attachment; filename=db-compare-" + type + ".csv");

    PrintWriter writer = response.getWriter();

    service.exportCsv(cacheId, type, writer);
}


public void exportCsv(String cacheId, String type, PrintWriter writer) {

    TableDiffResponseDTO diff = cache.get(cacheId);

    writer.println("TYPE,PRIMARY_KEY,COLUMN,TEST_VALUE,PROD_VALUE");

    List<RowDiffDTO> rows;

    switch (type) {
        case "MODIFIED":
            rows = diff.getModifiedRows();
            break;
        case "ONLY_TEST":
            rows = diff.getOnlyInTestRows();
            break;
        case "ONLY_PROD":
            rows = diff.getOnlyInProdRows();
            break;
        default:
            rows = new ArrayList<>();
            rows.addAll(diff.getModifiedRows());
            rows.addAll(diff.getOnlyInTestRows());
            rows.addAll(diff.getOnlyInProdRows());
    }

    for (RowDiffDTO r : rows) {

        if (r.getChangedColumns() != null) {
            for (ColumnDiffDTO c : r.getChangedColumns()) {
                writer.printf(
                        "MODIFIED,%s,%s,%s,%s%n",
                        r.getPrimaryKey(),
                        c.getColumnName(),
                        safe(c.getTestValue()),
                        safe(c.getProdValue()));
            }
        }
    }
}

private String safe(Object v){
    return v==null?"":v.toString().replace(",", " ");
}


===========================================
    List<ColumnDiffDTO> diffs = new ArrayList<>();

for(String col : compareColumns){
   ...
   if(!Objects.equals(tVal,pVal)){
       diffs.add(cd);
   }
}

if(!diffs.isEmpty()){
    dto.setChangedColumns(diffs);
    modified.add(dto);   // ← ONLY ONCE
}


===============================================
@GetMapping("/result")
public String result(
        String cacheId,
        @RequestParam(defaultValue="modified") String tab,
        @RequestParam(defaultValue="0") int page,
        Model model) {

    int pageSize = 30;

    PagedResponse<RowDiffDTO> data =
            service.getPaged(cacheId, tab, page, pageSize);

    model.addAttribute("data", data);
    model.addAttribute("cacheId", cacheId);
    model.addAttribute("tab", tab);

    return "db-compare";
}

=================================================
    public PagedResponse<RowDiffDTO> getPaged(
        String cacheId,
        String tab,
        int page,
        int size) {

    List<RowDiffDTO> source;

    TableDiffResponseDTO diff = cache.get(cacheId);

    switch(tab){
        case "test": source = diff.getOnlyInTestRows(); break;
        case "prod": source = diff.getOnlyInProdRows(); break;
        default: source = diff.getModifiedRows();
    }

    int start = page * size;
    int end = Math.min(start + size, source.size());

    List<RowDiffDTO> content =
            start >= source.size()
                    ? List.of()
                    : source.subList(start, end);

    return new PagedResponse<>(
            content,
            page,
            size,
            source.size()
    );
}
