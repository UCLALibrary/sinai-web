/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package edu.ucla.library.sinai.templates.impl;

import static edu.ucla.library.sinai.Constants.HBS_DATA_KEY;
import static edu.ucla.library.sinai.Constants.HBS_PATH_SKIP_KEY;
import static edu.ucla.library.sinai.Constants.MESSAGES;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringEscapeUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Handlebars.SafeString;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.context.MapValueResolver;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;

import edu.ucla.library.sinai.templates.HandlebarsTemplateEngine;
import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.templ.impl.CachingTemplateEngine;

/**
 * @author <a href="http://pmlopes@gmail.com">Paulo Lopes</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="mailto:ksclarke@library.ucla.edu">Kevin S. Clarke</a>
 */
public class HandlebarsTemplateEngineImpl extends CachingTemplateEngine<Template> implements
        HandlebarsTemplateEngine {

    private final Logger LOGGER = LoggerFactory.getLogger(HandlebarsTemplateEngineImpl.class, MESSAGES);

    private final Handlebars myHandlebars;

    public HandlebarsTemplateEngineImpl() {
        super(HandlebarsTemplateEngine.DEFAULT_TEMPLATE_EXTENSION, HandlebarsTemplateEngine.DEFAULT_MAX_CACHE_SIZE);
        myHandlebars = new Handlebars(new ClassPathTemplateLoader("/webroot"));

        /*
         * URL-encodes a string.
         */
        myHandlebars.registerHelper("urlencode", new Helper<String>() {

            @Override
            public String apply(final String str, final Options options) {
                try {
                    // do not want to encode colons
                    return URLEncoder.encode(str, "UTF-8").replace("%3A", ":");
                } catch (final UnsupportedEncodingException e) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("{} UTF-8 is unsupported: {}", HandlebarsTemplateEngineImpl.class, e.toString());
                    }
                    return "";
                }
            }
        });

        myHandlebars.registerHelper("formatManuscript", new Helper<ObjectNode>() {

            /**
             * Returns a <p> element that contains all of a manuscript's metadata fields.
             *
             * @param an {ObjectNode} - a Solr doc representing the manuscript
             * @returns {SafeString}
             */
            @Override
            public SafeString apply(final ObjectNode on, final Options options) {

                try {
                    // get a vertx JsonObject to make things easier
                    final JsonObject json = new JsonObject(new ObjectMapper().writeValueAsString(on));

                    // StringEscapeUtils.escapeHtml4 used to create HTML entities from special characters in the data (e.g., single quote)

                    // TODO: trim and/or detect trailing periods from some of the fields
                    final String ark = StringEscapeUtils.escapeHtml4(json.getString("ark_s", ""));
                    final String shelfMark = StringEscapeUtils.escapeHtml4(json.getString("shelf_mark_s", ""));
                    final String title = StringEscapeUtils.escapeHtml4(json.getString("title_s", ""));
                    final String primaryLanguage = StringEscapeUtils.escapeHtml4(json.getString("primary_language_s", ""));
                    final JsonArray secondaryLanguage = json.getJsonArray("secondary_languages_ss", new JsonArray());
                    final String languageDescription = StringEscapeUtils.escapeHtml4(json.getString("language_description_s", ""));
                    final String script = StringEscapeUtils.escapeHtml4(json.getString("script_s", ""));
                    final String scriptNote = StringEscapeUtils.escapeHtml4(json.getString("script_note_s", ""));
                    final String dateText = StringEscapeUtils.escapeHtml4(json.getString("date_text_s", ""));
                    final Integer dateOfOriginStart = json.getInteger("date_of_origin_start_i");
                    final Integer dateOfOriginEnd = json.getInteger("date_of_origin_end_i");
                    final String placeOfOrigin = StringEscapeUtils.escapeHtml4(json.getString("place_of_origin_s", ""));
                    final String communityOfOrigin = StringEscapeUtils.escapeHtml4(json.getString("community_of_origin_s", ""));
                    final String decorationNote = StringEscapeUtils.escapeHtml4(json.getString("decoration_note_s", ""));
                    final String supportMaterial = StringEscapeUtils.escapeHtml4(json.getString("support_material_s", ""));
                    final Integer folioCount = json.getInteger("folio_count_i");
                    final String currentForm = StringEscapeUtils.escapeHtml4(json.getString("current_form_s", ""));
                    final String manuscriptCondition = StringEscapeUtils.escapeHtml4(json.getString("manuscript_condition_s", ""));
                    final Integer manuscriptHeight = json.getInteger("manuscript_height_i");
                    final Integer manuscriptWidth = json.getInteger("manuscript_width_i");
                    final Integer manuscriptDepth = json.getInteger("manuscript_depth_i");
                    final Integer folioHeight = json.getInteger("folio_height_i");
                    final Integer folioWidth = json.getInteger("folio_width_i");
                    final String bindingStatus = StringEscapeUtils.escapeHtml4(json.getString("binding_status_s", ""));
                    final String bindingDescription = StringEscapeUtils.escapeHtml4(json.getString("binding_description_s", ""));
                    final String bindingCondition = StringEscapeUtils.escapeHtml4(json.getString("binding_condition_s", ""));
                    final String quireStructure = StringEscapeUtils.escapeHtml4(json.getString("quire_structure_s", ""));
                    final String foliationNote = StringEscapeUtils.escapeHtml4(json.getString("foliation_note_s", ""));
                    final String codicologicalNote = StringEscapeUtils.escapeHtml4(json.getString("codicological_note_s", ""));
                    final String previousCatalogInformation = StringEscapeUtils.escapeHtml4(json.getString("previous_catalog_information_s", ""));

                    // first row: shelf mark, should always be present
                    String p = "";
                    p += "<p>";
                    p += "<span class=\"bold\">"
                        + "<a class=\"shelf-mark-link\" href=\"/viewer/" + URLEncoder.encode(ark, "UTF-8").replace("%3A", ":") + "\">" + shelfMark + "</a>"
                        + "</span>"
                        + ". "
                        + "<span>"
                        + "St. Catherine's Monastery of the Sinai, Egypt." + " " + (Pattern.matches(".* NF .*", shelfMark) ? "New Finds" : "Old Collection") + "."
                        + "</span>"
                        + "<br>";

                    // second row: title, primaryLanguage, script
                    // (do not show a field if the get function returned "")
                    if (!title.equals("") || !primaryLanguage.equals("") || !script.equals("")) {
                        p += "<span>";
                        if (!title.equals("")) {
                            p += title + ".";
                        }
                        if (!primaryLanguage.equals("")) {
                            if (!title.equals("")) {
                                p += " ";
                            }
                            p += primaryLanguage + ".";
                        }
                        if (!script.equals("")) {
                            if (!title.equals("") || !primaryLanguage.equals("")) {
                                p += " ";
                            }
                            p += "Script: " + script + ".";
                        }
                        p += "</span>"
                            + "<br>";
                    }

                    // third row: dateText, dateOfOriginStart, dateOfOriginEnd
                    // (start and end dates must both be present for the range to be displayed)
                    if (!dateText.equals("") || (dateOfOriginStart != null && dateOfOriginEnd != null)) {
                        p += "<span>";
                        if (!dateText.equals("")) {
                            p += dateText;
                        }
                        if (dateOfOriginStart != null && dateOfOriginEnd != null) {
                            if (!dateText.equals("")) {
                                p += " ";
                            }
                            p += "(" + dateOfOriginStart.toString() + " to " + dateOfOriginEnd.toString() + ")";
                        }
                        p += ".";
                        p += "</span>"
                            + "<br>";
                    }

                    // fourth row: supportMaterial, folioCount
                    if (!supportMaterial.equals("") || folioCount != null) {
                        p += "<span>";
                        if (!supportMaterial.equals("")) {
                            p += supportMaterial;
                        }
                        if (folioCount != null) {
                            if (!supportMaterial.equals("")) {
                                p += ", ";
                            }
                            p += folioCount.toString() + " folios";
                        }
                        p += ".";
                        p += "<span>"
                            + "<br>";
                    }
                    p += "</p>";
                    p += "<div class=\"accordion\">"
                      + "<h2 class=\"manuscript-more-info-header\">Codicology & Overtext &darr;</h2>"
                      + "<div class=\"manuscript-more-info-body\">";

                    if (!title.equals("") ||
                        !primaryLanguage.equals("") ||
                        !secondaryLanguage.isEmpty() ||
                        !languageDescription.equals("") ||
                        !script.equals("") ||
                        !scriptNote.equals("") ||
                        (!dateText.equals("") || (dateOfOriginStart != null && dateOfOriginEnd != null)) ||
                        (!placeOfOrigin.equals("") || !communityOfOrigin.equals("")) ||
                        !decorationNote.equals("")) {
                        p += "<h3>Content and provenance of overtext</h3>";
                        p += !title.equals("") ? "<p>" + "Author, title: " + title + "." + "</p>" : "";
                        p += !primaryLanguage.equals("") ? "<p>" + "Primary language: " + primaryLanguage + "." + "</p>" : "";
                        p += !secondaryLanguage.isEmpty() ? "<p>" + "Secondary language(s): " + String.join(", ", secondaryLanguage.getList()) + "." + "</p>" : "";
                        p += !languageDescription.equals("") ? "<p>" + "Language note: " + languageDescription + "." + "</p>" : "";
                        p += !script.equals("") ? "<p>" + "Script: " + script + "." + "</p>" : "";
                        p += !scriptNote.equals("") ? "<p>" + "Script note: " + scriptNote + "." + "</p>" : "";
                        // Date
                        if (!dateText.equals("") || (dateOfOriginStart != null && dateOfOriginEnd != null)) {
                            p += "<p>"
                              + "Date: ";
                            if (!dateText.equals("")) {
                                p += dateText;
                            }
                            if (dateOfOriginStart != null && dateOfOriginEnd != null) {
                                if (!dateText.equals("")) {
                                    p += " ";
                                }
                                p += "(" + dateOfOriginStart.toString() + " to " + dateOfOriginEnd.toString() + ")";
                            }
                            p += ".";
                            p += "</p>";
                        }
                        // Provenance
                        if (!placeOfOrigin.equals("") || !communityOfOrigin.equals("")) {
                            p += "<p>"
                              + "Provenance: ";
                            if (!placeOfOrigin.equals("")) {
                                p += placeOfOrigin;
                            }
                            if (!communityOfOrigin.equals("")) {
                                if (!placeOfOrigin.equals("")) {
                                    p += ", ";
                                }
                                p += communityOfOrigin;
                            }
                            p += ".";
                            p += "</p>";
                        }
                        p += !decorationNote.equals("") ? "<p>" + "Decoration note: " + decorationNote + "." + "</p>" : "";
                    }

                    if (!supportMaterial.equals("") ||
                        folioCount != null ||
                        !currentForm.equals("") ||
                        !manuscriptCondition.equals("") ||
                        manuscriptHeight != null ||
                        manuscriptWidth != null ||
                        manuscriptDepth != null ||
                        folioHeight != null ||
                        folioWidth != null ||
                        !bindingStatus.equals("") ||
                        !bindingDescription.equals("") ||
                        !bindingCondition.equals("")) {
                        p += "<h3>Codicological information</h3>";
                        p += !supportMaterial.equals("") ? "<p>" + "Page material: " + supportMaterial + "." + "</p>" : "";
                        p += folioCount != null ? "<p>" + "Number of folios/fragments: " + folioCount + "." + "</p>" : "";
                        p += !currentForm.equals("") ? "<p>" + "Current form: " + currentForm + "." + "</p>" : "";
                        p += !manuscriptCondition.equals("") ? "<p>" + "Manuscript condition: " + manuscriptCondition + "." + "</p>" : "";

                        // manuscript dimensions
                        if (manuscriptHeight != null ||
                            manuscriptWidth != null ||
                            manuscriptDepth != null) {
                            p += "<p>" + "Manuscript dimensions in mm: ";
                            if (manuscriptHeight != null) {
                                p += "height [" + manuscriptHeight + "]";
                            }
                            if (manuscriptWidth != null) {
                                if (manuscriptHeight != null) {
                                    p += " x ";
                                }
                                p += "width [" + manuscriptWidth + "]";
                            }
                            if (manuscriptDepth != null) {
                                if (manuscriptHeight != null || manuscriptWidth != null) {
                                    p += " x ";
                                }
                                p += "depth [" + manuscriptDepth + "]";
                            }
                            p += "." + "</p>";
                        }

                        // typical folio dims
                        if (folioHeight != null ||
                            folioWidth != null) {
                            p += "<p>" + "Typical folio dimensions in mm: ";
                            if (folioHeight != null) {
                                p += "height [" + folioHeight + "]";
                            }
                            if (folioWidth != null) {
                                if (folioHeight != null) {
                                     p += " x ";
                                }
                                p += "width [" + folioWidth + "]";
                            }
                            p += "." + "</p>";
                        }

                        if (!bindingStatus.equals("") ||
                            !bindingDescription.equals("") ||
                            !bindingCondition.equals("")) {
                            p += "<h4>Binding</h4>";
                            p += !bindingStatus.equals("") ? "<p>" + "Relative date: " + bindingStatus + "." + "</p>" : "";
                            p += !bindingDescription.equals("") ? "<p>" + "Description: " + bindingDescription + "." + "</p>" : "";
                            p += !bindingCondition.equals("") ? "<p>" + "Condition: " + bindingCondition + "." + "</p>" : "";
                        }
                    }

                    if (!quireStructure.equals("") ||
                        !foliationNote.equals("") ||
                        !codicologicalNote.equals("")) {
                        p += "<h3>Collation</h3>";
                        p += !quireStructure.equals("") ? "<p>" + "Quire structure: " + quireStructure + "." + "</p>" : "";
                        p += !foliationNote.equals("") ? "<p>" + "Foliation note: " + foliationNote + "." + "</p>" : "";
                        p += !codicologicalNote.equals("") ? "<p>" + "Codicological note: " + codicologicalNote + "." + "</p>" : "";
                    }

                    if (!previousCatalogInformation.equals("")) {
                        p += "<h3>Previous catalog information</h3>";
                        p += !previousCatalogInformation.equals("") ? "<p>" + previousCatalogInformation + "." + "</p>" : "";
                    }

                    p += "</div></div>";

                    return new Handlebars.SafeString(p);
                } catch (IOException e) {
                    return new Handlebars.SafeString("<span>Error processing JSON for browse page manuscript template: " + e.getMessage() + "</span>");
                }
            }
        });

        myHandlebars.registerHelper("formatUndertextObjects", new Helper<ArrayNode>() {

            /**
             * Returns a <ul> element that lists all of a manuscript's undertext objects.
             *
             * @param an {ArrayNode} - an array of Solr docs, each representing one UTO
             * @returns {SafeString}
             */
            @Override
            public SafeString apply(final ArrayNode an, final Options options) {
                try {
                    // get a vertx JsonArray to make things easier
                    final JsonArray jsonArray = new JsonArray(new ObjectMapper().writeValueAsString(an));

                    String ul = "";
                    ul += "<ul class=\"undertext-objects-list\">";

                    Iterator<Object> it = jsonArray.iterator();
                    while (it.hasNext()) {
                        JsonObject json = (JsonObject) it.next();

                        final String author = StringEscapeUtils.escapeHtml4(json.getString("author_s", ""));
                        final String work = StringEscapeUtils.escapeHtml4(json.getString("work_s", ""));
                        final String genre = StringEscapeUtils.escapeHtml4(json.getString("genre_s", ""));
                        final String primaryLanguage = StringEscapeUtils.escapeHtml4(json.getString("primary_language_s", ""));
                        final String scriptName = StringEscapeUtils.escapeHtml4(json.getString("script_name_s", ""));
                        final String scriptCharacterization = StringEscapeUtils.escapeHtml4(json.getString("script_characterization_s", ""));
                        final JsonArray secondaryLanguage = json.getJsonArray("secondary_languages_ss", new JsonArray());
                        final String scriptDateText = StringEscapeUtils.escapeHtml4(json.getString("script_date_text_s", ""));
                        final Integer scriptDateStart = json.getInteger("script_date_start_i");
                        final Integer scriptDateEnd = json.getInteger("script_date_end_i");
                        final String placeOfOrigin = StringEscapeUtils.escapeHtml4(json.getString("place_of_origin_s", ""));
                        final String layoutComments = StringEscapeUtils.escapeHtml4(json.getString("", ""));
                        final JsonArray folios = json.getJsonArray("folios_s", new JsonArray());
                        final String undertextFolioOrder = StringEscapeUtils.escapeHtml4(json.getString("undertext_folio_order_s", ""));
                        final String folioOrderComments = StringEscapeUtils.escapeHtml4(json.getString("folio_order_comments_s", ""));
                        final String relatedUndertextObjects = StringEscapeUtils.escapeHtml4(json.getString("related_undertext_objects_s", ""));
                        final String textRemarks = StringEscapeUtils.escapeHtml4(json.getString("text_remarks_s", ""));
                        final String bibliography = StringEscapeUtils.escapeHtml4(json.getString("bibliography_s", ""));
                        final JsonArray scholarNames = json.getJsonArray("scholar_name_s", new JsonArray());

                        // whatever the first row is gets a hanging indent
                        Boolean hanging = false;

                        // first row: author, work, genre
                        String li = "";
                        li += "<li>";

                        if (!author.equals("") || !work.equals("") || !genre.equals("")) {
                            li += "<p>";
                            hanging = true;

                            if (!author.equals("")) {
                                li += author;
                            }
                            if (!work.equals("")) {
                                if (!author.equals("")) {
                                    li += ", ";
                                }
                                li += work;
                            }
                            if (!genre.equals("")) {
                                if (!author.equals("") || !work.equals("")) {
                                    li += ". ";
                                }
                                li += genre;
                            }
                            li += ".";
                            li += "</p>";
                        }

                        // second row: primaryLanguage, scriptName
                        if (!primaryLanguage.equals("") || !scriptName.equals("")) {
                            if (hanging == false) {
                                li += "<p>";
                                hanging = true;
                            } else {
                                li += "<p class=\"indent\">";
                            }
                            if (!primaryLanguage.equals("")) {
                                li += primaryLanguage + ".";
                            }
                            if (!scriptName.equals("")) {
                                if (!primaryLanguage.equals("")) {
                                    li += " ";
                                }
                                li += "Script: " + scriptName + ".";
                            }
                            li += "</p>";
                        }

                        // third row: scriptDateText, scriptDateStart, scriptDateEnd
                        if (!scriptDateText.equals("") || (scriptDateStart != null && scriptDateEnd != null)) {
                            if (hanging == false) {
                                li += "<p>";
                                hanging = true;
                            } else {
                                li += "<p class=\"indent\">";
                            }
                            if (!scriptDateText.equals("")) {
                                li += scriptDateText;
                            }
                            if (scriptDateStart != null && scriptDateEnd != null) {
                                if (!scriptDateText.equals("")) {
                                    li += " ";
                                }
                                li += "(" + scriptDateStart.toString() + " to " + scriptDateEnd.toString() + ")";
                            }
                            li += ".";
                            li += "</p>";
                        }

                        if (!folios.isEmpty()) {
                            if (hanging == false) {
                                li += "<p>";
                                hanging = true;
                            } else {
                                li += "<p class=\"indent\">";
                            }
                            li += "Folios: " + String.join(", ", folios.getList()) + ".";
                            li += "</p>";
                        }

                        if (!scholarNames.isEmpty()) {
                            if (hanging == false) {
                                li += "<p>";
                                hanging = true;
                            } else {
                                li += "<p class=\"indent\">";
                            }
                            li += String.join("; ", scholarNames.getList());
                            li += "</p>";
                        }

                        li += "<div class=\"accordion\">"
                           + "<h2 class=\"undertext-more-info-header\">More Information &darr;</h2>"
                           + "<div class=\"undertext-more-info-body\">";

                        if (!author.equals("") ||
                            !work.equals("") ||
                            !genre.equals("") ||
                            !primaryLanguage.equals("") ||
                            !scriptName.equals("") ||
                            !scriptCharacterization.equals("") ||
                            (!scriptDateText.equals("") || (scriptDateStart != null && scriptDateEnd != null)) ||
                            !placeOfOrigin.equals("")) {
                            li += "<h3>Identification and provenance</h3>";
                            li += !author.equals("") ? "<p>" + "Author: " + author + "." + "</p>" : "";
                            li += !work.equals("") ? "<p>" + "Title: " + work + "." + "</p>" : "";
                            li += !genre.equals("") ? "<p>" + "Genre: " + genre + "." + "</p>" : "";
                            li += !primaryLanguage.equals("") ? "<p>" + "Primary language: " + primaryLanguage + "." + "</p>" : "";
                            li += !scriptName.equals("") ? "<p>" + "Script: " + scriptName + "." + "</p>" : "";
                            li += !scriptCharacterization.equals("") ? "<p>" + "Script characterization: " + scriptCharacterization + "." + "</p>" : "";
                            li += !secondaryLanguage.isEmpty() ? "<p>" + "Secondary language(s): " + String.join(", ", secondaryLanguage.getList()) + "." + "</p>" : "";
                            if (!scriptDateText.equals("") || (scriptDateStart != null && scriptDateEnd != null)) {
                                li += "<p>"
                                   + "Date: ";
                                if (!scriptDateText.equals("")) {
                                    li += scriptDateText;
                                }
                                if (scriptDateStart != null && scriptDateEnd != null) {
                                    if (!scriptDateText.equals("")) {
                                        li += " ";
                                    }
                                    li += "(" + scriptDateStart.toString() + " to " + scriptDateEnd.toString() + ")";
                                }
                                li += ".";
                                li += "</p>";
                            }
                            li += !placeOfOrigin.equals("") ? "<p>" + "Place of origin: " + placeOfOrigin + "." + "</p>" : "";
                        }

                        if (!layoutComments.equals("")) {
                            li += "<h3>Layout</h3>";
                            li += !layoutComments.equals("") ? "<p>" + "Layout comments: " + layoutComments + "." + "</p>" : "";
                        }

                        if (!folios.isEmpty() ||
                            !undertextFolioOrder.equals("") ||
                            !folioOrderComments.equals("")) {
                            li += "<h3>Folios that preserve undertext</h3>";
                            li += !folios.isEmpty() ? "<p>" + "Folios: " + String.join(", ", folios.getList()) + "." : "";
                            li += !undertextFolioOrder.equals("") ? "<p>" + "Folios in order of unreconstructed undertext: " + undertextFolioOrder + "." + "</p>" : "";
                            li += !folioOrderComments.equals("") ? "<p>" + "Folio order comments: " + folioOrderComments + "." + "</p>" : "";
                        }

                        if (!relatedUndertextObjects.equals("")) {
                            li += "<h3>Relationship to other undertexts</h3>";
                            li += !relatedUndertextObjects.equals("") ? "<p>" + relatedUndertextObjects + "." + "</p>" : "";
                        }

                        if (!textRemarks.equals("")) {
                            li += "<h3>Additional remarks</h3>";
                            li += !textRemarks.equals("") ? "<p>" + textRemarks + "." + "</p>" : "";
                        }

                        if (!bibliography.equals("")) {
                            li += "<h3>Bibliography</h3>";
                            li += !bibliography.equals("") ? "<p>" + bibliography + "." + "</p>" : "";
                        }

                        li += !scholarNames.isEmpty() ? "<p class=\"scholar-names\">" + String.join(", ", scholarNames.getList()) + "." : "";

                        li += "</div></div>";

                        li += "</li>";
                        ul += li;
                    }
                    ul += "</ul>";

                    return new Handlebars.SafeString(ul);
                } catch (IOException e) {
                    return new Handlebars.SafeString("<span>Error processing JSON for browse page undertext object template: " + e.getMessage() + "</span>");
                }
            }
        });

        myHandlebars.registerHelper("formatManuscriptComponents", new Helper<ArrayNode>() {

            /**
             * Returns a <p> element that contains all of a manuscript component's metadata fields, with its text layers.
             *
             * @param an {ArrayNode} - { manuscript: , undertext_object: , manuscript_components
             * @returns {SafeString}
             */
            @Override
            public SafeString apply(final ArrayNode an, final Options options) {
                //LOGGER.info("formatManuscriptComponents");
                try {
                    // get a vertx JsonArray to make things easier
                    final JsonArray jsonArray = new JsonArray(new ObjectMapper().writeValueAsString(an));
                    //LOGGER.info(jsonArray.toString());
                    //LOGGER.info(new Integer(jsonArray.size()).toString());


                    String ul = "";
                    ul += "<ul class=\"manuscript-components-list\">";

                    Iterator<Object> it = jsonArray.iterator();
                    while (it.hasNext()) {
                        //LOGGER.info("formatManuscriptComponents.iterator");
                        JsonObject json = (JsonObject) it.next();

                        final String shelfMark = StringEscapeUtils.escapeHtml4(json.getString("shelf_mark_s", ""));
                        final String componentType = StringEscapeUtils.escapeHtml4(json.getString("component_type_s", ""));
                        final String folioNumber = StringEscapeUtils.escapeHtml4(json.getString("folio_number_s", ""));
                        final String folioSide = StringEscapeUtils.escapeHtml4(json.getString("folio_side_s", ""));

                        final String leadingConjoinComponentType = StringEscapeUtils.escapeHtml4(json.getString("leading_conjoin_component_type_s", ""));
                        final String leadingConjoinFolioNumber = StringEscapeUtils.escapeHtml4(json.getString("leading_conjoin_folio_number_s", ""));
                        final String leadingConjoinFolioSide = StringEscapeUtils.escapeHtml4(json.getString("leading_conjoin_folio_side_s", ""));
                        final String quire = StringEscapeUtils.escapeHtml4(json.getString("quire_s", ""));
                        final String quirePosition = StringEscapeUtils.escapeHtml4(json.getString("quire_position_s", ""));
                        final String alternateNumbering = StringEscapeUtils.escapeHtml4(json.getString("alternate_numbering_s", ""));

                        final String supportMaterial = StringEscapeUtils.escapeHtml4(json.getString("support_material_s", ""));
                        final String folioDimensions = StringEscapeUtils.escapeHtml4(json.getString("folio_dimensions_s", ""));

                        final Integer maxHeight = json.getInteger("max_height_i");
                        final Integer maxWidth = json.getInteger("max_width_i");
                        final Integer minHeight = json.getInteger("min_height_i");
                        final Integer minWidth = json.getInteger("min_width_i");

                        final String fleshHairSide = StringEscapeUtils.escapeHtml4(json.getString("flesh_hair_side_s", ""));
                        final String parchmentQuality = StringEscapeUtils.escapeHtml4(json.getString("parchment_quality_s", ""));
                        final String parchmentDescription = StringEscapeUtils.escapeHtml4(json.getString("parchment_description_s", ""));
                        final String palimpsested = StringEscapeUtils.escapeHtml4(json.getString("palimpsested_s", ""));
                        final String erasureMethod = StringEscapeUtils.escapeHtml4(json.getString("erasure_method_s", ""));

                        final JsonArray undertextLayers = json.getJsonArray("undertext_layers", new JsonArray());
                        final JsonObject overtextLayer = json.getJsonObject("overtext_layer");

                        String li = "";
                        li += "<li>";
                        li += "<h3 class=\"manuscript-component-header\">" + shelfMark + ", ";
                        if (!componentType.equals("") ||
                            !folioNumber.equals("") ||
                            !folioSide.equals("")) {

                            if (!componentType.equals("")) {
                                li += componentType;
                            }
                            if (!folioNumber.equals("")) {
                                if (!componentType.equals("")) {
                                    li += " ";
                                }
                                li += folioNumber;
                            }
                            if (!folioSide.equals("")) {
                                if (!componentType.equals("") || !folioNumber.equals("")) {
                                    li += " ";
                                }
                                li += folioSide;
                            }
                        } else {
                            li += "Unidentified";
                        }
                        /*
                        if (!componentType.equals("folio")) {
                            li += " " + componentType;
                        }
                        li += !folioNumber.equals("") ?  " " + folioNumber : "";
                        li += !folioSide.equals("") ? " " + folioSide : "";
                        */
                        li += "." + "</h3>";
                        li += "<div class=\"manuscript-component-body\">";

                        if (!leadingConjoinComponentType.equals("") ||
                            !leadingConjoinFolioNumber.equals("") ||
                            !leadingConjoinFolioSide.equals("") ||
                            !quire.equals("") ||
                            !quirePosition.equals("") ||
                            !alternateNumbering.equals("")) {

                            li += "<h3>Codicological Context of Folio</h3>";
                            if (!leadingConjoinComponentType.equals("") ||
                                !leadingConjoinFolioNumber.equals("") ||
                                !leadingConjoinFolioSide.equals("")) {

                                li += "<p>"
                                   + "Conjoin: ";
                                if (!leadingConjoinComponentType.equals("")) {
                                    li += leadingConjoinComponentType;
                                }
                                if (!leadingConjoinFolioNumber.equals("")) {
                                    if (!leadingConjoinComponentType.equals("")) {
                                        li += " ";
                                    }
                                    li += leadingConjoinFolioNumber;
                                }
                                if (!leadingConjoinFolioSide.equals("")) {
                                    if (!leadingConjoinComponentType.equals("") || !leadingConjoinFolioNumber.equals("")) {
                                        li += " ";
                                    }
                                    li += leadingConjoinFolioSide;
                                }
                                li += "</p>";
                            }
                            li += !quire.equals("") ? "<p>" + "Quire number: " + quire + "." + "</p>" : "";
                            li += !quirePosition.equals("") ? "<p>" + "Quire position: " + quirePosition + "." + "</p>" : "";
                            li += !alternateNumbering.equals("") ? "<p>" + "Alternative numbering: " + alternateNumbering + "." + "</p>" : "";
                        }

                        if (!supportMaterial.equals("") ||
                            !folioDimensions.equals("") ||
                            (maxHeight != null && maxWidth != null) ||
                            (minHeight != null && minWidth != null) ||
                            !fleshHairSide.equals("") ||
                            !parchmentQuality.equals("") ||
                            !parchmentDescription.equals("") ||
                            !palimpsested.equals("") ||
                            !erasureMethod.equals("")) {

                            li += "<h3>Physical Description</h3>";
                            li += !supportMaterial.equals("") ? "<p>" + "Support: " + supportMaterial + "</p>" : "";
                            li += !folioDimensions.equals("") ? "<p>" + "Manuscript folio dimensions (mm): " + folioDimensions + "." + "</p>" : "";
                            if (!folioDimensions.equals("") ||
                                (maxHeight != null && maxWidth != null) ||
                                (minHeight != null && minWidth != null)) {
                                li += !folioDimensions.equals("") ? "<p>" + "Manuscript folio dimensions (mm): " + folioDimensions + "." + "</p>" : "<p>Manuscript folio dimensions (mm):</p>";
                                li += (maxHeight != null && maxWidth != null) ? "<p class=\"indent\">" + "Maximum (mm): " + maxHeight + " x " + maxWidth + "." + "</p>" : "";
                                li += (minHeight != null && minWidth != null) ? "<p class=\"indent\">" + "Minimum (mm): " + minHeight + " x " + minWidth + "." + "</p>" : "";
                                li += "<p class=\"indentindent\">if different from typical folio dimensions for manuscript.</p>";
                            }
                            li += !fleshHairSide.equals("") ? "<p>" + "Side: " + fleshHairSide + "." + "</p>" : "";
                            li += !parchmentQuality.equals("") ? "<p>" + "Parchment quality: " + parchmentQuality + "." + "</p>" : "";
                            li += !parchmentDescription.equals("") ? "<p>" + "Description of parchment: " + parchmentDescription + "." + "</p>" : "";
                            li += !palimpsested.equals("") ? "<p>" + "Palimpsested?: " + palimpsested + "." + "</p>" : "";
                            li += !erasureMethod.equals("") ? "<p>" + "Method of erasure: " + erasureMethod + "." + "</p>" : "";
                        }

                        if (undertextLayers.size() > 0) {
                            li += "<h3>Undertext(s)</h3>";
                            Iterator<Object> utlIt = undertextLayers.iterator();
                            for (Integer i = 0; utlIt.hasNext(); i++) {

                                JsonObject utl = (JsonObject) utlIt.next();

                                final String workTitle = StringEscapeUtils.escapeHtml4(utl.getString("work_title_s", ""));
                                final String author = StringEscapeUtils.escapeHtml4(utl.getString("author_s", ""));
                                final String workPassage = StringEscapeUtils.escapeHtml4(utl.getString("work_passage_s", ""));
                                final String genre = StringEscapeUtils.escapeHtml4(utl.getString("genre_s", ""));
                                final String primaryLanguage = StringEscapeUtils.escapeHtml4(utl.getString("primary_language_s", ""));
                                final String script = StringEscapeUtils.escapeHtml4(utl.getString("script_s", ""));
                                final String scriptNote = StringEscapeUtils.escapeHtml4(utl.getString("script_note_s", ""));
                                final JsonArray secondaryLanguage = utl.getJsonArray("secondary_languages_ss", new JsonArray());
                                final String scriptDateText = StringEscapeUtils.escapeHtml4(utl.getString("script_date_text_s", ""));
                                final Integer scriptDateStart = utl.getInteger("script_date_start_i");
                                final Integer scriptDateEnd = utl.getInteger("script_date_end_i");
                                final String placeOfOrigin = StringEscapeUtils.escapeHtml4(utl.getString("place_of_origin_s", ""));
                                final JsonArray folios = utl.getJsonArray("folios_ss", new JsonArray());
                                final String undertextFolioOrder = StringEscapeUtils.escapeHtml4(utl.getString("undertext_folio_order_s", ""));
                                final String folioOrderComments = StringEscapeUtils.escapeHtml4(utl.getString("folio_order_comments_s", ""));
                                final Boolean marginaliaPresent = utl.getBoolean("marginalia_present_b");
                                final String marginalia = StringEscapeUtils.escapeHtml4(utl.getString("marginalia_s", ""));
                                final Boolean nonTextualContentPresent = utl.getBoolean("nontextual_content_present_b");
                                final String nonTextualContent = StringEscapeUtils.escapeHtml4(utl.getString("nontextual_content_s", ""));
                                final String catchwords = StringEscapeUtils.escapeHtml4(utl.getString("catchwords_s", ""));
                                final String signatures = StringEscapeUtils.escapeHtml4(utl.getString("signatures_s", ""));
                                final Integer underTextOrientation = utl.getInteger("under_text_orientation_i");
                                final Boolean prickings = utl.getBoolean("prickings_b");
                                final Boolean ruledLines = utl.getBoolean("ruled_lines_b");
                                final String preservationNotes = StringEscapeUtils.escapeHtml4(utl.getString("preservation_notes_s", ""));
                                final String remarks = StringEscapeUtils.escapeHtml4(utl.getString("remarks_s", ""));
                                final String notes = StringEscapeUtils.escapeHtml4(utl.getString("notes_s", ""));
                                final JsonArray scholarNames = utl.getJsonArray("scholar_name_ss", new JsonArray());

                                li += "<h4>Undertext #" + new Integer(i + 1).toString() + "</h4>";
                                li += "<div class=\"indent\">";

                                if (!workTitle.equals("") ||
                                    !author.equals("") ||
                                    !workPassage.equals("") ||
                                    !genre.equals("") ||
                                    !primaryLanguage.equals("") ||
                                    !script.equals("") ||
                                    !scriptNote.equals("") ||
                                    !secondaryLanguage.isEmpty() ||
                                    (!scriptDateText.equals("") || (scriptDateStart != null && scriptDateEnd != null)) ||
                                    !placeOfOrigin.equals("")) {

                                    li += "<h4>Identity and Provenance</h4>";
                                    li += !workTitle.equals("") ? "<p>" + "Title: " + workTitle + "." + "</p>" : "";
                                    li += !author.equals("") ? "<p>" + "Author: " + author + "." + "</p>" : "";
                                    li += !workPassage.equals("") ? "<p>" + "Passage: " + workPassage + "." + "</p>" : "";
                                    li += !genre.equals("") ? "<p>" + "Genre: " + genre + "." + "</p>" : "";
                                    li += !primaryLanguage.equals("") ? "<p>" + "Primary language: " + primaryLanguage + "." + "</p>" : "";
                                    li += !script.equals("") ? "<p>" + "Script: " + script + "." + "</p>" : "";
                                    li += !scriptNote.equals("") ? "<p>" + "Script note: " + scriptNote + "." + "</p>" : "";
                                    li += !secondaryLanguage.isEmpty() ? "<p>" + "Secondary language(s): " + String.join(", ", secondaryLanguage.getList()) + "." + "</p>" : "";
                                    if (!scriptDateText.equals("") || (scriptDateStart != null && scriptDateEnd != null)) {
                                        li += "<p>"
                                           + "Date: ";
                                        if (!scriptDateText.equals("")) {
                                            li += scriptDateText;
                                        }
                                        if (scriptDateStart != null && scriptDateEnd != null) {
                                            if (!scriptDateText.equals("")) {
                                                li += " ";
                                            }
                                            li += "(" + scriptDateStart.toString() + " to " + scriptDateEnd.toString() + ")";
                                        }
                                        li += ".";
                                        li += "</p>";
                                    }
                                    li += !placeOfOrigin.equals("") ? "<p>" + "Place of origin: " + placeOfOrigin + "." + "</p>" : "";
                                }
                                if (!folios.isEmpty() ||
                                    !undertextFolioOrder.equals("") ||
                                    !folioOrderComments.equals("")) {
                                    li += "<h3>Folios that preserve undertext</h3>";
                                    li += !folios.isEmpty() ? "<p>" + "Folios: " + String.join(", ", folios.getList()) + "." : "";
                                    li += !undertextFolioOrder.equals("") ? "<p>" + "Folios in order of reconstructed undertext: " + undertextFolioOrder + "." + "</p>" : "";
                                    li += !folioOrderComments.equals("") ? "<p>" + "Folio order comments: " + folioOrderComments + "." + "</p>" : "";
                                }
                                if (marginaliaPresent != null ||
                                    !marginalia.equals("")) {
                                    li += "<h4>Marginalia</h4>";
                                    li += marginaliaPresent != null ? "<p>" + "Marginalia present?: " + (marginaliaPresent ? "Yes" : "No") + "." + "</p>" : "";
                                    li += !marginalia.equals("") ? "<p>" + "Marginalia: " + marginalia + "." + "</p>" : "";
                                }

                                if (nonTextualContentPresent != null ||
                                    !nonTextualContent.equals("")) {
                                    li += "<h4>Non-textual content</h4>";
                                    li += nonTextualContentPresent != null ? "<p>" + "Non-textual content?: " + (nonTextualContentPresent ? "Yes" : "No") + "." + "</p>" : "";
                                    li += !nonTextualContent.equals("") ? "<p>" + "Non-textual content description: " + nonTextualContent + "." + "</p>" : "";
                                }

                                if (!catchwords.equals("") ||
                                    !signatures.equals("") ||
                                    underTextOrientation != null ||
                                    prickings != null ||
                                    ruledLines != null ||
                                    !preservationNotes.equals("")) {

                                    li += "<h4>Codicological information</h4>";
                                    li += !catchwords.equals("") ? "Catchwords: " + catchwords + "</p>" : "";
                                    li += !signatures.equals("") ? "Quire signatures: " + signatures + "</p>" : "";
                                    li += underTextOrientation != null ? "Undertext orientation: " + underTextOrientation.toString() + "</p>" : "";
                                    if (prickings != null || ruledLines != null) {
                                        li += "<p>Physical evidence of undertext (if low legibility)</p>";
                                        li += prickings != null ? "<p class=\"indent\">" + "Prickings: " + (prickings ? "Yes" : "No") + "</p>" : "";
                                        li += ruledLines != null ? "<p class=\"indent\">" + "Ruled lines: " + (ruledLines ? "Yes" : "No") + "</p>" : "";
                                    }
                                    li += !preservationNotes.equals("") ? "Notes: " + preservationNotes + "</p>" : "";
                                }

                                if (!remarks.equals("") ||
                                    !notes.equals("")) {

                                    li += "<h4>Additional remarks about folio</h4>";
                                    li += !remarks.equals("") ? remarks + "</p>" : "";
                                    li += !notes.equals("") ? notes + "</p>" : "";
                                }

                                li += !scholarNames.isEmpty() ? "<p class=\"scholar-names\">" + String.join(", ", scholarNames.getList()) + "." : "";
                                li += "</div>";

                            }
                        }

                        if (overtextLayer != null) {
                            final String title = StringEscapeUtils.escapeHtml4(overtextLayer.getString("title_s", ""));
                            final String textIdentity = StringEscapeUtils.escapeHtml4(overtextLayer.getString("text_identity_s", ""));
                            final String primaryLanguage = StringEscapeUtils.escapeHtml4(overtextLayer.getString("primary_language_s", ""));
                            final String folioScript = StringEscapeUtils.escapeHtml4(overtextLayer.getString("script_s", ""));
                            final String scriptNote = StringEscapeUtils.escapeHtml4(overtextLayer.getString("script_note_s", ""));
                            final String scriptDateText = StringEscapeUtils.escapeHtml4(overtextLayer.getString("script_date_text_s", ""));
                            final Integer scriptDateStart = overtextLayer.getInteger("script_date_start_i");
                            final Integer scriptDateEnd = overtextLayer.getInteger("script_date_end_i");

                            final Boolean marginaliaPresent = overtextLayer.getBoolean("marginalia_present_b");
                            final String marginalia = StringEscapeUtils.escapeHtml4(overtextLayer.getString("marginalia_s", ""));

                            final Boolean nonTextualContentPresent = overtextLayer.getBoolean("nontextual_content_present_b");
                            final String nonTextualContent = StringEscapeUtils.escapeHtml4(overtextLayer.getString("nontextual_content_s", ""));
                            final String decoration = StringEscapeUtils.escapeHtml4(json.getString("decoration_s", ""));

                            final String notes = StringEscapeUtils.escapeHtml4(overtextLayer.getString("notes_s", ""));

                            if (!title.equals("") ||
                                !textIdentity.equals("") ||
                                !primaryLanguage.equals("") ||
                                !folioScript.equals("") ||
                                !scriptNote.equals("") ||
                                (!scriptDateText.equals("") || (scriptDateStart != null && scriptDateEnd != null)) ||
                                marginaliaPresent != null ||
                                !marginalia.equals("") ||
                                nonTextualContentPresent != null ||
                                !nonTextualContent.equals("") ||
                                !decoration.equals("") ||
                                !notes.equals("")) {

                                li += "<h3>Overtext</h3>";
                                li += "<div class=\"indent\">";
                                if (!title.equals("") ||
                                    !textIdentity.equals("") ||
                                    !primaryLanguage.equals("") ||
                                    !folioScript.equals("") ||
                                    !scriptNote.equals("") ||
                                    (!scriptDateText.equals("") || (scriptDateStart != null && scriptDateEnd != null))) {


                                    li += "<h4>Identification and Provenance</h4>";
                                    li += !title.equals("") ? "<p>" + "Title: " + title + "." + "</p>" : "";
                                    li += !textIdentity.equals("") ? "<p>" + "Text identity: " + textIdentity + "." + "</p>" : "";
                                    li += !primaryLanguage.equals("") ? "<p>" + "Primary language: " + primaryLanguage + "." + "</p>" : "";
                                    li += !folioScript.equals("") ? "<p>" + "Script: " + folioScript + "." + "</p>" : "";
                                    li += !scriptNote.equals("") ? "<p>" + "Script note: " + scriptNote + "." + "</p>" : "";
                                    if (!scriptDateText.equals("") || (scriptDateStart != null && scriptDateEnd != null)) {
                                        li += "<p>"
                                           + "Date: ";
                                        if (!scriptDateText.equals("")) {
                                            li += scriptDateText;
                                        }
                                        if (scriptDateStart != null && scriptDateEnd != null) {
                                            if (!scriptDateText.equals("")) {
                                                li += " ";
                                            }
                                            li += "(" + scriptDateStart.toString() + " to " + scriptDateEnd.toString() + ")";
                                        }
                                        li += ".";
                                        li += "</p>";
                                    }
                                }

                                if (marginaliaPresent != null ||
                                    !marginalia.equals("")) {

                                    li += "<h4>Marginalia</h4>";
                                    li += marginaliaPresent != null ? "<p>" + "Marginalia present?: " + (marginaliaPresent ? "Yes" : "No") + "." + "</p>" : "";
                                    li += !marginalia.equals("") ? "<p>" + "Marginalia: " + marginalia + "." + "</p>" : "";
                                }

                                if (nonTextualContentPresent != null ||
                                    !nonTextualContent.equals("") ||
                                    !decoration.equals("")) {

                                    li += "<h4>Non-textual content</h4>";
                                    li += nonTextualContentPresent != null ? "<p>" + "Non-textual content?: " + (nonTextualContentPresent ? "Yes" : "No") + "." + "</p>" : "";
                                    li += !nonTextualContent.equals("") ? "<p>" + "Non-textual content description: " + nonTextualContent + "." + "</p>" : "";
                                    li += !decoration.equals("") ? "<p>" + "Decoration: " + decoration + "." + "</p>" : "";
                                }

                                if (!notes.equals("")) {

                                    li += "<h4>Notes</h4>";
                                    li += "<p>" + notes + "</p>";
                                }
                                li += "</div>";
                            }
                        }
                        li +="</div>";

                        li += "</li>";

                        ul += li;
                    }
                    ul += "</ul>";

                    return new Handlebars.SafeString(ul);
                } catch (IOException e) {
                    return new Handlebars.SafeString("<span>Error processing JSON for browse page manuscript component template: " + e.getMessage() + "</span>");
                }
            }
        });

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Handlebars template engine created");
        }
    }

    @Override
    public HandlebarsTemplateEngine setExtension(final String aExtension) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Setting Handlebars template extension: {}", aExtension);
        }

        doSetExtension(aExtension);
        return this;
    }

    @Override
    public HandlebarsTemplateEngine setMaxCacheSize(final int aMaxCacheSize) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Setting Handlebars max cache size: {}", aMaxCacheSize);
        }

        this.cache.setMaxSize(aMaxCacheSize);
        return null;
    }

    @Override
    public void render(final RoutingContext aContext, final String aTemplateDirName, final String aTemplateFileName,
            final Handler<AsyncResult<Buffer>> aHandler) {
        render(aContext, Paths.get(aTemplateDirName, aTemplateFileName).toString(), aHandler);
    }

    @Override
    public void render(final RoutingContext aContext, final String aTemplateFileName,
            final Handler<AsyncResult<Buffer>> aHandler) {
        final Object skip = aContext.data().get(HBS_PATH_SKIP_KEY);
        final String templateFileName;

        if (skip != null) {
            try {
                final String[] pathParts = URLDecoder.decode(aTemplateFileName, "UTF-8").split(File.separator);
                final StringBuilder pathBuilder = new StringBuilder();

                for (int index = 0; index < (pathParts.length - (int) skip); index++) {
                    pathBuilder.append(pathParts[index]).append(File.separatorChar);
                }

                templateFileName = pathBuilder.deleteCharAt(pathBuilder.length() - 1).toString();

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Using skip paths ({}) to get template file: {}", skip, aTemplateFileName);
                }
            } catch (final UnsupportedEncodingException details) {
                throw new RuntimeException("JVM doesn't support UTF-8?!", details);
            }
        } else {
            templateFileName = aTemplateFileName;
        }

        try {
            Template template = cache.get(templateFileName);

            if (template == null) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Loading Handlebars template '{}' into cache", templateFileName);
                }

                synchronized (this) {
                    template = myHandlebars.compile(templateFileName);
                    cache.put(templateFileName, template);
                }
            }

            final Map<String, Object> dataMap = aContext.data();
            Context context = (Context) dataMap.get(HBS_DATA_KEY);

            if (context == null) {
                final Map<String, Boolean> map = new HashMap<String, Boolean>();

                if (aContext.user() != null) {
                    map.put("logged-in", true);
                } else {
                    map.put("logged-in", false);
                }

                context = Context.newBuilder(map).resolver(MapValueResolver.INSTANCE).build();
            } else {
                if (aContext.user() != null) {
                    context.data("logged-in", true);
                } else {
                    context.data("logged-in", false);
                }
            }

            final String templateOutput = template.apply(context);

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Handlebars template output: {}", templateOutput);
            }

            aHandler.handle(Future.succeededFuture(Buffer.buffer(templateOutput)));
        } catch (final Exception details) {
            LOGGER.error(details, details.getMessage());
            aHandler.handle(Future.failedFuture(details));
        }
    }

    @Override
    public Handlebars getHandlebars() {
        return myHandlebars;
    }

}
