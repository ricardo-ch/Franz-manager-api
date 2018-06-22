package com.greencomnetworks.franzmanager.entities;

public class Cluster {
    public String name;
    public String brokersConnectString;
    public String jmxConnectString;

    public Cluster(){}

    public Cluster(String name, String brokersConnectString, String jmxConnectString) {
        this.name = name;
        this.brokersConnectString = brokersConnectString;
        this.jmxConnectString = jmxConnectString;
    }

    @Override
    public String toString() {
        return "Cluster: " + this.name;
    }
}
