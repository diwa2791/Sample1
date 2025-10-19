package com.example.k8sui.controller;

import com.example.k8sui.model.PodInfo;
import com.example.k8sui.model.Summary;
import com.example.k8sui.service.K8sService;
import io.fabric8.kubernetes.api.model.Pod;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class K8sController {

    private final K8sService k8sService;

    public K8sController(K8sService k8sService) {
        this.k8sService = k8sService;
    }

    // initial page render (server-side)
    @GetMapping("/")
    public String pods(Model model) {
        List<Pod> pods = k8sService.listPods();
        List<PodInfo> infos = pods.stream().map(this::toPodInfo).collect(Collectors.toList());
        model.addAttribute("pods", infos);
        return "pods";
    }

   
    /**
     * Namespace-aware restart endpoint.
     * POST /restart/{podName}?namespace=foo
     * Returns JSON { "ok": true, "message": "..." } with appropriate HTTP status.
     */
    @PostMapping("/restart/{podName}")
    @ResponseBody
    public ResponseEntity<?> restartPod(
            @PathVariable String podName,
            @RequestParam(name = "namespace", required = false) String namespace) {

        if (namespace == null || namespace.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "namespace is required"));
        }

        boolean success = k8sService.restartPod(namespace, podName);
        if (success) {
            return ResponseEntity.ok(Map.of("ok", true, "message", "Pod restart requested"));
        } else {
            return ResponseEntity.status(404).body(Map.of("ok", false, "message", "Pod not found or restart failed"));
        }
    }
    
    /**
     * Namespace-aware logs page.
     * GET /logs/{podName}?namespace=foo
     * Renders the logs.html template with 'logs' and 'podName' (and namespace).
     */
    @GetMapping("/logs/{podName}")
    public String podLogs(
            @PathVariable String podName,
            @RequestParam(name = "namespace", required = false) String namespace,
            Model model) {

        if (namespace == null || namespace.isBlank()) {
            // default to "default" namespace if not provided, or you can redirect back to UI error
            namespace = "default";
        }

        String logs = k8sService.getPodLogs(namespace, podName);
        model.addAttribute("podName", podName);
        model.addAttribute("namespace", namespace);
        model.addAttribute("logs", logs);
        return "logs";
    }

    // helper: convert Kubernetes Pod -> PodInfo
    private PodInfo toPodInfo(Pod pod) {
        String name = pod.getMetadata().getName();
        String status = pod.getStatus() != null && pod.getStatus().getPhase() != null ? pod.getStatus().getPhase() : "Unknown";
        String namespace = pod.getMetadata().getNamespace();
        String creation = pod.getMetadata().getCreationTimestamp(); // ISO offset format
        String age = formatAge(creation);
        return new PodInfo(name, status, namespace, age);
    }

    // friendly age like "3d", "5h", "12m"
    private String formatAge(String isoTimestamp) {
        if (isoTimestamp == null) return "-";
        try {
            OffsetDateTime created = OffsetDateTime.parse(isoTimestamp);
            Duration d = Duration.between(created, OffsetDateTime.now());
            long days = d.toDays();
            if (days > 0) return days + "d";
            long hours = d.toHours();
            if (hours > 0) return hours + "h";
            long minutes = d.toMinutes();
            if (minutes > 0) return minutes + "m";
            long seconds = d.getSeconds();
            return seconds + "s";
        } catch (DateTimeParseException e) {
            return "-";
        }
    }
    
    @GetMapping("/api/namespaces")
    @ResponseBody
    public List<String> listNamespaces() {
        return k8sService.listNamespaces();
    }

    @GetMapping("/api/pods")
    @ResponseBody
    public List<PodInfo> podsApi(@RequestParam(required = false) String namespace) {
        List<Pod> pods = k8sService.listPods(namespace);
        return pods.stream().map(this::toPodInfo).toList();
    }

    @GetMapping("/api/summary")
    @ResponseBody
    public Summary getSummary(@RequestParam(required = false) String namespace) {
        return k8sService.getSummary(namespace);
    }

}
