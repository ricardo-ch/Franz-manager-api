package com.greencomnetworks.franzmanager.resources;

import com.greencomnetworks.franzmanager.entities.Broker;
import com.greencomnetworks.franzmanager.entities.Cluster;
import com.greencomnetworks.franzmanager.services.AdminClientService;
import com.greencomnetworks.franzmanager.services.ConstantsService;
import com.greencomnetworks.franzmanager.services.KafkaMetricsService;
import com.greencomnetworks.franzmanager.utils.KafkaUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.config.ConfigResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

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
        if(StringUtils.isEmpty(clusterId)) clusterId = "Default";
        this.clusterId = clusterId;
        this.adminClient = AdminClientService.getAdminClient(clusterId);
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

            Collection<ConfigResource> configResources = brokers.stream().map(broker -> new ConfigResource(ConfigResource.Type.BROKER, broker.idString())).collect(Collectors.toSet());
            Map<ConfigResource, Config> brokersConfigs = adminClient.describeConfigs(configResources).all().get();

            return brokers.stream().map(broker -> {
                ConfigResource configResource = new ConfigResource(ConfigResource.Type.BROKER, broker.idString());
                Config config = brokersConfigs.get(configResource);

                Map<String, String> configs = new HashMap<>();
                for (ConfigEntry entry : config.entries()) {
                    configs.put(entry.name(), entry.value());
                }

                Float bytesIn = null;
                Float bytesOut = null;
                try {
                    MBeanServerConnection mbsc = mBeanServerConnections.get(broker.host());
                    bytesIn = Float.valueOf(mbsc.getAttribute(new ObjectName("kafka.server:type=BrokerTopicMetrics,name=BytesInPerSec"), "OneMinuteRate").toString());
                    bytesOut = Float.valueOf(mbsc.getAttribute(new ObjectName("kafka.server:type=BrokerTopicMetrics,name=BytesOutPerSec"), "OneMinuteRate").toString());
                } catch (Exception e) {
                    logger.error("Error while retrieving JMX data: {}", e.getMessage(), e);
                }

                return new Broker(broker.idString(), broker.host(), broker.port(), configs, bytesIn, bytesOut);
            }).collect(Collectors.toList());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch(ExecutionException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    @GET
    @Path("/{brokerId}")
    public Broker getBroker(@PathParam("brokerId") String brokerId) {
        try {
            Config config = KafkaUtils.describeBrokerConfig(adminClient, brokerId);
            if(config == null) {
                throw new NotFoundException("This broker (" + brokerId + ") doesn't exist.");
            }

            Collection<Node> brokers = adminClient.describeCluster().nodes().get();
            Node node = brokers.stream().filter(n -> n.idString().equals(brokerId)).findAny().orElse(null);
            if (node == null) {
                throw new NotFoundException("This broker (" + brokerId + ") doesn't exist.");
            }


            Map<String, String> configs = new HashMap<>();
            for (ConfigEntry entry : config.entries()) {
                configs.put(entry.name(), entry.value());
            }

            Float bytesIn = null;
            Float bytesOut = null;
            try {
                MBeanServerConnection mbsc = mBeanServerConnections.get(node.host());
                bytesIn = Float.valueOf(mbsc.getAttribute(new ObjectName("kafka.server:type=BrokerTopicMetrics,name=BytesInPerSec"), "OneMinuteRate").toString());
                bytesOut = Float.valueOf(mbsc.getAttribute(new ObjectName("kafka.server:type=BrokerTopicMetrics,name=BytesOutPerSec"), "OneMinuteRate").toString());
            } catch (Exception e) {
                logger.error("Error while retrieving JMX data: {}", e.getMessage(), e);
            }

            return new Broker(node.idString(), node.host(), node.port(), configs, bytesIn, bytesOut);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        }
    }
}
