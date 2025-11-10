@RestController
@RequestMapping("/api/scripts")
public class ScriptController {
  private final ScriptService svc;
  @Value("${repo.prodFolder}") String prodFolder;

  public ScriptController(ScriptService svc){ this.svc = svc; }

  @GetMapping("/{commitId}/files")
  public List<Map<String,Object>> files(@PathVariable String commitId) {
    return svc.listFiles(commitId);
  }

  @GetMapping("/{commitId}/file")
  public Map<String,Object> file(@PathVariable String commitId, @RequestParam String path) {
    return svc.getFile(commitId, path);
  }

  static record ExecReq(String path, List<String> dbs) {}
  @PostMapping("/{commitId}/execute")
  public ResponseEntity<String> exec(@PathVariable String commitId, @RequestBody ExecReq req) {
    if (req.path()==null || req.path().isBlank()) return ResponseEntity.badRequest().body("Missing path");
    if (req.dbs()==null || req.dbs().isEmpty())   return ResponseEntity.badRequest().body("Choose at least one DB");
    String log = svc.execute(commitId, req.path(), req.dbs());
    return ResponseEntity.ok(log);
  }

  static record PromoteReq(String path) {}
  @PostMapping("/{commitId}/promote")
  public ResponseEntity<String> promote(@PathVariable String commitId, @RequestBody PromoteReq req) {
    if (req.path()==null || req.path().isBlank()) return ResponseEntity.badRequest().body("Missing path");
    String r = svc.promote(commitId, req.path(), prodFolder);
    return ResponseEntity.ok("Promoted.\n"+r);
  }
}
