
package edu.ucla.library.sinai;

import java.nio.charset.Charset;

public interface Constants {

    String UTF_8_ENCODING = Charset.forName("UTF-8").name();

    String MESSAGES = "sinai_messages";

    /* The following are properties that can be set by the user */

    String HTTP_PORT_PROP = "sinai.port";

    String HTTP_PORT_REDIRECT_PROP = "sinai.redirect.port";

    String HTTP_HOST_PROP = "sinai.host";

    String URL_SCHEME_PROP = "sinai.url.scheme";

    String TEMP_DIR_PROP = "sinai.temp.dir";

    String IMAGE_SERVER_PROP = "sinai.image.server";

    String SOLR_SERVER_PROP = "sinai.solr.server";

    String LOG_LEVEL_PROP = "sinai.log.level";

    String KEY_PASS_PROP = "sinai.key.pass";

    String JCEKS_PROP = "sinai.jceks";

    String JKS_PROP = "sinai.jks";

    String METRICS_REG_PROP = "sinai.metrics";

    /* Metadata database login properties */

    String KATIKON_HOST = "katikon.host";

    String KATIKON_PORT = "katikon.port";

    String KATIKON_DATABASE = "katikon.database";

    String KATIKON_USER = "katikon.user";

    String KATIKON_PASSWORD = "katikon.password";

    String KATIKON_SSL = "katikon.ssl";

    String KATIKON_SSLFACTORY = "katikon.sslfactory";

    /* These config values are only used internally. */

    String SHARED_DATA_KEY = "sinai.shared.data";

    String SOLR_SERVICE_KEY = "sinai.solr";

    String SEARCH_SERVICE_KEY = "sinai.search";

    String SEARCH_SERVICE_MESSAGE_ADDRESS = "search";

    String CONFIG_KEY = "sinai.config";

    String GOOGLE_OAUTH_CLIENT_ID = "sinai.oauth.google.clientId";

    String FACEBOOK_OAUTH_CLIENT_ID = "sinai.oauth.facebook.clientId";

    String TWITTER_OAUTH_CLIENT_ID = "sinai.oauth.twitter.clientId";

    String TWITTER_OAUTH_SECRET_KEY = "sinai.oauth.twitter.secretKey";

    String ID_KEY = "id";

    String HBS_DATA_KEY = "hbs.data";

    String HBS_PATH_SKIP_KEY = "hbs.path.skip";

    String SINAI_ARRAY = "sinai.json.array";

    String SEARCH_CACHE_KEY = "hbs.search.data";

    /* Message values */

    String SUCCESS_RESPONSE = "success";

    String FAILURE_RESPONSE = "failure";

    /* Error codes */

    int SEARCH_SERVICE_ERROR_SOLR_FAILURE = 1;
}
