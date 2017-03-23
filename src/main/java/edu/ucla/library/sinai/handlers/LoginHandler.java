
package edu.ucla.library.sinai.handlers;

import static edu.ucla.library.sinai.Constants.FAILURE_RESPONSE;
import static edu.ucla.library.sinai.Constants.HBS_DATA_KEY;
import static edu.ucla.library.sinai.Constants.HTTP_HOST_PROP;
import static edu.ucla.library.sinai.Metadata.CONTENT_TYPE;
import static edu.ucla.library.sinai.Metadata.TEXT_MIME_TYPE;
import static edu.ucla.library.sinai.Metadata.JSON_MIME_TYPE;

import javax.security.auth.login.FailedLoginException;
import java.lang.ClassCastException;

import org.javatuples.Pair;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import info.freelibrary.util.StringUtils;

import edu.ucla.library.sinai.Configuration;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.DecodeException;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTOptions;
import io.vertx.ext.web.RoutingContext;

public class LoginHandler extends SinaiHandler {

    public static final String GOOGLE = "google";

    public static final String FACEBOOK = "facebook";

    private static final String GOOGLE_HOST = "www.googleapis.com";

    private static final String GOOGLE_PATH = "/oauth2/v3/tokeninfo?access_token={}";

    private static final String FACEBOOK_HOST = "graph.facebook.com";

    private static final String FACEBOOK_PATH = "/debug_token?input_token={}&access_token={}";

    // TODO: check JSON service aud to make sure it matches the client ID
    private final JWTAuth myJwtAuth;

    public LoginHandler(final Configuration aConfig, final JWTAuth aJwtAuth) {
        super(aConfig);
        myJwtAuth = aJwtAuth;
    }

