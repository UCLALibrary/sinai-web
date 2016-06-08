
package edu.ucla.library.sinai.verticles;

import static edu.ucla.library.sinai.Configuration.DEFAULT_SESSION_TIMEOUT;
import static edu.ucla.library.sinai.Constants.CONFIG_KEY;
import static edu.ucla.library.sinai.Constants.JCEKS_PROP;
import static edu.ucla.library.sinai.Constants.JKS_PROP;
import static edu.ucla.library.sinai.Constants.KEY_PASS_PROP;
import static edu.ucla.library.sinai.Constants.SHARED_DATA_KEY;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import javax.naming.ConfigurationException;

import info.freelibrary.util.IOUtils;

import edu.ucla.library.sinai.Configuration;
import edu.ucla.library.sinai.RoutePatterns;
import edu.ucla.library.sinai.handlers.FailureHandler;
import edu.ucla.library.sinai.handlers.LoginHandler;
import edu.ucla.library.sinai.handlers.LogoutHandler;
import edu.ucla.library.sinai.handlers.MetricsHandler;
import edu.ucla.library.sinai.handlers.MiradorHandler;
import edu.ucla.library.sinai.handlers.PageHandler;
import edu.ucla.library.sinai.handlers.SearchHandler;
import edu.ucla.library.sinai.handlers.StatusHandler;
import edu.ucla.library.sinai.templates.HandlebarsTemplateEngine;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CookieHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.TemplateHandler;
import io.vertx.ext.web.handler.UserSessionHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import io.vertx.ext.web.templ.TemplateEngine;

public class SinaiMainVerticle extends AbstractSinaiVerticle implements RoutePatterns {

    private Configuration myConfig;

    @Override
    public void start(final Future<Void> aFuture) throws ConfigurationException, IOException {
        final SessionHandler sessionHandler = SessionHandler.create(LocalSessionStore.create(vertx));
        final TemplateEngine templateEngine = HandlebarsTemplateEngine.create();
        final TemplateHandler templateHandler = TemplateHandler.create(templateEngine);
        final HttpServerOptions options = new HttpServerOptions();
        final Router router = Router.router(vertx);
        final JWTAuth jwtAuth;

        // Store our parsed configuration so we can access it when needed
        myConfig = new Configuration(config());
        vertx.sharedData().getLocalMap(SHARED_DATA_KEY).put(CONFIG_KEY, myConfig);

        // Set the port on which we want to listen for connections
        options.setPort(myConfig.getPort());
        options.setHost("0.0.0.0");
        options.setCompressionSupported(true);

        // Use https or http, but switching between them requires re-ingesting everything
        if (myConfig.usesHttps()) {
            final String jksProperty = System.getProperty(JKS_PROP, JKS_PROP);
            final String ksPassword = System.getProperty(KEY_PASS_PROP, "");
            final JksOptions jksOptions = new JksOptions().setPassword(ksPassword);
            final JsonObject jceksConfig = new JsonObject();
            final File jksFile = new File(jksProperty);

            jceksConfig.put("path", JCEKS_PROP).put("type", "jceks").put("password", ksPassword);
            jwtAuth = JWTAuth.create(vertx, new JsonObject().put("keyStore", jceksConfig));

            if (jksFile.exists()) {
                LOGGER.info("Using a system JKS configuration: {}", jksFile);
                jksOptions.setPath(jksFile.getAbsolutePath());
            } else {
                final InputStream inStream = getClass().getResourceAsStream("/" + jksProperty);

                if (inStream != null) {
                    jksOptions.setValue(Buffer.buffer(IOUtils.readBytes(inStream)));
                } else {
                    LOGGER.debug("Trying to use the build's default JKS: {}", jksProperty);
                    jksOptions.setPath("target/classes/" + jksProperty);
                }
            }

            options.setSsl(true).setKeyStoreOptions(jksOptions);
            sessionHandler.setCookieHttpOnlyFlag(true).setCookieSecureFlag(true);
            sessionHandler.setSessionTimeout(DEFAULT_SESSION_TIMEOUT);

            configureHttpRedirect(aFuture);
        } else {
            jwtAuth = null;
        }

        // Some reused handlers
        final FailureHandler failureHandler = new FailureHandler(myConfig, templateEngine);
        final SearchHandler searchHandler = new SearchHandler(myConfig);

        // Configure some basics
        router.route().handler(BodyHandler.create().setUploadsDirectory(myConfig.getTempDir().getAbsolutePath()));
        router.route().handler(CookieHandler.create());
        router.route().handler(sessionHandler);

        final LoginHandler loginHandler = new LoginHandler(myConfig, jwtAuth);
        final LogoutHandler logoutHandler = new LogoutHandler(myConfig);

        // Serve static files like images, scripts, css, etc.
        router.getWithRegex(STATIC_FILES_RE).handler(StaticHandler.create());

        // Put everything behind an authentication check
        if (jwtAuth != null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Using the JWT authentication handler");
            }

            router.route().handler(UserSessionHandler.create(jwtAuth));
            router.route().handler(JWTAuthHandler.create(jwtAuth, "/login-response"));
        }

        // Login and logout routes
        router.get(LOGOUT).handler(logoutHandler);
        router.get(ROOT).handler(loginHandler);
        router.post(ROOT).handler(loginHandler);
        router.getWithRegex(LOGIN_RESPONSE_RE).handler(loginHandler);
        router.getWithRegex(LOGIN_RESPONSE_RE).handler(templateHandler).failureHandler(failureHandler);

        // Route for Mirador viewing
        router.getWithRegex(VIEWER_RE).handler(new MiradorHandler(myConfig));

        // Then we have the plain old administrative UI patterns
        router.getWithRegex(METRICS_RE).handler(new MetricsHandler(myConfig));

        // We just use the generic handler for browse page
        router.get(BROWSE).handler(new PageHandler(myConfig));

        // Create a catch-all that passes content to the template handler
        router.get().handler(templateHandler).failureHandler(failureHandler);

        // Configure our StatusHandler, used by the Nagios script
        router.get(STATUS).handler(new StatusHandler(myConfig));

        // Start the server and start listening for connections
        vertx.createHttpServer(options).requestHandler(router::accept).listen(response -> {
            if (response.succeeded()) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("{} deployed: {}", SinaiMainVerticle.class.getName(), deploymentID());
                }

                aFuture.complete();
            } else {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Sinai server started at port: {}", myConfig.getPort());
                }

                aFuture.fail(response.cause());
            }
        });
    }

    /**
     * Redirect all requests to the non-secure port to the secure port when there is a secure port available.
     *
     * @param aFuture A verticle future that we can fail if we can't bind to the redirect port
     */
    private void configureHttpRedirect(final Future<Void> aFuture) {
        vertx.createHttpServer().requestHandler(redirect -> {
            final HttpServerResponse response = redirect.response();
            final String httpsURL = "https://" + myConfig.getHost() + ":" + myConfig.getPort() + redirect.uri();

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Redirecting HTTP request to: {}", httpsURL);
            }

            response.setStatusCode(303).putHeader("Location", httpsURL).end();
            response.close();
        }).listen(myConfig.getRedirectPort(), response -> {
            if (response.failed()) {
                if (response.cause() != null) {
                    LOGGER.error("{}", response.cause(), response.cause());
                }

                aFuture.fail(LOGGER.getMessage("Could not configure redirect port: {}", myConfig.getRedirectPort()));
            }
        });

        // FIXME: Accidentally connecting to http port with a https connection fails badly
        // https://bugs.eclipse.org/bugs/show_bug.cgi?id=479488
    }

}
