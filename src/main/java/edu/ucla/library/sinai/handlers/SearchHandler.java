
package edu.ucla.library.sinai.handlers;

import static edu.ucla.library.sinai.Constants.HBS_DATA_KEY;
import static edu.ucla.library.sinai.Constants.SEARCH_VERTICLE_MESSAGE_ADDRESS;
import static edu.ucla.library.sinai.RoutePatterns.SEARCH_RESULTS_RE;
import static edu.ucla.library.sinai.handlers.FailureHandler.ERROR_HEADER;
import static edu.ucla.library.sinai.handlers.FailureHandler.ERROR_MESSAGE;

import java.io.IOException;

import com.github.jknack.handlebars.Context;

import info.freelibrary.util.StringUtils;

import edu.ucla.library.sinai.Configuration;
import edu.ucla.library.sinai.Constants;
import edu.ucla.library.sinai.templates.impl.ShareableContext;

import io.vertx.core.Future;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.core.shareddata.SharedData;
import io.vertx.ext.web.RoutingContext;

/**
 * A generic page handler.
 */
public class SearchHandler extends SinaiHandler {

    public SearchHandler(final Configuration aConfig) {
        super(aConfig);
    }

    /**
     * The basic handle method for a Handler<RoutingContext>.
     */
    @Override
    public void handle(final RoutingContext aContext) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Handling request on PageHandler");
        }
        final String errorMessage;

        // If user is navigating to the browse page, need to load metadata
        if (aContext.normalisedPath().equals(SEARCH_RESULTS_RE)) {
            final HttpMethod method = aContext.request().method();
            final String searchQueryParam = aContext.request().getParam("search");

            if (method == HttpMethod.GET) {
                final SharedData sharedData = aContext.vertx().sharedData();
                final LocalMap<String, ShareableContext> cache = sharedData.getLocalMap(Constants.SEARCH_CACHE_KEY);
                final String solrQueryString = StringUtils.trimToNull(searchQueryParam) == null ? "*" : "\"" + searchQueryParam + "\"";

                // Check cache to see if we've already done and cached this search; use those results if we have
                if (cache != null && cache.containsKey(solrQueryString)) {
                    aContext.data().put(HBS_DATA_KEY, cache.get(solrQueryString).getHandlebarsContext());
                    aContext.next();
                } else {
                    final DeliveryOptions searchMsgDeliveryOpts =
                            new DeliveryOptions().setSendTimeout(myConfig.getSearchTimeout());
                    final JsonObject searchMsg = new JsonObject().put("searchQuery", solrQueryString);

                    // Delegate the search result processing to SearchVerticle.
                    aContext.vertx().eventBus().send(SEARCH_VERTICLE_MESSAGE_ADDRESS, searchMsg, searchMsgDeliveryOpts, reply -> {
                        if (reply.succeeded()) {
                            final JsonObject searchResults = new JsonObject()
                                .put("searchResults", reply.result().body());

                            LOGGER.info("New search succeeded");
                            if (LOGGER.isDebugEnabled()) {
                                LOGGER.debug("Sending search results to template engine: {}", searchResults.toString());
                            }

                            // The markup generation takes a while, so use vertx.executeBlocking.
                            aContext.vertx().executeBlocking(promise -> {
                                try {
                                    promise.complete(toHbsContext(searchResults, aContext));
                                } catch (final IOException details) {
                                    final String myErrorMessage = msg("Handlebars context generation failed: {}", details.getMessage());
                                    promise.complete(Future.failedFuture(myErrorMessage));
                                }
                            }, ar -> {
                                if (ar.succeeded()) {
                                    final Context context = (Context) ar.result();

                                    // Put our search results in an in-memory cache so they can be reused
                                    cache.put(solrQueryString, new ShareableContext(context));

                                    aContext.data().put(HBS_DATA_KEY, context);
                                    aContext.next();
                                } else {
                                    // toHbsContext threw an exception
                                    final Throwable errorHbs = ar.cause();

                                    aContext.put(ERROR_MESSAGE, errorHbs);
                                    aContext.fail(500);
                                }
                            });
                        } else {
                            final Throwable searchError = reply.cause();
                            final String searchErrorUserMsg = "Search failed. Please try again later or <a href=\"/contacts\">contact us</a> for assistance.";

                            LOGGER.info(searchError.toString());
                            if (LOGGER.isDebugEnabled()) {
                                searchError.printStackTrace();
                            }

                            aContext.put(ERROR_MESSAGE, searchErrorUserMsg);
                            aContext.fail(503);
                        }
                    });
                }
            } else {
                // not supported
                errorMessage = msg("Request failed: {}", "this route only supports GET");

                aContext.put(ERROR_HEADER, "Request not allowed");
                aContext.put(ERROR_MESSAGE, errorMessage);

                fail(aContext, new Error(errorMessage));
            }
        } else {
            // We also need what's set in SinaiHandler
            try {
                aContext.data().put(HBS_DATA_KEY, toHbsContext(new JsonObject(), aContext));
                aContext.next();
            } catch (final IOException details) {
                errorMessage = msg("Handlebars context generation failed: {}", new JsonObject().toString());

                aContext.put(ERROR_HEADER, "Internal Server Error");
                aContext.put(ERROR_MESSAGE, errorMessage);

                fail(aContext, new Error(errorMessage));
            }
        }
    }
}
