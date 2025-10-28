package com.example.k8sui.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

//@RestController
public class ApiFallbackController {

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
}