
package edu.ucla.library.sinai.services;

import static edu.ucla.library.sinai.Constants.CONFIG_KEY;
import static edu.ucla.library.sinai.Constants.SHARED_DATA_KEY;

import edu.ucla.library.sinai.Configuration;
import edu.ucla.library.sinai.services.impl.SearchServiceImpl;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceproxy.ProxyHelper;

@ProxyGen
@VertxGen
public interface SearchService {

    static SearchService create(final Vertx aVertx) {
        return new SearchServiceImpl((Configuration) aVertx.sharedData().getLocalMap(SHARED_DATA_KEY).get(CONFIG_KEY),
                aVertx);
    }

    static SearchService createProxy(final Vertx aVertx, final String aAddress) {
        return ProxyHelper.createProxy(SearchService.class, aVertx, aAddress);
    }

    /**
     * Performs the search.
     *
     * @param resultHandler the result handler called when the search results have been retrieved. The async result indicates
     *                      whether the call was successful or not.
     * @return
     */
    public void search(String searchQuery, Handler<AsyncResult<JsonArray>> resultHandler);
}
