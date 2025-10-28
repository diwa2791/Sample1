package com.example.k8sui.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.k8sui.service.AuthScriptService;

@RestController
public class AuthScriptController {

    private final AuthScriptService authScriptService;

    public AuthScriptController(AuthScriptService authScriptService) {
        this.authScriptService = authScriptService;
    }

    /**
     * Runs the login script for the given namespace and waits for it to complete.
     * POST /api/run-login-script?namespace=default
     */
    @PostMapping("/api/run-login-script")
    public ResponseEntity<?> runLoginScript(@RequestParam String namespace) {
        try {
            AuthScriptService.ScriptResult r = authScriptService.runLoginScriptForNamespace(namespace);
            Map<String, Object> body = new HashMap<>();
            body.put("exitCode", r.exitCode);
            body.put("stdout", r.stdout);
            body.put("stderr", r.stderr);
            // 0 -> success
            if (r.exitCode == 0) return ResponseEntity.ok(body);
            return ResponseEntity.status(500).body(body);
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.badRequest().body(Map.of("error", iae.getMessage()));
        } catch (AuthScriptService.AuthScriptException e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
