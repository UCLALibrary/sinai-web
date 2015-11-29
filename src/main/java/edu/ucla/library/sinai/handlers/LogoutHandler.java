
package edu.ucla.library.sinai.handlers;

import static edu.ucla.library.sinai.RoutePatterns.ROOT;

import edu.ucla.library.sinai.Configuration;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;

public class LogoutHandler extends SinaiHandler {

    public LogoutHandler(final Configuration aConfig) {
        super(aConfig);
    }

    @Override
    public void handle(final RoutingContext aContext) {
        final HttpServerResponse response = aContext.response();
        final Session session = aContext.session();

        if (LOGGER.isDebugEnabled()) {
            final JsonObject principal = aContext.user().principal();
            final String user = principal.getString("name");
            final String email = principal.getString("email");

            LOGGER.debug("Logging out of session '{}': {} ({})", session.id(), user, email);
        }

        aContext.clearUser();
        response.setStatusCode(303).putHeader("Location", ROOT).end();
    }

}
