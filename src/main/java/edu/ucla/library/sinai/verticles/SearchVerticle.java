package edu.ucla.library.sinai.verticles;

import static edu.ucla.library.sinai.Constants.MESSAGES;
import static edu.ucla.library.sinai.Constants.SEARCH_SERVICE_KEY;
import static edu.ucla.library.sinai.Constants.SEARCH_SERVICE_MESSAGE_ADDRESS;
import static edu.ucla.library.sinai.Constants.SEARCH_SERVICE_ERROR_SOLR_FAILURE;

import edu.ucla.library.sinai.services.SearchService;
import edu.ucla.library.sinai.services.impl.SearchServiceImpl;
import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceproxy.ProxyHelper;

public class SearchVerticle extends AbstractSinaiVerticle {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass(), MESSAGES);

    private SearchService service;

    @Override
    public void start() {
        service = new SearchServiceImpl(getConfiguration(), vertx);
        // Register the service proxy on the event bus
        ProxyHelper.registerService(SearchService.class, vertx, service, SEARCH_SERVICE_KEY);

        // Tell our verticle to listen for search jobs
        vertx.eventBus().<JsonObject>consumer(SEARCH_SERVICE_MESSAGE_ADDRESS).handler(aMessage -> {
            service.search(aMessage.body().getString("searchQuery"), promise -> {
                if (promise.failed()) {
                    aMessage.fail(SEARCH_SERVICE_ERROR_SOLR_FAILURE, promise.cause().toString());
                } else {
                    aMessage.reply(promise.result());
                }
            });
        });
    }
}
