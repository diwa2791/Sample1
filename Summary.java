package com.example.k8sui.model;

public class Summary {
    private int running;
    private int pending;
    private int abnormal;
    private int servicesWithZeroPods;

    public Summary() {}

    public Summary(int running, int pending, int abnormal, int servicesWithZeroPods) {
        this.running = running;
        this.pending = pending;
        this.abnormal = abnormal;
        this.servicesWithZeroPods = servicesWithZeroPods;
    }

    public int getRunning() { return running; }
    public void setRunning(int running) { this.running = running; }

    public int getPending() { return pending; }
    public void setPending(int pending) { this.pending = pending; }

    public int getAbnormal() { return abnormal; }
    public void setAbnormal(int abnormal) { this.abnormal = abnormal; }

    public int getServicesWithZeroPods() { return servicesWithZeroPods; }
    public void setServicesWithZeroPods(int servicesWithZeroPods) { this.servicesWithZeroPods = servicesWithZeroPods; }
}
