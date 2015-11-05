
package edu.ucla.library.sinai.handlers;

import edu.ucla.library.sinai.Configuration;
import edu.ucla.library.sinai.verticles.SinaiMainVerticle;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.dropwizard.MetricsService;
import io.vertx.ext.web.RoutingContext;

public class StatusHandler extends SinaiHandler {

    /* The critical flag as expected by our Nagios script */
    private final String CRITICAL = "CRITICAL_";

    /* The okay flag as expected by our Nagios script */
    private final String OK = "OK_";

    /* The warning flag as expected by our Nagios script */
    private final String WARNING = "WARNING_";

    /* The unknown flag as expected by our Nagios script */
    private final String UNKNOWN = "UNKNOWN_";

    /* Count is one of the out of the box metrics */
    private final String COUNT = "count";

    public StatusHandler(final Configuration aConfig) {
        super(aConfig);
    }

    @Override
    public void handle(final RoutingContext aContext) {
        final MetricsService metricsService = MetricsService.create(aContext.vertx());
        final JsonObject metrics = metricsService.getMetricsSnapshot(aContext.vertx());
        final String[] pathParts = aContext.request().path().split("\\/");
        final String statusCheck = pathParts[pathParts.length - 1];
        final HttpServerResponse response = aContext.response();

        if (statusCheck.equals("basic")) {
            switch (metrics.getJsonObject("vertx.verticles." + SinaiMainVerticle.class.getName(), new JsonObject()
                    .put(COUNT, -1)).getInteger(COUNT)) {
                case 0:
                    response.end(CRITICAL + "Sinai Main Verticle is dead");
                    break;
                case 1:
                    response.end(OK + "Sinai Main Verticle is alive");
                    break;
                default:
                    response.end(UNKNOWN + "Sinai Main Verticle state is unknown");
            }
        }

        response.close();
    }

}
