package com.example.k8sui.service;

import com.example.k8sui.model.Summary;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentList;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Kubernetes service wrapper using Fabric8 client.
 *
 * Note: This version uses Jackson's ObjectMapper for conversions to avoid
 * dependence on Fabric8 methods that may not exist in older versions.
 */
@Service
public class K8sService {

    private final KubernetesClient client;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public K8sService(KubernetesClient client) {
        this.client = client;
    }

    public KubernetesClient getClient() {
        return this.client;
    }

    /** List all namespaces in the cluster */
    public List<String> listNamespaces() {
        return client.namespaces().list().getItems().stream()
                .map(ns -> ns.getMetadata().getName())
                .collect(Collectors.toList());
    }

    /** List pods in a specific namespace (or all if null) */
    public List<Pod> listPods(String namespace) {
        if (namespace == null || namespace.isBlank() || "all".equalsIgnoreCase(namespace)) {
            return client.pods().inAnyNamespace().list().getItems();
        }
        return client.pods().inNamespace(namespace).list().getItems();
    }

    /** List all pods across namespaces. */
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

            Object result = client.pods().inNamespace(namespace).withName(podName).delete();

            if (result instanceof Boolean) {
                return (Boolean) result;
            } else if (result instanceof List<?>) {
                List<?> list = (List<?>) result;
                return !list.isEmpty();
            } else {
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
        // This method assumes default namespace context; if you need namespace-specific, add param
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
            return client.pods().inNamespace(namespace).withName(podName).getLog();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Get a single Deployment object (as a Map for JSON response)
     */
    public Map<String, Object> getDeployment(String namespace, String name) {
        Deployment dep = client.apps().deployments()
                .inNamespace(namespace)
                .withName(name)
                .get();

        if (dep == null) {
            return null;
        }

        try {
            return MAPPER.convertValue(dep, Map.class);
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "Conversion failed");
            err.put("message", e.getMessage());
            return err;
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
                            if (r.contains("containercreating") || r.contains("creating") || r.contains("create")) {
                                hasCreating = true;
                            }
                        }
                    }
                }
            }

            if ("Running".equalsIgnoreCase(phase) && totalContainers == 1 && readyContainers == 1) {
                runningCount++;
            }

            if ("Pending".equalsIgnoreCase(phase) || hasCreating) {
                pendingCount++;
            }

            if ("Failed".equalsIgnoreCase(phase) || hasCrashLoop || totalRestarts > 0) {
                abnormalCount++;
            }
        }

        int servicesZeroPods = 0;
        List<io.fabric8.kubernetes.api.model.Service> services = client.services().inAnyNamespace().list().getItems();
        for (io.fabric8.kubernetes.api.model.Service svc : services) {
            if (svc.getSpec() == null) continue;
            Map<String, String> selector = svc.getSpec().getSelector();
            if (selector == null || selector.isEmpty()) continue;
            String ns = (svc.getMetadata() != null && svc.getMetadata().getNamespace() != null)
                    ? svc.getMetadata().getNamespace()
                    : "default";

            List<Pod> matched = client.pods().inNamespace(ns).withLabels(selector).list().getItems();
            if (matched == null || matched.isEmpty()) {
                servicesZeroPods++;
            }
        }

        return new Summary(runningCount, pendingCount, abnormalCount, servicesZeroPods);
    }

    public boolean updateConfigMapKey(String namespace, String configMapName, String key, String value) {
        try {
            if (namespace == null || namespace.isBlank()) namespace = "default";
            ConfigMap cm = client.configMaps().inNamespace(namespace).withName(configMapName).get();
            if (cm == null) {
                ConfigMap newCm = new ConfigMap();
                newCm.setMetadata(new ObjectMeta());
                newCm.getMetadata().setName(configMapName);
                newCm.setData(new java.util.HashMap<>());
                newCm.getData().put(key, value);
                client.configMaps().inNamespace(namespace).create(newCm);
                return true;
            } else {
                client.configMaps().inNamespace(namespace).withName(configMapName).edit(existing -> {
                    if (existing.getData() == null) existing.setData(new java.util.HashMap<>());
                    existing.getData().put(key, value);
                    return existing;
                });
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    public boolean scaleDeployment(String namespace, String deploymentName, int replicas) {
        try {
            if (namespace == null || namespace.isBlank()) namespace = "default";
            Deployment dep = client.apps().deployments().inNamespace(namespace).withName(deploymentName).get();
            if (dep == null) return false;
            try {
                client.apps().deployments().inNamespace(namespace).withName(deploymentName).scale(replicas);
                return true;
            } catch (NoSuchMethodError | UnsupportedOperationException ex) {
                client.apps().deployments().inNamespace(namespace).withName(deploymentName).edit(d -> {
                    d.getSpec().setReplicas(replicas);
                    return d;
                });
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    public Map<String, String> getConfigMapData(String namespace, String name) {
        try {
            if (namespace == null || namespace.isBlank()) namespace = "default";
            ConfigMap cm = client.configMaps()
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            if (cm == null || cm.getData() == null) {
                return Map.of();
            }
            return cm.getData();
        } catch (Exception e) {
            return Map.of();
        }
    }

    public Map<String, Object> getConfigMapFull(String namespace, String name) {
        if (namespace == null || namespace.isBlank()) namespace = "default";
        ConfigMap cm = client.configMaps().inNamespace(namespace).withName(name).get();
        if (cm == null) return null;
        return Map.of(
                "metadata", cm.getMetadata(),
                "data", cm.getData() != null ? cm.getData() : Map.of()
        );
    }

    /**
     * Replace entire ConfigMap data atomically.
     * - Creates a timestamped backup copy first (namespace, name-backup-TIMESTAMP)
     * - If baseResourceVersion provided, checks for match and returns false on mismatch
     */
    public boolean replaceConfigMapFull(String namespace, String name, Map<String, String> newData, String baseResourceVersion) {
        if (namespace == null || namespace.isBlank()) namespace = "default";
        try {
            ConfigMap existing = client.configMaps().inNamespace(namespace).withName(name).get();
            if (existing == null) {
                ConfigMap cm = new ConfigMap();
                ObjectMeta meta = new ObjectMeta();
                meta.setName(name);
                cm.setMetadata(meta);
                cm.setData(newData != null ? newData : Map.of());
                client.configMaps().inNamespace(namespace).create(cm);
                return true;
            }

            if (baseResourceVersion != null && !baseResourceVersion.isBlank()) {
                String rv = existing.getMetadata().getResourceVersion();
                if (!baseResourceVersion.equals(rv)) {
                    return false;
                }
            }

            try {
                String ts = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(ZonedDateTime.now());
                String backupName = name + "-backup-" + ts;
                ConfigMap backup = new ConfigMap();
                ObjectMeta bm = new ObjectMeta();
                bm.setName(backupName);
                bm.setNamespace(namespace);
                backup.setMetadata(bm);
                backup.setData(existing.getData() == null ? Map.of() : existing.getData());
                client.configMaps().inNamespace(namespace).create(backup);
            } catch (Exception ex) {
                // backup failure shouldn't block replace
            }

            client.configMaps().inNamespace(namespace).withName(name).edit(cm -> {
                cm.setData(newData != null ? newData : Map.of());
                return cm;
            });

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public List<Map<String, Object>> listDeployments(String namespace) {
        DeploymentList list = client.apps().deployments()
                .inNamespace(namespace)
                .list();

        if (list == null || list.getItems() == null) {
            return Collections.emptyList();
        }

        return list.getItems().stream()
                .map(dep -> Map.of(
                        "name", dep.getMetadata().getName(),
                        "replicas", dep.getSpec() != null ? dep.getSpec().getReplicas() : null,
                        "availableReplicas", dep.getStatus() != null ? dep.getStatus().getAvailableReplicas() : null,
                        "labels", dep.getMetadata().getLabels()
                ))
                .collect(Collectors.toList());
    }
}
