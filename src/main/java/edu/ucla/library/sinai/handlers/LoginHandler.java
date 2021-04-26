
package edu.ucla.library.sinai.handlers;

import static edu.ucla.library.sinai.Constants.HBS_DATA_KEY;

import java.io.IOException;

import edu.ucla.library.sinai.Configuration;
import edu.ucla.library.sinai.Metadata;
import edu.ucla.library.sinai.RoutePatterns;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.jwt.JWTOptions;
import io.vertx.ext.web.Cookie;
import io.vertx.ext.web.RoutingContext;

public class LoginHandler extends SinaiHandler {

    private static final String TOKEN = "token";

    private final JWTAuth myJwtAuth;

    public LoginHandler(final Configuration aConfig, final JWTAuth aJwtAuth) {
        super(aConfig);
        myJwtAuth = aJwtAuth;
    }

    /**
     * Handles the authentication callback request from <sinai-id.org>.
     *
     * The authentication flow for this application doesn't correspond to any of Vert.x's built-in AuthProviders.
     * Instead, we authenticate a user by trusting a callback HTTP request originating from <sinai-id.org>.
     *
     * Essentially, we check that the token provided in that request matches the token that we've placed in a cookie in
     * the user's browser. That cookie is created or updated whenever <code>src/main/webapp/templates/header.hbs</code>
     * is rendered while the user is not logged in, and is used solely for the initial authentication request.
     *
     * @param aContext A routing context
     */
    @Override
    public void handle(final RoutingContext aContext) {
        final MultiMap params = aContext.request().params();
        final HttpServerResponse response = aContext.response();

        if (params.contains(TOKEN)) {
            final String rawToken = params.get(TOKEN);
            final Cookie cookie = aContext.getCookie(TOKEN);

            if (cookie != null && rawToken != null && rawToken.equals(cookie.getValue())) {
                // At this point, we can trust that the sinai-id service has authenticated the user.
                //
                // Since the authentication flow for this application doesn't correspond to any of Vert.x's built-in
                // AuthProviders, the following JWT transaction is essentially a no-op that we perform for one reason
                // only: putting a User on the RoutingContext. We never actually send the token to the client, so each
                // call to this handler generates the same token.
                final JWTOptions jwtOptions = new JWTOptions().setExpiresInSeconds(60);
                final String token = myJwtAuth.generateToken(new JsonObject().put("sub", "asdf"), jwtOptions); // whatevs

                // We now authenticate the useless token so that we can obtain a reference to a User object via the
                // AuthProvider API.
                myJwtAuth.authenticate(new JsonObject().put("jwt", token), authHandler -> {
                    if (authHandler.succeeded()) {
                        final User user = authHandler.result();

                        // This starts the user's session, which is tracked by a Vert.x-managed session cookie
                        aContext.setUser(user);

                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("User successfully validated");
                        }
                        aContext.reroute(RoutePatterns.SEARCH_RESULTS_RE);
                    } else {
                        LOGGER.error(authHandler.cause(), "Authentication did not succeed");
                        response.putHeader(Metadata.CONTENT_TYPE, Metadata.TEXT_MIME_TYPE);
                        response.end(authHandler.cause().getMessage());
                    }
                });
            } else {
                final String message = "Tried to authenticate, but the tokens didn't match";
                LOGGER.error(message);
                response.putHeader(Metadata.CONTENT_TYPE, Metadata.TEXT_MIME_TYPE).end(message);
            }
        } else {
            try {
                aContext.data().put(HBS_DATA_KEY, toHbsContext(new JsonObject(), aContext));
                aContext.next();
            } catch (final IOException e) {
                e.printStackTrace();
                LOGGER.error(e.getMessage());
            }

        }
    }
}
