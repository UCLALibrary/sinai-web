
package edu.ucla.library.sinai.handlers;

import static edu.ucla.library.sinai.Constants.HBS_DATA_KEY;
import static edu.ucla.library.sinai.Constants.HBS_PATH_SKIP_KEY;
import static edu.ucla.library.sinai.Constants.SOLR_SERVICE_KEY;
import static edu.ucla.library.sinai.handlers.FailureHandler.ERROR_HEADER;
import static edu.ucla.library.sinai.handlers.FailureHandler.ERROR_MESSAGE;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import org.slf4j.Logger;

import edu.ucla.library.sinai.Configuration;
import edu.ucla.library.sinai.services.SolrService;
import edu.ucla.library.sinai.util.PathUtils;
import info.freelibrary.util.LoggerFactory;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;

public class MiradorHandler extends SinaiHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(MiradorHandler.class);

    public MiradorHandler(final Configuration aConfig) {
        super(aConfig);
    }

    @Override
    public void handle(final RoutingContext aContext) {

        // get the ARKs of the manifests from Solr
        final SolrService solr = SolrService.createProxy(aContext.vertx(), SOLR_SERVICE_KEY);
        final JsonObject manuscriptSolrQuery = new JsonObject()
                .put("q", "record_type_s:manuscript AND publish_b:true")
                .put("fl", "ark_s")
                .put("group", "true")
                .put("group.main", "true")
                .put("group.field", "ark_s")
                .put("rows", 10000000);

        solr.search(manuscriptSolrQuery, aHandler -> {

            String errorMessage;

            if (aHandler.succeeded()) {

                final String path;
                final String selected = aContext.request().getParam("selected");
                final String requestPath = aContext.request().uri();
                final int index = requestPath.indexOf("?");


                final JsonObject jsonNode = new JsonObject().put("manifests", aHandler.result().getJsonObject("response").getJsonArray("docs"));

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

                try {
                    final HttpMethod method = aContext.request().method();
                    if (method == HttpMethod.POST) {
                        Set<FileUpload> uploads = aContext.fileUploads();
                        if (uploads.size() != 1) {
                            errorMessage = msg("Only one file upload is allowed at a time");

                            aContext.response().setStatusCode(400);
                            aContext.put(ERROR_MESSAGE, errorMessage);
                            fail(aContext, new Error(errorMessage));
                        }
                        final Path filePath = Paths.get(uploads.toArray(new FileUpload[0])[0].uploadedFileName());
                        final String workspaceSettings = new String(Files.readAllBytes(filePath), "UTF8");

                        // validate JSON
                        try {
                            new JsonObject(workspaceSettings);
                        } catch (Exception ex) {
                            errorMessage = msg("Uploaded file is not valid JSON");

                            aContext.response().setStatusCode(400);
                            aContext.put(ERROR_MESSAGE, errorMessage);
                            fail(aContext, new Error(errorMessage));
                        }

                        jsonNode.put("workspaceSettings", workspaceSettings);
                    }
                    aContext.data().put(HBS_DATA_KEY, toHbsContext(jsonNode, aContext));
                    /* To drop the ID from the path for template processing */
                    aContext.data().put(HBS_PATH_SKIP_KEY, skip + slashCount(PathUtils.decode(id)));
                    aContext.next();
                } catch (IOException e) {
                    e.printStackTrace();

                    errorMessage = msg("Handlebars context generation failed: {}", jsonNode.toString());

                    aContext.put(ERROR_HEADER, "Internal Server Error");
                    aContext.put(ERROR_MESSAGE, errorMessage);
                    fail(aContext, new Error(errorMessage));
                }
            } else {
                // fail
                errorMessage = aHandler.cause().getMessage();

                aContext.put(ERROR_HEADER, "Request not allowed");
                aContext.put(ERROR_MESSAGE, errorMessage);
                fail(aContext, aHandler.cause());
            }
        });

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
