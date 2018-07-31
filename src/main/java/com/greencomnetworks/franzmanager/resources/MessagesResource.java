package com.greencomnetworks.franzmanager.resources;

import com.greencomnetworks.franzmanager.entities.Cluster;
import com.greencomnetworks.franzmanager.entities.Message;
import com.greencomnetworks.franzmanager.services.AdminClientService;
import com.greencomnetworks.franzmanager.services.ConstantsService;
import com.greencomnetworks.franzmanager.utils.FUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Path("/topics/{topicId}/messages")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class MessagesResource {
    private static final Logger logger = LoggerFactory.getLogger(MessagesResource.class);

    private String clusterId;
    private Cluster cluster;
    private AdminClient adminClient;

    public MessagesResource(@HeaderParam("clusterId") String clusterId){
        this.clusterId = clusterId;
        this.adminClient = AdminClientService.getAdminClient(this.clusterId);
        for (Cluster cluster : ConstantsService.clusters) {
            if(StringUtils.equals(cluster.name, clusterId)){
                this.cluster = cluster;
                break;
            }
        }
        if(this.cluster == null){
            throw new NotFoundException("Cluster not found for id " + clusterId);
        }
    }
    @GET
    public Object getMessages(@PathParam("topicId") String topicId,
                              @DefaultValue("10") @QueryParam("quantity") Integer quantity,
                              @QueryParam("from") Long from) {
        KafkaFuture<Map<String, TopicDescription>> describedTopicsFuture = adminClient.describeTopics(Stream.of(topicId).collect(Collectors.toSet())).all();
        Map<String, TopicDescription> describedTopics = FUtils.getOrElse(() -> describedTopicsFuture.get(), null);
        if (describedTopics == null) {
            throw new NotFoundException("This topic (" + topicId + ") doesn't exist.");
        }

        final Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, cluster.brokersConnectString);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "franz-manager-api");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        final Consumer<String, String> consumer = new KafkaConsumer<>(props);
        try {
            List<Message> messages = new ArrayList<>();
            List<TopicPartition> topicPartitions = consumer.partitionsFor(topicId).stream().map(partitionInfo -> new TopicPartition(topicId, partitionInfo.partition())).collect(Collectors.toList());

            consumer.assign(topicPartitions);
            consumer.seekToEnd(topicPartitions);

            Map<TopicPartition, Long> beginningOffsets;

            if (from != null) {
                Map<TopicPartition, Long> map = new HashMap<>();
                Map<TopicPartition, Long> beginningOffsetsCopy = new HashMap<>();
                topicPartitions.forEach(topicPartition -> map.put(topicPartition, from));

                consumer.offsetsForTimes(map).forEach((topicPartition, offsetAndTimestamp) -> {
                    if (offsetAndTimestamp != null) {
                        beginningOffsetsCopy.put(topicPartition, offsetAndTimestamp.offset());
                    } else {
                        beginningOffsetsCopy.put(topicPartition, 0L);
                    }
                });

                beginningOffsets = beginningOffsetsCopy;
            } else {
                beginningOffsets = consumer.beginningOffsets(topicPartitions);
            }

            for (TopicPartition topicPartition : topicPartitions) {
                long offset = consumer.position(topicPartition);
                long beginningOffset = beginningOffsets.get(topicPartition);

                if (from != null) {
                    consumer.seek(topicPartition, beginningOffset);
                } else {
                    if (offset - beginningOffset < quantity) {
                        consumer.seekToBeginning(FUtils.List.of(topicPartition));
                    } else {
                        consumer.seek(topicPartition, offset - quantity);
                    }
                }
            }

            final int giveUp = 5;
            int noRecordsCount = 0;
            while (true) {
                final ConsumerRecords<String, String> consumerRecords = consumer.poll(Duration.ofMillis(100));
                if (consumerRecords.count() == 0) {
                    noRecordsCount++;
                    if (noRecordsCount > giveUp) break;
                } else {
                    consumerRecords.forEach(record -> {
                        messages.add(new Message(record.value(), record.key(), record.partition(), record.offset(), record.timestamp()));
                    });
                    if (from == null && messages.size() >= topicPartitions.size() * quantity) break;
                }
            }


            List<Message> resultMessages = messages.stream().sorted((m1, m2) -> -Long.compare(m1.timestamp, m2.timestamp)).collect(Collectors.toList());

            if (from != null) {
                return resultMessages.stream().filter(message -> message.timestamp != -1 && message.timestamp > from).collect(Collectors.toList());
            } else {
                return resultMessages.stream().limit(quantity).collect(Collectors.toList());
            }
        } finally {
            consumer.close();
        }
    }
}
