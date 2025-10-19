package com.example.k8sui.service;

import com.example.k8sui.model.Summary;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Kubernetes service wrapper using Fabric8 client.
 *
 * Note: we import org.springframework.stereotype.Service for the Spring annotation.
 * To avoid a name collision with io.fabric8.kubernetes.api.model.Service we refer
 * to that class by its fully qualified name where needed.
 */
@Service
public class K8sService {

    private final KubernetesClient client;

    public K8sService() {
        this.client = new KubernetesClientBuilder().build();
    }
    
    /** List all namespaces in the cluster */
    public List<String> listNamespaces() {
        return client.namespaces().list().getItems().stream()
                .map(ns -> ns.getMetadata().getName())
                .toList();
    }
    
    /** List pods in a specific namespace (or all if null) */
    public List<Pod> listPods(String namespace) {
        if (namespace == null || namespace.isBlank() || "all".equalsIgnoreCase(namespace)) {
            return client.pods().inAnyNamespace().list().getItems();
        }
        return client.pods().inNamespace(namespace).list().getItems();
    }


    /**
     * List all pods across namespaces.
     */
    public List<Pod> listPods() {
        return client.pods().inAnyNamespace().list().getItems();
    }

    /**
     * Restart a pod by namespace + name (deletes the pod; a controller will recreate it).
     * Returns true if deletion was acknowledged (pod existed & deletion attempted), false if not found.
     */
    public boolean restartPod(String namespace, String podName) {
        if (namespace == null || namespace.isBlank() || podName == null || podName.isBlank()) {
            return false;
        }

        try {
            Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
            if (pod == null) {
                return false; // not found
            }

            // The delete() call may return either Boolean or List<StatusDetails>
            Object result = client.pods().inNamespace(namespace).withName(podName).delete();

            if (result instanceof Boolean) {
                return (Boolean) result;
            } else if (result instanceof List<?>) {
                List<?> list = (List<?>) result;
                return !list.isEmpty(); // true if deletion details exist
            } else {
                // unknown return type, assume deletion attempted
                return true;
            }
        } catch (Exception e) {
            System.err.println("Error deleting pod: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get a ConfigMap by name in the current context (default namespace resolution applies).
     */
    public ConfigMap getConfigMap(String name) {
        return client.configMaps().withName(name).get();
    }

    /**
     * Update (or create data map) for a ConfigMap key.
     */
    public void updateConfigMap(String name, String key, String value) {
        client.configMaps().withName(name).edit(cm -> {
            if (cm.getData() == null) {
                cm.setData(new HashMap<>());
            }
            cm.getData().put(key, value);
            return cm;
        });
    }

    /**
     * Fetch logs for a pod in a specific namespace. Returns the logs string,
     * or a message starting with "ERROR:" if something went wrong / pod not found.
     */
    public String getPodLogs(String namespace, String podName) {
        if (namespace == null || namespace.isBlank() || podName == null || podName.isBlank()) {
            return "ERROR: namespace or podName missing";
        }
        try {
            Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
            if (pod == null) {
                return "ERROR: pod not found in namespace " + namespace;
            }
            // You can pass tailing/limit options if desired (client.pods().inNamespace(ns).withName(name).tailingLines(200).getLog())
            return client.pods().inNamespace(namespace).withName(podName).getLog();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Compute the dashboard summary:
     * 1) Running: pods with phase Running AND totalContainers==1 && readyContainers==1 (1/1)
     * 2) Pending: phase Pending OR any container waiting reason contains "ContainerCreating"/"creating"
     * 3) Abnormal: phase Failed OR any container showing CrashLoopBackOff OR totalRestarts > 0
     * 4) Services with zero pods: services that have a selector but match no pods in their namespace
     */
    public Summary getSummary(String namespace) {
        List<Pod> pods = listPods(namespace);

        int runningCount = 0;
        int pendingCount = 0;
        int abnormalCount = 0;

        for (Pod pod : pods) {
            String phase = "Unknown";
            if (pod.getStatus() != null && pod.getStatus().getPhase() != null) {
                phase = pod.getStatus().getPhase();
            }

            List<ContainerStatus> statuses = (pod.getStatus() != null) ? pod.getStatus().getContainerStatuses() : null;

            int totalContainers = 0;
            int readyContainers = 0;
            int totalRestarts = 0;
            boolean hasCrashLoop = false;
            boolean hasCreating = false;

            if (statuses != null) {
                totalContainers = statuses.size();
                for (ContainerStatus cs : statuses) {
                    if (cs == null) continue;
                    Boolean ready = cs.getReady();
                    if (ready != null && ready) readyContainers++;
                    Integer rc = cs.getRestartCount();
                    if (rc != null) totalRestarts += rc;

                    if (cs.getState() != null && cs.getState().getWaiting() != null) {
                        String waitingReason = cs.getState().getWaiting().getReason();
                        if (waitingReason != null) {
                            String r = waitingReason.toLowerCase();
                            if (r.contains("crashloop")) hasCrashLoop = true;
                            // common reason names may vary; check for creating or containercreating
                            if (r.contains("containercreating") || r.contains("creating") || r.contains("create")) {
                                hasCreating = true;
                            }
                        }
                    }
                }
            }

            // 1) Running: phase Running and 1/1 (single container ready)
            if ("Running".equalsIgnoreCase(phase) && totalContainers == 1 && readyContainers == 1) {
                runningCount++;
            }

            // 2) Pending: phase Pending or container creating
            if ("Pending".equalsIgnoreCase(phase) || hasCreating) {
                pendingCount++;
            }

            // 3) Abnormal: failed, crashloop, or any restarts > 0
            if ("Failed".equalsIgnoreCase(phase) || hasCrashLoop || totalRestarts > 0) {
                abnormalCount++;
            }
        }

        // 4) Services with zero pods
        int servicesZeroPods = 0;
        List<io.fabric8.kubernetes.api.model.Service> services = client.services().inAnyNamespace().list().getItems();
        for (io.fabric8.kubernetes.api.model.Service svc : services) {
            if (svc.getSpec() == null) continue;
            Map<String, String> selector = svc.getSpec().getSelector();
            if (selector == null || selector.isEmpty()) continue; // skip headless/no-selector services
            String ns = (svc.getMetadata() != null && svc.getMetadata().getNamespace() != null)
                    ? svc.getMetadata().getNamespace()
                    : "default";

            // find pods that match the selector in the same namespace
            List<Pod> matched = client.pods().inNamespace(ns).withLabels(selector).list().getItems();
            if (matched == null || matched.isEmpty()) {
                servicesZeroPods++;
            }
        }

        return new Summary(runningCount, pendingCount, abnormalCount, servicesZeroPods);
    }
}
