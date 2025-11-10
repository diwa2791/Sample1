package com.example.k8sui.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.k8sui.model.Summary;
import com.example.k8sui.service.K8sService;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.servlet.http.HttpServletRequest;

import io.fabric8.kubernetes.client.dsl.LogWatch;

import org.springframework.http.ResponseEntity;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

/**
 * REST Controller for Kubernetes dashboard operations.
 *
 * Provides endpoints for pods, configmaps, deployments, and summary data.
 * Uses K8sService (Fabric8 client) for all backend operations.
 */
@RestController
@RequestMapping("/api")
public class K8sController {

	private final K8sService k8sService;
    private final ObjectMapper mapper = new ObjectMapper();

    public K8sController(K8sService k8sService) {
        this.k8sService = k8sService;
    }

    @GetMapping("/summary")
    public ResponseEntity<?> getSummary(@RequestParam(required = false, defaultValue = "default") String namespace) {
        Summary s = k8sService.getSummary(namespace);
        return ResponseEntity.ok(s);
    }

    @GetMapping("/namespaces")
    public ResponseEntity<?> listNamespaces() {
        return ResponseEntity.ok(k8sService.listNamespaces());
    }

    @GetMapping("/pods")
    public ResponseEntity<?> listPods(@RequestParam(required = false, defaultValue = "default") String namespace) {
        
    	
    	return ResponseEntity.ok(k8sService.listPods(namespace));
    }



    @GetMapping("/services/zero")
    public ResponseEntity<?> listServicesZero(@RequestParam(required = false, defaultValue = "default") String namespace) {
        return ResponseEntity.ok(k8sService.listServicesWithZeroPods(namespace));
    }
    
    @GetMapping("/services")
    public ResponseEntity<?> listServices(
        @RequestParam(required = false, defaultValue = "default") String namespace) {
        return ResponseEntity.ok(k8sService.listServices(namespace));
    }


    // ConfigMap endpoints (already expected by the UI)
    @GetMapping("/configmap/{name}/full")
    public ResponseEntity<?> getConfigMapFull(@RequestParam(defaultValue = "default") String namespace,
                                              @PathVariable String name) {
        var cm = k8sService.getConfigMapFull(namespace, name);
        if (cm == null) return ResponseEntity.status(404).body(Map.of("error","ConfigMap not found"));
        return ResponseEntity.ok(cm);
    }

    @PutMapping("/configmap/{name}/full")
    public ResponseEntity<?> replaceConfigMapFull(@RequestParam(defaultValue = "default") String namespace,
                                                  @PathVariable String name,
                                                  @RequestBody Map<String, String> newData,
                                                  @RequestParam(required = false) String resourceVersion) {
        boolean ok = k8sService.replaceConfigMapFull(namespace, name, newData, resourceVersion);
        return ok ? ResponseEntity.ok(Map.of("status","updated")) :
                     ResponseEntity.status(409).body(Map.of("error","Resource version mismatch or failed update"));
    }

    // DEPLOYMENTS

    // List deployments (used by replica modal)
    @GetMapping("/deployments")
    public ResponseEntity<?> listDeployments(@RequestParam(required = false, defaultValue = "default") String namespace) {
        List<Map<String,Object>> list = k8sService.listDeployments(namespace);
        return ResponseEntity.ok(list);
    }

    // Get a specific deployment
    @GetMapping("/deployments/{namespace}/{name}")
    public ResponseEntity<?> getDeployment(@PathVariable String namespace, @PathVariable String name) {
        var dep = k8sService.getDeployment(namespace, name);
        if (dep == null) return ResponseEntity.status(404).body(Map.of("error","Deployment not found"));
        return ResponseEntity.ok(dep);
    }

    // Replace deployment (save from editor)
    @PutMapping("/deployments/{namespace}/{name}")
    public ResponseEntity<?> replaceDeployment(@PathVariable String namespace, @PathVariable String name,
                                               @RequestBody Map<String, Object> body) {
        try {
            KubernetesClient client = k8sService.getClient();
            Deployment updated = mapper.convertValue(body, Deployment.class);
            if (updated.getMetadata() == null) updated.setMetadata(new io.fabric8.kubernetes.api.model.ObjectMeta());
            updated.getMetadata().setName(name);
            if (updated.getMetadata().getNamespace() == null || updated.getMetadata().getNamespace().isBlank()) {
                updated.getMetadata().setNamespace(namespace);
            }
            Deployment result = client.apps().deployments().inNamespace(namespace).withName(name).replace(updated);
            if (result == null) {
                return ResponseEntity.status(500).body(Map.of("error","Replace returned null"));
            }
            return ResponseEntity.ok(Map.of("status","updated","name", result.getMetadata().getName()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error","Failed to replace deployment","detail",e.getMessage()));
        }
    }

    // Scale endpoint used by frontend (POST): /api/deployments/{namespace}/{name}/scale
    @PostMapping("/deployments/{namespace}/{name}/scale")
    public ResponseEntity<?> scaleDeployment(@PathVariable String namespace, @PathVariable String name,
                                             @RequestBody Map<String, Object> body) {
        Object replicasObj = body.get("replicas");
        if (replicasObj == null) return ResponseEntity.badRequest().body(Map.of("error","Missing replicas"));
        int replicas = (replicasObj instanceof Number) ? ((Number)replicasObj).intValue() : Integer.parseInt(replicasObj.toString());
        boolean ok = k8sService.scaleDeployment(namespace, name, replicas);
        return ok ? ResponseEntity.ok(Map.of("status","scaled","replicas",replicas)) :
                    ResponseEntity.status(404).body(Map.of("error","Deployment not found"));
    }

    
    @RequestMapping(path = "/api/**", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> apiFallback(HttpServletRequest req) {
        String uri = req.getRequestURI();
        String method = req.getMethod();
        return ResponseEntity.status(404).body(Map.of(
                "error", "No API handler for request",
                "method", method,
                "path", uri
        ));
    }
    
    @PostMapping("/pods/{namespace}/{name}/restart")
    public ResponseEntity<?> restartPod(
            @PathVariable String namespace,
            @PathVariable String name) {
        boolean ok = k8sService.restartPod(namespace, name);
        return ok ? ResponseEntity.ok(Map.of("status", "restarting", "pod", name))
                  : ResponseEntity.status(404).body(Map.of("error", "Pod not found"));
    }

    // Optional: if someone hits it with GET, return 405 instead of static 404
    @GetMapping("/pods/{namespace}/{name}/restart")
    public ResponseEntity<?> restartPodWrongMethod() {
        return ResponseEntity.status(405).body(Map.of("error","Use POST /api/pods/{ns}/{name}/restart"));
    }
    
    

    @GetMapping(value = "/pods/{namespace}/{name}/logs", produces = "text/plain")
    public ResponseEntity<String> getPodLogs(
            @PathVariable String namespace,
            @PathVariable String name,
            @RequestParam(required = false) String container,
            @RequestParam(required = false, defaultValue = "false") boolean previous,
            @RequestParam(required = false) Integer tailLines,
            @RequestParam(required = false) Integer sinceSeconds) {

        String logs = k8sService.getPodLogs(namespace, name);
        		//, container, previous, tailLines, sinceSeconds);
        return ResponseEntity.ok(logs == null ? "" : logs);
    }

}
