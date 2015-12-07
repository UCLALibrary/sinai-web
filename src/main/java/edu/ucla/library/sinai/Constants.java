
package edu.ucla.library.sinai;

import java.nio.charset.Charset;

public interface Constants {

    public static final String UTF_8_ENCODING = Charset.forName("UTF-8").name();

    public static final String MESSAGES = "sinai_messages";

    /* The following are properties that can be set by the user */

    public static final String HTTP_PORT_PROP = "sinai.port";

    public static final String HTTP_PORT_REDIRECT_PROP = "sinai.redirect.port";

    public static final String HTTP_HOST_PROP = "sinai.host";

    public static final String URL_SCHEME_PROP = "sinai.url.scheme";

    public static final String TEMP_DIR_PROP = "sinai.temp.dir";

    public static final String SOLR_SERVER_PROP = "sinai.solr.server";

    public static final String LOG_LEVEL_PROP = "sinai.log.level";

    public static final String KEY_PASS_PROP = "sinai.key.pass";

    public static final String JCEKS_PROP = "sinai.jceks";

    public static final String JKS_PROP = "sinai.jks";

    public static final String METRICS_REG_PROP = "sinai.metrics";

    /* These config values are only used internally. */

    public static final String SHARED_DATA_KEY = "sinai.shared.data";

    public static final String SOLR_SERVICE_KEY = "sinai.solr";

    public static final String CONFIG_KEY = "sinai.config";

    public static final String GOOGLE_OAUTH_CLIENT_ID = "sinai.oauth.google.clientId";

    public static final String FACEBOOK_OAUTH_CLIENT_ID = "sinai.oauth.facebook.clientId";

    public static final String TWITTER_OAUTH_CLIENT_ID = "sinai.oauth.twitter.clientId";

    public static final String TWITTER_OAUTH_SECRET_KEY = "sinai.oauth.twitter.secretKey";

    public static final String ID_KEY = "id";

    public static final String HBS_DATA_KEY = "hbs.data";

    public static final String HBS_PATH_SKIP_KEY = "hbs.path.skip";

    public static final String SINAI_ARRAY = "sinai.json.array";

    public static final String OAUTH_USERS = "sinai.oauth.users";

    /* Message values */

    public static final String SUCCESS_RESPONSE = "success";

    public static final String FAILURE_RESPONSE = "failure";

}
