package com.greencomnetworks.franzmanager.providers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greencomnetworks.franzmanager.utils.GCNObjectMapper;

import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;

/**
 * Created by Loïc Gaillard.
 */
@Provider
public class ObjectMapperProvider implements ContextResolver<ObjectMapper> {

    public static final ObjectMapper objectMapper = GCNObjectMapper.defaultInstance();

    @Override
    public ObjectMapper getContext(Class<?> type) {
        return objectMapper;
    }
}
