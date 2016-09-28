
package edu.ucla.library.sinai.handlers;

import static edu.ucla.library.sinai.Constants.HBS_DATA_KEY;
import static edu.ucla.library.sinai.Constants.MANUSCRIPT_METADATA_PROP;
import static edu.ucla.library.sinai.RoutePatterns.BROWSE;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import edu.ucla.library.sinai.Configuration;
import io.vertx.ext.web.RoutingContext;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.lang.Exception;

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
        ObjectNode jsonNode;
        String errorMessage;

        // If user is navigating to the browse page, need to load metadata
        if (aContext.normalisedPath().equals(BROWSE)) {
            try {
                String metadataFilePath = System.getProperty(MANUSCRIPT_METADATA_PROP);
                InputStream in = getClass().getResourceAsStream(metadataFilePath); 
                BufferedReader metadataFile = new BufferedReader(new InputStreamReader(in));
                jsonNode = (ObjectNode) mapper.readTree(metadataFile);
            } catch (Exception e) {
                errorMessage = "Something went wrong while loading the manuscript metadata. ERROR: " + e.toString();
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("{} " + errorMessage, getClass().getSimpleName());
                }

                jsonNode = mapper.createObjectNode();
                jsonNode.put("error", errorMessage);
            }
        } else {
            jsonNode = mapper.createObjectNode();
        }

        // We also need what's set in SinaiHandler
        aContext.data().put(HBS_DATA_KEY, toHbsContext(jsonNode, aContext));
        aContext.next();
    }

}
