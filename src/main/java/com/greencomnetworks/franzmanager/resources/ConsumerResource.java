package com.greencomnetworks.franzmanager.resources;

import com.greencomnetworks.franzmanager.entities.ConsumerOffsetRecord;
import com.greencomnetworks.franzmanager.services.KafkaConsumerOffsetReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.Collection;


@Path("/consumers")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ConsumerResource {
    private static final Logger log = LoggerFactory.getLogger(ConsumerResource.class);

    String clusterId;

    public ConsumerResource(@HeaderParam("clusterId") String clusterId){
        this.clusterId = clusterId;
    }

    @GET
    public Collection<ConsumerOffsetRecord> get(@QueryParam("group") String group, @QueryParam("topic") String topic){
        Collection<ConsumerOffsetRecord> result = KafkaConsumerOffsetReader.getInstance().getConsumerOffsetRecords(clusterId);

        if(null != group){
            result = filterByGroup(result, group);
        }
        if(null != topic){
            result = filterByTopic(result, topic);
        }

        return result;
    }

    private Collection<ConsumerOffsetRecord> filterByGroup(Collection<ConsumerOffsetRecord> consumerOffsetRecords, String group){
        log.info("filterByGroup: size is " + consumerOffsetRecords.size());

        Collection<ConsumerOffsetRecord> result = new ArrayList<>();

        for(ConsumerOffsetRecord record : consumerOffsetRecords){
            if(record.getGroup().equals(group)){
                result.add(record);
            }
        }

        log.info("filterByGroup: size is now " + result.size());
        return result;
    }

    private Collection<ConsumerOffsetRecord> filterByTopic(Collection<ConsumerOffsetRecord> consumerOffsetRecords, String topic){
        log.info("filterByTopic: size is " + consumerOffsetRecords.size());
        Collection<ConsumerOffsetRecord> result = new ArrayList<>();

        for(ConsumerOffsetRecord record : consumerOffsetRecords){
            if(record.getTopic().equals(topic)){
                result.add(record);
            }
        }

        log.info("filterByTopic: size is now " + result.size());
        return result;
    }
}