    @Override
    public void handle(final RoutingContext aContext) {
        final HttpMethod method = aContext.request().method();

        // GET requests get served a template page for next actions
        if (method == HttpMethod.GET) {
            final ObjectMapper mapper = new ObjectMapper();
            final ObjectNode jsonNode = mapper.createObjectNode();

            jsonNode.put(HTTP_HOST_PROP.replace('.', '-'), myConfig.getServer());
            jsonNode.put(GOOGLE, myConfig.getOAuthClientID(GOOGLE));
            jsonNode.put(FACEBOOK, myConfig.getOAuthClientID(FACEBOOK));

            // Put our JSON data into our context so it can be used by handlebars
            aContext.data().put(HBS_DATA_KEY, toHbsContext(jsonNode, aContext));
            aContext.next();
        } else if (method == HttpMethod.POST) {
            final String token = aContext.request().getParam("token");
            final String site = aContext.request().getParam("site");

            if (StringUtils.trimToNull(token) != null && StringUtils.trimToNull(site) != null) {
                final HttpClientOptions options = new HttpClientOptions().setSsl(true);
                final HttpClient client = aContext.vertx().createHttpClient(options);

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Processing {} login token: {}", site, token);
                }

                checkOAuthToken(client, aContext, site, token);
            } else {
                LOGGER.warn("Received a login POST message without a token");
                aContext.fail(500);
            }
        } else {
            LOGGER.warn("Received a {} request but only POST and GET are supported", method.name());
            aContext.response().headers().add("Allow", "GET, POST");
            aContext.fail(405);
        }
    }

    private void checkOAuthToken(final HttpClient aClient, final RoutingContext aContext, final String aSite,
            final String aToken) {
        final Pair<String, String> hostPath = getHostPath(aSite, aToken);
        final String site = StringUtils.upcase(aSite);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Verifying user login token with {}: {}", site, aToken);
        }

        final HttpClientRequest request = aClient.get(443, hostPath.getValue0(), hostPath.getValue1(), handler -> {
            if (handler.statusCode() == 200) {
                handler.bodyHandler(new JWTBodyHandler(aClient, aContext));
            } else {
                final HttpServerResponse response = aContext.response();
                final String statusMessage = handler.statusMessage();
                final int statusCode = handler.statusCode();

                LOGGER.error("{} verfication responded with: {} [{}]", site, statusMessage, statusCode);
                response.setStatusCode(statusCode).setStatusMessage(statusMessage).close();
                aClient.close();
            }
        }).exceptionHandler(exception -> {
            fail(aContext, exception);
            aClient.close();
        });

        request.end();
    }

    private Pair<String, String> getHostPath(final String aService, final String aToken) {
        final String service = aService.toLowerCase(); // Should already by lower-case, but...

        if (service.equals(GOOGLE)) {
            return Pair.with(GOOGLE_HOST, StringUtils.format(GOOGLE_PATH, aToken));
        } else if (service.equals(FACEBOOK)) {
            return Pair.with(FACEBOOK_HOST, StringUtils.format(FACEBOOK_PATH, aToken, aToken));
        } else {
            throw new RuntimeException(StringUtils.format("Unexpected OAuth service: {}", aService));
        }
    }

    private class JWTBodyHandler implements Handler<Buffer> {

        private final RoutingContext myContext;

        private final HttpClient myClient;

        private JWTBodyHandler(final HttpClient aClient, final RoutingContext aContext) {
            myContext = aContext;
            myClient = aClient;
        }

        @Override
        public void handle(final Buffer aBody) {
            LOGGER.debug("{} handling body: {}", getClass().getSimpleName(), aBody.toString());

            try {
                final JsonObject jwt = extractJWT(new JsonObject(aBody.toString()));
                final JWTOptions jwtOptions = new JWTOptions().setExpiresInMinutes(new Long(120));
                final String token = myJwtAuth.generateToken(jwt, jwtOptions);

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Token's decoded JSON contents: {}", aBody.toString());
                }

                // Authenticating will give us a user which we can put into the session
                myJwtAuth.authenticate(new JsonObject().put("jwt", token), authHandler -> {
                    final HttpServerResponse response = myContext.response();

                    if (authHandler.succeeded()) {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("User successfully validated");
                        }

                        myContext.setUser(authHandler.result());
                        response.putHeader(CONTENT_TYPE, TEXT_MIME_TYPE);
                        response.end("success");
                    } else {
                        LOGGER.error(authHandler.cause(), "Authentication did not succeed");
                        response.putHeader(CONTENT_TYPE, TEXT_MIME_TYPE);
                        response.end("failure");
                    }

                    myClient.close();
                });
            } catch (final FailedLoginException details) {
                final HttpServerResponse response = myContext.response();
                String loggerMessage;
                String responseBody;

                // Check to see why the login failed
                try {
                    // The following statement will throw DecodeException if details.getMessage()
                    // does not return a valid string-ified JsonObject
                    final JsonObject exceptionJsonMessage = new JsonObject(details.getMessage());

                    try {
                        // Log the concatenation of the JSON values
                        loggerMessage = exceptionJsonMessage.getString("message") + ": "
                            + exceptionJsonMessage.getString("email");
                    } catch (ClassCastException e) {
                        LOGGER.error(
                            "Cannot decode the error message of the exception thrown by extractJWT:"
                            + "invalid JSON");

                        // TODO: will never reach this statement, but should discuss what to do here
                        throw e;
                    }

                    // If we get here, send JSON response to client for easier parsing
                    response.putHeader(CONTENT_TYPE, JSON_MIME_TYPE);
                    // TODO: make a new constant for this error message (FAILURE_RESPONSE_BAD_EMAIL)
                    responseBody = details.getMessage();

                } catch (DecodeException e) {
                    loggerMessage = details.getMessage();

                    // If we get here, send plain-text response to client (won't be parsed)
                    // TODO: all responses should probably be the same format (JSON)
                    response.putHeader(CONTENT_TYPE, TEXT_MIME_TYPE);
                    responseBody = FAILURE_RESPONSE;
                }

                response.end(responseBody);

                LOGGER.error(loggerMessage);

                myClient.close();
            }
        }

        private JsonObject extractJWT(final JsonObject aJsonObject) throws FailedLoginException {
            final JsonObject jsonObject = new JsonObject();
            final String email = aJsonObject.getString("email", "");
            final String[] users = myConfig.getUsers();

            // If we don't have any configured users, we allow all
            if (users.length != 0) {
                boolean found = false;

                for (int index = 0; index < users.length; index++) {
                    if (users[index].equals(email)) {
                        found = true;
                    }
                }

                if (!found) {
                    if (email.equals("")) {
                        // TODO: might want to pass JSON instead of a string, for consistency with
                        // the 'else' block below
                        throw new FailedLoginException("No email was retrieved from OAuth");
                    } else {
                        // Use the extant JsonObject
                        jsonObject.put("message", "Not an allowed email");
                        jsonObject.put("email", email);
                        throw new FailedLoginException(jsonObject.toString());
                    }
                }
            }

            jsonObject.put("email", email);

            if (aJsonObject.containsKey("name")) {
                jsonObject.put("name", aJsonObject.getString("name"));
            } else {
                LOGGER.warn("User login JWT does not contain a name");
            }

            return jsonObject;
        }
    }
}
