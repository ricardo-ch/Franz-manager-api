package com.greencomnetworks.franzmanager.entities;

public class Message {
    public final String message;
    public final String key;
    public final Integer partition;
    public final Long offset;
    public final Long timestamp;

    public Message(String message, String key, Integer partition, Long offset, Long timestamp) {
        this.message = message;
        this.key = key;
        this.partition = partition;
        this.offset = offset;
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "Message: " + message + ", key:" + key + ", partition:" + partition + ", offset:" + offset + ", timestamp:" + timestamp;
    }
}
