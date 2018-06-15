package edu.ucla.library.sinai.handlers;

import edu.ucla.library.sinai.Configuration;
import io.vertx.ext.web.RoutingContext;

/**
 * A handler that redirects requests to PDF_PROXY_RE routes to PDF_RE routes,
 * so we can have nice URLs without filename extensions.
 */
public class PDFProxyHandler extends SinaiHandler {

    public PDFProxyHandler(final Configuration aConfig) {
        super(aConfig);
    }

    @Override
    public void handle(final RoutingContext aContext) {
        // TODO: check the normalisedPath and serve the proper PDF based on it, or return error page
        aContext.reroute("/pdfs/test.pdf");
    }

}
