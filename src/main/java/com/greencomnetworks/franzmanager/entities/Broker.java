package com.greencomnetworks.franzmanager.entities;


import java.util.Map;

public class Broker {
    public final String id;
    public final String host;
    public final Integer port;
    public final Float bytesIn;
    public final Float bytesOut;
    public final Map<String, String> configurations;
    public final State state;

    public Broker(String id, String host, Integer port, Map<String, String> configurations, Float bytesIn, Float bytesOut, State state) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.configurations = configurations;
        this.bytesIn = bytesIn;
        this.bytesOut = bytesOut;
        this.state = state;
    }

    @Override
    public String toString() {
        return "Cluster: " + id + ", " + host + ":" + port + ", " + configurations.toString();
    }

    public enum State {
        OK,
        UNSTABLE,
        BROKEN
    }
}
