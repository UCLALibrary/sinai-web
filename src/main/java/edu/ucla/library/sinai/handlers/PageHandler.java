
package edu.ucla.library.sinai.handlers;

import static edu.ucla.library.sinai.Constants.HBS_DATA_KEY;
import static edu.ucla.library.sinai.Constants.SOLR_SERVICE_KEY;
import static edu.ucla.library.sinai.RoutePatterns.BROWSE_RE;
import static edu.ucla.library.sinai.handlers.FailureHandler.ERROR_HEADER;
import static edu.ucla.library.sinai.handlers.FailureHandler.ERROR_MESSAGE;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.ucla.library.sinai.Configuration;
import edu.ucla.library.sinai.services.SolrService;
import edu.ucla.library.sinai.util.SearchResultComparator;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * A generic page handler.
 */
public class PageHandler extends SinaiHandler {

    private JsonArray manuscripts = new JsonArray();
    private JsonArray undertextObjects = new JsonArray();
    private JsonArray manuscriptComponents = new JsonArray();
    private JsonArray overtextLayers = new JsonArray();
    private JsonArray undertextLayers = new JsonArray();

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

        // If user is navigating to the browse page, need to load metadata
        if (aContext.normalisedPath().equals(BROWSE_RE)) {
            final SolrService service = SolrService.createProxy(aContext.vertx(), SOLR_SERVICE_KEY);
            final HttpMethod method = aContext.request().method();
            final String searchQueryParam = aContext.request().getParam("search");

            if (method == HttpMethod.GET) {

                // get all manuscript IDs from all records that match search
                final JsonObject manuscriptIdSolrQuery = new JsonObject()
                        .put("q", "keyword_t:" + (searchQueryParam == null ? "*" : ("\"" + searchQueryParam + "\"")) + " AND manuscript_id_i:[* TO *]")
                        .put("fl", "manuscript_id_i").put("group", "true").put("group.main", "true")
                        .put("group.field", "manuscript_id_i").put("rows", 10000000);

                service.search(manuscriptIdSolrQuery, manuscriptIdSolrSearch -> {

                    final String firstHandlerErrorMessage;

                    if (manuscriptIdSolrSearch.succeeded()) {

                        final JsonObject solrResponse = manuscriptIdSolrSearch.result().getJsonObject("response");

                        // If we get any hits, return a list of manuscripts
                        if (solrResponse.getInteger("numFound") > 0) {

                            Function<Object, String> getManuscriptId = solrResponseDocument -> {
                                return ((JsonObject) solrResponseDocument).getInteger("manuscript_id_i").toString();
                            };
                            Stream<Object> solrResponseDocumentStream = solrResponse.getJsonArray("docs").stream();
                            ArrayList<String> manuscriptIdList = solrResponseDocumentStream.map(getManuscriptId)
                                    .collect(Collectors.toCollection(ArrayList::new));

                            final JsonObject manuscriptsSolrQuery = new JsonObject()
                                    .put("q",
                                            "record_type_s:manuscript AND publish_b:true AND manuscript_id_i:("
                                                    + String.join(" ", manuscriptIdList.toArray(new String[0])) + ")")
                                    .put("sort", "shelf_mark_s asc").put("rows", 10000000);
                            final JsonObject undertextObjectsSolrQuery = new JsonObject()
                                    .put("q",
                                            "record_type_s:undertext_object AND manuscript_id_i:("
                                                    + String.join(" ", manuscriptIdList.toArray(new String[0])) + ")")
                                    .put("sort", "primary_language_s asc").put("rows", 10000000);
                            final JsonObject manuscriptComponentsSolrQuery = new JsonObject()
                                    .put("q",
                                            "record_type_s:manuscript_component AND manuscript_id_i:("
                                                    + String.join(" ", manuscriptIdList.toArray(new String[0])) + ")")
                                    .put("sort", "position_i asc").put("rows", 10000000);
                            final JsonObject overtextLayersSolrQuery = new JsonObject()
                                    .put("q",
                                            "record_type_s:overtext_layer AND manuscript_id_i:("
                                                    + String.join(" ", manuscriptIdList.toArray(new String[0])) + ")")
                                    .put("rows", 10000000);
                            final JsonObject undertextLayersSolrQuery = new JsonObject()
                                    .put("q",
                                            "record_type_s:undertext_layer AND manuscript_id_i:("
                                                    + String.join(" ", manuscriptIdList.toArray(new String[0])) + ")")
                                    .put("rows", 10000000);

                            /*
                             *  Each of these handlers sets one of the private member variables of the enclosing class,
                             *  to be used by combineSearchResults.
                             *
                             *  Each one calls the handler directly above it, except for undertextLayersSearchHandler,
                             *  which calls combineSearchResults.
                             */
                            Handler<AsyncResult<JsonObject>> undertextLayersSolrSearchHandler = search -> {
                                if (search.succeeded()) {
                                    undertextLayers = search.result().getJsonObject("response").getJsonArray("docs");
                                    combineSearchResults(aContext);
                                } else {
                                    aContext.put(ERROR_HEADER, "Solr Search Error");
                                    aContext.put(ERROR_MESSAGE, search.cause().getMessage());
                                    fail(aContext, search.cause());
                                }
                            };
                            Handler<AsyncResult<JsonObject>> overtextLayersSolrSearchHandler = search -> {
                                if (search.succeeded()) {
                                    overtextLayers = search.result().getJsonObject("response").getJsonArray("docs");
                                    service.search(undertextLayersSolrQuery, undertextLayersSolrSearchHandler);
                                } else {
                                    aContext.put(ERROR_HEADER, "Solr Search Error");
                                    aContext.put(ERROR_MESSAGE, search.cause().getMessage());
                                    fail(aContext, search.cause());
                                }
                            };
                            Handler<AsyncResult<JsonObject>> manuscriptComponentsSolrSearchHandler = search -> {
                                if (search.succeeded()) {
                                    manuscriptComponents = search.result().getJsonObject("response")
                                            .getJsonArray("docs");
                                    service.search(overtextLayersSolrQuery, overtextLayersSolrSearchHandler);
                                } else {
                                    aContext.put(ERROR_HEADER, "Solr Search Error");
                                    aContext.put(ERROR_MESSAGE, search.cause().getMessage());
                                    fail(aContext, search.cause());
                                }
                            };

                            Handler<AsyncResult<JsonObject>> undertextObjectsSolrSearchHandler = search -> {
                                if (search.succeeded()) {
                                    undertextObjects = search.result().getJsonObject("response").getJsonArray("docs");
                                    service.search(manuscriptComponentsSolrQuery, manuscriptComponentsSolrSearchHandler);
                                } else {
                                    aContext.put(ERROR_HEADER, "Solr Search Error");
                                    aContext.put(ERROR_MESSAGE, search.cause().getMessage());
                                    fail(aContext, search.cause());
                                }
                            };
                            Handler<AsyncResult<JsonObject>> manuscriptsSolrSearchHandler = search -> {
                                if (search.succeeded()) {
                                    manuscripts = search.result().getJsonObject("response").getJsonArray("docs");
                                    service.search(undertextObjectsSolrQuery, undertextObjectsSolrSearchHandler);
                                } else {
                                    aContext.put(ERROR_HEADER, "Solr Search Error");
                                    aContext.put(ERROR_MESSAGE, search.cause().getMessage());
                                    fail(aContext, search.cause());
                                }
                            };

                            // Start searching!
                            service.search(manuscriptsSolrQuery, manuscriptsSolrSearchHandler);
                        } else {
                            // no results; set these members after each search
                            manuscripts = new JsonArray();
                            undertextObjects = new JsonArray();
                            manuscriptComponents = new JsonArray();
                            overtextLayers = new JsonArray();
                            undertextLayers = new JsonArray();

                            combineSearchResults(aContext);
                        }
                    } else {
                        firstHandlerErrorMessage = msg("Solr search failed: {}", manuscriptIdSolrSearch.cause().getMessage());

                        aContext.put(ERROR_HEADER, "Solr Search Error");
                        aContext.put(ERROR_MESSAGE, firstHandlerErrorMessage);
                        fail(aContext, manuscriptIdSolrSearch.cause());
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
                aContext.data().put(HBS_DATA_KEY, toHbsContext(new JsonObject(), aContext));
                aContext.next();
            } catch (IOException e) {
                e.printStackTrace();

                errorMessage = msg("Handlebars context generation failed: {}", new JsonObject().toString());

                aContext.put(ERROR_HEADER, "Internal Server Error");
                aContext.put(ERROR_MESSAGE, errorMessage);
                fail(aContext, new Error(errorMessage));
            }
        }
    }

    /*
     * Builds a list of manuscripts that are shaped like so:
     *
     * manuscript: {
     *   undertext_objects: [ {}, ... ],
     *   manuscript_components: [
     *     {
     *       overtext_layer: {},
     *       undertext_layers: [ {}, ... ],
     *       ...
     *     },
     *     ...
     *   ],
     *   ...
     * }
     *
     */
    private void combineSearchResults(final RoutingContext aContext) {

        // TODO: undertext_layers needs key undertext_object_id, and transform undertextLayers into hash table
        final ArrayList<JsonObject> searchResults = new ArrayList<JsonObject>();
        final JsonObject templateJson = new JsonObject();
        final SearchResultComparator searchResultComparator = new SearchResultComparator();

        // Maps manuscript IDs to undertext object arrays
        final JsonObject manuscriptIdToUndertextObjects = new JsonObject();

        // Maps undertext object IDs to undertext objects
        final JsonObject undertextObjectIdToUndertextObject = new JsonObject();

        Iterator<Object> utoItt = undertextObjects.iterator();
        while (utoItt.hasNext()) {
            JsonObject uto = (JsonObject) utoItt.next();
            if (manuscriptIdToUndertextObjects.getJsonArray(uto.getInteger("manuscript_id_i").toString()) == null) {
                manuscriptIdToUndertextObjects.put(uto.getInteger("manuscript_id_i").toString(), new JsonArray());
            }

            manuscriptIdToUndertextObjects.getJsonArray(uto.getInteger("manuscript_id_i").toString()).add(uto);
            undertextObjectIdToUndertextObject.put(uto.getInteger("undertext_object_id_i").toString(), uto);
        }

        Iterator<Object> mIt = manuscripts.iterator();
        while (mIt.hasNext()) {
            JsonObject searchResult = new JsonObject();
            JsonObject m = (JsonObject) mIt.next();
            final Integer mId = m.getInteger("manuscript_id_i");
            final String shelfMark = m.getString("shelf_mark_s", "");
            JsonArray resultMcs = new JsonArray();

            searchResult.put("manuscript", m);
            searchResult.put("undertext_objects", manuscriptIdToUndertextObjects.getJsonArray(mId.toString()));

            Iterator<Object> mcIt = manuscriptComponents.iterator();
            while (mcIt.hasNext()) {
                JsonObject mc = (JsonObject) mcIt.next();
                if (mc.getInteger("manuscript_id_i").equals(mId)) {

                    final Integer manuscriptComponentId = mc.getInteger("manuscript_component_id_i");
                    final String decoration = mc.getString("decoration_s", "");
                    mc.put("shelf_mark_s", shelfMark);
                    mc.put("support_material_s", m.getString("support_material_s"));

                    final JsonArray utls = new JsonArray();
                    Iterator<Object> utlIt = undertextLayers.iterator();
                    while (utlIt.hasNext()) {
                        JsonObject utl = (JsonObject) utlIt.next();
                        if (utl.getInteger("manuscript_component_id_i").equals(manuscriptComponentId)) {
                            // TODO: need place_of_origin_s and scholar_name_ss from UTO

                            final Integer utlUtoId = utl.getInteger("undertext_object_id_i");
                            if (utlUtoId != null) {
                                JsonObject uto = undertextObjectIdToUndertextObject.getJsonObject(utlUtoId.toString());
                                utl.put("work_s", uto.getString("work_s", ""));
                                utl.put("author_s", uto.getString("author_s", ""));
                                utl.put("genre_s", uto.getString("genre_s", ""));
                                utl.put("primary_language_undertext_object_s", uto.getString("primary_language_s", ""));
                                utl.put("script_s", uto.getString("script_s", ""));
                                utl.put("script_characterization_s", uto.getString("script_characterization_s", ""));
                                utl.put("script_date_text_s", uto.getString("script_date_text_s", ""));
                                utl.put("script_date_start_i", uto.getInteger("script_date_start_i"));
                                utl.put("script_date_end_i", uto.getInteger("script_date_end_i"));
                                utl.put("place_of_origin_s", uto.getString("place_of_origin_s", ""));
                                utl.put("folios_ss", uto.getJsonArray("folios_ss", new JsonArray()));
                                utl.put("undertext_folio_order_s", uto.getString("undertext_folio_order_s", ""));
                                utl.put("folio_order_comments_s", uto.getString("folio_order_comments", ""));
                                utl.put("scholar_name_ss", uto.getJsonArray("scholar_name_ss", new JsonArray()));
                            }
                            utls.add(utl);
                        }
                    }
                    mc.put("undertext_layers", utls);

                    // TODO: change OTLs into hash table by manuscript_component_id_i
                    Iterator<Object> otlIt = overtextLayers.iterator();
                    while (otlIt.hasNext()) {
                        JsonObject otl = (JsonObject) otlIt.next();
                        if (otl.getInteger("manuscript_component_id_i").equals(manuscriptComponentId)) {
                            otl.put("decoration_s", decoration);
                            mc.put("overtext_layer", otl);
                            // only ever one overtext layer, so leave loop
                            break;
                        }
                    }

                    resultMcs.add(mc);
                }
            }
            searchResult.put("manuscript_components", resultMcs);

            searchResults.add(searchResult);
        }
        Collections.sort(searchResults, searchResultComparator);
        templateJson.put("searchResults", new JsonArray(searchResults));

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Sending to template: {}", templateJson.toString());
        }

        try {
            aContext.data().put(HBS_DATA_KEY, toHbsContext(templateJson, aContext));
            aContext.next();
        } catch (IOException e) {
            e.printStackTrace();

            final String errorMessageNoUtoResults = msg("Handlebars context generation failed: {}",
                    templateJson.toString());

            aContext.put(ERROR_HEADER, "Internal Server Error");
            aContext.put(ERROR_MESSAGE, errorMessageNoUtoResults);
            fail(aContext, new Error(errorMessageNoUtoResults));
        }
    }

}
