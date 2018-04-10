package com.greencomnetworks.franzmanager.services;

import com.greencomnetworks.franzmanager.utils.FUtils;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.KafkaAdminClient;

public class AdminClientService {
    private static AdminClient adminClient = KafkaAdminClient.create(FUtils.Map.<String, Object>builder()
            .put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, ConstantsService.brokersList)
            .build());

    private AdminClientService() {
    }

    public static AdminClient getInstance() {
        return adminClient;
    }
}