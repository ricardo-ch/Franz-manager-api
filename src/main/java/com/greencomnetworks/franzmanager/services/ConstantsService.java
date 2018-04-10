package com.greencomnetworks.franzmanager.services;

import com.greencomnetworks.franzmanager.utils.FUtils;

public class ConstantsService {
    public static final String brokersList = FUtils.getOrElse(System.getenv("KAFKA_BROKERS"), "localhost:9092");
}
