package com.greencomnetworks.franzmanager.utils;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;

import java.util.List;
import java.util.stream.Collectors;

public class KafkaUtils {
    public static <K,V> List<TopicPartition> topicPartitionsOf(KafkaConsumer<K,V> consumer, String topic) {
        return consumer.partitionsFor(topic).stream()
                .map(pi -> new TopicPartition(pi.topic(), pi.partition()))
                .collect(Collectors.toList());
    }
}
