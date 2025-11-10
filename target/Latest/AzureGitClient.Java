@Component
public class AzureGitClient {
  @Value("${azure.org}") String org;
  @Value("${azure.project}") String project;
  @Value("${azure.repo}") String repo;
  @Value("${azure.pat}") String pat;

  private HttpHeaders headers() {
    HttpHeaders h = new HttpHeaders();
    String basic = Base64.getEncoder().encodeToString((":"+pat).getBytes(StandardCharsets.UTF_8));
    h.set("Authorization","Basic "+basic);
    return h;
  }

  // List changed files for a commit
  public List<Map<String,Object>> listFiles(String commitId) {
    // Changes API: GET .../commits/{commitId}/changes?api-version=7.1
    String url = String.format(
      "https://dev.azure.com/%s/%s/_apis/git/repositories/%s/commits/%s/changes?api-version=7.1",
      org, project, repo, commitId
    );
    var res = new RestTemplate().exchange(url, HttpMethod.GET, new HttpEntity<>(headers()), Map.class).getBody();
    // Parse “changes” -> items with path & size (simplified)
    List<Map<String,Object>> out = new ArrayList<>();
    List<Map<String,Object>> changes = (List<Map<String,Object>>) res.get("changes");
    if (changes != null) {
      for (var c : changes) {
        Map i = (Map) c.get("item");
        if (i!=null && i.get("path")!=null) {
          var m = new LinkedHashMap<String,Object>();
          m.put("name", new File((String)i.get("path")).getName());
          m.put("path", (String)i.get("path"));
          m.put("size", c.get("changeType")); // Azure doesn’t return size here; optional
          m.put("folder", new File((String)i.get("path")).getParent());
          out.add(m);
        }
      }
    }
    return out;
  }

  // Get file content by commit + path
  public String getFile(String commitId, String path) {
    // Items API: GET .../items?path={path}&versionDescriptor.version={commitId}&includeContent=true
    String url = String.format(
      "https://dev.azure.com/%s/%s/_apis/git/repositories/%s/items?path=%s&versionType=commit&version=%s&includeContent=true&api-version=7.1",
      org, project, repo, UriUtils.encodePath(path, StandardCharsets.UTF_8), commitId
    );
    var res = new RestTemplate().exchange(url, HttpMethod.GET, new HttpEntity<>(headers()), Map.class).getBody();
    return (String) res.get("content");
  }

  // Promote: create a commit that writes the same content under prod folder
  public String promoteToProd(String commitId, String sourcePath, String prodFolder) {
    String content = getFile(commitId, sourcePath);
    String prodPath = prodFolder.endsWith("/") ? prodFolder + new File(sourcePath).getName()
                                               : prodFolder + "/" + new File(sourcePath).getName();

    // Create a push with one change (Add/Edit) using the Pushes API
    String pushesUrl = String.format(
      "https://dev.azure.com/%s/%s/_apis/git/repositories/%s/pushes?api-version=7.1",
      org, project, repo
    );
    Map<String,Object> change = Map.of(
      "changeType","edit",
      "item", Map.of("path", prodPath),
      "newContent", Map.of("content", content, "contentType","rawtext")
    );
    Map<String,Object> commit = Map.of("comment","Promote to prod: "+sourcePath+" from "+commitId,
                                       "changes", List.of(change));
    Map<String,Object> body = Map.of("refUpdates", List.of(Map.of("name","refs/heads/main", "oldObjectId","0000000000000000000000000000000000000000")),
                                     "commits", List.of(commit));
    var res = new RestTemplate().exchange(pushesUrl, HttpMethod.POST,
      new HttpEntity<>(body, headers()), Map.class).getBody();
    return String.valueOf(((Map)res.get("commits")).toString()); // or return push id
  }
}
