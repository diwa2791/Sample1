package com.example.k8sui.main;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.example.k8sui.controller", "com.example.k8sui.service"})
public class K8sUiDashboardApplication {

    public static void main(String[] args) {
        SpringApplication.run(K8sUiDashboardApplication.class, args);
    }
}
