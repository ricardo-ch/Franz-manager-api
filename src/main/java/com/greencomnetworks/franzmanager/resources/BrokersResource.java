package com.greencomnetworks.franzmanager.resources;

import com.greencomnetworks.franzmanager.entities.Broker;
import com.greencomnetworks.franzmanager.entities.Cluster;
import com.greencomnetworks.franzmanager.entities.HttpError;
import com.greencomnetworks.franzmanager.services.AdminClientService;
import com.greencomnetworks.franzmanager.services.ConstantsService;
import com.greencomnetworks.franzmanager.services.KafkaMetricsService;
import com.greencomnetworks.franzmanager.utils.FUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.config.ConfigResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.*;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Path("/brokers")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class BrokersResource {
    private static final Logger logger = LoggerFactory.getLogger(BrokersResource.class);

    private String clusterId;
    private Cluster cluster;
    private AdminClient adminClient;
    private HashMap<String, MBeanServerConnection> mBeanServerConnections;

    public BrokersResource(@HeaderParam("clusterId") String clusterId){
        this.clusterId = clusterId == null ? "Default" : clusterId;
        this.adminClient = AdminClientService.getAdminClient(this.clusterId);
        this.mBeanServerConnections = KafkaMetricsService.getMBeanServerConnections(clusterId);
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
    public List<Broker> getBrokers() {
        try {
            Collection<Node> brokers = adminClient.describeCluster().nodes().get();

            Collection<ConfigResource> configResources = brokers.stream().map(cluster -> new ConfigResource(ConfigResource.Type.BROKER, cluster.idString())).collect(Collectors.toSet());
            Map<ConfigResource, Config> brokersConfigs = adminClient.describeConfigs(configResources).all().get();

            return brokers.stream().map(cluster -> {
                Config badFormattedConfigs = brokersConfigs.entrySet().stream().filter(entry -> entry.getKey().name().equals(cluster.idString())).findFirst().get().getValue();

                Map<String, String> configs = new HashMap<>();
                for (ConfigEntry entry : badFormattedConfigs.entries()) {
                    configs.put(entry.name(), entry.value());
                }

                Float bytesIn = null;
                Float bytesOut = null;

                try {
                    MBeanServerConnection mbsc = mBeanServerConnections.get(cluster.host());
                    bytesIn = Float.valueOf(mbsc.getAttribute(new ObjectName("kafka.server:type=BrokerTopicMetrics,name=BytesInPerSec"), "OneMinuteRate").toString());
                    bytesOut = Float.valueOf(mbsc.getAttribute(new ObjectName("kafka.server:type=BrokerTopicMetrics,name=BytesOutPerSec"), "OneMinuteRate").toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }

                return new Broker(cluster.idString(), cluster.host(), cluster.port(), configs, bytesIn, bytesOut);
            }).collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @GET
    @Path("/{brokerId}")
    public Object getBroker(@PathParam("brokerId") String brokerId) {
        try {
            Collection<Node> brokers = adminClient.describeCluster().nodes().get();
            Node node = FUtils.getOrElse(() -> brokers.stream().filter(n -> n.idString().equals(brokerId)).collect(Collectors.toList()).get(0), null);

            if (node == null) {
                return Response.status(Response.Status.NOT_FOUND.getStatusCode())
                        .entity(new HttpError(Response.Status.NOT_FOUND.getStatusCode(), "This cluster (" + brokerId + ") doesn't exist."))
                        .build();
            }

            Collection<ConfigResource> configResource = Stream.of(new ConfigResource(ConfigResource.Type.BROKER, brokerId)).collect(Collectors.toSet());

            Map<ConfigResource, Config> clusterConfigs = adminClient.describeConfigs(configResource).all().get();

            Map<String, String> configs = new HashMap<>();
            for (ConfigEntry entry : clusterConfigs.values().stream().findFirst().get().entries()) {
                configs.put(entry.name(), entry.value());
            }

            Float bytesIn = null;
            Float bytesOut = null;

            try {
                MBeanServerConnection mbsc = mBeanServerConnections.get(node.host());
                bytesIn = Float.valueOf(mbsc.getAttribute(new ObjectName("kafka.server:type=BrokerTopicMetrics,name=BytesInPerSec"), "OneMinuteRate").toString());
                bytesOut = Float.valueOf(mbsc.getAttribute(new ObjectName("kafka.server:type=BrokerTopicMetrics,name=BytesOutPerSec"), "OneMinuteRate").toString());
            } catch (Exception e) {
                e.printStackTrace();
            }

            return new Broker(node.idString(), node.host(), node.port(), configs, bytesIn, bytesOut);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
