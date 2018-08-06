package com.greencomnetworks.franzmanager.services;

import com.greencomnetworks.franzmanager.entities.ConsumerOffsetRecord;
import com.greencomnetworks.franzmanager.utils.KafkaUtils;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.InterruptException;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.protocol.types.*;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serdes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class KafkaConsumerOffsetReader {
    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerOffsetReader.class);

    private static final String CONSUMER_OFFSET_TOPIC = "__consumer_offsets";
    private static final String CONSUMER_GROUP_ID = "franz-manager-api_consumer-offset-reader";

    private HashMap<String, Map<String, ConsumerOffsetRecord>> consumerOffsetRecordArray = new HashMap<>();

    private static final AtomicReference<KafkaConsumerOffsetReader> _instance = new AtomicReference<>();

    public static KafkaConsumerOffsetReader getInstance() {
        KafkaConsumerOffsetReader instance = _instance.get();
        if(instance == null) {
            _instance.compareAndSet(null, new KafkaConsumerOffsetReader());
            instance = _instance.get();
        }
        return instance;
    }

    public static void init(){
        getInstance();
    }

    private KafkaConsumerOffsetReader() {
        ConstantsService.clusters.forEach(cluster -> {
            startConsumer(cluster.name, cluster.brokersConnectString);
        });
    }

    private void startConsumer(String clusterId, String bootstrapServers) {
        logger.info("Connecting to '{}': {}", bootstrapServers, CONSUMER_OFFSET_TOPIC);

        Map<String, Object> config = new HashMap<>();
        config.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(CommonClientConfigs.CLIENT_ID_CONFIG, CONSUMER_GROUP_ID);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, CONSUMER_GROUP_ID);
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.put(ConsumerConfig.EXCLUDE_INTERNAL_TOPICS_CONFIG, false);

        Deserializer<ByteBuffer> deserializer = Serdes.ByteBuffer().deserializer();

        final KafkaConsumer<ByteBuffer, ByteBuffer> consumer = new KafkaConsumer<>(config, deserializer, deserializer);
        final AtomicBoolean running = new AtomicBoolean(true);

        Thread thread = new Thread(() -> {
            try {
                while (running.get()) {
                    Map<String, ConsumerOffsetRecord> consumerOffsetRecords = new HashMap<>();
                    consumerOffsetRecordArray.put(clusterId, consumerOffsetRecords);

                    // TODO: add hook to update the topicPartitions when they are updated, it might be safer to use a subscribe here
                    // with auto commit disabled, and a unique groupId fro each instance.
                    //                                       lgaillard - 30/08/2018
                    List<TopicPartition> topicPartitions = KafkaUtils.topicPartitionsOf(consumer, CONSUMER_OFFSET_TOPIC);
                    consumer.assign(topicPartitions);
                    consumer.seekToBeginning(topicPartitions);
                    while (running.get()) {
                        try {
                            ConsumerRecords<ByteBuffer, ByteBuffer> records = consumer.poll(Duration.ofMillis(100));

                            for (ConsumerRecord<ByteBuffer, ByteBuffer> record : records) {
                                ByteBuffer keyByteBuffer = record.key();
                                ByteBuffer valueByteBuffer = record.value();

                                try {
                                    Short keyVersion = keyByteBuffer.getShort();

                                    if (keyVersion == 1) {
                                        ConsumerOffsetRecord consumerOffsetRecord = new ConsumerOffsetRecord();

                                        ZonedDateTime timestamp = ZonedDateTime.now();

                                        Schema OFFSET_COMMIT_KEY_SCHEMA = new Schema(
                                                new Field("group", Type.STRING),
                                                new Field("topic", Type.STRING),
                                                new Field("partition", Type.INT32));

                                        BoundField OFFSET_KEY_GROUP_FIELD = OFFSET_COMMIT_KEY_SCHEMA.get("group");
                                        BoundField OFFSET_KEY_TOPIC_FIELD = OFFSET_COMMIT_KEY_SCHEMA.get("topic");
                                        BoundField OFFSET_KEY_PARTITION_FIELD = OFFSET_COMMIT_KEY_SCHEMA.get("partition");

                                        Struct struct = OFFSET_COMMIT_KEY_SCHEMA.read(keyByteBuffer);

                                        consumerOffsetRecord.setGroup(struct.get(OFFSET_KEY_GROUP_FIELD).toString());
                                        consumerOffsetRecord.setTopic(struct.get(OFFSET_KEY_TOPIC_FIELD).toString());
                                        consumerOffsetRecord.setPartition(Integer.valueOf(struct.get(OFFSET_KEY_PARTITION_FIELD).toString()));

                                        String messageKey = consumerOffsetRecord.getGroup() + "." + consumerOffsetRecord.getTopic() + "." + consumerOffsetRecord.getPartition();

                                        if (valueByteBuffer == null) {
                                            consumerOffsetRecords.remove(messageKey);
                                        } else {
                                            Short valueVersion = valueByteBuffer.getShort();
                                            logger.debug("Value Version is " + valueVersion);
                                            Schema OFFSET_COMMIT_VALUE_SCHEMA_V1 = new Schema(new Field("offset", Type.INT64),
                                                    new Field("metadata", Type.STRING, "Associated metadata.", ""),
                                                    new Field("commit_timestamp", Type.INT64),
                                                    new Field("expire_timestamp", Type.INT64));

                                            BoundField OFFSET_VALUE_OFFSET_FIELD_V1 = OFFSET_COMMIT_VALUE_SCHEMA_V1.get("offset");
                                            BoundField OFFSET_VALUE_METADATA_FIELD_V1 = OFFSET_COMMIT_VALUE_SCHEMA_V1.get("metadata");
                                            BoundField OFFSET_VALUE_COMMIT_TIMESTAMP_FIELD_V1 = OFFSET_COMMIT_VALUE_SCHEMA_V1.get("commit_timestamp");
                                            BoundField OFFSET_VALUE_EXPIRE_TIMESTAMP_FIELD_V1 = OFFSET_COMMIT_VALUE_SCHEMA_V1.get("expire_timestamp");

                                            struct = OFFSET_COMMIT_VALUE_SCHEMA_V1.read(valueByteBuffer);

                                            //TODO deal with metadata
                                            //logger.info("Metadata : " + struct.get(OFFSET_VALUE_METADATA_FIELD_V1).toString());

                                            consumerOffsetRecord.setOffset(Long.valueOf(struct.get(OFFSET_VALUE_OFFSET_FIELD_V1).toString()));
                                            consumerOffsetRecord.setCommitTimestamp(
                                                    ZonedDateTime.ofInstant(
                                                            Instant.ofEpochMilli(Long.parseLong(struct.get(OFFSET_VALUE_COMMIT_TIMESTAMP_FIELD_V1).toString())), ZoneId.of("UTC")));

                                            consumerOffsetRecord.setExpireTimestamp(
                                                    ZonedDateTime.ofInstant(
                                                            Instant.ofEpochMilli(Long.parseLong(struct.get(OFFSET_VALUE_EXPIRE_TIMESTAMP_FIELD_V1).toString())), ZoneId.of("UTC")));


                                            consumerOffsetRecord.setTimestamp(timestamp);

                                            consumerOffsetRecords.put(messageKey, consumerOffsetRecord);
                                        }
                                    }

                                } catch (NullPointerException npe) {
                                    logger.error("Got null pointer while processing message from consumer offset topic: " + npe.getMessage(), npe);
                                    npe.printStackTrace();
                                }
                            }
                        } catch (WakeupException | InterruptException e) {
                            /* noop */
                        } catch (KafkaException e) {
                            logger.error("Unhandled kafka error: {}\nRestarting consumer...", e.getMessage(), e);
                            break;
                        }
                    }
                }
            } finally {
                consumer.close();
            }
        }, "ConsumerOffsetReader-" + clusterId);

        thread.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running.set(false);
            consumer.wakeup();
            try { thread.join(2000); } catch (InterruptedException e) { /* noop */ }
        }));
    }


    public Collection<ConsumerOffsetRecord> getConsumerOffsetRecords(String clusterId) {
        return this.consumerOffsetRecordArray.get(clusterId).values();
    }
}


