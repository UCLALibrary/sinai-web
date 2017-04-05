
package edu.ucla.library.sinai.handlers;

import static edu.ucla.library.sinai.Constants.HBS_DATA_KEY;
import static edu.ucla.library.sinai.Constants.SOLR_SERVICE_KEY;
import static edu.ucla.library.sinai.RoutePatterns.ROOT;
import static edu.ucla.library.sinai.RoutePatterns.ADMIN;
import static edu.ucla.library.sinai.handlers.FailureHandler.ERROR_HEADER;
import static edu.ucla.library.sinai.handlers.FailureHandler.ERROR_MESSAGE;
import static edu.ucla.library.sinai.util.SolrUtils.DOCS;
import static edu.ucla.library.sinai.util.SolrUtils.RESPONSE;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.commons.validator.routines.EmailValidator;

import info.freelibrary.util.StringUtils;

import edu.ucla.library.sinai.Configuration;
import edu.ucla.library.sinai.services.SolrService;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class AdminHandler extends SinaiHandler {

    public AdminHandler(final Configuration aConfig) {
        super(aConfig);
    }

    @Override
    public void handle(final RoutingContext aContext) {
    	final SolrService service = SolrService.createProxy(aContext.vertx(), SOLR_SERVICE_KEY);
    	final HttpMethod method = aContext.request().method();

        if (method == HttpMethod.GET) {
            // Get all user records
            final JsonObject solrQuery = new JsonObject().put("q", "record_type:user").put("rows", 10000000).put("sort", "email%20asc");
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Constructing new Solr query: {}", solrQuery);
            }

            service.search(solrQuery, handler -> {
                if (handler.succeeded()) {
                    final JsonObject solrJson = handler.result();

                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Solr response: {}", solrJson.toString());
                    }

                    aContext.data().put(HBS_DATA_KEY, toHbsContext(toJsonNode(solrJson), aContext));
                    aContext.next();
                } else {
                    fail(aContext, handler.cause());
                    aContext.put(ERROR_HEADER, "Search Error");
                    aContext.put(ERROR_MESSAGE, msg("Solr search failed: {}", handler.cause().getMessage()));
                }
            });
        } else if (method == HttpMethod.POST) {
        	final String email = aContext.request().getFormAttribute("email");
            final String formIsAdmin = aContext.request().getFormAttribute("is_admin");

        	final boolean isAdmin;

            // Validate user input
            final boolean isEmailValid = EmailValidator.getInstance().isValid(email);
            if (!isEmailValid) {
                LOGGER.error("Invalid email address: {}", email);

                aContext.put(ERROR_HEADER, "Form Submission Error");
                aContext.put(ERROR_MESSAGE, msg("Invalid email address: {}", email));
                aContext.fail(400);
            } else {
                if (formIsAdmin.equals("true")) {
                    isAdmin = true;
                } else if (formIsAdmin.equals("false")) {
                    isAdmin = false;
                } else {
                    LOGGER.error("Invalid is_admin value: {}", formIsAdmin);

                    aContext.put(ERROR_HEADER, "Form Submission Error");
                    aContext.put(ERROR_MESSAGE, msg("Invalid is_admin value: {}", formIsAdmin));
                    aContext.fail(400);
                    return;
                }
    
                // User input all good, so construct query to add a new user record
        	    final JsonObject solrQuery = new JsonObject().put("email", email).put("is_admin", isAdmin).put("record_type", "user");
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Constructing new Solr query: {}", solrQuery);
                }

                service.index(solrQuery, handler -> {
                    if (handler.succeeded()) {
                        final String solrJson = handler.result();
    
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("Solr response: {}", solrJson);
                        }
                    } else {
                        fail(aContext, handler.cause());
                        aContext.put(ERROR_HEADER, "Index Error");
                        aContext.put(ERROR_MESSAGE, msg("Solr index failed: {}", handler.cause().getMessage()));
                    }
                
                    // Redirect to Admin
                    final HttpServerResponse response = aContext.response();
                    response.setStatusCode(303).putHeader("Location", ADMIN).end();
                });
            }
        }
        else {
            LOGGER.error("Received a {} request but only POST and GET are supported", method.name());
            aContext.response().headers().add("Allow", "GET, POST");
            aContext.fail(405);
        }
    }

    /* FIXME: We need a better way to work with all this JSON -- a Solr object(?) */
    private ObjectNode toJsonNode(final JsonObject aJsonObject) {
        final JsonObject emptyObject = new JsonObject();
        final JsonObject response = aJsonObject.getJsonObject(RESPONSE, emptyObject);
        final JsonArray docs = response.getJsonArray(DOCS, new JsonArray());
        final ObjectMapper mapper = new ObjectMapper();
        final ObjectNode jsonNode = mapper.createObjectNode();
        final ArrayNode userArray = jsonNode.putArray("users");

        // Return the list of users to display on the page
        for (int index = 0; index < docs.size(); index++) {
            final JsonObject jsonObject = docs.getJsonObject(index);
            final ObjectNode objNode = mapper.createObjectNode();

            objNode.put("email",  jsonObject.getString("email"));
            objNode.put("is_admin", jsonObject.getBoolean("is_admin"));
            
            userArray.add(objNode);
        }
        return jsonNode;
    }
}
