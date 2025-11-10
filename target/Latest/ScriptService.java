@Service
public class ScriptService {
  private final AzureGitClient git;
  private final JdbcTemplate sit;
  private final JdbcTemplate qa;

  public ScriptService(AzureGitClient git,
                       @Qualifier("sit") JdbcTemplate sit,
                       @Qualifier("qa")  JdbcTemplate qa) {
    this.git = git; this.sit = sit; this.qa = qa;
  }

  public List<Map<String,Object>> listFiles(String commitId) {
    return git.listFiles(commitId);
  }

  public Map<String,Object> getFile(String commitId, String path) {
    String content = git.getFile(commitId, path);
    return Map.of("path", path, "content", content==null?"":content);
  }

  public String execute(String commitId, String path, List<String> dbs) {
    String sql = git.getFile(commitId, path);
    if (sql == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found");
    // naive split on ';' lines; adjust for your RDBMS & script format
    String[] stmts = sql.split(";(\\s*\\r?\\n)");
    StringBuilder log = new StringBuilder();
    for (String target : dbs) {
      JdbcTemplate jt = switch (target.toUpperCase()) {
        case "SIT" -> sit;
        case "QA"  -> qa;
        default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown DB "+target);
      };
      log.append("=== ").append(target).append(" ===\n");
      for (String s : stmts) {
        String q = s.trim();
        if (q.isEmpty()) continue;
        jt.execute(q);
        log.append("OK: ").append(q.length()>60?q.substring(0,60)+"…":q).append("\n");
      }
    }
    return log.toString();
  }

  public String promote(String commitId, String path, String prodFolder) {
    return git.promoteToProd(commitId, path, prodFolder);
  }
}
