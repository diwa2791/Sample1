package com.example.k8sui.controller;



import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Redirect root and /pods to pods.html so relative asset paths resolve correctly.
 */
@Controller
public class SpaForwardController {

    // Redirect root to pods.html (so opening http://host:8080/ loads the dashboard)
    @GetMapping({"/", ""})
    public String redirectRoot() {
        return "redirect:/pods.html";
    }

    // Redirect /pods to pods.html (keeps browser URL clean, prevents relative asset 404)
    @GetMapping("/pods")
    public String redirectPods() {
        return "redirect:/pods.html";
    }

    // If you have client routes like /services, /deployments used by SPA, forward them:
    @GetMapping({"/services", "/deployments"})
    public String forwardSpaRoutes() {
        return "forward:/pods.html";
    }
}
