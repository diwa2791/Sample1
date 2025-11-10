@Configuration
public class DbConfig {
  @Bean("sit") public JdbcTemplate sit(@Value("${db.sit.url}") String url,
                                       @Value("${db.sit.user}") String user,
                                       @Value("${db.sit.pass}") String pass) {
    DriverManagerDataSource ds = new DriverManagerDataSource(url, user, pass);
    return new JdbcTemplate(ds);
  }
  @Bean("qa") public JdbcTemplate qa(@Value("${db.qa.url}") String url,
                                     @Value("${db.qa.user}") String user,
                                     @Value("${db.qa.pass}") String pass) {
    DriverManagerDataSource ds = new DriverManagerDataSource(url, user, pass);
    return new JdbcTemplate(ds);
  }
}
