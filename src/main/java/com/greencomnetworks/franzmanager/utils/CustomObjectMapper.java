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

public class CustomObjectMapper extends ObjectMapper {

    private static AtomicReference<CustomObjectMapper> _defaultInstance = new AtomicReference<>();
    public static CustomObjectMapper defaultInstance() {
        if(_defaultInstance.get() == null) {
            _defaultInstance.compareAndSet(null, new CustomObjectMapper());
        }
        return _defaultInstance.get();
    }

    public CustomObjectMapper() {
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
}
