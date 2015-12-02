package edu.ucla.library.sinai.handlers;

import static edu.ucla.library.sinai.Constants.HBS_DATA_KEY;
import static edu.ucla.library.sinai.Constants.HBS_PATH_SKIP_KEY;

import org.slf4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.JsonNodeValueResolver;

import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.sinai.Configuration;
import edu.ucla.library.sinai.util.PathUtils;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

public class MiradorHandler implements Handler<RoutingContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MiradorHandler.class);

    private final Configuration myConfig;

    public MiradorHandler(final Configuration aConfig) {
        myConfig = aConfig;
    }

    @Override
    public void handle(final RoutingContext aContext) {
        final ObjectMapper mapper = new ObjectMapper();
        final ObjectNode jsonNode = mapper.createObjectNode();
        final String requestPath = aContext.request().uri();
        final String id = requestPath.split("\\/")[2];

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Getting item page for : {} ({})", id, requestPath);
        }

        jsonNode.put("id", id);

        /* To drop the ID from the path for template processing */
        aContext.data().put(HBS_PATH_SKIP_KEY, 1 + slashCount(PathUtils.decode(id)));
        aContext.data().put(HBS_DATA_KEY, toHbsContext(jsonNode, aContext));
        aContext.next();
    }

    /**
     * Prepares the supplied JSON object for use in the Handlebars context.
     *
     * @param aJsonNode A JSON object
     * @param aContext A context with the current session information
     * @return A Handlebars context that can be passed to the template engine
     */
    Context toHbsContext(final ObjectNode aJsonObject, final RoutingContext aContext) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("{} JSON passed to template page: {}", getClass().getSimpleName(), aJsonObject.toString());
        }

        return Context.newBuilder(aJsonObject).resolver(JsonNodeValueResolver.INSTANCE).build();
    }

    /**
     * Returns the number of slashes in the supplied ID.
     *
     * @param aID An identifier
     * @return The number of slashes in the identifier
     */
    int slashCount(final String aID) {
        return aID.length() - aID.replace("/", "").length();
    }

}
