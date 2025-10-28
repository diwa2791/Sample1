package com.example.k8sui.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    
    @GetMapping(
    		  value = "/api/namespaces/{namespace}/pods/{name}/logs",
    		  produces = { MediaType.TEXT_PLAIN_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE }
    		)
    		public ResponseEntity<?> getPodLogs(
    		        @PathVariable String namespace,
    		        @PathVariable String name,
    		        @RequestParam(required = false) Integer lines,          // last N lines
    		        @RequestParam(required = false) Integer sinceSeconds,   // last N seconds
    		        @RequestParam(required = false) String container,       // container name
    		        @RequestParam(required = false, defaultValue = "false") boolean previous,
    		        @RequestParam(required = false, defaultValue = "false") boolean stream
    		) {
        try {
            var client = k8sService.getClient();
            var podRes = client.pods().inNamespace(namespace).withName(name);

            // Start as PrettyLoggable (non-generic in 7.x); both PodResource and inContainer(...) implement it.
            PrettyLoggable op = (container != null && !container.isBlank())
                    ? podRes.inContainer(container)
                    : podRes;

            // Add options by casting to the sibling capability interfaces.
            if (lines != null && lines > 0) {
                op = ((Tailable) op).tailingLines(lines);
            }
            if (sinceSeconds != null && sinceSeconds > 0) {
                op = ((Timeable) op).sinceSeconds(sinceSeconds);
            }
            if (previous) {
                op = ((Terminateable) op).terminated(); // previous container logs
            }
            // If you want timestamps and your version exposes it on Prettyable/withTimestamps():
            // op = ((Prettyable) op).usingTimestamps(); // comment out if not present in 7.0.0

            if (stream) {
                SseEmitter emitter = new SseEmitter(0L);
                var exec = Executors.newSingleThreadExecutor(r -> {
                    var t = new Thread(r, "log-stream-" + namespace + "-" + name + (container != null ? "-" + container : ""));
                    t.setDaemon(true); return t;
                });
                exec.submit(() -> streamWithWatchLog((Loggable) op, emitter, exec));
                return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(emitter);
            } else {
                String log = ((Loggable) op).getLog();
                return ResponseEntity.ok(log == null ? "" : log);
            }
        } catch (Exception e) {
            return ResponseEntity.status(404).body("ERROR: " + e.getMessage());
        }
    }

    private void streamWithWatchLog(
            Loggable op, SseEmitter emitter, java.util.concurrent.ExecutorService exec) {
        LogWatch watch = null;
        try (EmitterLineOutput out = new EmitterLineOutput(emitter)) {
            watch = (LogWatch) op.watchLog(out); // 7.x returns LogWatch
            out.await();
        } catch (Exception e) {
            safeCompleteWithError(emitter, e);
        } finally {
            if (watch != null) try { watch.close(); } catch (Exception ignored) {}
            try { emitter.complete(); } catch (Exception ignored) {}
            exec.shutdownNow();
        }
    }

    /** OutputStream -> SSE-per-line */
    static class EmitterLineOutput extends OutputStream implements AutoCloseable {
        private final SseEmitter emitter;
        private final StringBuilder buf = new StringBuilder(4096);
        private final Object lock = new Object();
        private final CountDownLatch done = new CountDownLatch(1);
        private volatile boolean closed = false;

        EmitterLineOutput(SseEmitter emitter) { this.emitter = emitter; }

        @Override public void write(int b) throws IOException { write(new byte[]{(byte) b}, 0, 1); }

        @Override public void write(byte[] b, int off, int len) throws IOException {
            if (closed) return;
            String s = new String(b, off, len, StandardCharsets.UTF_8);
            synchronized (lock) {
                buf.append(s);
                int idx;
                while ((idx = indexOfNewline(buf)) >= 0) {
                    String line = buf.substring(0, idx);
                    buf.delete(0, idx + 1);
                    emitter.send(SseEmitter.event().name("log").data(line));
                }
            }
        }

        private int indexOfNewline(StringBuilder sb) {
            for (int i = 0; i < sb.length(); i++) {
                char c = sb.charAt(i);
                if (c == '\n') return (i > 0 && sb.charAt(i - 1) == '\r') ? i - 1 : i;
            }
            return -1;
        }

        void await() { try { done.await(); } catch (InterruptedException ignored) {} }

        @Override public void close() {
            if (!closed) {
                closed = true;
                try {
                    synchronized (lock) {
                        if (buf.length() > 0) {
                            try { emitter.send(SseEmitter.event().name("log").data(buf.toString())); } catch (Exception ignored) {}
                            buf.setLength(0);
                        }
                    }
                } finally { done.countDown(); }
            }
        }
    }

    private static void safeCompleteWithError(SseEmitter emitter, Exception e) {
        try { emitter.send(SseEmitter.event().name("error").data(e.getMessage())); } catch (Exception ignored) {}
        try { emitter.completeWithError(e); } catch (Exception ignored) {}
    }


}
