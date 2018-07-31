package com.greencomnetworks.franzmanager.entities;

import java.time.ZonedDateTime;

public class ConsumerOffsetRecord {
    private String group;
    private String topic;
    private Integer partition;
    private Long offset;
    private ZonedDateTime timestamp;
    private ZonedDateTime commitTimestamp;
    private ZonedDateTime expireTimestamp;


    public String getGroup() {
        return group;
    }
    public void setGroup(String group) {
        this.group = group;
    }
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
    public ZonedDateTime getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(ZonedDateTime timestamp) {
        this.timestamp = timestamp;
    }


    public ZonedDateTime getCommitTimestamp() {
        return commitTimestamp;
    }
    public void setCommitTimestamp(ZonedDateTime commitTimestamp) {
        this.commitTimestamp = commitTimestamp;
    }
    public ZonedDateTime getExpireTimestamp() {
        return expireTimestamp;
    }
    public void setExpireTimestamp(ZonedDateTime expireTimestamp) {
        this.expireTimestamp = expireTimestamp;
    }
}
