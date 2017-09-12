
package edu.ucla.library.sinai.handlers;

import static edu.ucla.library.sinai.Constants.HBS_DATA_KEY;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.ucla.library.sinai.Configuration;
import edu.ucla.library.sinai.Metadata;
import edu.ucla.library.sinai.RoutePatterns;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTOptions;
import io.vertx.ext.web.Cookie;
import io.vertx.ext.web.RoutingContext;

public class LoginHandler extends SinaiHandler {

    private static final String TOKEN = "token";

    private final JWTAuth myJwtAuth;

    public LoginHandler(final Configuration aConfig, final JWTAuth aJwtAuth) {
        super(aConfig);
        myJwtAuth = aJwtAuth;
    }

    @Override
    public void handle(final RoutingContext aContext) {
        final MultiMap params = aContext.request().params();

        if (params.contains(TOKEN)) {
            final String rawToken = params.get(TOKEN);
            final JWTOptions jwtOptions = new JWTOptions().setExpiresInSeconds(60L);
            final String token = myJwtAuth.generateToken(new JsonObject().put("sub", "asdf"), jwtOptions);
            final Cookie cookie = aContext.getCookie(TOKEN);

            // Not perfect, we know, but a good faith effort
            if ((cookie != null) && (rawToken != null) && rawToken.equals(cookie.getValue())) {
                myJwtAuth.authenticate(new JsonObject().put("jwt", token), authHandler -> {
                    final HttpServerResponse response = aContext.response();

                    if (authHandler.succeeded()) {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("User successfully validated");
                        }

                        aContext.setUser(authHandler.result());
                        aContext.reroute(RoutePatterns.BROWSE);
                    } else {
                        LOGGER.error(authHandler.cause(), "Authentication did not succeed");
                        response.putHeader(Metadata.CONTENT_TYPE, Metadata.TEXT_MIME_TYPE);
                        response.end(authHandler.cause().getMessage());
                    }
                });
            } else {
                final String message = "Tried to authenticate, but the tokens didn't match";
                LOGGER.error(message);
                aContext.response().putHeader(Metadata.CONTENT_TYPE, Metadata.TEXT_MIME_TYPE).end(message);
            }
        } else {
            aContext.data().put(HBS_DATA_KEY, toHbsContext(new ObjectMapper().createObjectNode(), aContext));
            aContext.next();
        }
    }
}
