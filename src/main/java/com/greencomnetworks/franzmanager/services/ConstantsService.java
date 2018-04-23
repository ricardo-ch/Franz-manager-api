package com.greencomnetworks.franzmanager.services;

import com.greencomnetworks.franzmanager.utils.FUtils;

public class ConstantsService {
    public static final String brokersList = FUtils.getOrElse(System.getenv("KAFKA_BROKERS"), "10.46.2.91:9092,10.46.2.92:9092,10.46.2.93:9092");
    public static final String[] brokersJmxUrl = FUtils.getOrElse(System.getenv("KAFKA_BROKERS_JMX"), "10.46.2.91:9997,10.46.2.92:9997,10.46.2.93:9997").split(",");
}
