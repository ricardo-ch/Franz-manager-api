package com.greencomnetworks.franzmanager.resources;

import com.greencomnetworks.franzmanager.entities.Metric;
import com.greencomnetworks.franzmanager.services.AdminClientService;
import com.greencomnetworks.franzmanager.services.KafkaMetricsService;
import com.greencomnetworks.franzmanager.utils.FUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.common.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.*;
import javax.management.openmbean.CompositeData;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.lang.management.MemoryUsage;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;


@Path("/metrics")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class MetricsResource {
    private static final Logger logger = LoggerFactory.getLogger(MetricsResource.class);

    private HashMap<String, MBeanServerConnection> mBeanServerConnections;
    private AdminClient adminClient;

    public MetricsResource(@HeaderParam("clusterId") String clusterId) {
        this.mBeanServerConnections = KafkaMetricsService.getMBeanServerConnections(clusterId);
        this.adminClient = AdminClientService.getAdminClient(clusterId);
    }

    @GET
    public List<Metric> get(@QueryParam("metricLocation") String metricLocation,
                            @QueryParam("metricType") String metricType,
                            @QueryParam("metricName") String metricName,
                            @QueryParam("additional") String additional) throws IOException, AttributeNotFoundException, MBeanException, ReflectionException, MalformedObjectNameException, ExecutionException, InterruptedException {
        if (StringUtils.isEmpty(metricLocation)) {
            throw new BadRequestException("Missing query parameter 'metricLocation'");
        } else if (StringUtils.isEmpty(metricType)) {
            throw new BadRequestException("Missing query parameter 'metricType'");
        }

        String queryString = metricLocation + ":";
        queryString += "type=" + metricType;
        if (metricName != null && !metricName.equals("HeapMemoryUsage")) {
            queryString += ",name=" + metricName;
        }

        if (additional != null) {
            queryString += "," + additional;
        }

        ObjectName objName = new ObjectName(queryString);
        HashMap<String, MBeanServerConnection> mbscs = mBeanServerConnections;
        Collection<Node> brokers = adminClient.describeCluster().nodes().get().stream().map(broker -> {
            try {
                if (broker.host().equals(InetAddress.getLocalHost().getHostName())) {
                    return new Node(broker.id(), "127.0.0.1", broker.port(), broker.rack());
                }
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
            return broker;
        }).collect(Collectors.toList());
        List<Metric> metrics = new ArrayList<>();

        for (String brokerHost : mbscs.keySet()) {
            try {
                MBeanServerConnection mbsc = mbscs.get(brokerHost);
                Node currentBroker = brokers.stream().filter(n -> n.host().equals(brokerHost)).findFirst().get();
                Metric metric = new Metric(metricType, metricName, currentBroker.id(), new HashMap<>());
                MBeanInfo beanInfo = mbsc.getMBeanInfo(objName);
                for (MBeanAttributeInfo attr : beanInfo.getAttributes()) {
                    if (metricName != null && metricName.equals("HeapMemoryUsage")) { //specific case for this metric
                        CompositeData cd = (CompositeData) mbsc.getAttribute(objName, metricName);
                        Arrays.stream(new String[]{"committed", "init", "max", "used"}).forEach(key -> {
                            metric.metrics.put(key, cd.get(key));
                        });
                        logger.warn(cd.values().toString());
                    } else {
                        Object value = mbsc.getAttribute(objName, attr.getName());
                        if (NumberUtils.isCreatable(String.valueOf(value))) {
                            Float floatValue = Float.parseFloat(String.valueOf(value));
                            Float existingValue = Float.parseFloat(String.valueOf(FUtils.getOrElse(metric.metrics.get(attr.getName()), 0)));
                            metric.metrics.put(attr.getName(), floatValue + existingValue);
                        } else {
                            metric.metrics.put(attr.getName(), value);
                        }
                    }
                }
                metrics.add(metric);
            } catch (IntrospectionException e) {
                // that means a jmx server is not available
                logger.warn("A jmx server cannot be reached : {}", e.getMessage());
            } catch (InstanceNotFoundException | NoSuchElementException e) {
                logger.warn("Cannot retrieved this metric {{}}, maybe your kafka need to be upgraded.", queryString);
            }
        }
        return metrics;
    }
}
