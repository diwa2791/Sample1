package com.example.k8sui.service;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RequestLoggingFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        if (req instanceof HttpServletRequest) {
            HttpServletRequest r = (HttpServletRequest) req;
            log.info("Incoming request: {} {}", r.getMethod(), r.getRequestURI());
        }
        chain.doFilter(req, res);
    }
}