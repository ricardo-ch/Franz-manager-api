package com.greencomnetworks.franzmanager.entities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;

public class Metric {
    public final String type;
    public final String name;
    public final String topic;
    public final HashMap<String, Object> metrics;

    @JsonCreator
    public Metric(String type, String name, String topic, HashMap<String, Object> metrics) {
        this.type = type;
        this.name = name;
        this.topic = topic;
        this.metrics = metrics;
    }

    @Override
    public String toString() {
        return "Metric " + type + " / " + name;
    }
}
