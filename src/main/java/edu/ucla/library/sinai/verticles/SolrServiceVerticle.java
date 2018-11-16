
package edu.ucla.library.sinai.verticles;

import static edu.ucla.library.sinai.Constants.SOLR_SERVICE_KEY;
import static edu.ucla.library.sinai.util.SolrUtils.SOLR_OK_STATUS;
import static edu.ucla.library.sinai.util.SolrUtils.SOLR_STATUS;

import edu.ucla.library.sinai.services.SolrService;
import edu.ucla.library.sinai.services.impl.SolrServiceImpl;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceproxy.ProxyHelper;

/**
 * A very simple verticle that publishes the Solr service.
 *
 * TODO: Jiiify uses basically the same code, so perhaps this could become an imported class at some point?
 */
public class SolrServiceVerticle extends AbstractSinaiVerticle {

    private SolrService myService;

    @Override
    public void start(final Future<Void> aFuture) throws Exception {
        final String solr = getConfiguration().getSolrServer().getBaseURL() + "/admin/ping?wt=json";
        final HttpClient client = vertx.createHttpClient();
        final HttpClientRequest request;

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Trying to connect to Solr server: {}", solr);
        }

        // Create a connection to see if Solr responds at the expected location
        // The call to handlePingResponse is also necessary for setting up the connection

        request = client.getAbs(solr, response -> {
            handlePingResponse(response, aFuture, client);
        }).exceptionHandler(exceptionHandler -> {
            LOGGER.error("Couldn't connect to Solr server: [" + exceptionHandler.getMessage() + "]");
            client.close();
            aFuture.fail(exceptionHandler.getMessage());
        });

        request.end();
    }

    /**
     * Handle the response from Solr. If the response indicates everything ok, register the Solr service.
     */
    private void handlePingResponse(final HttpClientResponse aResponse, final Future<Void> aFuture,
            final HttpClient aClient) {

        // TODO: once ping is ready, delete the uncommented code and uncomment the commented code
        // Instantiate and register a Solr service

        if (aResponse.statusCode() == 200) {
            aResponse.bodyHandler(body -> {
                final String status = new JsonObject(body.toString()).getString(SOLR_STATUS);

                if (status != null && status.equals(SOLR_OK_STATUS)) {

                    // Instantiate and register a Solr service
                    myService = new SolrServiceImpl(getConfiguration(), vertx);
                    ProxyHelper.registerService(SolrService.class, vertx, myService, SOLR_SERVICE_KEY);

                    LOGGER.debug("Successfully connected to Solr server");

                    aClient.close();
                    aFuture.complete();
                } else {
                    aClient.close();
                    aFuture.fail("Unexpected Solr server status response: " + status);
                }
            });
        } else {
            LOGGER.error("Couldn't connect to Solr server: [" + aResponse.statusCode() + ": " + aResponse
                    .statusMessage() + "]");
            aClient.close();
            aFuture.fail(aResponse.statusMessage() + " [" + aResponse.statusCode() + "]");
        }
    }
}
