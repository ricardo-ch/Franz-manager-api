package com.greencomnetworks.franzmanager.resources;

import com.greencomnetworks.franzmanager.core.ConflictException;
import com.greencomnetworks.franzmanager.entities.Broker;
import com.greencomnetworks.franzmanager.entities.Cluster;
import com.greencomnetworks.franzmanager.entities.Partition;
import com.greencomnetworks.franzmanager.entities.Topic;
import com.greencomnetworks.franzmanager.services.AdminClientService;
import com.greencomnetworks.franzmanager.services.ConstantsService;
import com.greencomnetworks.franzmanager.utils.FUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.PartitionInfo;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Path("/topics")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TopicsResource {
    private static final Logger logger = LoggerFactory.getLogger(TopicsResource.class);

    String clusterId;
    Cluster cluster;
    AdminClient adminClient;

    public TopicsResource(@HeaderParam("clusterId") String clusterId){
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
    public List<Topic> getTopics(@QueryParam("idOnly") boolean idOnly, @QueryParam("shortVersion") boolean shortVersion) {
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

                        Map<String, String> configurations = null;
                        if(!shortVersion) {
                            configurations = entry.getValue().entries().stream()
                                    .collect(Collectors.toMap(
                                            ConfigEntry::name,
                                            ConfigEntry::value
                                    ));
                        }
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
            throw new ConflictException("This topic (" + topic.id + ") already exist.");
        }

        BrokersResource brokersResource = new BrokersResource(clusterId);
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
    public Object getTopic(@PathParam("topicId") String topicId) {
        Collection<ConfigResource> configResources = Stream.of(new ConfigResource(ConfigResource.Type.TOPIC, topicId)).collect(Collectors.toSet());

        KafkaFuture<Map<String, TopicDescription>> describedTopicsFuture = adminClient.describeTopics(Stream.of(topicId).collect(Collectors.toSet())).all();
        KafkaFuture<Map<ConfigResource, Config>> describedConfigsFuture = adminClient.describeConfigs(configResources).all();

        Map<String, TopicDescription> describedTopics = FUtils.getOrElse(() -> describedTopicsFuture.get(), null);
        Map<ConfigResource, Config> describedConfigs = FUtils.getOrElse(() -> describedConfigsFuture.get(), null);

        if (describedConfigs == null || describedTopics == null) {
            throw new NotFoundException("This topic (" + topicId + ") doesn't exist.");
        }

        Map<String, String> configurations = describedConfigs.values().stream().findFirst().get().entries().stream().collect(Collectors.toMap(
                ConfigEntry::name,
                ConfigEntry::value
        ));

        return new Topic(topicId, describedTopics.get(topicId).partitions().size(), describedTopics.get(topicId).partitions().get(0).replicas().size(), configurations);
    }

    @PUT
    @Path("/{topicId}")
    public Response updateTopicConfig(@PathParam("topicId") String topicId, HashMap<String, String> configurations) {
        logger.info(configurations.toString());
        Map<ConfigResource, Config> configs = new HashMap<>();
        ConfigResource configResource = new ConfigResource(ConfigResource.Type.TOPIC, topicId);
        List<ConfigEntry> configEntries = configurations.entrySet().stream().map(entry -> new ConfigEntry(entry.getKey(), entry.getValue())).collect(Collectors.toList());
        configs.put(configResource, new Config(configEntries));
        adminClient.alterConfigs(configs);
        return null; // TODO: send proper response
    }

    @DELETE
    @Path("/{topicId}")
    public Response deleteTopic(@PathParam("topicId") String topicId) {
        try {
            if (!topicExist(topicId)) {
                throw new NotFoundException("This topic (" + topicId + ") doesn't exist.");
            }

            Collection<String> topics = FUtils.Set.of(topicId);
            adminClient.deleteTopics(topics).all().get();

            return Response.ok().build();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @GET
    @Path("/{topicId}/partitions")
    public List<Partition> getTopicPartitions(@PathParam("topicId") String topicId) {
        Properties config = new Properties();
        config.put("bootstrap.servers", cluster.brokersConnectString);
        KafkaConsumer<ByteBuffer, ByteBuffer> consumer = new KafkaConsumer<>(config, Serdes.ByteBuffer().deserializer(), Serdes.ByteBuffer().deserializer());
        try {
            List<TopicPartition> topicPartitions = consumer.partitionsFor(topicId).stream()
                    .map(pi -> new TopicPartition(pi.topic(), pi.partition()))
                    .collect(Collectors.toList());
            Map<TopicPartition, Long> offsetsEnd = consumer.endOffsets(topicPartitions);
            Map<TopicPartition, Long> offsetsBeginning = consumer.beginningOffsets(topicPartitions);

            List<PartitionInfo> partitionInfos = consumer.partitionsFor(topicId);

            return offsetsEnd.entrySet()
                    .stream()
                    .map(entry -> {
                        PartitionInfo partition = partitionInfos.stream().filter(partitionInfo -> partitionInfo.partition() == entry.getKey().partition()).collect(Collectors.toList()).get(0);
                        return new Partition(topicId, entry.getKey().partition(), offsetsBeginning.get(entry.getKey()),
                                entry.getValue(), partition.leader().id(), nodesToInts(partition.replicas()), nodesToInts(partition.inSyncReplicas()), nodesToInts(partition.offlineReplicas()));
                    })
                    .collect(Collectors.toList());
        } finally {
            consumer.close();
        }
    }

    @POST
    @Path("/{topicId}/partitions")
    public List<Partition> postTopicPartitions(@PathParam("topicId") String topicId, @QueryParam("quantity") Integer quantity) {
        Properties config = new Properties();
        config.put("bootstrap.servers", cluster.brokersConnectString);
        KafkaConsumer<ByteBuffer, ByteBuffer> consumer = new KafkaConsumer<>(config, Serdes.ByteBuffer().deserializer(), Serdes.ByteBuffer().deserializer());
        try {
            List<PartitionInfo> partitionInfos = consumer.partitionsFor(topicId);

            HashMap<String, NewPartitions> newPartitions = new HashMap<>();
            newPartitions.put(topicId, NewPartitions.increaseTo(partitionInfos.size() + quantity));
            adminClient.createPartitions(newPartitions);
        } finally {
            consumer.close();
        }
        return null; // TODO: send proper response
    }

    private boolean topicExist(String id) {
        return FUtils.getOrElse(() -> adminClient.describeTopics(Stream.of(id).collect(Collectors.toSet())).all().get(), null) != null;
    }

    private int[] nodesToInts(Node[] nodes) {
        return Arrays.stream(nodes).mapToInt(node -> node.id()).toArray();
    }
}