package com.example.k8sui.model;

public class PodInfo {
    private String name;
    private String status;
    private String namespace;
    private String age;

    public PodInfo() {}

    public PodInfo(String name, String status, String namespace, String age) {
        this.name = name;
        this.status = status;
        this.namespace = namespace;
        this.age = age;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }

    public String getAge() { return age; }
    public void setAge(String age) { this.age = age; }
}