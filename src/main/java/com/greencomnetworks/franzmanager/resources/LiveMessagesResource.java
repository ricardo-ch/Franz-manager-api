package com.greencomnetworks.franzmanager.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.greencomnetworks.franzmanager.entities.Cluster;
import com.greencomnetworks.franzmanager.entities.Message;
import com.greencomnetworks.franzmanager.services.ConstantsService;
import com.greencomnetworks.franzmanager.utils.CustomObjectMapper;
import com.greencomnetworks.franzmanager.utils.KafkaUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.glassfish.grizzly.websockets.DataFrame;
import org.glassfish.grizzly.websockets.WebSocket;
import org.glassfish.grizzly.websockets.WebSocketApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.NotFoundException;
import java.time.Duration;
import java.util.*;

public class LiveMessagesResource extends WebSocketApplication {
    private static final Logger logger = LoggerFactory.getLogger(LiveMessagesResource.class);
    private Map<WebSocket, Thread> socketThreadMap = new HashMap<>();
    private Map<Thread, FranzConsumer> threadRunnableMap = new HashMap<>();

    private static LiveMessagesResource instance = new LiveMessagesResource();

    public static LiveMessagesResource getInstance() {
        return instance;
    }

    @Override
    public void onMessage(WebSocket socket, String data) {
        String action = data.split(":")[0];
        switch (action) {
            case "subscribe":
                this.newSocketConsumer(socket, data.split(":")[1], data.split(":")[2]);
                break;
            case "close":
                socket.close();
                break;
            default:
                logger.error("Unknown socket action : " + action);
                break;
        }
    }

    @Override
    public void onConnect(WebSocket socket) {
        logger.info("new websocket connection");
    }

    @Override
    public void onClose(WebSocket socket, DataFrame frame) {
        Thread franzConsumerThread = socketThreadMap.get(socket);
        FranzConsumer franzConsumerRunnable = threadRunnableMap.get(franzConsumerThread);
        try {
            franzConsumerRunnable.shutdown();
            franzConsumerThread.join();
        } catch (InterruptedException e) {
            logger.info("closed thread " + franzConsumerThread.getId());
        }
        threadRunnableMap.remove(franzConsumerThread);
        socketThreadMap.remove(socket);
        logger.info("websocket closed, consumer " + franzConsumerRunnable.id + " closed.");
    }

    private void newSocketConsumer(WebSocket socket, String topic, String clusterId) {
        FranzConsumer franzConsumerRunnable = new FranzConsumer("franz-manager-api", topic, socket, clusterId);
        Thread franzConsumerThread = new Thread(franzConsumerRunnable);
        franzConsumerThread.start();
        socketThreadMap.put(socket, franzConsumerThread);
        threadRunnableMap.put(franzConsumerThread, franzConsumerRunnable);
    }

    private class FranzConsumer implements Runnable {
        private final KafkaConsumer<String, String> consumer;
        private final String topic;
        private final String id;
        private final WebSocket socket;

        private FranzConsumer(String groupId,
                              String topic,
                              WebSocket socket,
                              String clusterId) {
            this.id = UUID.randomUUID().toString();
            this.topic = topic;
            this.socket = socket;
            final Properties props = new Properties();
            Cluster cluster = null;
            for (Cluster c : ConstantsService.clusters) {
                if (StringUtils.equals(c.name, clusterId)) {
                    cluster = c;
                    break;
                }
            }
            if (cluster == null) {
                throw new NotFoundException("Cluster not found for the id " + clusterId);
            }
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, cluster.brokersConnectString);
            props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
            props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            this.consumer = new KafkaConsumer<>(props);
        }

        @Override
        public void run() {
            try {
                ObjectMapper objectMapper = CustomObjectMapper.defaultInstance();
                List<TopicPartition> topicPartitions = KafkaUtils.topicPartitionsOf(consumer, this.topic);
                consumer.assign(topicPartitions);
                consumer.seekToEnd(topicPartitions);

                while (true) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                    ArrayList<Message> messages = new ArrayList<>();
                    for (ConsumerRecord<String, String> record : records) {
                        Message message = new Message(record.value(), record.key(), record.partition(), record.offset(), record.timestamp());
                        messages.add(message);
                    }
                    if (messages.size() > 0) {
                        logger.info("{}: consumed {} message(s)", this.id, String.valueOf(messages.size()));
                        try {
                            String s = objectMapper.writeValueAsString(messages);
                            this.socket.send(s);
                        } catch (JsonProcessingException e) {
                            logger.error("Unable to serializer messages: {}", e.getMessage(), e);
                        }
                        Thread.sleep(1000);
                    }
                }
            } catch (WakeupException | InterruptedException e) {
                // ignore for shutdown
            } finally {
                consumer.close();
            }
        }

        public void shutdown() {
            consumer.wakeup();
        }
    }
}