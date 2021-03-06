
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
import java.util.ArrayList;
import java.util.List;

import javax.naming.ConfigurationException;

import com.fasterxml.jackson.core.JsonProcessingException;

import info.freelibrary.util.IOUtils;

import edu.ucla.library.sinai.Configuration;
import edu.ucla.library.sinai.RoutePatterns;
import edu.ucla.library.sinai.handlers.AdminHandler;
import edu.ucla.library.sinai.handlers.FailureHandler;
import edu.ucla.library.sinai.handlers.LoginHandler;
import edu.ucla.library.sinai.handlers.LogoutHandler;
import edu.ucla.library.sinai.handlers.MetricsHandler;
import edu.ucla.library.sinai.handlers.MiradorHandler;
import edu.ucla.library.sinai.handlers.PDFProxyHandler;
import edu.ucla.library.sinai.handlers.SearchHandler;
import edu.ucla.library.sinai.handlers.StatusHandler;
import edu.ucla.library.sinai.templates.HandlebarsTemplateEngine;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
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
    public void start(final Future<Void> aFuture) throws ConfigurationException, IOException,
            JsonProcessingException {
        new Configuration(config(), vertx, configHandler -> {
            if (configHandler.succeeded()) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("App configured successfully");
                }
                myConfig = configHandler.result();

                deploySinaiVerticles(deployHandler -> {
                    if (deployHandler.succeeded()) {
                        initializeMainVerticle(aFuture);
                    } else {
                        aFuture.fail(deployHandler.cause());
                    }
                });
            } else {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.error("App configuration failed: [ " + configHandler.cause().toString() + " ]");
                }
                aFuture.fail(configHandler.cause());
            }
        });
    }

    private void initializeMainVerticle(final Future<Void> aFuture) {
        final SessionHandler sessionHandler = SessionHandler.create(LocalSessionStore.create(vertx));
        final TemplateEngine templateEngine = HandlebarsTemplateEngine.create();
        final TemplateHandler templateHandler = TemplateHandler.create(templateEngine);
        final HttpServerOptions options = new HttpServerOptions();
        final Router router = Router.router(vertx);
        final JWTAuth jwtAuth;

        // Store our parsed configuration so we can access it when needed
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

            // Get JKS from an external configuration file
            if (jksFile.exists()) {
                LOGGER.info("Using a system JKS configuration: {}", jksFile);
                jksOptions.setPath(jksFile.getAbsolutePath());
            } else {
                final InputStream inStream = getClass().getResourceAsStream("/" + jksProperty);

                // Get JKS configuration from a configuration file in the jar file
                if (inStream != null) {
                    LOGGER.debug("Loading JKS configuration from jar file");

                    try {
                        jksOptions.setValue(Buffer.buffer(IOUtils.readBytes(inStream)));
                    } catch (final IOException details) {
                        throw new RuntimeException(details);
                    }
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

        // Configure some basics
        router.route().handler(BodyHandler.create().setUploadsDirectory(myConfig.getTempDir().getAbsolutePath()));
        router.route().handler(CookieHandler.create());
        router.route().handler(sessionHandler);

        final LoginHandler loginHandler = new LoginHandler(myConfig, jwtAuth);
        final LogoutHandler logoutHandler = new LogoutHandler(myConfig);
        final AdminHandler adminHandler = new AdminHandler(myConfig);
        final SearchHandler searchHandler = new SearchHandler(myConfig);
        final PDFProxyHandler pdfProxyHandler = new PDFProxyHandler(myConfig);
        final MiradorHandler miradorHandler = new MiradorHandler(myConfig);

        // Serve static files like images, scripts, css, etc.
        router.getWithRegex(STATIC_FILES_RE).handler(StaticHandler.create());

        // Authentication check
        if (jwtAuth != null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Using the JWT authentication handler");
            }

            router.route().handler(UserSessionHandler.create(jwtAuth));
            router.getWithRegex(AUTHENTICATION_CHECK_RE).handler(JWTAuthHandler.create(jwtAuth));
        }

        router.get(ROOT).handler(loginHandler);
        router.getWithRegex(LOGIN_RESPONSE_RE).handler(loginHandler);
        router.getWithRegex(LOGIN_RESPONSE_RE).handler(templateHandler).failureHandler(failureHandler);
        router.get(LOGOUT).handler(logoutHandler);

        // Route for Mirador viewing
        router.getWithRegex(VIEWER_RE).handler(miradorHandler);
        router.postWithRegex(VIEWER_RE).handler(miradorHandler);

        // Then we have the plain old administrative UI patterns
        router.getWithRegex(METRICS_RE).handler(new MetricsHandler(myConfig));

        router.getWithRegex(SEARCH_RESULTS_RE).handler(searchHandler);

        router.get(ADMIN).handler(adminHandler);
        router.post(ADMIN).handler(adminHandler);

        // Handle PDF requests before the catch-all handler (which is never passed control on requests at these routes)
        router.getWithRegex(PDF_PROXY_RE).handler(pdfProxyHandler);
        router.getWithRegex(PDF_RE).handler(StaticHandler.create());

        // Create a catch-all that passes content to the template handler
        router.get().handler(templateHandler).failureHandler(failureHandler);

        // Pass content to template handler on POST requests to viewer only
        router.postWithRegex(VIEWER_RE).handler(templateHandler).failureHandler(failureHandler);

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

    @SuppressWarnings("rawtypes")
    private void deploySinaiVerticles(final Handler<AsyncResult<Void>> aHandler) {
        final DeploymentOptions metadataHarvestWorkerOptions = new DeploymentOptions();
        final DeploymentOptions searchWorkerOptions = new DeploymentOptions();
        final DeploymentOptions options = new DeploymentOptions();
        final List<Future> futures = new ArrayList<>();
        final Future<Void> future = Future.future();

        if (aHandler != null) {
            future.setHandler(aHandler);

            // Pool size of one should be fine since it's only run once per day [Implicit: .setInstances(1)]
            metadataHarvestWorkerOptions.setWorkerPoolName("Indexing pool").setWorkerPoolSize(1).setWorker(true);
            searchWorkerOptions.setWorkerPoolName("Searching pool").setWorkerPoolSize(4).setWorker(true);

            futures.add(deployVerticle(SolrServiceVerticle.class.getName(), options, Future.future()));
            futures.add(deployVerticle(MetadataHarvestVerticle.class.getName(), metadataHarvestWorkerOptions, Future.future()));
            futures.add(deployVerticle(SearchVerticle.class.getName(), searchWorkerOptions, Future.future()));

            // Confirm all our verticles were successfully deployed
            CompositeFuture.all(futures).setHandler(handler -> {
                if (handler.succeeded()) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("All verticles were deployed successfully");
                    }

                    future.complete();
                } else {
                    LOGGER.error("One or more verticles failed to deploy");
                    future.fail(handler.cause());
                }
            });
        }
    }

    /**
     * Deploys a particular verticle.
     *
     * @param aVerticleName The name of the verticle to deploy
     * @param aOptions Any deployment options that should be considered
     */
    private Future<Void> deployVerticle(final String aVerticleName, final DeploymentOptions aOptions,
            final Future<Void> aFuture) {
        vertx.deployVerticle(aVerticleName, aOptions, response -> {
            try {
                final String name = Class.forName(aVerticleName).getSimpleName();

                if (response.succeeded()) {
                    LOGGER.debug("Successfully deployed {} [{}]", name, response.result());
                    aFuture.complete();
                } else {
                    LOGGER.error("Failed to launch {}", name, response.cause());
                    aFuture.fail(response.cause());
                }
            } catch (final ClassNotFoundException details) {
                aFuture.fail(details);
            }
        });

        return aFuture;
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
