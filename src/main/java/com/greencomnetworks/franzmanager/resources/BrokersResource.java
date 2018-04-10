package com.greencomnetworks.franzmanager.resources;

import com.greencomnetworks.franzmanager.entities.Broker;
import com.greencomnetworks.franzmanager.entities.HttpError;
import com.greencomnetworks.franzmanager.services.AdminClientService;
import com.greencomnetworks.franzmanager.utils.FUtils;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.config.ConfigResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Path("/brokers")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class BrokersResource {
    private static final Logger logger = LoggerFactory.getLogger(BrokersResource.class);

    private final AdminClient adminClient;

    public BrokersResource() {
        this.adminClient = AdminClientService.getInstance();
    }

    public BrokersResource(AdminClient adminClient) {
        this.adminClient = adminClient;
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

                return new Broker(cluster.idString(), cluster.host(), cluster.port(), configs);
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

            return new Broker(node.idString(), node.host(), node.port(), configs);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
