package com.greencomnetworks.franzmanager.entities;

public class Partition {
    private String topic;
    private int partition;
    private long beginningOffset;
    private long endOffset;
    private int leader;
    private int[] replicas;
    private int[] inSyncReplicas;
    private int[] offlineReplicas;

    public Partition(String topic, int partition, long beginningOffset, long endOffset, int leader, int[] replicas, int[] inSyncReplicas, int[] offlineReplicas) {
        this.topic = topic;
        this.partition = partition;
        this.beginningOffset = beginningOffset;
        this.endOffset = endOffset;
        this.leader = leader;
        this.replicas = replicas;
        this.inSyncReplicas = inSyncReplicas;
        this.offlineReplicas = offlineReplicas;
    }

    public int getLeader() {
        return leader;
    }

    public void setLeader(int leader) {
        this.leader = leader;
    }

    public int[] getReplicas() {
        return replicas;
    }

    public void setReplicas(int[] replicas) {
        this.replicas = replicas;
    }

    public int[] getInSyncReplicas() {
        return inSyncReplicas;
    }

    public void setInSyncReplicas(int[] inSyncReplicas) {
        this.inSyncReplicas = inSyncReplicas;
    }

    public int[] getOfflineReplicas() {
        return offlineReplicas;
    }

    public void setOfflineReplicas(int[] offlineReplicas) {
        this.offlineReplicas = offlineReplicas;
    }

    public String getTopic() {
        return topic;
    }
    public void setTopic(String topic) {
        this.topic = topic;
    }
    public int getPartition() {
        return partition;
    }
    public void setPartition(int partition) {
        this.partition = partition;
    }
    public long getEndOffset() {
        return endOffset;
    }
    public void setEndOffset(long endOffset) {
        this.endOffset = endOffset;
    }
    public long getBeginningOffset() {
        return beginningOffset;
    }
    public void setBeginningOffset(long beginningOffset) {
        this.beginningOffset = beginningOffset;
    }
}
