
package edu.ucla.library.sinai.handlers;

import static edu.ucla.library.sinai.Constants.MESSAGES;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.JsonNodeValueResolver;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.sinai.Configuration;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

abstract class SinaiHandler implements Handler<RoutingContext> {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass(), MESSAGES);

    protected final Configuration myConfig;

    /**
     * Creates a default handler from which other Sinai Handlers can be derived.
     *
     * @param aConfig A Sinai configuration object
     */
    protected SinaiHandler(final Configuration aConfig) {
        myConfig = aConfig;
    }

    /**
     * Prepares the supplied JSON object for use in the Handlebars context.
     *
     * @param aJsonObject A JSON object
     * @param aContext A context with the current session information
     * @return A Handlebars context that can be passed to the template engine
     */
    Context toHbsContext(final JsonObject aJsonObject, final RoutingContext aContext) throws IOException,
            JsonParseException, JsonMappingException {
        final String host = System.getProperty("sinai.host", "localhost");
        final String port = System.getProperty("sinai.port", "8443");

        aJsonObject.put("imageserver", myConfig.getImageServer());
        aJsonObject.put("sinaiauthkey", System.getProperty("sinai.id.key", ""));

        // Add a workaround for developers testing on their machines
        aJsonObject.put("sinaihost", host.equals("localhost") ? host + ":" + port : host);

        return Context.newBuilder(new ObjectMapper().readValue(aJsonObject.toString(), ObjectNode.class)).resolver(
                JsonNodeValueResolver.INSTANCE).build();
    }

    /**
     * Shorthand for a commonly used call that formats a property before passing it to the Handlebars template engine.
     *
     * @param aString A property string that needs to be formatted before giving to the Handlebars template engine
     * @return A string formatted for use by the Handlebars template engine
     */
    String fmt(final String aString) {
        return aString.replace('.', '-');
    }

    /**
     * Shorthand for a commonly used call that formats a message string.
     *
     * @param aMessage A message that needs to be formatted with additional information before using
     * @param aDetails Additional information that will fill in the details of the message string
     * @return A string formatted and ready for use in a message
     */
    String msg(final String aMessage, final Object... aDetails) {
        return LOGGER.getMessage(aMessage, aDetails);
    }

    /**
     * A convenience method for failing a particular context.
     *
     * @param aContext The context to mark as a failure
     * @param aThrowable The exception that caused the context to fail
     */
    void fail(final RoutingContext aContext, final Throwable aThrowable) {
        fail(aContext, aThrowable, aThrowable.getMessage());
    }

    /**
     * A convenience method for failing a particular context.
     *
     * @param aContext The context to mark as a failure
     * @param aThrowable The exception that caused the context to fail
     * @param aMessage A more detailed message to supplement the exception message
     */
    void fail(final RoutingContext aContext, final Throwable aThrowable, final String aMessage) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("{} is failing this RoutingContext", getClass().getName());
        }

        aContext.fail(500);

        if (aThrowable != null) {
            aContext.fail(aThrowable);
        }

        aContext.put(FailureHandler.ERROR_MESSAGE, aMessage);
    }

    /**
     * A convenience method for failing a particular context.
     *
     * @param aContext The context to mark as a failure
     * @param aFailCode The type of HTTP response failure
     * @param aMessage A more detailed message to supplement the exception message
     */
    void fail(final RoutingContext aContext, final int aFailCode, final String aMessage) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("{} is failing this RoutingContext", getClass().getName());
        }

        aContext.fail(aFailCode);
        aContext.put(FailureHandler.ERROR_MESSAGE, aMessage);
    }
}
