package edu.ucla.library.sinai.services.impl;

import static edu.ucla.library.sinai.Constants.MESSAGES;
import static edu.ucla.library.sinai.Constants.SOLR_SERVICE_KEY;
import static edu.ucla.library.sinai.handlers.FailureHandler.ERROR_HEADER;
import static edu.ucla.library.sinai.handlers.FailureHandler.ERROR_MESSAGE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.ucla.library.sinai.Configuration;
import edu.ucla.library.sinai.services.SearchService;
import edu.ucla.library.sinai.services.SolrService;
import edu.ucla.library.sinai.util.SearchResultComparator;
import edu.ucla.library.sinai.util.UTOComparator;
import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.StringUtils;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class SearchServiceImpl implements SearchService {

    private final Logger LOGGER = LoggerFactory.getLogger(SearchServiceImpl.class, MESSAGES);

    private final Configuration myConfig;

    private final Vertx myVertx;

    private final SolrService mySolrService;

    public SearchServiceImpl(final Configuration aConfig, final Vertx aVertx) {
        myConfig = aConfig;
        myVertx = aVertx;
        mySolrService = SolrService.createProxy(aVertx, SOLR_SERVICE_KEY);
    }

    @Override
    public void search(String aSearchQuery, Handler<AsyncResult<JsonArray>> aResultHandler) {
        final JsonObject rawSearchResults = new JsonObject();

        // get all manuscript IDs from all records that match search
        final JsonObject manuscriptIdSolrQuery = new JsonObject()
            .put("q", "keyword_t:" + aSearchQuery + " AND manuscript_id_i:[* TO *]")
            .put("fl", "manuscript_id_i")
            .put("group", "true")
            .put("group.main", "true")
            .put("group.field", "manuscript_id_i")
            .put("rows", 10000000);

        LOGGER.debug("Starting a search for: {}", manuscriptIdSolrQuery.encodePrettily());

        mySolrService.search(manuscriptIdSolrQuery, manuscriptIdSolrSearch -> {
            final String firstHandlerErrorMessage;

            if (manuscriptIdSolrSearch.succeeded()) {
                final JsonObject solrResponse = manuscriptIdSolrSearch.result().getJsonObject("response");

                // If we get any hits, return a list of manuscripts
                if (solrResponse.getInteger("numFound") > 0) {
                    final Function<Object, String> getManuscriptId = solrResponseDocument -> {
                        return ((JsonObject) solrResponseDocument).getInteger("manuscript_id_i").toString();
                    };

                    final Stream<Object> solrResponseDocumentStream = solrResponse.getJsonArray("docs").stream();
                    final ArrayList<String> manuscriptIdList = solrResponseDocumentStream.map(getManuscriptId)
                            .collect(Collectors.toCollection(ArrayList::new));

                    final JsonObject manuscriptsSolrQuery = new JsonObject().put("q",
                            "record_type_s:manuscript AND publish_b:true AND manuscript_id_i:(" + String.join(" ",
                                    manuscriptIdList.toArray(new String[0])) + ")").put("sort", "shelf_mark_s asc")
                            .put("rows", 10000000);
                    final JsonObject undertextObjectsSolrQuery = new JsonObject().put("q",
                            "record_type_s:undertext_object AND manuscript_id_i:(" + String.join(" ", manuscriptIdList
                                    .toArray(new String[0])) + ")").put("sort", "primary_language_s asc").put("rows",
                                            10000000);
                    final JsonObject manuscriptComponentsSolrQuery = new JsonObject().put("q",
                            "record_type_s:manuscript_component AND manuscript_id_i:(" + String.join(" ",
                                    manuscriptIdList.toArray(new String[0])) + ")").put("sort", "position_i asc").put(
                                            "rows", 10000000);
                    final JsonObject overtextLayersSolrQuery = new JsonObject().put("q",
                            "record_type_s:overtext_layer AND manuscript_id_i:(" + String.join(" ", manuscriptIdList
                                    .toArray(new String[0])) + ")").put("rows", 10000000);
                    final JsonObject undertextLayersSolrQuery = new JsonObject().put("q",
                            "record_type_s:undertext_layer AND manuscript_id_i:(" + String.join(" ", manuscriptIdList
                                    .toArray(new String[0])) + ")").put("rows", 10000000);

                    /*
                     * Each of these handlers sets one of the private member variables of the enclosing class, to be
                     * used by combineSearchResults. Each one calls the handler directly above it, except for
                     * undertextLayersSearchHandler, which calls combineSearchResults.
                     */
                    final Handler<AsyncResult<JsonObject>> undertextLayersSolrSearchHandler = search -> {
                        if (search.succeeded()) {
                            rawSearchResults.put("undertextLayers", search.result().getJsonObject("response").getJsonArray("docs"));
                            aResultHandler.handle(Future.succeededFuture(combineSearchResults(rawSearchResults)));
                        } else {
                            aResultHandler.handle(Future.failedFuture(search.cause()));
                        }
                    };

                    final Handler<AsyncResult<JsonObject>> overtextLayersSolrSearchHandler = search -> {
                        if (search.succeeded()) {
                            rawSearchResults.put("overtextLayers", search.result().getJsonObject("response").getJsonArray("docs"));
                            mySolrService.search(undertextLayersSolrQuery, undertextLayersSolrSearchHandler);
                        } else {
                            aResultHandler.handle(Future.failedFuture(search.cause()));
                        }
                    };

                    final Handler<AsyncResult<JsonObject>> manuscriptComponentsSolrSearchHandler = search -> {
                        if (search.succeeded()) {
                            rawSearchResults.put("manuscriptComponents", search.result().getJsonObject("response").getJsonArray("docs"));
                            mySolrService.search(overtextLayersSolrQuery, overtextLayersSolrSearchHandler);
                        } else {
                            aResultHandler.handle(Future.failedFuture(search.cause()));
                        }
                    };

                    final Handler<AsyncResult<JsonObject>> undertextObjectsSolrSearchHandler = search -> {
                        if (search.succeeded()) {
                            // Sort by Language, then by Author, then by Title
                            final List<JsonObject> arrr = Collections.checkedList(search.result().getJsonObject(
                                    "response").getJsonArray("docs").getList(), JsonObject.class);
                            Collections.sort(arrr, new UTOComparator());
                            rawSearchResults.put("undertextObjects", new JsonArray(arrr));
                            mySolrService.search(manuscriptComponentsSolrQuery, manuscriptComponentsSolrSearchHandler);
                        } else {
                            aResultHandler.handle(Future.failedFuture(search.cause()));
                        }
                    };

                    final Handler<AsyncResult<JsonObject>> manuscriptsSolrSearchHandler = search -> {
                        if (search.succeeded()) {
                            rawSearchResults.put("manuscripts", search.result().getJsonObject("response").getJsonArray("docs"));
                            mySolrService.search(undertextObjectsSolrQuery, undertextObjectsSolrSearchHandler);
                        } else {
                            aResultHandler.handle(Future.failedFuture(search.cause()));
                        }
                    };
                    // Start searching!
                    mySolrService.search(manuscriptsSolrQuery, manuscriptsSolrSearchHandler);
                } else {
                    // no results
                    rawSearchResults
                        .put("manuscripts", new JsonArray())
                        .put("undertextObjects", new JsonArray())
                        .put("manuscriptComponents", new JsonArray())
                        .put("overtextLayers", new JsonArray())
                        .put("undertextLayers", new JsonArray());

                    final JsonArray combinedSearchResults = combineSearchResults(rawSearchResults);
                    aResultHandler.handle(Future.succeededFuture(combinedSearchResults));
                }
            } else {
                aResultHandler.handle(Future.failedFuture(manuscriptIdSolrSearch.cause()));
            }
        });
    }

    /*
     * Builds a list of manuscripts that are shaped like so:
     *
     * { manuscript: {},
     *   undertext_objects: [ {}, ... ],
     *   manuscript_components: [ { overtext_layer: {}, undertext_layers: [ {}, ... ], ... }, ... ] }
     */
    private JsonArray combineSearchResults(final JsonObject rawSearchResults) {
        // TODO: undertext_layers needs key undertext_object_id, and transform undertextLayers into hash table

        // The return value of this method.
        final JsonArray combinedSearchResults = new JsonArray();

        // Maps manuscript IDs to undertext object arrays
        final JsonObject manuscriptIdToUndertextObjects = new JsonObject();

        // Maps undertext object IDs to undertext objects
        final JsonObject undertextObjectIdToUndertextObject = new JsonObject();

        final Iterator<Object> utoItt = rawSearchResults.getJsonArray("undertextObjects").iterator();
        while (utoItt.hasNext()) {
            final JsonObject uto = (JsonObject) utoItt.next();

            if (manuscriptIdToUndertextObjects.getJsonArray(uto.getInteger("manuscript_id_i").toString()) == null) {
                manuscriptIdToUndertextObjects.put(uto.getInteger("manuscript_id_i").toString(), new JsonArray());
            }
            manuscriptIdToUndertextObjects.getJsonArray(uto.getInteger("manuscript_id_i").toString()).add(uto);
            undertextObjectIdToUndertextObject.put(uto.getInteger("undertext_object_id_i").toString(), uto);
        }

        final Iterator<Object> mIt = rawSearchResults.getJsonArray("manuscripts").iterator();

        while (mIt.hasNext()) {
            final JsonObject searchResult = new JsonObject();
            final JsonObject m = (JsonObject) mIt.next();
            final Integer mId = m.getInteger("manuscript_id_i");
            final String shelfMark = m.getString("shelf_mark_s", "");
            final JsonArray resultMcs = new JsonArray();

            searchResult.put("manuscript", m);
            searchResult.put("undertext_objects", manuscriptIdToUndertextObjects.getJsonArray(mId.toString()));

            final Iterator<Object> mcIt = rawSearchResults.getJsonArray("manuscriptComponents").iterator();

            while (mcIt.hasNext()) {
                final JsonObject mc = (JsonObject) mcIt.next();

                if (mc.getInteger("manuscript_id_i").equals(mId)) {

                    final Integer manuscriptComponentId = mc.getInteger("manuscript_component_id_i");
                    final String decoration = mc.getString("decoration_s", "");

                    mc.put("shelf_mark_s", shelfMark);
                    mc.put("support_material_s", m.getString("support_material_s"));

                    final JsonArray utls = new JsonArray();
                    final Iterator<Object> utlIt = rawSearchResults.getJsonArray("undertextLayers").iterator();

                    while (utlIt.hasNext()) {
                        final JsonObject utl = (JsonObject) utlIt.next();
                        if (utl.getInteger("manuscript_component_id_i").equals(manuscriptComponentId)) {
                            // TODO: need place_of_origin_s and scholar_name_ss from UTO

                            final Integer utlUtoId = utl.getInteger("undertext_object_id_i");
                            if (utlUtoId != null) {
                                final JsonObject uto = undertextObjectIdToUndertextObject.getJsonObject(utlUtoId
                                        .toString());
                                utl.put("work_s", uto.getString("work_s", ""));
                                utl.put("author_s", uto.getString("author_s", ""));
                                utl.put("genre_s", uto.getString("genre_s", ""));
                                utl.put("primary_language_undertext_object_s", uto.getString("primary_language_s",
                                        ""));
                                utl.put("script_name_s", uto.getString("script_name_s", ""));
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
                    final Iterator<Object> otlIt = rawSearchResults.getJsonArray("overtextLayers").iterator();

                    while (otlIt.hasNext()) {
                        final JsonObject otl = (JsonObject) otlIt.next();

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
            combinedSearchResults.add(searchResult);
        }
        return combinedSearchResults;
    }
}
