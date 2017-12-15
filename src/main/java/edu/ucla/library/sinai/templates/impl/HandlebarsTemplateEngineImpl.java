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
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
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
