package com.greencomnetworks.franzmanager.services;

import com.greencomnetworks.franzmanager.entities.Cluster;
import com.greencomnetworks.franzmanager.utils.FUtils;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.KafkaAdminClient;

import java.util.*;
import java.util.stream.Collectors;

public class AdminClientService {
    private static Map<String, AdminClient> adminClients = buildFromClusters(ConstantsService.clusters);

    private AdminClientService() {
    }

    static public AdminClient getAdminClient(String name) {
        if (name == null) {
            return adminClients.get("Default");
        }
        return adminClients.get(name);
    }

    static private Map<String, AdminClient> buildFromClusters(List<Cluster> clusters) {
        Map<String, AdminClient> adminClients = new HashMap<>();
        clusters.forEach(cluster -> {
            AdminClient adminClient = KafkaAdminClient.create(FUtils.Map.<String, Object>builder()
                    .put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, cluster.brokersConnectString)
                    .build());
            adminClients.put(cluster.name, adminClient);
        });
        return adminClients;
    }

    public static Map<String, AdminClient> getInstance() {
        return adminClients;
    }
}