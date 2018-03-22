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

                    final String ark = StringEscapeUtils.escapeHtml4(json.getString("ark", ""));
                    final String shelfMark = StringEscapeUtils.escapeHtml4(json.getString("shelf_mark_s", ""));
                    final String title = StringEscapeUtils.escapeHtml4(json.getString("title_s", ""));
                    final String primaryLanguage = StringEscapeUtils.escapeHtml4(json.getString("primary_language_s", ""));
                    final String script = StringEscapeUtils.escapeHtml4(json.getString("script_s", ""));
                    final String dateText = StringEscapeUtils.escapeHtml4(json.getString("date_text_s", ""));
                    final Integer dateOfOriginStart = json.getInteger("date_of_origin_start_i");
                    final Integer dateOfOriginEnd = json.getInteger("date_of_origin_end_i");
                    final String supportMaterial = StringEscapeUtils.escapeHtml4(json.getString("support_material_s", ""));
                    final Integer folioCount = json.getInteger("folio_count_i");

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

                        final String author = json.getString("author_s", "");
                        final String work = json.getString("work_s", "");
                        final String genre = json.getString("genre_s", "");
                        final String primaryLanguage = json.getString("primary_language_s", "");
                        final String scriptName = json.getString("script_name_s", "");
                        final String scriptDateText = json.getString("script_date_text_s", "");
                        final Integer scriptDateStart = json.getInteger("script_date_start_i");
                        final Integer scriptDateEnd = json.getInteger("script_date_end_i");
                        final JsonArray scholarNames = json.getJsonArray("scholar_name_s", new JsonArray());
                        final JsonArray folios = json.getJsonArray("folios_s", new JsonArray());

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
