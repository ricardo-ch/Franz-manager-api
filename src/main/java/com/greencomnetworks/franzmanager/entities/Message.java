package com.greencomnetworks.franzmanager.entities;

import org.apache.kafka.common.header.Headers;

public class Message {
    public final Headers headers;
    public final String message;
    public final String key;
    public final Integer partition;
    public final Long offset;
    public final Long timestamp;

    public Message(String message, String key, Integer partition, Long offset, Long timestamp, Headers headers) {
        this.message = message;
        this.key = key;
        this.partition = partition;
        this.offset = offset;
        this.timestamp = timestamp;
        this.headers = headers;
    }

    @Override
    public String toString() {
        return "Message: " + message + ", key:" + key + ", partition:" + partition + ", offset:" + offset + ", timestamp:" + timestamp;
    }
}
