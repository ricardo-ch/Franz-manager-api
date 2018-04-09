package com.greencomnetworks.franzmanager.resources;

import com.greencomnetworks.franzmanager.entities.Broker;
import com.greencomnetworks.franzmanager.entities.HttpError;
import com.greencomnetworks.franzmanager.entities.Partition;
import com.greencomnetworks.franzmanager.entities.Topic;
import com.greencomnetworks.franzmanager.services.AdminClientService;
import com.greencomnetworks.franzmanager.services.ConstantsService;
import com.greencomnetworks.franzmanager.utils.FUtils;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.serialization.Serdes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Path("/topics")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class
TopicsResource {
    private static final Logger logger = LoggerFactory.getLogger(TopicsResource.class);

    private final AdminClient adminClient;

    public TopicsResource() {
        this.adminClient = AdminClientService.getInstance();
    }

    public TopicsResource(AdminClient adminClient) {
        this.adminClient = adminClient;
    }

    @GET
    public List<Topic> getTopics(@QueryParam("idOnly") boolean idOnly) {
        try {
            Set<String> topics = adminClient.listTopics().names().get();

            // return only id
            if (idOnly) {
                return topics.stream()
                        .map(t -> new Topic(t))
                        .collect(Collectors.toList());
            }

            // need 2 kafka objects to get a complete topic descriptions
            Collection<ConfigResource> configResources = topics.stream().map(t -> new ConfigResource(ConfigResource.Type.TOPIC, t)).collect(Collectors.toList());

            KafkaFuture<Map<String, TopicDescription>> describedTopicsFuture = adminClient.describeTopics(topics).all();
            KafkaFuture<Map<ConfigResource, Config>> describedConfigsFuture = adminClient.describeConfigs(configResources).all();

            Map<String, TopicDescription> describedTopics = describedTopicsFuture.get();
            Map<ConfigResource, Config> describedConfigs = describedConfigsFuture.get();


            List<Topic> completeTopics = describedConfigs.entrySet().stream()
                    .map(entry -> {
                        String topicName = entry.getKey().name();
                        TopicDescription describedTopic = describedTopics.get(topicName);

                        Map<String, String> configurations = entry.getValue().entries().stream()
                                .collect(Collectors.toMap(
                                        ConfigEntry::name,
                                        ConfigEntry::value
                                ));
                        return new Topic(topicName, describedTopic.partitions().size(), describedTopic.partitions().get(0).replicas().size(), configurations);
                    }).collect(Collectors.toList());

            return completeTopics;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @POST
    public Response createTopic(Topic topic) {
        if (topicExist(topic.id)) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new HttpError(Response.Status.CONFLICT.getStatusCode(), "This topic (" + topic.id + ") already exist."))
                    .build();
        }

        BrokersResource brokersResource = new BrokersResource();
        Broker clusterConfig = brokersResource.getBrokers().get(0);

        int partitions = topic.partitions != null ? topic.partitions : Integer.parseInt(clusterConfig.configurations.get("num.partitions"));
        int replicationFactor = topic.replications != null ? topic.replications : Integer.parseInt(clusterConfig.configurations.get("default.replication.factor"));

        NewTopic newTopic = new NewTopic(topic.id, partitions, (short) replicationFactor);
        newTopic.configs(topic.configurations);

        List<NewTopic> newTopics = FUtils.List.of(newTopic);

        adminClient.createTopics(newTopics, new CreateTopicsOptions()).all();

        return Response.status(Response.Status.CREATED).build();
    }

    @GET
    @Path("/{topicId}")
    public Object getTopic(@PathParam("topicId") String topicId, @QueryParam("withPartitions") Boolean withPartitions) {
        Collection<ConfigResource> configResources = Stream.of(new ConfigResource(ConfigResource.Type.TOPIC, topicId)).collect(Collectors.toSet());

        KafkaFuture<Map<String, TopicDescription>> describedTopicsFuture = adminClient.describeTopics(Stream.of(topicId).collect(Collectors.toSet())).all();
        KafkaFuture<Map<ConfigResource, Config>> describedConfigsFuture = adminClient.describeConfigs(configResources).all();

        Map<String, TopicDescription> describedTopics = FUtils.getOrElse(() -> describedTopicsFuture.get(), null);
        Map<ConfigResource, Config> describedConfigs = FUtils.getOrElse(() -> describedConfigsFuture.get(), null);

        if (describedConfigs == null || describedTopics == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new HttpError(Response.Status.NOT_FOUND.getStatusCode(), "This topic (" + topicId + ") doesn't exist."))
                    .build();
        }

        Map<String, String> configurations = describedConfigs.values().stream().findFirst().get().entries().stream().collect(Collectors.toMap(
                ConfigEntry::name,
                ConfigEntry::value
        ));

        return new Topic(topicId, describedTopics.get(topicId).partitions().size(), describedTopics.get(topicId).partitions().get(0).replicas().size(), configurations);
    }

    @DELETE
    @Path("/{topicId}")
    public Response deleteTopic(@PathParam("topicId") String topicId) {
        try {
            if (!topicExist(topicId)) {
                return Response.status(Response.Status.CONFLICT)
                        .entity(new HttpError(Response.Status.NOT_FOUND.getStatusCode(), "This topic (" + topicId + ") doesn't exist."))
                        .build();
            }

            Collection<String> topics = FUtils.Set.of(topicId);
            adminClient.deleteTopics(topics).all().get();

            return Response.status(Response.Status.OK)
                    .build();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @GET
    @Path("/{topicId}/partitions")
    public List<Partition> getTopicPartitions(@PathParam("topicId") String topicId){
        Properties config = new Properties();
        config.put("bootstrap.servers", ConstantsService.brokersList);
        KafkaConsumer<ByteBuffer, ByteBuffer> consumer = new KafkaConsumer<>(config, Serdes.ByteBuffer().deserializer(), Serdes.ByteBuffer().deserializer());
        List<TopicPartition> topicPartitions = consumer.partitionsFor(topicId).stream()
                .map(pi -> new TopicPartition(pi.topic(), pi.partition()))
                .collect(Collectors.toList());
        Map<TopicPartition, Long> offsetsEnd = consumer.endOffsets(topicPartitions);

        return offsetsEnd.entrySet()
                .stream()
                .map(entry ->  new Partition(topicId, entry.getKey().partition(), entry.getValue()))
                .collect(Collectors.toList());
    }

    private boolean topicExist(String id) {
        return FUtils.getOrElse(() -> adminClient.describeTopics(Stream.of(id).collect(Collectors.toSet())).all().get(), null) != null;
    }
}
