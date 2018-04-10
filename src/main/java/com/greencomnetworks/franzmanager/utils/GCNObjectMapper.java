package com.greencomnetworks.franzmanager.utils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.ZonedDateTimeSerializer;

import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by Loïc Gaillard.
 */
public class GCNObjectMapper extends ObjectMapper {

    private static AtomicReference<GCNObjectMapper> _defaultInstance = new AtomicReference<>();
    public static GCNObjectMapper defaultInstance() {
        if(_defaultInstance.get() == null) {
            _defaultInstance.compareAndSet(null, new GCNObjectMapper());
        }
        return _defaultInstance.get();
    }

    public GCNObjectMapper() {
        registerModule(new Jdk8Module());
        registerModule(new JavaTimeModule()
                .addSerializer(ZonedDateTime.class, new JsonSerializer<ZonedDateTime>() {
                    // Convert to utc
                    @Override
                    public void serialize(ZonedDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                        ZonedDateTime utcValue = value.withZoneSameInstant(ZoneOffset.UTC);
                        ZonedDateTimeSerializer.INSTANCE.serialize(utcValue, gen, serializers);
                    }
                })
        );
        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
//
//    /**
//     * Reflexivity hack to inject the ObjectMapper in the an EIBPConnector instance
//     */
//    public static void eibpConnectorInjection(IConnector connectorInstance) {
//        try {
//            Field serviceField = EIBPConnectorJSR2.class.getDeclaredField("service");
//            serviceField.setAccessible(true);
//            JerseyWebTarget webTarget = (JerseyWebTarget) serviceField.get(connectorInstance);
//
//            Field configField = JerseyWebTarget.class.getDeclaredField("config");
//            configField.setAccessible(true);
//            ClientConfig clientConfig = (ClientConfig) configField.get(webTarget);
//            // Register ObjectMapperResolver.
//            // DO NOT replace it with a Lambda!!! Otherwise, you'll have a really bad day...
//            //noinspection Convert2Lambda
//            clientConfig.register(new ContextResolver<ObjectMapper>() {
//                private final ObjectMapper objectMapper = GCNObjectMapper.defaultInstance();
//                @Override
//                public ObjectMapper getContext(Class<?> type) {
//                    return objectMapper;
//                }
//            });
//        } catch (NoSuchFieldException|IllegalAccessException e) {
//            throw new RuntimeException(e);
//        }
//    }
}
