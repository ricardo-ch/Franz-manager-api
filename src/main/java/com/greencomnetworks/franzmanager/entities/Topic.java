package com.greencomnetworks.franzmanager.entities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class Topic {
    public final String id;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public final Integer partitions;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public final Integer replications;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public final Map<String, String> configurations;

    @JsonCreator
    public Topic(@JsonProperty(value="id", required=true) String id,
                 @JsonProperty("partitions") Integer partitions,
                 @JsonProperty("replication") Integer replications,
                 @JsonProperty("configurations") Map<String, String> configurations) {
        this.id = id;
        this.partitions = partitions;
        this.replications = replications;
        this.configurations = configurations;
    }

    public Topic(String id) {
        this.id = id;
        this.partitions = null;
        this.replications = null;
        this.configurations = null;
    }

    @Override
    public String toString() {
        return "Topic: " + id;
    }
}
