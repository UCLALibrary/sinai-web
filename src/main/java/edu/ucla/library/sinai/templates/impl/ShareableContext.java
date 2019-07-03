
package edu.ucla.library.sinai.templates.impl;

import com.github.jknack.handlebars.Context;

import io.vertx.core.shareddata.Shareable;

/**
 * Create a wrapper for our Handlebars template context which be safe to reuse.
 */
public class ShareableContext implements Shareable {

    private final Context myContext;

    public ShareableContext(final Context aContext) {
        myContext = aContext;
    }

    public Context getHandlebarsContext() {
        return myContext;
    }
}
