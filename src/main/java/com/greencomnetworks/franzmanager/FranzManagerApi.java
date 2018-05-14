package com.greencomnetworks.franzmanager;

import com.greencomnetworks.franzmanager.resources.LiveMessagesResource;
import com.greencomnetworks.franzmanager.services.KafkaConsumerOffsetReader;
import com.greencomnetworks.franzmanager.services.KafkaMetricsService;
import org.glassfish.grizzly.http.server.*;
import org.glassfish.grizzly.websockets.WebSocketAddOn;
import org.glassfish.grizzly.websockets.WebSocketEngine;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.logging.LoggingFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.URI;

public class FranzManagerApi {
    private static final Logger logger = LoggerFactory.getLogger(FranzManagerApi.class);

    private final FranzManagerApiConfig apiConfig;

    public FranzManagerApi() {
        this.apiConfig = FranzManagerApiConfig.fromProperties();
    }

    public FranzManagerApi(FranzManagerApiConfig apiConfig) {
        this.apiConfig = apiConfig;
    }

    public FranzManagerApi start() throws IOException {
        URI baseUri = UriBuilder.fromUri("http://0.0.0.0/").path(apiConfig.basePath).port(apiConfig.apiPort).build();
        ResourceConfig config = new ResourceConfig();

        config.register(LoggingFeature.class);
        config.register(JacksonFeature.class);

        config.packages(this.getClass().getPackage().getName() + ".providers");
        config.packages(this.getClass().getPackage().getName() + ".resources");

        HttpServer server = GrizzlyHttpServerFactory.createHttpServer(baseUri, config, false);

        // register websocket stuff
        WebSocketAddOn webSocketAddOn = new WebSocketAddOn();
        NetworkListener webSocketListener = new NetworkListener("websocket", "0.0.0.0", 5443);
        webSocketListener.registerAddOn(webSocketAddOn);
        server.addListener(webSocketListener);

        WebSocketEngine.getEngine().register(apiConfig.basePath, "/", LiveMessagesResource.getInstance());

        ServerConfiguration serverConfiguration = server.getServerConfiguration();
        serverConfiguration.addHttpHandler(new StaticHttpHandler("apidoc") {
            @Override
            protected void onMissingResource(Request request, Response response) throws Exception {
                super.onMissingResource(request, response);
            }
        }, HttpHandlerRegistration.builder().contextPath(apiConfig.basePath + "/apidoc").urlPattern("/").build());


        KafkaConsumerOffsetReader kafkaConsumerOffsetReader = KafkaConsumerOffsetReader.INSTANCE;
        KafkaMetricsService kafkaMetricsService = new KafkaMetricsService();

        server.start();

        logger.info("Server started on port {} under {}.", apiConfig.apiPort, apiConfig.basePath);

        return this;
    }

    public static void main(String[] args) {
        try {
            FranzManagerApi api = new FranzManagerApi();

            api.start();
        } catch (Throwable e) {
            logger.error("Couldn't start server", e);
            System.exit(1);
        }
    }
}
