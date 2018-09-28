package com.greencomnetworks.franzmanager.entities;


import java.util.List;
import java.util.Map;

public class Topic {
    public final String id;
    public List<Partition> partitions;
    public final Map<String, String> configurations;

    public Topic(String id, List<Partition> partitions, Map<String, String> configurations) {
        this.id = id;
        this.partitions = partitions;
        this.configurations = configurations;
    }

    public Topic(String id) {
        this.id = id;
        this.partitions = null;
        this.configurations = null;
    }

    @Override
    public String toString() {
        return "Topic: " + id;
    }
}