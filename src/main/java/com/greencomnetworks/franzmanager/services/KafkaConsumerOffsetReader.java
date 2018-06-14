package com.greencomnetworks.franzmanager.services;

import com.greencomnetworks.franzmanager.entities.ConsumerOffsetRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.protocol.types.*;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.ForeachAction;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.processor.WallclockTimestampExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Properties;

public enum KafkaConsumerOffsetReader {
    INSTANCE;

    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerOffsetReader.class);

    private static final String CONSUMER_OFFSET_TOPIC = "__consumer_offsets";

    private KafkaStreams streams;

    private HashMap<String, ConsumerOffsetRecord> consumerOffsetRecordArray = new HashMap<>();

    private KafkaConsumerOffsetReader() {
        Logger logger = LoggerFactory.getLogger(KafkaConsumerOffsetReader.class);
        logger.info("Will read topic : " + CONSUMER_OFFSET_TOPIC);

        final Properties streamsConfiguration = new Properties();
        // Give the Streams application a unique name.  The name must be unique in the Kafka cluster
        // against which the application is run.
        streamsConfiguration.put(StreamsConfig.APPLICATION_ID_CONFIG, KafkaConsumerOffsetReader.class.getName());
        // Where to find Kafka broker(s).
        streamsConfiguration.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, ConstantsService.brokersList);
        // Where to find the corresponding ZooKeeper ensemble.
        // Specify default (de)serializers for record keys and for record values.
        streamsConfiguration.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.ByteBuffer().getClass().getName());
        streamsConfiguration.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.ByteBuffer().getClass().getName());

        // Records should be flushed every 10 seconds. This is less than the default
        // in order to keep this example interactive.
        //streamsConfiguration.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 10 * 1000);

        //To handle old messages/topic without proper timestamp
        streamsConfiguration.put(StreamsConfig.DEFAULT_TIMESTAMP_EXTRACTOR_CLASS_CONFIG, WallclockTimestampExtractor.class);

        streamsConfiguration.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        //We're trying to read an internal topic.
        streamsConfiguration.put(ConsumerConfig.EXCLUDE_INTERNAL_TOPICS_CONFIG, false);

        // In the subsequent lines we define the processing topology of the Streams application.
        StreamsBuilder builder = new StreamsBuilder();

        KStream<ByteBuffer, ByteBuffer> consumerOffsetsStream = builder.stream(CONSUMER_OFFSET_TOPIC);
        consumerOffsetsStream.foreach(new ForeachAction<ByteBuffer, ByteBuffer>() {
            public void apply(ByteBuffer keyByteBuffer, ByteBuffer valueByteBuffer) {
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
                                        Instant.ofEpochMilli(Long.parseLong(struct.get(OFFSET_VALUE_COMMIT_TIMESTAMP_FIELD_V1).toString())), ZoneId.of("GMT")));

                        consumerOffsetRecord.setExpireTimestamp(
                                ZonedDateTime.ofInstant(
                                        Instant.ofEpochMilli(Long.parseLong(struct.get(OFFSET_VALUE_EXPIRE_TIMESTAMP_FIELD_V1).toString())), ZoneId.of("GMT")));


                        consumerOffsetRecord.setTimestamp(timestamp);

                        String messageKey = consumerOffsetRecord.getGroup() + "." + consumerOffsetRecord.getTopic() + "." + consumerOffsetRecord.getPartition();

                        consumerOffsetRecordArray.put(messageKey, consumerOffsetRecord);
                    }


                } catch (NullPointerException npe) {
                    logger.error("Got null pointer while processing message from consumer offset topic: " + npe.getMessage(), npe);
                    npe.printStackTrace();
                }

            }
        });

        this.streams = new KafkaStreams(builder.build(), streamsConfiguration);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Will close Streams");
            streams.close();
        }));

        logger.info("Will start Stream");
        this.streams.cleanUp();
        this.streams.start();
        logger.info("Exiting constructor");
    }

    public Collection<ConsumerOffsetRecord> getConsumerOffsetRecords() {
        return this.consumerOffsetRecordArray.values();
    }

}


