package com.greencomnetworks.franzmanager.resources;

import com.greencomnetworks.franzmanager.entities.ConsumerOffsetRecord;
import com.greencomnetworks.franzmanager.entities.HttpError;
import com.greencomnetworks.franzmanager.entities.Metric;
import com.greencomnetworks.franzmanager.services.KafkaConsumerOffsetReader;
import com.greencomnetworks.franzmanager.services.KafkaMetricsService;
import com.greencomnetworks.franzmanager.utils.FUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.*;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.*;


@Path("/metrics")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class MetricsResource {
    private static final Logger logger = LoggerFactory.getLogger(MetricsResource.class);

    @GET
    public Object get(@QueryParam("metricType") String metricType, @QueryParam("metricName") String metricName, @QueryParam("topic") String topic) throws IOException, AttributeNotFoundException, MBeanException, ReflectionException, InstanceNotFoundException, MalformedObjectNameException {
        if (metricName == null) {
            return new HttpError(400, "Missing query parameter 'metricName'");
        } else if (metricType == null) {
            return new HttpError(400, "Missing query parameter 'metricType'");
        }

        String queryString = "kafka.server:";
        queryString += "type=" + metricType;
        queryString += ",name=" + metricName;

        if (topic != null) {
            queryString += ",topic=" + topic;
        }

        ObjectName objName = new ObjectName(queryString);
        Metric metric = new Metric(metricType, metricName, topic, new HashMap<>());

        for (MBeanServerConnection mbsc : KafkaMetricsService.mBeanServerConnections.values()) {
            try {
                MBeanInfo beanInfo = mbsc.getMBeanInfo(objName);
                for (MBeanAttributeInfo attr : beanInfo.getAttributes()) {
                    Object value = mbsc.getAttribute(objName, attr.getName());
                    if (NumberUtils.isCreatable(String.valueOf(value))) {
                        Float floatValue = Float.parseFloat(String.valueOf(value));
                        Float existingValue = Float.parseFloat(String.valueOf(FUtils.getOrElse(metric.metrics.get(attr.getName()), 0)));
                        metric.metrics.put(attr.getName(), floatValue + existingValue);
                    } else {
                        metric.metrics.put(attr.getName(), value);
                    }
                }
            } catch (IntrospectionException e) {
                // that means a jmx server is not available
                logger.warn("A jmx server cannot be reached : {}", e.getMessage());
            } catch (InstanceNotFoundException e){
                logger.warn("Cannot retrieved this metric {{}}, maybe your kafka need to be upgraded.", queryString);
            }
        }

        return metric;
    }
}
