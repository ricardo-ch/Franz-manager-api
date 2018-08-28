package com.greencomnetworks.franzmanager.entities;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.HashMap;

public class Metric {
    public final String type;
    public final String name;
    public final int brokerId;
    public final HashMap<String, Object> metrics;

    @JsonCreator
    public Metric(String type, String name, int brokerId, HashMap<String, Object> metrics) {
        this.type = type;
        this.name = name;
        this.brokerId = brokerId;
        this.metrics = metrics;
    }

    @Override
    public String toString() {
        return "Metric " + type + " / " + name;
    }
}
