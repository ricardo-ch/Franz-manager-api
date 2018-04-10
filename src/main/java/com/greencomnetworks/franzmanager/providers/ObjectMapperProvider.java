package com.greencomnetworks.franzmanager.providers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greencomnetworks.franzmanager.utils.CustomObjectMapper;

import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;

/**
<<<<<<< HEAD
 * Created by Loïc Gaillard.
=======
 *
>>>>>>> github/master
 */
@Provider
public class ObjectMapperProvider implements ContextResolver<ObjectMapper> {

    public static final ObjectMapper objectMapper = CustomObjectMapper.defaultInstance();

    @Override
    public ObjectMapper getContext(Class<?> type) {
        return objectMapper;
    }
}
