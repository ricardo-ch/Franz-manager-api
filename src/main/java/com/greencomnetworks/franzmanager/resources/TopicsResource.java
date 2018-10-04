package com.greencomnetworks.franzmanager.resources;

import com.greencomnetworks.franzmanager.core.ConflictException;
import com.greencomnetworks.franzmanager.entities.*;
import com.greencomnetworks.franzmanager.services.AdminClientService;
import com.greencomnetworks.franzmanager.services.ConstantsService;
import com.greencomnetworks.franzmanager.utils.FUtils;
import com.greencomnetworks.franzmanager.utils.KafkaUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serdes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Path("/topics")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TopicsResource {
    private static final Logger logger = LoggerFactory.getLogger(TopicsResource.class);

    String clusterId;
    Cluster cluster;
    AdminClient adminClient;

    public TopicsResource(@HeaderParam("clusterId") String clusterId) {
        this.clusterId = clusterId;
        this.adminClient = AdminClientService.getAdminClient(this.clusterId);
        for (Cluster cluster : ConstantsService.clusters) {
            if (StringUtils.equals(cluster.name, clusterId)) {
                this.cluster = cluster;
                break;
            }
        }
        if (this.cluster == null) {
            throw new NotFoundException("Cluster not found for id " + clusterId);
        }
    }

    @GET
    public List<Topic> getTopics(@QueryParam("idOnly") boolean idOnly, @QueryParam("shortVersion") boolean shortVersion) {
        try {
            ListTopicsOptions listTopicsOptions = new ListTopicsOptions();
            listTopicsOptions.listInternal(true);
            Set<String> topics = adminClient.listTopics(listTopicsOptions).names().get();

            // return only id
            if (idOnly) {
                return topics.stream()
                        .map(t -> new Topic(t))
                        .collect(Collectors.toList());
            }

            // need 2 kafka objects to get a complete topic descriptions
            List<ConfigResource> configResources = topics.stream().map(t -> new ConfigResource(ConfigResource.Type.TOPIC, t)).collect(Collectors.toList());

            KafkaFuture<Map<String, TopicDescription>> describedTopicsFuture = adminClient.describeTopics(topics).all();
            KafkaFuture<Map<ConfigResource, Config>> describedConfigsFuture = adminClient.describeConfigs(configResources).all();

            Map<String, TopicDescription> describedTopics = describedTopicsFuture.get();
            Map<ConfigResource, Config> describedConfigs = describedConfigsFuture.get();


            List<Topic> completeTopics = describedConfigs.entrySet().stream()
                    .map(entry -> {
                        String topicName = entry.getKey().name();
                        TopicDescription describedTopic = describedTopics.get(topicName);

                        Map<String, String> configurations = null;
                        if (!shortVersion) {
                            configurations = entry.getValue().entries().stream()
                                    .collect(Collectors.toMap(
                                            ConfigEntry::name,
                                            ConfigEntry::value
                                    ));
                        }
                        List<Partition> topicPartitions = describedTopic.partitions().stream()
                                .map(topicPartitionInfo -> new Partition(topicName, -1, -1, topicPartitionInfo, null)).collect(Collectors.toList());
                        return new Topic(topicName, topicPartitions, configurations);
                    }).collect(Collectors.toList());

            return completeTopics;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    @POST
    public Response createTopic(TopicCreation topic) {
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

        try {
            adminClient.createTopics(newTopics).all().get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        }

        return Response.status(Response.Status.CREATED).build();
    }

    @GET
    @Path("/{topicId}")
    public Topic getTopic(@PathParam("topicId") String topicId) {
        // NOTE: the calls are in sequence instead of being in parallel, which leads to a slower response time, but I don't think it's really an issue here...
        //                                 - lgaillard 01/08/2018
        TopicDescription topicDescription = KafkaUtils.describeTopic(adminClient, topicId);
        Config config = KafkaUtils.describeTopicConfig(adminClient, topicId);

        if (topicDescription == null || config == null) {
            throw new NotFoundException("This topic (" + topicId + ") doesn't exist.");
        }

        Map<String, String> configurations = config.entries().stream().collect(Collectors.toMap(
                ConfigEntry::name,
                ConfigEntry::value
        ));

        List<Partition> topicPartitions = topicDescription.partitions().stream()
                .map(topicPartitionInfo -> new Partition(topicId, -1, -1, topicPartitionInfo, null)).collect(Collectors.toList());
        return new Topic(topicId, topicPartitions, configurations);
    }

    @PUT
    @Path("/{topicId}")
    public Response updateTopicConfig(@PathParam("topicId") String topicId, Map<String, String> configurations) {
        if (!topicExist(topicId)) {
            throw new NotFoundException("This topic (" + topicId + ") doesn't exist.");
        }

        logger.info("Updating config for '{}': {}", topicId, configurations);

        try {
            ConfigResource configResource = new ConfigResource(ConfigResource.Type.TOPIC, topicId);
            List<ConfigEntry> configEntries = configurations.entrySet().stream().map(entry -> new ConfigEntry(entry.getKey(), entry.getValue())).collect(Collectors.toList());
            Map<ConfigResource, Config> configs = FUtils.Map.of(configResource, new Config(configEntries));
            adminClient.alterConfigs(configs).all().get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        }

        return Response.ok().build();
    }

    @DELETE
    @Path("/{topicId}")
    public Response deleteTopic(@PathParam("topicId") String topicId) {
        if (!topicExist(topicId)) {
            throw new NotFoundException("This topic (" + topicId + ") doesn't exist.");
        }

        try {
            Collection<String> topics = FUtils.Set.of(topicId);
            adminClient.deleteTopics(topics).all().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }

        return Response.ok().build();
    }

    @GET
    @Path("/{topicId}/partitions")
    public List<Partition> getTopicPartitions(@PathParam("topicId") String topicId) {
        if (!topicExist(topicId)) {
            throw new NotFoundException("This topic (" + topicId + ") doesn't exist.");
        }

        Properties config = new Properties();
        config.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, cluster.brokersConnectString);
        Deserializer<byte[]> deserializer = Serdes.ByteArray().deserializer();
        KafkaConsumer<byte[], byte[]> consumer = new KafkaConsumer<>(config, deserializer, deserializer);
        try {
            List<PartitionInfo> partitionInfos = consumer.partitionsFor(topicId);
            List<TopicPartition> topicPartitions = partitionInfos.stream().map(pi -> new TopicPartition(pi.topic(), pi.partition())).collect(Collectors.toList());
            Map<TopicPartition, Long> offsetsBeginning = consumer.beginningOffsets(topicPartitions);
            Map<TopicPartition, Long> offsetsEnd = consumer.endOffsets(topicPartitions);

            return partitionInfos.stream().map(pi -> {
                TopicPartition tp = new TopicPartition(pi.topic(), pi.partition());
                return new Partition(tp.topic(), tp.partition(), offsetsBeginning.get(tp), offsetsEnd.get(tp),
                        pi.leader().id(), nodesToInts(pi.replicas()), nodesToInts(pi.inSyncReplicas()), nodesToInts(pi.offlineReplicas()));
            }).collect(Collectors.toList());
        } finally {
            consumer.close();
        }
    }

    @POST
    @Path("/{topicId}/partitions")
    public Response postTopicPartitions(@PathParam("topicId") String topicId, @QueryParam("quantity") Integer quantity) {
        TopicDescription topicDescription = KafkaUtils.describeTopic(adminClient, topicId);
        if (topicDescription == null) {
            throw new NotFoundException("This topic (" + topicId + ") doesn't exist.");
        }

        try {
            Map<String, NewPartitions> newPartitions = FUtils.Map.of(topicId, NewPartitions.increaseTo(topicDescription.partitions().size() + quantity));
            adminClient.createPartitions(newPartitions).all().get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        }

        return Response.status(Response.Status.CREATED).build();
    }


    private boolean topicExist(String id) {
        return KafkaUtils.describeTopic(adminClient, id) != null;
    }

    private int[] nodesToInts(Node[] nodes) {
        return Arrays.stream(nodes).mapToInt(node -> node.id()).toArray();
    }
}