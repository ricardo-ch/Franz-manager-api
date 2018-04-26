package com.greencomnetworks.franzmanager.services;

import com.greencomnetworks.franzmanager.utils.FUtils;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;

public class KafkaMetricsService {
    private static final Logger logger = LoggerFactory.getLogger(KafkaMetricsService.class);
    public static HashMap<String, MBeanServerConnection> mBeanServerConnections = new HashMap<>();

    public KafkaMetricsService() {
        for (String url : ConstantsService.brokersJmxUrl) {
            boolean connected = false;
            while (!connected) {
                try {
                    JMXServiceURL jmxUrl = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + url + "/jmxrmi");
                    JMXConnector jmxc = JMXConnectorFactory.connect(jmxUrl, null);
                    mBeanServerConnections.put(url.split(":")[0], jmxc.getMBeanServerConnection());
                    connected = true;
                } catch (MalformedURLException e) {
                    throw new RuntimeException("The following url has a bad format : " + url, e);
                } catch (IOException e) {
                    logger.error("Cannot connect to the following url '{}': {}", url, e.getMessage());
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException e1) {
                        // don't care.
                    }
                }
            }
        }
    }
}