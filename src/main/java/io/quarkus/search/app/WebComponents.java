package io.quarkus.search.app;

import io.quarkiverse.web.bundler.runtime.Bundle;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import org.jboss.resteasy.reactive.Cache;

import java.net.URI;
import java.net.URISyntaxException;

@Path("/web-components")
public class WebComponents {

    @Inject
    Bundle bundle;

    @Context
    UriInfo uriInfo;

    // This route allows to access the web-component js on a fixed path
    // without affecting caching of the script
    @Path("/search.js")
    @Cache(noCache = true)
    @GET
    public Response script() {
        URI baseUri = uriInfo.getBaseUri();
        URI redirectUri = baseUri.resolve(bundle.script("main"));
        return Response.temporaryRedirect(redirectUri).build();
    }
}
