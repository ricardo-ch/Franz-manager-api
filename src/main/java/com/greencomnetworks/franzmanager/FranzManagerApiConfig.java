package com.greencomnetworks.franzmanager;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;

public class FranzManagerApiConfig {
    public final String projectId;
    public final String basePath;
    public final int apiPort;

    public FranzManagerApiConfig(String projectId, String basePath, int apiPort) {
        this.projectId = projectId;
        this.basePath = basePath;
        this.apiPort = apiPort;
    }

    public static FranzManagerApiConfig fromProperties() {
        try {
            PropertiesConfiguration properties = new Configurations().properties("config.properties");
            return new FranzManagerApiConfig(
                    properties.getString("project_id"),
                    "/" + properties.getString("base_path"),
                    properties.getInt("api_port")
            );
        } catch (ConfigurationException e) {
            throw new RuntimeException("Couldn't load properties 'config.properties'", e);
        }
    }
}
