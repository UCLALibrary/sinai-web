
package edu.ucla.library.sinai.services;

import static edu.ucla.library.sinai.Constants.CONFIG_KEY;
import static edu.ucla.library.sinai.Constants.SHARED_DATA_KEY;

import edu.ucla.library.sinai.Configuration;
import edu.ucla.library.sinai.services.impl.SolrServiceImpl;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceproxy.ServiceProxyBuilder;

/**
 * Solr service interface that is used to generate the handler, proxy code, etc.
 */
@ProxyGen
@VertxGen
public interface SolrService {

    static SolrService create(final Vertx aVertx) {
        return new SolrServiceImpl((Configuration) aVertx.sharedData().getLocalMap(SHARED_DATA_KEY).get(CONFIG_KEY),
                aVertx);
    }

    static SolrService createProxy(final Vertx aVertx, final String aAddress) {
        final Configuration config = (Configuration) aVertx.sharedData().getLocalMap(SHARED_DATA_KEY).get(CONFIG_KEY);

        return new ServiceProxyBuilder(aVertx) //
                .setAddress(aAddress) //
                .setOptions(new DeliveryOptions().setSendTimeout(config.getSearchTimeout()))
                .build(SolrService.class);
    }

    void search(JsonObject aJsonObject, Handler<AsyncResult<JsonObject>> aHandler);

    void index(JsonObject aJsonObject, Handler<AsyncResult<String>> aHandler);

}