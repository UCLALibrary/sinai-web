
package edu.ucla.library.sinai.handlers;

import static edu.ucla.library.sinai.Constants.HBS_DATA_KEY;
import static edu.ucla.library.sinai.Constants.HBS_PATH_SKIP_KEY;

import java.net.URISyntaxException;

import org.slf4j.Logger;

import com.fasterxml.jackson.databind.node.ObjectNode;

import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.sinai.Configuration;
import edu.ucla.library.sinai.util.PathUtils;
import io.vertx.ext.web.RoutingContext;

public class MiradorHandler extends SinaiHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(MiradorHandler.class);

    public MiradorHandler(final Configuration aConfig) {
        super(aConfig);
    }

    @Override
    public void handle(final RoutingContext aContext) {
        final String selected = aContext.request().getParam("selected");
        final String requestPath = aContext.request().uri();
        final int index = requestPath.indexOf("?");
        final String path;

        // Need all the metadata so that all manuscripts are loaded into Mirador
        final ObjectNode jsonNode = myConfig.getManuscriptMetadata();

        if (index != -1) {
            path = requestPath.substring(0, index);
        } else {
            path = requestPath;
        }

        final String[] pathParts = path.split("\\/");
        final String id = pathParts[2];
        final int skip;

        if (pathParts.length > 3) {
            skip = 2 + slashCount(PathUtils.decode(pathParts[3]));
            jsonNode.put("selected", pathParts[3]);
        } else if (selected != null) {
            try {
                jsonNode.put("selected", PathUtils.encodeIdentifier(selected));
            } catch (final URISyntaxException details) {
                LOGGER.warn("Bad 'selected' value: {}", selected, details);
            }

            skip = 1;
        } else {
            skip = 1;
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Getting item page for : {} ({})", id, path);
        }

        jsonNode.put("id", id);

        /* To drop the ID from the path for template processing */
        aContext.data().put(HBS_PATH_SKIP_KEY, skip + slashCount(PathUtils.decode(id)));
        aContext.data().put(HBS_DATA_KEY, toHbsContext(jsonNode, aContext));
        aContext.next();
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
