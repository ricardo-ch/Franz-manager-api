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
    private static HashMap<String, HashMap<String, MBeanServerConnection>> mBeanServerConnections = new HashMap<>();

    public static void init(){
        ConstantsService.clusters.forEach(cluster -> {
            mBeanServerConnections.put(cluster.name, new HashMap<>());

            for (String url : cluster.jmxConnectString.split(",")) {
                try {
                    JMXServiceURL jmxUrl = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + url + "/jmxrmi");
                    JMXConnector jmxc = JMXConnectorFactory.connect(jmxUrl, null);
                    mBeanServerConnections.get(cluster.name).put(url.split(":")[0], jmxc.getMBeanServerConnection());
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
        });
    }

    public static HashMap<String, MBeanServerConnection> getMBeanServerConnections(String clusterId){
        if(clusterId == null){
            clusterId = "Default";
        }
        return mBeanServerConnections.get(clusterId);
    }
}