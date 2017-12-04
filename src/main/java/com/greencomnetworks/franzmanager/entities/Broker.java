package com.greencomnetworks.franzmanager.entities;


import java.util.Map;

public class Broker {
    public final String id;
    public final String host;
    public final Integer port;
    public final Map<String, String> configurations;

    public Broker(String id, String host, Integer port, Map<String, String> configurations) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.configurations = configurations;
    }

    @Override
    public String toString() {
        return "Cluster: " + id + ", " + host + ":" + port + ", " + configurations.toString();
    }
}
