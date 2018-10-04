package com.greencomnetworks.franzmanager.services;

import com.greencomnetworks.franzmanager.entities.Cluster;
import com.greencomnetworks.franzmanager.utils.FUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.KafkaAdminClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminClientService {
    private static Map<String, AdminClient> adminClients = buildFromClusters(ConstantsService.clusters);

    private AdminClientService() {
    }

    static public AdminClient getAdminClient(String name) {
        if(StringUtils.isEmpty(name)) {
            name = "Default";
        }
        if (StringUtils.equals(name, "Default") && !adminClients.containsKey(name)) {
            return adminClients.values().iterator().next();
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