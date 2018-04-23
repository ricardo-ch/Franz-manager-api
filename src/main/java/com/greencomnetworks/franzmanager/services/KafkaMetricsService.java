package com.greencomnetworks.franzmanager.services;

import com.greencomnetworks.franzmanager.utils.FUtils;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.KafkaAdminClient;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;

public class KafkaMetricsService {
    public static ArrayList<MBeanServerConnection> mBeanServerConnections = new ArrayList<>();

    public KafkaMetricsService() {
        for (String url : ConstantsService.brokersJmxUrl) {
            try {
                JMXServiceURL jmxUrl = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + url + "/jmxrmi");
                JMXConnector jmxc = JMXConnectorFactory.connect(jmxUrl , null);
                mBeanServerConnections.add(jmxc.getMBeanServerConnection());
            } catch (MalformedURLException e) {
                e.printStackTrace();
                throw new RuntimeException("The following url has a bad format : " + url);
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException("Cannot connect to the following url : " + url);
            }
        }
    }
}