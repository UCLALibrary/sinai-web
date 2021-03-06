
package edu.ucla.library.sinai.handlers;

import static edu.ucla.library.sinai.Metadata.CONTENT_TYPE;
import static edu.ucla.library.sinai.Metadata.JSON_MIME_TYPE;

import edu.ucla.library.sinai.Configuration;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.dropwizard.MetricsService;
import io.vertx.ext.web.RoutingContext;

public class MetricsHandler extends SinaiHandler {

    public MetricsHandler(final Configuration aConfig) {
        super(aConfig);
    }

    @Override
    public void handle(final RoutingContext aContext) {
        final MetricsService metricsService = MetricsService.create(aContext.vertx());
        final JsonObject metrics = metricsService.getMetricsSnapshot(aContext.vertx());
        final HttpServerResponse response = aContext.response();

        response.headers().add(CONTENT_TYPE, JSON_MIME_TYPE);
        response.end(metrics.toString());
        response.close();
    }

}
