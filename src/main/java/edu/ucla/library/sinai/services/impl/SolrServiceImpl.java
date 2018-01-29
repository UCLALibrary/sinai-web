
package edu.ucla.library.sinai.services.impl;

import static edu.ucla.library.sinai.Constants.MESSAGES;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Iterator;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.sinai.Configuration;
import edu.ucla.library.sinai.Metadata;
import edu.ucla.library.sinai.services.SolrService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.JsonObject;

/**
 * Solr service implementation.
 */
public class SolrServiceImpl implements SolrService {

    private final Logger LOGGER = LoggerFactory.getLogger(SolrServiceImpl.class, MESSAGES);

    private final Configuration myConfig;

    private final Vertx myVertx;

    public SolrServiceImpl(final Configuration aConfig, final Vertx aVertx) {
        myConfig = aConfig;
        myVertx = aVertx;
    }

    /**
     * Query Solr with the query params supplied in aJsonObject.
     */
    @Override
    public void search(final JsonObject aJsonObject, final Handler<AsyncResult<JsonObject>> aHandler) {
        String solr = myConfig.getSolrServer().getBaseURL() + "/query";
        String queryString = "?";
        Iterator<String> keys = aJsonObject.fieldNames().iterator();

        int queryCounter = 0;
        while(keys.hasNext()) {
            String key = keys.next();
            String value;
            try {
                value = aJsonObject.getString(key);
            } catch (ClassCastException e) {
                value = aJsonObject.getInteger(key).toString();
            }
            try {
                queryString += ((queryCounter == 0) ? "" : "&") + key + "=" + URLEncoder.encode(value, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                aHandler.handle(Future.failedFuture("Cannot encode Solr query URL"));
            }
            queryCounter++;
        }

        solr += queryString;
        final HttpClient client = myVertx.createHttpClient();
        final HttpClientRequest request;

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Sending Solr query to: {}", solr);
        }

        request = client.getAbs(solr, response -> {
            if (response.statusCode() == 200) {
                response.bodyHandler(body -> {
                    aHandler.handle(Future.succeededFuture(new JsonObject(body.toString())));
                });
            } else {
                aHandler.handle(Future.failedFuture(response.statusMessage()));
            }
        }).exceptionHandler(exceptionHandler -> {
            aHandler.handle(Future.failedFuture(exceptionHandler));
        });

        request.end();
        client.close();

    }

    /**
     * Update Solr with the params supplied in aJsonObject.
     */
    @Override
    public void index(final JsonObject aJsonObject, final Handler<AsyncResult<String>> aHandler) {
        String solr = myConfig.getSolrServer().getBaseURL() + "/update?json.command=false&commit=true";
        final HttpClient client = myVertx.createHttpClient();
        final HttpClientRequest request;

        request = client.postAbs(solr, response -> {
            if (response.statusCode() == 200) {
                aHandler.handle(Future.succeededFuture());
            } else {
                aHandler.handle(Future.failedFuture(response.statusMessage()));
            }
        }).exceptionHandler(exceptionHandler -> {
            aHandler.handle(Future.failedFuture(exceptionHandler));
        });

        request.putHeader(Metadata.CONTENT_TYPE, Metadata.JSON_MIME_TYPE);
        request.end(aJsonObject.toString());
        client.close();
    }

}
