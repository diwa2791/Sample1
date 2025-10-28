package com.example.k8sui.config;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KubernetesClientConfig {

    private final K8sProperties props;

    public KubernetesClientConfig(K8sProperties props) {
        this.props = props;
    }

    @Bean(destroyMethod = "close")
    public KubernetesClient kubernetesClient() {
        if ("manual".equalsIgnoreCase(props.getMode())) {
            Config config;

            // Prefer token if provided
            if (props.getToken() != null && !props.getToken().isBlank()) {
                config = new ConfigBuilder()
                        .withMasterUrl(props.getApiServer())
                        .withOauthToken(props.getToken())
                        .withTrustCerts(props.isTrustCerts())
                        .build();
            } else {
                config = new ConfigBuilder()
                        .withMasterUrl(props.getApiServer())
                        .withUsername(props.getUsername())
                        .withPassword(props.getPassword())
                        .withTrustCerts(props.isTrustCerts())
                        .build();
            }

            System.out.println("🔗 [K8S] Connected using manual mode: " + props.getApiServer());
            return new KubernetesClientBuilder().withConfig(config).build();

        } else {
            // Auto: use default kubeconfig or in-cluster credentials
            System.out.println("🔗 [K8S] Using kubeconfig or in-cluster credentials (auto mode)");
            return new KubernetesClientBuilder().build();
        }
    }
}
