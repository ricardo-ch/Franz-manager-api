package com.greencomnetworks.franzmanager.services;

import com.greencomnetworks.franzmanager.entities.Cluster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;

public class KafkaMetricsService {
    private static final Logger logger = LoggerFactory.getLogger(KafkaMetricsService.class);
    private static HashMap<String, HashMap<String, MBeanServerConnection>> mBeanServerConnections = new HashMap<>();

    public static void init() {
        new Thread(new JmxConnectivityCheck(), "JmxConnectivityCheck").start();
    }

    public static HashMap<String, MBeanServerConnection> getMBeanServerConnections(String clusterId) {
        if (clusterId == null) {
            clusterId = "Default";
        }
        return mBeanServerConnections.get(clusterId);
    }


    private static class JmxConnectivityCheck implements Runnable {
        public void run() {
            while (true) {
                try {
                    ConstantsService.clusters.forEach(cluster -> {
                        mBeanServerConnections.put(cluster.name, new HashMap<>());
                        for (String url : cluster.jmxConnectString.split(",")) {
                            HashMap<String, MBeanServerConnection> mbsc = mBeanServerConnections.get(cluster.name);
                            if (mbsc.get(url.split(":")[0]) == null
                                /*|| mBeanServerConnections.get(url.split(":")[0]) == null*/) {
                                connectJmx(cluster, url);
                            } else {
                                logger.warn(mbsc.get(url.split(":")[0]).toString());
                            }
                        }
                    });
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        private static void connectJmx(Cluster cluster, String url) {
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
    }
}