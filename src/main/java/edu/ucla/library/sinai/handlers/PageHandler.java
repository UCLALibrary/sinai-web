
package edu.ucla.library.sinai.handlers;

import static edu.ucla.library.sinai.Constants.HBS_DATA_KEY;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import edu.ucla.library.sinai.Configuration;
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
        final ObjectMapper mapper = new ObjectMapper();
        final ObjectNode jsonNode = mapper.createObjectNode();

        // We're not adding specific here yet but we do need what's set in SinaiHandler
        aContext.data().put(HBS_DATA_KEY, toHbsContext(jsonNode, aContext));
        aContext.next();
    }

}
