package com.greencomnetworks.franzmanager.providers;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import javax.ws.rs.ext.Provider;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.time.ZonedDateTime;

/**
 *
 */
@Provider
public class CustomParamConverterProvider implements ParamConverterProvider {

    @Override
    public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations) {
        if(genericType == ZonedDateTime.class) {
            return new ParamConverter<T>() {
                @SuppressWarnings("unchecked")
                @Override
                public T fromString(final String value) {
                    try {
                        if (value == null) return null;
                        return (T) ZonedDateTime.parse(value);
                    } catch (Exception e) {
                        throw new ProcessingException(e);
                    }
                }

                @Override
                public String toString(final T value) {
                    if(value == null) return null;
                    return value.toString();
                }
            };
        }
        return null;
    }
}
