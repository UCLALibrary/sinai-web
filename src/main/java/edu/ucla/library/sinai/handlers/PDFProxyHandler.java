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
        switch (aContext.normalisedPath()) {
        case "/terms-of-use/please-read":
            aContext.reroute("/pdfs/A-1_Terms-of-Use_20180628.pdf");
            break;
        case "/terms-of-use/contributors":
            aContext.reroute("/pdfs/A-2-3_Citing-Contributors_20180628.pdf");
            break;
        case "/user-guide":
            aContext.reroute("/pdfs/B-0_User-Guide-TOC_20180629-rev.pdf");
            break;
        case "/user-guide/mss-shelfmarks":
            aContext.reroute("/pdfs/B-1_Manuscript-Shelfmarks_20180629.pdf");
            break;
        case "/user-guide/mss-terms":
            aContext.reroute("/pdfs/B-4_Descriptions-Glossary_20180629.pdf");
            break;
        case "/user-guide/search-tips":
            aContext.reroute("/pdfs/B-5_Keyword-Search-Tips_20180629.pdf");
            break;
        case "/user-guide/works-cited":
            aContext.reroute("/pdfs/B-6_Works-Cited_20180628.pdf");
            break;
        case "/publications":
            aContext.reroute("/pdfs/C-1_Publications_20180629.pdf");
            break;
        case "/contacts":
            aContext.reroute("/pdfs/C-2_Contact-Us_20180628.pdf");
            break;
        case "/user-guide/navigating-site":
        case "/user-guide/choosing-images":
        default:
            aContext.reroute("/pdfs/test.pdf");
            break;
        }
    }

}
