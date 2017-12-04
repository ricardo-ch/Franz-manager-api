package com.greencomnetworks.franzmanager.providers;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import java.io.IOException;

/**  private static final Logger logger = LoggerFactory.getLogger(AuthorizationRequestFilter.class);

    private APIKeyService apiKeyService = EIBPConnectorJSR2.getInstance().getAPIKeyService();

    @Context
    private ResourceInfo resourceInfo;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String method = requestContext.getMethod();

        if(StringUtils.equals(method, HttpMethod.OPTIONS)) return;

        ExtendedUriInfo uriInfo = (ExtendedUriInfo) requestContext.getUriInfo();

        Class<?> resourceClass = resourceInfo.getResourceClass();
        if(resourceClass != null && resourceClass.isAnnotationPresent(NoAuthentication.class)) return;

        // Skip authentication if env == local (for easier debug/dev), might need a more robust way to detect the environment
        if(StringUtils.equals(System.getenv("ENV"), "LOCAL")) return;

        String apiKey = requestContext.getHeaderString("GCN-APIKEY");

        if(StringUtils.isEmpty(apiKey)) {
            throw new ForbiddenException("no API key provided");
        }

        String session = requestContext.getHeaderString("SESSION");

        String path = "/" + uriInfo.getMatchedURIs().stream().findFirst().orElse("");

        boolean authorized = apiKeyService.checkAPIKeyRights(apiKey, null, method, path, session);

        if(!authorized) {
            throw new ForbiddenException("this path or method is not allowed");
        }
    }
 * Created by Loïc Gaillard.
 */
//@Provider
//@Priority(0)
public class AuthorizationRequestFilter implements ContainerRequestFilter {
    @Override
    public void filter(ContainerRequestContext containerRequestContext) throws IOException {

    }
//    private static final Logger logger = LoggerFactory.getLogger(AuthorizationRequestFilter.class);
//
//    private APIKeyService apiKeyService = EIBPConnectorJSR2.getInstance().getAPIKeyService();
//
//    @Context
//    private ResourceInfo resourceInfo;
//
//    @Override
//    public void filter(ContainerRequestContext requestContext) throws IOException {
//        String method = requestContext.getMethod();
//
//        if(StringUtils.equals(method, HttpMethod.OPTIONS)) return;
//
//        ExtendedUriInfo uriInfo = (ExtendedUriInfo) requestContext.getUriInfo();
//
//        Class<?> resourceClass = resourceInfo.getResourceClass();
//        if(resourceClass != null && resourceClass.isAnnotationPresent(NoAuthentication.class)) return;
//
//        // Skip authentication if env == local (for easier debug/dev), might need a more robust way to detect the environment
//        if(StringUtils.equals(System.getenv("ENV"), "LOCAL")) return;
//
//        String apiKey = requestContext.getHeaderString("GCN-APIKEY");
//
//        if(StringUtils.isEmpty(apiKey)) {
//            throw new ForbiddenException("no API key provided");
//        }
//
//        String session = requestContext.getHeaderString("SESSION");
//
//        String path = "/" + uriInfo.getMatchedURIs().stream().findFirst().orElse("");
//
//        boolean authorized = apiKeyService.checkAPIKeyRights(apiKey, null, method, path, session);
//
//        if(!authorized) {
//            throw new ForbiddenException("this path or method is not allowed");
//        }
//    }
}
