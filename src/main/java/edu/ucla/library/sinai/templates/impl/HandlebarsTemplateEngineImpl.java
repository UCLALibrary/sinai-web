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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringEscapeUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.context.MapValueResolver;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.sinai.templates.HandlebarsTemplateEngine;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.templ.impl.CachingTemplateEngine;

/**
 * The Handlebars template engine. This takes JSON and passes it to the Handlebars transformer to generate HTML pages.
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
        myHandlebars.registerHelper("urlencode", (str, options) -> {
            return URLEncoder.encode(str.toString(), StandardCharsets.UTF_8.toString()).replace("%3A", ":");
        });

        myHandlebars.registerHelper("formatManuscript", (on, options) -> {

            try {
                // get a vertx JsonObject to make things easier
                final JsonObject json = new JsonObject(new ObjectMapper().writeValueAsString(on));

                // StringEscapeUtils.escapeHtml4 used to create HTML entities from special characters in the data
                // (e.g., single quote)

                // TODO: trim and/or detect trailing periods from some of the fields
                final String ark = StringEscapeUtils.escapeHtml4(json.getString("ark_s", ""));
                final String shelfMark = StringEscapeUtils.escapeHtml4(json.getString("shelf_mark_s", ""));
                final String title = StringEscapeUtils.escapeHtml4(json.getString("title_s", ""));
                final String primaryLanguage = StringEscapeUtils.escapeHtml4(json.getString("primary_language_s",
                        ""));
                final JsonArray secondaryLanguage = json.getJsonArray("secondary_languages_ss", new JsonArray());
                final String languageDescription = StringEscapeUtils.escapeHtml4(json.getString(
                        "language_description_s", ""));
                final String script = StringEscapeUtils.escapeHtml4(json.getString("script_s", ""));
                final String scriptNote = StringEscapeUtils.escapeHtml4(json.getString("script_note_s", ""));
                final String dateText = StringEscapeUtils.escapeHtml4(json.getString("date_text_s", ""));
                final Integer dateOfOriginStart = json.getInteger("date_of_origin_start_i");
                final Integer dateOfOriginEnd = json.getInteger("date_of_origin_end_i");
                final String placeOfOrigin = StringEscapeUtils.escapeHtml4(json.getString("place_of_origin_s", ""));
                final String communityOfOrigin = StringEscapeUtils.escapeHtml4(json.getString("community_of_origin_s",
                        ""));
                final String decorationNote = StringEscapeUtils.escapeHtml4(json.getString("decoration_note_s", ""));
                final String supportMaterial = StringEscapeUtils.escapeHtml4(json.getString("support_material_s",
                        ""));
                final Integer folioCount = json.getInteger("folio_count_i");
                final String currentForm = StringEscapeUtils.escapeHtml4(json.getString("current_form_s", ""));
                final String manuscriptCondition = StringEscapeUtils.escapeHtml4(json.getString(
                        "manuscript_condition_s", ""));
                final Integer manuscriptHeight = json.getInteger("manuscript_height_i");
                final Integer manuscriptWidth = json.getInteger("manuscript_width_i");
                final Integer manuscriptDepth = json.getInteger("manuscript_depth_i");
                final Integer folioHeight = json.getInteger("folio_height_i");
                final Integer folioWidth = json.getInteger("folio_width_i");
                final String bindingStatus = StringEscapeUtils.escapeHtml4(json.getString("binding_status_s", ""));
                final String bindingDescription = StringEscapeUtils.escapeHtml4(json.getString(
                        "binding_description_s", ""));
                final String bindingCondition = StringEscapeUtils.escapeHtml4(json.getString("binding_condition_s",
                        ""));
                final String quireStructure = StringEscapeUtils.escapeHtml4(json.getString("quire_structure_s", ""));
                final String foliationNote = StringEscapeUtils.escapeHtml4(json.getString("foliation_note_s", ""));
                final String codicologicalNote = StringEscapeUtils.escapeHtml4(json.getString("codicological_note_s",
                        ""));
                final String previousCatalogInformation = StringEscapeUtils.escapeHtml4(json.getString(
                        "previous_catalog_information_s", ""));

                // first row: shelf mark, should always be present
                String p = "";
                p += "<p>";
                p += "<span class=\"bold\">" + "<a class=\"shelf-mark-link\" href=\"/viewer/" + URLEncoder.encode(ark,
                        "UTF-8").replace("%3A", ":") + "\">" + shelfMark + "</a>" + "</span>" + ". " + "<span>" +
                        "St. Catherine's Monastery of the Sinai, Egypt." + " " + (Pattern.matches(".* NF .*",
                                shelfMark) ? "New Finds" : "Old Collection") + "." + "</span>" + "<br>";

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
                    p += "</span>" + "<br>";
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
                    p += "</span>" + "<br>";
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
                    p += "<span>" + "<br>";
                }
                p += "</p>";
                p += "<div class=\"accordion\">" +
                        "<h2 class=\"manuscript-more-info-header\">Codicology & Overtext &darr;</h2>" +
                        "<div class=\"manuscript-more-info-body\">";

                if (!title.equals("") || !primaryLanguage.equals("") || !secondaryLanguage.isEmpty() ||
                        !languageDescription.equals("") || !script.equals("") || !scriptNote.equals("") || (!dateText
                                .equals("") || (dateOfOriginStart != null && dateOfOriginEnd != null)) ||
                        (!placeOfOrigin.equals("") || !communityOfOrigin.equals("")) || !decorationNote.equals("")) {
                    p += "<h3>Content and provenance of overtext</h3>";
                    p += !title.equals("") ? "<p>" + "Author, title: " + title + "." + "</p>" : "";
                    p += !primaryLanguage.equals("") ? "<p>" + "Primary language: " + primaryLanguage + "." + "</p>"
                            : "";
                    p += !secondaryLanguage.isEmpty() ? "<p>" + "Secondary language(s): " + String.join(", ",
                            secondaryLanguage.getList()) + "." + "</p>" : "";
                    p += !languageDescription.equals("") ? "<p>" + "Language note: " + languageDescription + "." +
                            "</p>" : "";
                    p += !script.equals("") ? "<p>" + "Script: " + script + "." + "</p>" : "";
                    p += !scriptNote.equals("") ? "<p>" + "Script note: " + scriptNote + "." + "</p>" : "";
                    // Date
                    if (!dateText.equals("") || (dateOfOriginStart != null && dateOfOriginEnd != null)) {
                        p += "<p>" + "Date: ";
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
                        p += "<p>" + "Provenance: ";
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
                    p += !decorationNote.equals("") ? "<p>" + "Decoration note: " + decorationNote + "." + "</p>"
                            : "";
                }

                if (!supportMaterial.equals("") || folioCount != null || !currentForm.equals("") ||
                        !manuscriptCondition.equals("") || manuscriptHeight != null || manuscriptWidth != null ||
                        manuscriptDepth != null || folioHeight != null || folioWidth != null || !bindingStatus.equals(
                                "") || !bindingDescription.equals("") || !bindingCondition.equals("")) {
                    p += "<h3>Codicological information</h3>";
                    p += !supportMaterial.equals("") ? "<p>" + "Page material: " + supportMaterial + "." + "</p>"
                            : "";
                    p += folioCount != null ? "<p>" + "Number of folios/fragments: " + folioCount + "." + "</p>" : "";
                    p += !currentForm.equals("") ? "<p>" + "Current form: " + currentForm + "." + "</p>" : "";
                    p += !manuscriptCondition.equals("") ? "<p>" + "Manuscript condition: " + manuscriptCondition +
                            "." + "</p>" : "";

                    // manuscript dimensions
                    if (manuscriptHeight != null || manuscriptWidth != null || manuscriptDepth != null) {
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
                    if (folioHeight != null || folioWidth != null) {
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

                    if (!bindingStatus.equals("") || !bindingDescription.equals("") || !bindingCondition.equals("")) {
                        p += "<h4>Binding</h4>";
                        p += !bindingStatus.equals("") ? "<p>" + "Relative date: " + bindingStatus + "." + "</p>"
                                : "";
                        p += !bindingDescription.equals("") ? "<p>" + "Description: " + bindingDescription + "." +
                                "</p>" : "";
                        p += !bindingCondition.equals("") ? "<p>" + "Condition: " + bindingCondition + "." + "</p>"
                                : "";
                    }
                }

                if (!quireStructure.equals("") || !foliationNote.equals("") || !codicologicalNote.equals("")) {
                    p += "<h3>Collation</h3>";
                    p += !quireStructure.equals("") ? "<p>" + "Quire structure: " + quireStructure + "." + "</p>"
                            : "";
                    p += !foliationNote.equals("") ? "<p>" + "Foliation note: " + foliationNote + "." + "</p>" : "";
                    p += !codicologicalNote.equals("") ? "<p>" + "Codicological note: " + codicologicalNote + "." +
                            "</p>" : "";
                }

                if (!previousCatalogInformation.equals("")) {
                    p += "<h3>Previous catalog information</h3>";
                    p += !previousCatalogInformation.equals("") ? "<p>" + previousCatalogInformation + "." + "</p>"
                            : "";
                }

                p += "</div></div>";

                return new Handlebars.SafeString(p);
            } catch (final IOException e) {
                return new Handlebars.SafeString("<span>Error processing JSON for browse page manuscript template: " +
                        e.getMessage() + "</span>");
            }
        });

        myHandlebars.registerHelper("formatUndertextObjects", (an, options) -> {
            try {
                // get a vertx JsonArray to make things easier
                final JsonArray jsonArray = new JsonArray(new ObjectMapper().writeValueAsString(an));

                String ul = "";
                ul += "<ul class=\"undertext-objects-list\">";

                final Iterator<Object> it = jsonArray.iterator();
                while (it.hasNext()) {
                    final JsonObject json = (JsonObject) it.next();

                    final String author = StringEscapeUtils.escapeHtml4(json.getString("author_s", ""));
                    final String work = StringEscapeUtils.escapeHtml4(json.getString("work_s", ""));
                    final String genre = StringEscapeUtils.escapeHtml4(json.getString("genre_s", ""));
                    final String primaryLanguage = StringEscapeUtils.escapeHtml4(json.getString("primary_language_s",
                            ""));
                    final String scriptName = StringEscapeUtils.escapeHtml4(json.getString("script_name_s", ""));
                    final String scriptCharacterization = StringEscapeUtils.escapeHtml4(json.getString(
                            "script_characterization_s", ""));
                    final JsonArray secondaryLanguage = json.getJsonArray("secondary_languages_ss", new JsonArray());
                    final String scriptDateText = StringEscapeUtils.escapeHtml4(json.getString("script_date_text_s",
                            ""));
                    final Integer scriptDateStart = json.getInteger("script_date_start_i");
                    final Integer scriptDateEnd = json.getInteger("script_date_end_i");
                    final String placeOfOrigin = StringEscapeUtils.escapeHtml4(json.getString("place_of_origin_s",
                            ""));
                    final String layoutComments = StringEscapeUtils.escapeHtml4(json.getString("layout_comments_s",
                            ""));
                    final JsonArray folios = json.getJsonArray("folios_ss", new JsonArray());
                    final String undertextFolioOrder = StringEscapeUtils.escapeHtml4(json.getString(
                            "undertext_folio_order_s", ""));
                    final String folioOrderComments = StringEscapeUtils.escapeHtml4(json.getString(
                            "folio_order_comments_s", ""));
                    final String relatedUndertextObjects = StringEscapeUtils.escapeHtml4(json.getString(
                            "related_undertext_objects_s", ""));
                    final String textRemarks = StringEscapeUtils.escapeHtml4(json.getString("text_remarks_s", ""));
                    final String bibliography = StringEscapeUtils.escapeHtml4(json.getString("bibliography_s", ""));
                    final JsonArray scholarNames = json.getJsonArray("scholar_name_ss", new JsonArray());

                    // whatever the first row is gets a hanging indent
                    Boolean hanging = false;

                    // first row: author, work, genre
                    String li = "";
                    li += "<li>";

                    if (!author.equals("") || !work.equals("") || !genre.equals("")) {
                        li += "<p class=\"bold\">";
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

                    li += "<div class=\"accordion\">" +
                            "<h2 class=\"undertext-more-info-header\">More Information &darr;</h2>" +
                            "<div class=\"undertext-more-info-body\">";

                    if (!author.equals("") || !work.equals("") || !genre.equals("") || !primaryLanguage.equals("") ||
                            !scriptName.equals("") || !scriptCharacterization.equals("") || (!scriptDateText.equals(
                                    "") || (scriptDateStart != null && scriptDateEnd != null)) || !placeOfOrigin
                                            .equals("")) {
                        li += "<h3>Identification and provenance</h3>";
                        li += !author.equals("") ? "<p>" + "Author: " + author + "." + "</p>" : "";
                        li += !work.equals("") ? "<p>" + "Title: " + work + "." + "</p>" : "";
                        li += !genre.equals("") ? "<p>" + "Genre: " + genre + "." + "</p>" : "";
                        li += !primaryLanguage.equals("") ? "<p>" + "Primary language: " + primaryLanguage + "." +
                                "</p>" : "";
                        li += !scriptName.equals("") ? "<p>" + "Script: " + scriptName + "." + "</p>" : "";
                        li += !scriptCharacterization.equals("") ? "<p>" + "Script characterization: " +
                                scriptCharacterization + "." + "</p>" : "";
                        li += !secondaryLanguage.isEmpty() ? "<p>" + "Secondary language(s): " + String.join(", ",
                                secondaryLanguage.getList()) + "." + "</p>" : "";
                        if (!scriptDateText.equals("") || (scriptDateStart != null && scriptDateEnd != null)) {
                            li += "<p>" + "Date: ";
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
                        li += !placeOfOrigin.equals("") ? "<p>" + "Place of origin: " + placeOfOrigin + "." + "</p>"
                                : "";
                    }

                    if (!layoutComments.equals("")) {
                        li += "<h3>Layout</h3>";
                        li += !layoutComments.equals("") ? "<p>" + "Layout comments: " + layoutComments + "." + "</p>"
                                : "";
                    }

                    if (!folios.isEmpty() || !undertextFolioOrder.equals("") || !folioOrderComments.equals("")) {
                        li += "<h3>Folios that preserve undertext</h3>";
                        li += !folios.isEmpty() ? "<p>" + "Folios: " + String.join(", ", folios.getList()) + "." : "";
                        li += !undertextFolioOrder.equals("") ? "<p>" +
                                "Folios in order of reconstructed undertext: " + undertextFolioOrder + "." + "</p>"
                                : "";
                        li += !folioOrderComments.equals("") ? "<p>" + "Folio order comments: " + folioOrderComments +
                                "." + "</p>" : "";
                    }

                    if (!relatedUndertextObjects.equals("")) {
                        li += "<h3>Relationship to other undertexts</h3>";
                        li += !relatedUndertextObjects.equals("") ? "<p>" + relatedUndertextObjects + "." + "</p>"
                                : "";
                    }

                    if (!textRemarks.equals("")) {
                        li += "<h3>Additional remarks</h3>";
                        li += !textRemarks.equals("") ? "<p>" + textRemarks + "." + "</p>" : "";
                    }

                    if (!bibliography.equals("")) {
                        li += "<h3>Bibliography</h3>";
                        li += !bibliography.equals("") ? "<p>" + bibliography + "." + "</p>" : "";
                    }

                    li += !scholarNames.isEmpty() ? "<p class=\"scholar-names\">" + String.join(", ", scholarNames
                            .getList()) + "." : "";

                    li += "</div></div>";

                    li += "</li>";
                    ul += li;
                }
                ul += "</ul>";

                return new Handlebars.SafeString(ul);
            } catch (final IOException e) {
                return new Handlebars.SafeString(
                        "<span>Error processing JSON for browse page undertext object template: " + e.getMessage() +
                                "</span>");
            }
        });

        myHandlebars.registerHelper("formatManuscriptComponents", (an, options) -> {
            // LOGGER.info("formatManuscriptComponents");
            try {
                // get a vertx JsonArray to make things easier
                final JsonArray jsonArray = new JsonArray(new ObjectMapper().writeValueAsString(an));
                // LOGGER.info(jsonArray.toString());
                // LOGGER.info(new Integer(jsonArray.size()).toString());

                String ul = "";
                ul += "<ul class=\"manuscript-components-list\">";

                final Iterator<Object> it = jsonArray.iterator();
                while (it.hasNext()) {
                    // LOGGER.info("formatManuscriptComponents.iterator");
                    final JsonObject json = (JsonObject) it.next();

                    final String shelfMark = StringEscapeUtils.escapeHtml4(json.getString("shelf_mark_s", ""));
                    final String componentType = StringEscapeUtils.escapeHtml4(json.getString("component_type_s",
                            ""));
                    final String folioNumber = StringEscapeUtils.escapeHtml4(json.getString("folio_number_s", ""));
                    final String folioSide = StringEscapeUtils.escapeHtml4(json.getString("folio_side_s", ""));

                    final String leadingConjoinComponentType = StringEscapeUtils.escapeHtml4(json.getString(
                            "leading_conjoin_component_type_s", ""));
                    final String leadingConjoinFolioNumber = StringEscapeUtils.escapeHtml4(json.getString(
                            "leading_conjoin_folio_number_s", ""));
                    final String leadingConjoinFolioSide = StringEscapeUtils.escapeHtml4(json.getString(
                            "leading_conjoin_folio_side_s", ""));

                    final String trailingConjoinComponentType = StringEscapeUtils.escapeHtml4(json.getString(
                            "trailing_conjoin_component_type_s", ""));
                    final String trailingConjoinFolioNumber = StringEscapeUtils.escapeHtml4(json.getString(
                            "trailing_conjoin_folio_number_s", ""));
                    final String trailingConjoinFolioSide = StringEscapeUtils.escapeHtml4(json.getString(
                            "trailing_conjoin_folio_side_s", ""));

                    final String quire = StringEscapeUtils.escapeHtml4(json.getString("quire_s", ""));
                    final String quirePosition = StringEscapeUtils.escapeHtml4(json.getString("quire_position_s",
                            ""));
                    final String alternateNumbering = StringEscapeUtils.escapeHtml4(json.getString(
                            "alternate_numbering_s", ""));

                    final String supportMaterial = StringEscapeUtils.escapeHtml4(json.getString("support_material_s",
                            ""));
                    final String folioDimensions = StringEscapeUtils.escapeHtml4(json.getString("folio_dimensions_s",
                            ""));

                    final Integer maxHeight = json.getInteger("max_height_i");
                    final Integer maxWidth = json.getInteger("max_width_i");
                    final Integer minHeight = json.getInteger("min_height_i");
                    final Integer minWidth = json.getInteger("min_width_i");

                    final String fleshHairSide = StringEscapeUtils.escapeHtml4(json.getString("flesh_hair_side_s",
                            ""));
                    final String parchmentQuality = StringEscapeUtils.escapeHtml4(json.getString(
                            "parchment_quality_s", ""));
                    final String parchmentDescription = StringEscapeUtils.escapeHtml4(json.getString(
                            "parchment_description_s", ""));
                    final String palimpsested = StringEscapeUtils.escapeHtml4(json.getString("palimpsested_s", ""));
                    final String erasureMethod = StringEscapeUtils.escapeHtml4(json.getString("erasure_method_s",
                            ""));

                    final JsonArray undertextLayers = json.getJsonArray("undertext_layers", new JsonArray());
                    final JsonObject overtextLayer = json.getJsonObject("overtext_layer");

                    String li = "";
                    li += "<li>";
                    li += "<h3 class=\"manuscript-component-header\">" + shelfMark + ", ";
                    if (!componentType.equals("") || !folioNumber.equals("") || !folioSide.equals("")) {

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
                     * if (!componentType.equals("folio")) { li += " " + componentType; } li +=
                     * !folioNumber.equals("") ? " " + folioNumber : ""; li += !folioSide.equals("") ? " " + folioSide
                     * : "";
                     */
                    li += "." + "</h3>";
                    li += "<div class=\"manuscript-component-body\">";

                    if (!leadingConjoinComponentType.equals("") || !leadingConjoinFolioNumber.equals("") ||
                            !leadingConjoinFolioSide.equals("") || !trailingConjoinComponentType.equals("") ||
                            !trailingConjoinFolioNumber.equals("") || !trailingConjoinFolioSide.equals("") || !quire
                                    .equals("") || !quirePosition.equals("") || !alternateNumbering.equals("")) {

                        li += "<h3>Codicological Context of Folio</h3>";
                        if (!leadingConjoinComponentType.equals("") || !leadingConjoinFolioNumber.equals("") ||
                                !leadingConjoinFolioSide.equals("")) {

                            li += "<p>" + "Conjoin: ";
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
                                if (!leadingConjoinComponentType.equals("") || !leadingConjoinFolioNumber.equals(
                                        "")) {
                                    li += " ";
                                }
                                li += leadingConjoinFolioSide;
                            }
                            li += "</p>";
                        } else if (!trailingConjoinComponentType.equals("") || !trailingConjoinFolioNumber.equals(
                                "") || !trailingConjoinFolioSide.equals("")) {

                            li += "<p>" + "Conjoin: ";
                            if (!trailingConjoinComponentType.equals("")) {
                                li += trailingConjoinComponentType;
                            }
                            if (!trailingConjoinFolioNumber.equals("")) {
                                if (!trailingConjoinComponentType.equals("")) {
                                    li += " ";
                                }
                                li += trailingConjoinFolioNumber;
                            }
                            if (!trailingConjoinFolioSide.equals("")) {
                                if (!trailingConjoinComponentType.equals("") || !trailingConjoinFolioNumber.equals(
                                        "")) {
                                    li += " ";
                                }
                                li += trailingConjoinFolioSide;
                            }
                            li += "</p>";
                        }
                        li += !quire.equals("") ? "<p>" + "Quire number: " + quire + "." + "</p>" : "";
                        li += !quirePosition.equals("") ? "<p>" + "Quire position: " + quirePosition + "." + "</p>"
                                : "";
                        li += !alternateNumbering.equals("") ? "<p>" + "Alternative numbering: " +
                                alternateNumbering + "." + "</p>" : "";
                    }

                    if (!supportMaterial.equals("") || !folioDimensions.equals("") || (maxHeight != null &&
                            maxWidth != null) || (minHeight != null && minWidth != null) || !fleshHairSide.equals(
                                    "") || !parchmentQuality.equals("") || !parchmentDescription.equals("") ||
                            !palimpsested.equals("") || !erasureMethod.equals("")) {

                        li += "<h3>Physical Description</h3>";
                        li += !supportMaterial.equals("") ? "<p>" + "Support: " + supportMaterial + "</p>" : "";
                        li += !folioDimensions.equals("") ? "<p>" + "Manuscript folio dimensions (mm): " +
                                folioDimensions + "." + "</p>" : "";
                        if (!folioDimensions.equals("") || (maxHeight != null && maxWidth != null) ||
                                (minHeight != null && minWidth != null)) {
                            li += !folioDimensions.equals("") ? "<p>" + "Manuscript folio dimensions (mm): " +
                                    folioDimensions + "." + "</p>" : "<p>Manuscript folio dimensions (mm):</p>";
                            li += (maxHeight != null && maxWidth != null) ? "<p class=\"indent\">" +
                                    "Maximum (mm): " + maxHeight + " x " + maxWidth + "." + "</p>" : "";
                            li += (minHeight != null && minWidth != null) ? "<p class=\"indent\">" +
                                    "Minimum (mm): " + minHeight + " x " + minWidth + "." + "</p>" : "";
                            li += "<p class=\"indentindent\">if different from typical folio dimensions for manuscript.</p>";
                        }
                        li += !fleshHairSide.equals("") ? "<p>" + "Side: " + fleshHairSide + "." + "</p>" : "";
                        li += !parchmentQuality.equals("") ? "<p>" + "Parchment quality: " + parchmentQuality + "." +
                                "</p>" : "";
                        li += !parchmentDescription.equals("") ? "<p>" + "Description of parchment: " +
                                parchmentDescription + "." + "</p>" : "";
                        li += !palimpsested.equals("") ? "<p>" + "Palimpsested?: " + palimpsested + "." + "</p>" : "";
                        li += !erasureMethod.equals("") ? "<p>" + "Method of erasure: " + erasureMethod + "." + "</p>"
                                : "";
                    }

                    // TODO: get title, author, genre, and date from undertext_objects, then display them in, and
                    // uncomment, the following block

                    if (undertextLayers.size() > 0) {
                        li += "<h3>Undertext(s)</h3>";
                        li += "<ul class=\"folio-undertexts-list\">";
                        final Iterator<Object> utlIt = undertextLayers.iterator();
                        for (Integer i = 0; utlIt.hasNext(); i++) {

                            final JsonObject utl = (JsonObject) utlIt.next();

                            final String work = StringEscapeUtils.escapeHtml4(utl.getString("work_s", ""));
                            final String author = StringEscapeUtils.escapeHtml4(utl.getString("author_s", ""));
                            final String workPassage = StringEscapeUtils.escapeHtml4(utl.getString("work_passage_s",
                                    ""));
                            final String genre = StringEscapeUtils.escapeHtml4(utl.getString("genre_s", ""));

                            // Folio primary language vs UTO primary language
                            final String primaryLanguage1 = StringEscapeUtils.escapeHtml4(utl.getString(
                                    "primary_language_s", ""));
                            final String primaryLanguageUndertextObject = StringEscapeUtils.escapeHtml4(utl.getString(
                                    "primary_language_undertext_object_s", ""));
                            final String scriptName = StringEscapeUtils.escapeHtml4(utl.getString("script_name_s",
                                    ""));
                            final String scriptCharacterization = StringEscapeUtils.escapeHtml4(utl.getString(
                                    "script_characterization_s", ""));
                            final String scriptNote1 = StringEscapeUtils.escapeHtml4(utl.getString("script_note_s",
                                    ""));
                            final JsonArray secondaryLanguage = utl.getJsonArray("secondary_languages_ss",
                                    new JsonArray());
                            final String scriptDateText1 = StringEscapeUtils.escapeHtml4(utl.getString(
                                    "script_date_text_s", ""));
                            final Integer scriptDateStart1 = utl.getInteger("script_date_start_i");
                            final Integer scriptDateEnd1 = utl.getInteger("script_date_end_i");
                            final String placeOfOrigin = StringEscapeUtils.escapeHtml4(utl.getString(
                                    "place_of_origin_s", ""));
                            final JsonArray folios = utl.getJsonArray("folios_ss", new JsonArray());
                            final String undertextFolioOrder = StringEscapeUtils.escapeHtml4(utl.getString(
                                    "undertext_folio_order_s", ""));
                            final String folioOrderComments = StringEscapeUtils.escapeHtml4(utl.getString(
                                    "folio_order_comments_s", ""));
                            final Boolean marginaliaPresent1 = utl.getBoolean("marginalia_present_b");
                            final String marginalia1 = StringEscapeUtils.escapeHtml4(utl.getString("marginalia_s",
                                    ""));
                            final Boolean nonTextualContentPresent1 = utl.getBoolean("nontextual_content_present_b");
                            final String nonTextualContent1 = StringEscapeUtils.escapeHtml4(utl.getString(
                                    "nontextual_content_s", ""));
                            final String catchwords = StringEscapeUtils.escapeHtml4(utl.getString("catchwords_s",
                                    ""));
                            final String signatures = StringEscapeUtils.escapeHtml4(utl.getString("signatures_s",
                                    ""));
                            final Integer underTextOrientation = utl.getInteger("under_text_orientation_i");
                            final Boolean prickings = utl.getBoolean("prickings_b");
                            final Boolean ruledLines = utl.getBoolean("ruled_lines_b");
                            final String preservationNotes = StringEscapeUtils.escapeHtml4(utl.getString(
                                    "preservation_notes_s", ""));
                            final String remarks = StringEscapeUtils.escapeHtml4(utl.getString("remarks_s", ""));
                            final String notes1 = StringEscapeUtils.escapeHtml4(utl.getString("notes_s", ""));
                            final JsonArray scholarNames = utl.getJsonArray("scholar_name_ss", new JsonArray());

                            li += "<li>";
                            if (!work.equals("") || !author.equals("") || !genre.equals("") ||
                                    !primaryLanguageUndertextObject.equals("") || !scriptName.equals("") ||
                                    (!scriptDateText1.equals("") || (scriptDateStart1 != null &&
                                            scriptDateEnd1 != null))) {

                                // Concatenation goes here
                                Boolean hanging = false;

                                if (!author.equals("") || !work.equals("") || !genre.equals("")) {
                                    li += "<p class=\"bold\">";
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
                                if (!primaryLanguageUndertextObject.equals("") || !scriptName.equals("")) {
                                    if (hanging == false) {
                                        li += "<p>";
                                        hanging = true;
                                    } else {
                                        li += "<p class=\"indent\">";
                                    }
                                    if (!primaryLanguageUndertextObject.equals("")) {
                                        li += primaryLanguageUndertextObject + ".";
                                    }
                                    if (!scriptName.equals("")) {
                                        if (!primaryLanguageUndertextObject.equals("")) {
                                            li += " ";
                                        }
                                        li += "Script: " + scriptName + ".";
                                    }
                                    li += "</p>";
                                }

                                // third row: scriptDateText, scriptDateStart, scriptDateEnd
                                if (!scriptDateText1.equals("") || (scriptDateStart1 != null &&
                                        scriptDateEnd1 != null)) {
                                    if (hanging == false) {
                                        li += "<p>";
                                        hanging = true;
                                    } else {
                                        li += "<p class=\"indent\">";
                                    }
                                    if (!scriptDateText1.equals("")) {
                                        li += scriptDateText1;
                                    }
                                    if (scriptDateStart1 != null && scriptDateEnd1 != null) {
                                        if (!scriptDateText1.equals("")) {
                                            li += " ";
                                        }
                                        li += "(" + scriptDateStart1.toString() + " to " + scriptDateEnd1.toString() +
                                                ")";
                                    }
                                    li += ".";
                                    li += "</p>";
                                }
                            } else {
                                // fall back on this if no metadata present
                                li += "<h4>Undertext #" + new Integer(i + 1).toString() + "</h4>";
                            }

                            li += "<div class=\"indent\">";
                            if (!work.equals("") || !author.equals("") || !workPassage.equals("") || !genre.equals(
                                    "") || !primaryLanguage1.equals("") || !scriptName.equals("") || !scriptNote1
                                            .equals("") || !secondaryLanguage.isEmpty() || (!scriptDateText1.equals(
                                                    "") || (scriptDateStart1 != null && scriptDateEnd1 != null)) ||
                                    !placeOfOrigin.equals("")) {

                                li += "<h4>Identification and provenance</h4>";
                                li += !work.equals("") ? "<p>" + "Title: " + work + "." + "</p>" : "";
                                li += !author.equals("") ? "<p>" + "Author: " + author + "." + "</p>" : "";
                                li += !workPassage.equals("") ? "<p>" + "Passage: " + workPassage + "." + "</p>" : "";
                                li += !genre.equals("") ? "<p>" + "Genre: " + genre + "." + "</p>" : "";
                                li += !primaryLanguage1.equals("") ? "<p>" + "Primary language: " + primaryLanguage1 +
                                        "." + "</p>" : "";
                                li += !scriptName.equals("") ? "<p>" + "Script: " + scriptName + "." + "</p>" : "";
                                li += !scriptNote1.equals("") ? "<p>" + "Script note: " + scriptNote1 + "." + "</p>"
                                        : "";
                                li += !secondaryLanguage.isEmpty() ? "<p>" + "Secondary language(s): " + String.join(
                                        ", ", secondaryLanguage.getList()) + "." + "</p>" : "";
                                if (!scriptDateText1.equals("") || (scriptDateStart1 != null &&
                                        scriptDateEnd1 != null)) {
                                    li += "<p>" + "Date: ";
                                    if (!scriptDateText1.equals("")) {
                                        li += scriptDateText1;
                                    }
                                    if (scriptDateStart1 != null && scriptDateEnd1 != null) {
                                        if (!scriptDateText1.equals("")) {
                                            li += " ";
                                        }
                                        li += "(" + scriptDateStart1.toString() + " to " + scriptDateEnd1.toString() +
                                                ")";
                                    }
                                    li += ".";
                                    li += "</p>";
                                }
                                li += !placeOfOrigin.equals("") ? "<p>" + "Place of origin: " + placeOfOrigin + "." +
                                        "</p>" : "";
                            }
                            if (!folios.isEmpty() || !undertextFolioOrder.equals("") || !folioOrderComments.equals(
                                    "")) {
                                li += "<h3>Folios that preserve undertext</h3>";
                                li += !folios.isEmpty() ? "<p>" + "Folios: " + String.join(", ", folios.getList()) +
                                        "." : "";
                                li += !undertextFolioOrder.equals("") ? "<p>" +
                                        "Folios in order of reconstructed undertext: " + undertextFolioOrder + "." +
                                        "</p>" : "";
                                li += !folioOrderComments.equals("") ? "<p>" + "Folio order comments: " +
                                        folioOrderComments + "." + "</p>" : "";
                            }
                            if (marginaliaPresent1 != null || !marginalia1.equals("")) {
                                li += "<h4>Marginalia</h4>";
                                li += marginaliaPresent1 != null ? "<p>" + "Marginalia present?: " +
                                        (marginaliaPresent1 ? "Yes" : "No") + "." + "</p>" : "";
                                li += !marginalia1.equals("") ? "<p>" + "Marginalia: " + marginalia1 + "." + "</p>"
                                        : "";
                            }

                            if (nonTextualContentPresent1 != null || !nonTextualContent1.equals("")) {
                                li += "<h4>Non-textual content</h4>";
                                li += nonTextualContentPresent1 != null ? "<p>" + "Non-textual content?: " +
                                        (nonTextualContentPresent1 ? "Yes" : "No") + "." + "</p>" : "";
                                li += !nonTextualContent1.equals("") ? "<p>" + "Non-textual content description: " +
                                        nonTextualContent1 + "." + "</p>" : "";
                            }

                            if (!catchwords.equals("") || !signatures.equals("") || underTextOrientation != null ||
                                    prickings != null || ruledLines != null || !preservationNotes.equals("")) {

                                li += "<h4>Codicological information</h4>";
                                li += !catchwords.equals("") ? "Catchwords: " + catchwords + "</p>" : "";
                                li += !signatures.equals("") ? "Quire signatures: " + signatures + "</p>" : "";
                                li += underTextOrientation != null ? "Undertext orientation: " + underTextOrientation
                                        .toString() + "</p>" : "";
                                if (prickings != null || ruledLines != null) {
                                    li += "<p>Physical evidence of undertext (if low legibility)</p>";
                                    li += prickings != null ? "<p class=\"indent\">" + "Prickings: " + (prickings
                                            ? "Yes" : "No") + "</p>" : "";
                                    li += ruledLines != null ? "<p class=\"indent\">" + "Ruled lines: " + (ruledLines
                                            ? "Yes" : "No") + "</p>" : "";
                                }
                                li += !preservationNotes.equals("") ? "Notes: " + preservationNotes + "</p>" : "";
                            }

                            if (!remarks.equals("") || !notes1.equals("")) {

                                li += "<h4>Additional remarks about folio</h4>";
                                li += !remarks.equals("") ? remarks + "</p>" : "";
                                li += !notes1.equals("") ? notes1 + "</p>" : "";
                            }

                            li += !scholarNames.isEmpty() ? "<p class=\"scholar-names\">" + String.join(", ",
                                    scholarNames.getList()) + "." : "";
                            li += "</div>";
                            li += "</li>";

                        }
                        li += "</ul>";
                    }

                    if (overtextLayer != null) {
                        final String title = StringEscapeUtils.escapeHtml4(overtextLayer.getString("title_s", ""));
                        final String textIdentity = StringEscapeUtils.escapeHtml4(overtextLayer.getString(
                                "text_identity_s", ""));
                        final String primaryLanguage2 = StringEscapeUtils.escapeHtml4(overtextLayer.getString(
                                "primary_language_s", ""));
                        final String folioScript = StringEscapeUtils.escapeHtml4(overtextLayer.getString("script_s",
                                ""));
                        final String scriptNote2 = StringEscapeUtils.escapeHtml4(overtextLayer.getString(
                                "script_note_s", ""));
                        final String scriptDateText2 = StringEscapeUtils.escapeHtml4(overtextLayer.getString(
                                "script_date_text_s", ""));
                        final Integer scriptDateStart2 = overtextLayer.getInteger("script_date_start_i");
                        final Integer scriptDateEnd2 = overtextLayer.getInteger("script_date_end_i");

                        final Boolean marginaliaPresent2 = overtextLayer.getBoolean("marginalia_present_b");
                        final String marginalia2 = StringEscapeUtils.escapeHtml4(overtextLayer.getString(
                                "marginalia_s", ""));

                        final Boolean nonTextualContentPresent2 = overtextLayer.getBoolean(
                                "nontextual_content_present_b");
                        final String nonTextualContent2 = StringEscapeUtils.escapeHtml4(overtextLayer.getString(
                                "nontextual_content_s", ""));
                        final String decoration = StringEscapeUtils.escapeHtml4(json.getString("decoration_s", ""));

                        final String notes2 = StringEscapeUtils.escapeHtml4(overtextLayer.getString("notes_s", ""));

                        if (!title.equals("") || !textIdentity.equals("") || !primaryLanguage2.equals("") ||
                                !folioScript.equals("") || !scriptNote2.equals("") || (!scriptDateText2.equals("") ||
                                        (scriptDateStart2 != null && scriptDateEnd2 != null)) ||
                                marginaliaPresent2 != null || !marginalia2.equals("") ||
                                nonTextualContentPresent2 != null || !nonTextualContent2.equals("") || !decoration
                                        .equals("") || !notes2.equals("")) {

                            li += "<h3>Overtext</h3>";
                            li += "<div class=\"indent\">";
                            if (!title.equals("") || !textIdentity.equals("") || !primaryLanguage2.equals("") ||
                                    !folioScript.equals("") || !scriptNote2.equals("") || (!scriptDateText2.equals(
                                            "") || (scriptDateStart2 != null && scriptDateEnd2 != null))) {

                                li += "<h4>Identification and Provenance</h4>";
                                li += !title.equals("") ? "<p>" + "Title: " + title + "." + "</p>" : "";
                                li += !textIdentity.equals("") ? "<p>" + "Text identity: " + textIdentity + "." +
                                        "</p>" : "";
                                li += !primaryLanguage2.equals("") ? "<p>" + "Primary language: " + primaryLanguage2 +
                                        "." + "</p>" : "";
                                li += !folioScript.equals("") ? "<p>" + "Script: " + folioScript + "." + "</p>" : "";
                                li += !scriptNote2.equals("") ? "<p>" + "Script note: " + scriptNote2 + "." + "</p>"
                                        : "";
                                if (!scriptDateText2.equals("") || (scriptDateStart2 != null &&
                                        scriptDateEnd2 != null)) {
                                    li += "<p>" + "Date: ";
                                    if (!scriptDateText2.equals("")) {
                                        li += scriptDateText2;
                                    }
                                    if (scriptDateStart2 != null && scriptDateEnd2 != null) {
                                        if (!scriptDateText2.equals("")) {
                                            li += " ";
                                        }
                                        li += "(" + scriptDateStart2.toString() + " to " + scriptDateEnd2.toString() +
                                                ")";
                                    }
                                    li += ".";
                                    li += "</p>";
                                }
                            }

                            if (marginaliaPresent2 != null || !marginalia2.equals("")) {

                                li += "<h4>Marginalia</h4>";
                                li += marginaliaPresent2 != null ? "<p>" + "Marginalia present?: " +
                                        (marginaliaPresent2 ? "Yes" : "No") + "." + "</p>" : "";
                                li += !marginalia2.equals("") ? "<p>" + "Marginalia: " + marginalia2 + "." + "</p>"
                                        : "";
                            }

                            if (nonTextualContentPresent2 != null || !nonTextualContent2.equals("") || !decoration
                                    .equals("")) {

                                li += "<h4>Non-textual content</h4>";
                                li += nonTextualContentPresent2 != null ? "<p>" + "Non-textual content?: " +
                                        (nonTextualContentPresent2 ? "Yes" : "No") + "." + "</p>" : "";
                                li += !nonTextualContent2.equals("") ? "<p>" + "Non-textual content description: " +
                                        nonTextualContent2 + "." + "</p>" : "";
                                li += !decoration.equals("") ? "<p>" + "Decoration: " + decoration + "." + "</p>"
                                        : "";
                            }

                            if (!notes2.equals("")) {

                                li += "<h4>Notes</h4>";
                                li += "<p>" + notes2 + "</p>";
                            }
                            li += "</div>";
                        }
                    }
                    li += "</div>";

                    li += "</li>";

                    ul += li;
                }
                ul += "</ul>";

                return new Handlebars.SafeString(ul);
            } catch (final IOException e) {
                return new Handlebars.SafeString(
                        "<span>Error processing JSON for browse page manuscript component template: " + e
                                .getMessage() + "</span>");
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

        cache.setMaxSize(aMaxCacheSize);
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

        // Add some browser caching
        aContext.response().putHeader("Cache-Control", "max-age=86400");

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
                final Map<String, Boolean> map = new HashMap<>();

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
        } catch (final FileNotFoundException details) {
            LOGGER.debug(details.getMessage(), details);
            aHandler.handle(Future.failedFuture(details));
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
