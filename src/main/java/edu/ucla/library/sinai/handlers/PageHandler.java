
package edu.ucla.library.sinai.handlers;

import static edu.ucla.library.sinai.Constants.HBS_DATA_KEY;
import static edu.ucla.library.sinai.Constants.SOLR_SERVICE_KEY;
import static edu.ucla.library.sinai.RoutePatterns.BROWSE_RE;
import static edu.ucla.library.sinai.handlers.FailureHandler.ERROR_HEADER;
import static edu.ucla.library.sinai.handlers.FailureHandler.ERROR_MESSAGE;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.ucla.library.sinai.Configuration;
import edu.ucla.library.sinai.services.SolrService;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * A generic page handler.
 */
public class PageHandler extends SinaiHandler {

    public PageHandler(final Configuration aConfig) {
        super(aConfig);
    }

    /**
     * The basic handle method for a Handler<RoutingContext>.
     */
    @Override
    public void handle(final RoutingContext aContext) {
        final JsonObject jsonNode;
        final String errorMessage;

        final JsonObject templateJson = new JsonObject();

        // If user is navigating to the browse page, need to load metadata
        if (aContext.normalisedPath().equals(BROWSE_RE)) {
            final SolrService service = SolrService.createProxy(aContext.vertx(), SOLR_SERVICE_KEY);
            final HttpMethod method = aContext.request().method();
            final String searchQueryParam = aContext.request().getParam("search");

            if (method == HttpMethod.GET) {

                // get all manuscript IDs from 1) published manuscripts and 2) UTOs that match search query
                final JsonObject manuscriptIdSolrQuery = new JsonObject()
                    .put("q", "keyword_t:" + (searchQueryParam == null ? "*" : ("\"" + searchQueryParam + "\"")) + " AND record_type:manuscript AND publish:true")
                    .put("fl", "id")
                    .put("group",  "true")
                    .put("group.main",  "true")
                    .put("group.field",  "id")
                    .put("rows", 10000000);

                final JsonObject utoManuscriptIdSolrQuery = new JsonObject()
                    .put("q", "keyword_t:" + (searchQueryParam == null ? "*" : ("\"" + searchQueryParam + "\"")) + " AND record_type:undertext_object")
                    .put("fl", "manuscript_id")
                    .put("group",  "true")
                    .put("group.main",  "true")
                    .put("group.field",  "manuscript_id")
                    .put("rows", 10000000);

                // 1) published manuscripts
                service.search(manuscriptIdSolrQuery, firstHandler -> {

                    final String firstHandlerErrorMessage;

                    if (firstHandler.succeeded()) {

                        // 2) UTOs
                        service.search(utoManuscriptIdSolrQuery, secondHandler -> {

                            final String secondHandlerErrorMessage;

                            if (secondHandler.succeeded()) {

                                // merge the manuscript ID numbers into one array
                                ArrayList<String> manuscriptIdList = new ArrayList<String>();

                                Function<Object, String> mapper1 = s -> ((JsonObject) s).getInteger("id").toString();
                                Function<Object, String> mapper2 = s -> ((JsonObject) s).getInteger("manuscript_id").toString();

                                Stream<Object> s1 = firstHandler.result().getJsonObject("response").getJsonArray("docs").stream();
                                ArrayList<String> firstList = s1.map(mapper1).collect(Collectors.toCollection(ArrayList::new));

                                Stream<Object> s2 = secondHandler.result().getJsonObject("response").getJsonArray("docs").stream();
                                ArrayList<String> secondList = s2.map(mapper2).collect(Collectors.toCollection(ArrayList::new));

                                manuscriptIdList.addAll(firstList);
                                manuscriptIdList.addAll(secondList);


                                final JsonArray searchResults = new JsonArray();

                                // check if we have any results
                                if (manuscriptIdList.size() > 0) {

                                    // get the full Solr documents this time
                                    final JsonObject manuscriptSolrQuery = new JsonObject()
                                        .put("q", "record_type:manuscript AND publish:true AND id:(" + String.join(" ", manuscriptIdList.toArray(new String[0])) + ")")
                                        .put("sort", "shelf_mark_s asc")
                                        .put("rows", 10000000);

                                    // again: first, manuscripts
                                    service.search(manuscriptSolrQuery, thirdHandler -> {

                                        final String thirdHandlerErrorMessage;

                                        if (thirdHandler.succeeded()) {

                                            final ArrayList<String> utoManuscriptIdList = thirdHandler.result().getJsonObject("response").getJsonArray("docs").stream()
                                                .map(mapper1).collect(Collectors.toCollection(ArrayList::new));

                                            if (utoManuscriptIdList.size() > 0) {
	                                            final JsonObject utoSolrQuery = new JsonObject()
	                                                    .put("q", "record_type:undertext_object AND manuscript_id:(" + String.join(" ", utoManuscriptIdList.toArray(new String[0])) + ")")
	                                                    .put("sort", "author_s asc")
	                                                    .put("rows", 10000000);

	                                            // second, UTOs
	                                            service.search(utoSolrQuery, fourthHandler -> {

	                                                final String fourthHandlerErrorMessage;

	                                                if (fourthHandler.succeeded()) {

	                                                    final JsonArray manuscripts = thirdHandler.result().getJsonObject("response").getJsonArray("docs");
	                                                    final JsonArray utos = fourthHandler.result().getJsonObject("response").getJsonArray("docs");

	                                                    // put each search result (manuscript with it's UTOs) into an array
	                                                    Iterator<Object> manuscriptsIt = manuscripts.iterator();
	                                                    while (manuscriptsIt.hasNext()) {
	                                                        JsonObject result = new JsonObject();
	                                                        JsonObject resultManuscript = (JsonObject) manuscriptsIt.next();
	                                                        JsonArray resultUtos = new JsonArray();

	                                                        result.put("manuscript", resultManuscript);

	                                                        Iterator<Object> utoIt = utos.iterator();
	                                                        while (utoIt.hasNext()) {
	                                                            JsonObject uto = (JsonObject) utoIt.next();
	                                                            if (uto.getInteger("manuscript_id") == resultManuscript.getInteger("id")) {
	                                                                resultUtos.add(uto);
	                                                            }
	                                                        }
	                                                        result.put("undertext_objects", resultUtos);

	                                                        searchResults.add(result);
	                                                    }
	                                                    templateJson.put("searchResults", searchResults);

	                                                    if (LOGGER.isDebugEnabled()) {
	                                                        LOGGER.debug("Sending to template: {}", templateJson.toString());
	                                                    }

	                                                    try {
	                                                        aContext.data().put(HBS_DATA_KEY, toHbsContext(templateJson, aContext));
	                                                        aContext.next();
	                                                    } catch (IOException e) {
	                                                        e.printStackTrace();

	                                                        fourthHandlerErrorMessage = msg("Handlebars context generation failed: {}", templateJson.toString());

	                                                        aContext.put(ERROR_HEADER, "Internal Server Error");
	                                                        aContext.put(ERROR_MESSAGE, fourthHandlerErrorMessage);
	                                                        fail(aContext, new Error(fourthHandlerErrorMessage));
	                                                    }
	                                                } else {
	                                                    // error
	                                                    fourthHandlerErrorMessage = fourthHandler.cause().getMessage();

	                                                    aContext.put(ERROR_HEADER, "Solr Search Error");
	                                                    aContext.put(ERROR_MESSAGE, fourthHandlerErrorMessage);
	                                                    fail(aContext, fourthHandler.cause());
	                                                }
	                                            });
                                            } else {
                                                // no uto results
                                                final JsonArray manuscripts = thirdHandler.result().getJsonObject("response").getJsonArray("docs");

                                                // put each search result (manuscript) into an array
                                                Iterator<Object> manuscriptsIt = manuscripts.iterator();
                                                while (manuscriptsIt.hasNext()) {
                                                    JsonObject result = new JsonObject();
                                                    JsonObject resultManuscript = (JsonObject) manuscriptsIt.next();
                                                    JsonArray resultUtos = new JsonArray();

                                                    result.put("manuscript", resultManuscript);

                                                    searchResults.add(result);
                                                }
                                                templateJson.put("searchResults", searchResults);

                                                if (LOGGER.isDebugEnabled()) {
                                                    LOGGER.debug("Sending to template: {}", templateJson.toString());
                                                }

                                                try {
                                                    aContext.data().put(HBS_DATA_KEY, toHbsContext(templateJson, aContext));
                                                    aContext.next();
                                                } catch (IOException e) {
                                                    e.printStackTrace();

                                                    final String errorMessageNoUtoResults = msg("Handlebars context generation failed: {}", templateJson.toString());

                                                    aContext.put(ERROR_HEADER, "Internal Server Error");
                                                    aContext.put(ERROR_MESSAGE, errorMessageNoUtoResults);
                                                    fail(aContext, new Error(errorMessageNoUtoResults));
                                                }
                                            }
                                        } else {
                                            // error
                                            thirdHandlerErrorMessage = thirdHandler.cause().getMessage();

                                            aContext.put(ERROR_HEADER, "Solr Search Error");
                                            aContext.put(ERROR_MESSAGE, thirdHandlerErrorMessage);
                                            fail(aContext, thirdHandler.cause());
                                        }
                                    });
                                } else {
                                    // no results for the search
                                    templateJson.put("searchResults", searchResults);

                                    try {
                                        aContext.data().put(HBS_DATA_KEY, toHbsContext(templateJson, aContext));
                                        aContext.next();
                                    } catch (IOException e) {
                                        e.printStackTrace();

                                        secondHandlerErrorMessage = msg("Handlebars context generation failed: {}", templateJson.toString());

                                        aContext.put(ERROR_HEADER, "Internal Server Error");
                                        aContext.put(ERROR_MESSAGE, secondHandlerErrorMessage);
                                        fail(aContext, new Error(secondHandlerErrorMessage));
                                    }
                                }
                            } else {
                                // failure
                                secondHandlerErrorMessage = secondHandler.cause().getMessage();

                                aContext.put(ERROR_HEADER, "Solr Search Error");
                                aContext.put(ERROR_MESSAGE, secondHandlerErrorMessage);
                                fail(aContext, secondHandler.cause());
                            }
                        });
                    } else {
                        firstHandlerErrorMessage = msg("Solr search failed: {}", firstHandler.cause().getMessage());

                        aContext.put(ERROR_HEADER, "Solr Search Error");
                        aContext.put(ERROR_MESSAGE, firstHandlerErrorMessage);
                        fail(aContext, firstHandler.cause());
                    }
                });

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
                aContext.data().put(HBS_DATA_KEY, toHbsContext(templateJson, aContext));
                aContext.next();
            } catch (IOException e) {
                e.printStackTrace();

                errorMessage = msg("Handlebars context generation failed: {}", templateJson.toString());

                aContext.put(ERROR_HEADER, "Internal Server Error");
                aContext.put(ERROR_MESSAGE, errorMessage);
                fail(aContext, new Error(errorMessage));
            }
        }
    }

}
