package com.greencomnetworks.franzmanager.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.greencomnetworks.franzmanager.entities.Cluster;
import com.greencomnetworks.franzmanager.utils.CustomObjectMapper;
import com.greencomnetworks.franzmanager.utils.FUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ConstantsService {
    private static final Logger logger = LoggerFactory.getLogger(KafkaMetricsService.class);
    public static List<Cluster> clusters = new ArrayList<>();

    public static void init() throws RuntimeException {
        String KAFKA_CONF = System.getenv("KAFKA_CONF");
        String KAFKA_BROKERS = System.getenv("KAFKA_BROKERS");
        String KAFKA_BROKERS_JMX = System.getenv("KAFKA_BROKERS_JMX");

        if (KAFKA_CONF != null) {
            try {
                clusters = CustomObjectMapper.defaultInstance().readValue(KAFKA_CONF, new TypeReference<List<Cluster>>(){});
            } catch(IOException e){
                throw new RuntimeException(e);
            }
        } else if (KAFKA_BROKERS != null && KAFKA_BROKERS_JMX != null) {
            clusters = FUtils.List.of(new Cluster("Default", KAFKA_BROKERS, KAFKA_BROKERS_JMX));
        } else {
            throw new RuntimeException("Need at least one of these environment variables : KAFKA_CONF only or KAFKA_BROKERS and KAFKA_BROKERS_JMX");
        }

        logger.info("---------- <CLUSTERS> ----------");
        clusters.forEach(cluster -> {
            logger.info(cluster.toString());
        });
        logger.info("---------- </CLUSTERS> ----------");
    }
}
