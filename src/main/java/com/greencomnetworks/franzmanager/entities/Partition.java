package com.greencomnetworks.franzmanager.entities;
import java.time.ZonedDateTime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.greencomnetworks.franzmanager.services.ZonedDateTimeConverter;

public class Partition {
    private static Gson gson = new GsonBuilder().serializeNulls().registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeConverter()).create();

    public Partition(String topic,Integer partition, Long offset) {
        this.topic = topic;
        this.partition = partition;
        this.offset = offset;
    }
    
    private String topic;
    private Integer partition;
    private Long offset;

    public String getTopic() {
        return topic;
    }
    public void setTopic(String topic) {
        this.topic = topic;
    }
    public Integer getPartition() {
        return partition;
    }
    public void setPartition(Integer partition) {
        this.partition = partition;
    }
    public Long getOffset() {
        return offset;
    }
    public void setOffset(Long offset) {
        this.offset = offset;
    }

    public String toString(){
        return gson.toJson(this);
    }

}
