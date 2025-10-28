package com.example.k8sui.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "k8s")
public class K8sProperties {
    private String mode;        // auto | manual
    private String apiServer;
    private String username;
    private String password;
    private String token;
    private boolean trustCerts = true;

    // getters and setters
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getApiServer() { return apiServer; }
    public void setApiServer(String apiServer) { this.apiServer = apiServer; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public boolean isTrustCerts() { return trustCerts; }
    public void setTrustCerts(boolean trustCerts) { this.trustCerts = trustCerts; }
}
