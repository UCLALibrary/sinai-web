
package edu.ucla.library.sinai;

import static edu.ucla.library.sinai.Constants.FACEBOOK_OAUTH_CLIENT_ID;
import static edu.ucla.library.sinai.Constants.GOOGLE_OAUTH_CLIENT_ID;
import static edu.ucla.library.sinai.Constants.HTTP_HOST_PROP;
import static edu.ucla.library.sinai.Constants.HTTP_PORT_PROP;
import static edu.ucla.library.sinai.Constants.HTTP_PORT_REDIRECT_PROP;
import static edu.ucla.library.sinai.Constants.MESSAGES;
import static edu.ucla.library.sinai.Constants.SOLR_SERVER_PROP;
import static edu.ucla.library.sinai.Constants.TEMP_DIR_PROP;
import static edu.ucla.library.sinai.Constants.TWITTER_OAUTH_CLIENT_ID;
import static edu.ucla.library.sinai.Constants.TWITTER_OAUTH_SECRET_KEY;
import static edu.ucla.library.sinai.Constants.URL_SCHEME_PROP;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;

import javax.naming.ConfigurationException;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.sinai.handlers.LoginHandler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.Shareable;
import io.vertx.ext.web.handler.BodyHandler;

/**
 * A developer-friendly wrapper around the default Vertx configuration JsonObject.
 *
 * @author Kevin S. Clarke <a href="mailto:ksclarke@library.ucla.edu">ksclarke@library.ucla.edu</a>
 */
public class Configuration implements Shareable {

    public static final int DEFAULT_PORT = 8443;

    public static final int DEFAULT_REDIRECT_PORT = 8000;

    public static final String DEFAULT_HOST = "localhost";

    public static final File DEFAULT_TEMP_DIR = new File(System.getProperty("java.io.tmpdir"), TEMP_DIR_PROP);

    public static final int RETRY_COUNT = 10;

    public static final long DEFAULT_SESSION_TIMEOUT = 7200000L; // two hours

    private static final String DEFAULT_SOLR_SERVER = "http://localhost:8983/solr/sinai";

    private final Logger LOGGER = LoggerFactory.getLogger(Configuration.class, MESSAGES);

    private final int myPort;

    private final int myRedirectPort;

    private final String myHost;

    private final File myTempDir;

    private final URL mySolrServer;

    private final String myURLScheme;

    private final String myGoogleClientID;

    private final String myFacebookClientID;

    private final String myTwitterClientID;

    private final String myTwitterSecretKey;

    /**
     * Creates a new Sinai configuration object, which simplifies accessing configuration information.
     *
     * @param aConfig A JSON configuration
     * @throws ConfigurationException If there is trouble reading or setting a configuration option
     */
    public Configuration(final JsonObject aConfig) throws ConfigurationException, IOException {
        myTempDir = setTempDir(aConfig);
        myPort = setPort(aConfig);
        myRedirectPort = setRedirectPort(aConfig);
        myHost = setHost(aConfig);
        mySolrServer = setSolrServer(aConfig);
        myURLScheme = setURLScheme(aConfig);
        myGoogleClientID = setGoogleClientID(aConfig);
        myFacebookClientID = setFacebookClientID(aConfig);
        myTwitterClientID = setTwitterClientID(aConfig);
        myTwitterSecretKey = setTwitterSecretKey(aConfig);
    }

    public String setGoogleClientID(final JsonObject aConfig) {
        final Properties properties = System.getProperties();

        // We'll give command line properties first priority then fall back to our JSON configuration
        if (properties.containsKey(GOOGLE_OAUTH_CLIENT_ID)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Found {} set in system properties", GOOGLE_OAUTH_CLIENT_ID);
            }

            return properties.getProperty(GOOGLE_OAUTH_CLIENT_ID);
        } else {
            return aConfig.getString(GOOGLE_OAUTH_CLIENT_ID, "");
        }
    }

    public String setFacebookClientID(final JsonObject aConfig) {
        final Properties properties = System.getProperties();

        // We'll give command line properties first priority then fall back to our JSON configuration
        if (properties.containsKey(FACEBOOK_OAUTH_CLIENT_ID)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Found {} set in system properties", FACEBOOK_OAUTH_CLIENT_ID);
            }

            return properties.getProperty(FACEBOOK_OAUTH_CLIENT_ID);
        } else {
            return aConfig.getString(FACEBOOK_OAUTH_CLIENT_ID, "");
        }
    }

    public String setTwitterClientID(final JsonObject aConfig) {
        final Properties properties = System.getProperties();

        // We'll give command line properties first priority then fall back to our JSON configuration
        if (properties.containsKey(TWITTER_OAUTH_CLIENT_ID)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Found {} set in system properties", TWITTER_OAUTH_CLIENT_ID);
            }

            return properties.getProperty(TWITTER_OAUTH_CLIENT_ID);
        } else {
            return aConfig.getString(TWITTER_OAUTH_CLIENT_ID, "");
        }
    }

    public String setTwitterSecretKey(final JsonObject aConfig) {
        final Properties properties = System.getProperties();

        // We'll give command line properties first priority then fall back to our JSON configuration
        if (properties.containsKey(TWITTER_OAUTH_SECRET_KEY)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Found {} set in system properties", TWITTER_OAUTH_SECRET_KEY);
            }

            return properties.getProperty(TWITTER_OAUTH_SECRET_KEY);
        } else {
            return aConfig.getString(TWITTER_OAUTH_SECRET_KEY, "");
        }
    }

    public String getOAuthClientID(final String aService) {
        final String service = aService.toLowerCase();

        if (service.equals(LoginHandler.GOOGLE)) {
            return myGoogleClientID;
        } else if (service.equals(LoginHandler.TWITTER)) {
            return myTwitterClientID;
        } else if (service.equals(LoginHandler.FACEBOOK)) {
            return myFacebookClientID;
        }

        // FIXME: something better than a RuntimeException
        throw new RuntimeException("Unsupported OAuth service");
    }

    public String getOAuthClientSecretKey(final String aService) {
        final String service = aService.toLowerCase();

        if (service.equals(LoginHandler.GOOGLE)) {
            return "";
        } else if (service.equals(LoginHandler.TWITTER)) {
            return myTwitterSecretKey;
        } else if (service.equals(LoginHandler.FACEBOOK)) {
            return "";
        }

        // FIXME: something better than a RuntimeException
        throw new RuntimeException("Unsupported OAuth service");
    }

    /**
     * The number of times a message should be retried if it times out.
     *
     * @return The number of times a message should be tried if it times out
     */
    public int getRetryCount() {
        // FIXME: Add optional configuration through system property
        return RETRY_COUNT;
    }

    /**
     * Gets the port at which Sinai has been configured to run.
     *
     * @return The port at which Sinai has been configured to run
     */
    public int getPort() {
        return myPort;
    }

    /**
     * Gets the redirect port that redirects to the secure port.
     *
     * @return The redirect port that redirects to the secure port
     */
    public int getRedirectPort() {
        return myRedirectPort;
    }

    /**
     * The host name of the server.
     *
     * @return The host name of the server
     */
    public String getHost() {
        return myHost;
    }

    /**
     * The scheme the server is using (e.g., http or https).
     *
     * @return The scheme the server is using
     */
    public String getScheme() {
        return myURLScheme;
    }

    /**
     * Returns true if the server is using https; else, false.
     *
     * @return True if the server is using https; else, false
     */
    public boolean usesHttps() {
        return myURLScheme.equals("https");
    }

    /**
     * Returns the base URL of the Sinai image server, including: scheme, host, and port (if something other than 80).
     *
     * @return The base URL of the Sinai image server
     */
    public String getServer() {
        return getScheme() + "://" + getHost() + (getPort() != 80 ? ":" + getPort() : "");
    }

    /**
     * Gets Solr server Sinai is configured to use.
     *
     * @return The Solr server that Sinai should be able to use
     */
    public URL getSolrServer() {
        return mySolrServer;
    }

    /**
     * Gets the directory into which file uploads should be put. If "java.io.tmpdir" is configured as the file uploads
     * location, a <code>Sinai-file-uploads</code> directory will be created in the system's temp directory and file
     * uploads will be written there; otherwise, the supplied configured directory is used as the file uploads folder.
     *
     * @return The directory into which uploads should be put
     */
    public File getTempDir() {
        final File defaultTempDir = new File(BodyHandler.DEFAULT_UPLOADS_DIRECTORY);

        // Sadly, Vertx BodyHandler wants to create this directory on its construction
        if (!defaultTempDir.equals(myTempDir) && defaultTempDir.exists() && !defaultTempDir.delete()) {
            LOGGER.error("Couldn't delete the BodyHandler default uploads directory");
        }

        return myTempDir;
    }

    private String setURLScheme(final JsonObject aConfig) throws ConfigurationException {
        final Properties properties = System.getProperties();
        final String https = "https";
        final String http = "http";

        // We'll give command line properties first priority then fall back to our JSON configuration
        if (properties.containsKey(URL_SCHEME_PROP)) {
            final String scheme = properties.getProperty(URL_SCHEME_PROP);

            if (LOGGER.isDebugEnabled()) {
                if (scheme.equals(http)) {
                    LOGGER.debug("Found {} set in system properties as: {}", URL_SCHEME_PROP, http);
                } else if (scheme.equals(https)) {
                    LOGGER.debug("Found {} set in system properties as: {}", URL_SCHEME_PROP, https);
                }
            }

            if (!scheme.equals(http) && !scheme.equals(https)) {
                LOGGER.warn("Found {} set in system properties but its value ({}) isn't value so using: {}",
                        URL_SCHEME_PROP, scheme, https);

                return https;
            } else {
                LOGGER.info("Setting Sinai URL scheme to: {}", scheme);
                return scheme;
            }
        } else {
            return https;
        }
    }

    private URL setSolrServer(final JsonObject aConfig) throws ConfigurationException {
        final Properties properties = System.getProperties();
        final String solrServer;

        // We'll give command line properties first priority then fall back to our JSON configuration
        if (properties.containsKey(SOLR_SERVER_PROP)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Found {} set in system properties", SOLR_SERVER_PROP);
            }

            solrServer = properties.getProperty(SOLR_SERVER_PROP);
        } else {
            solrServer = aConfig.getString(SOLR_SERVER_PROP, DEFAULT_SOLR_SERVER);
        }

        // Check that it's a proper URL; we'll let the verticle test whether it's up and functioning
        try {
            return new URL(solrServer);
        } catch (final MalformedURLException details) {
            throw new ConfigurationException("Solr server URL is not well-formed: " + solrServer);
        }
    }

    /**
     * Sets the host at which Sinai listens.
     *
     * @param aConfig A JsonObject with configuration information
     * @throws ConfigurationException If there is trouble configuring Sinai
     */
    private String setHost(final JsonObject aConfig) throws ConfigurationException {
        String host;

        try {
            final Properties properties = System.getProperties();

            // We'll give command line properties first priority then fall back to our JSON configuration
            if (properties.containsKey(HTTP_HOST_PROP)) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Found {} set in system properties", HTTP_HOST_PROP);
                }

                host = properties.getProperty(HTTP_HOST_PROP);
            } else {
                host = aConfig.getString(HTTP_HOST_PROP, DEFAULT_HOST);
            }
        } catch (final NumberFormatException details) {
            LOGGER.warn("Supplied port isn't valid so trying to use {} instead", DEFAULT_PORT);
            host = DEFAULT_HOST;
        }

        LOGGER.info("Setting Sinai HTTP host to: {}", host);
        return host;
    }

    /**
     * Sets the port at which Sinai listens.
     *
     * @param aConfig A JsonObject with configuration information
     * @throws ConfigurationException If there is trouble configuring Sinai
     */
    private int setPort(final JsonObject aConfig) throws ConfigurationException {
        int port;

        try {
            final Properties properties = System.getProperties();

            // We'll give command line properties first priority then fall back to our JSON configuration
            if (properties.containsKey(HTTP_PORT_PROP)) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Found {} set in system properties", HTTP_PORT_PROP);
                }

                port = Integer.parseInt(properties.getProperty(HTTP_PORT_PROP));
            } else {
                port = aConfig.getInteger(HTTP_PORT_PROP, DEFAULT_PORT);
            }
        } catch (final NumberFormatException details) {
            LOGGER.warn("Supplied port isn't valid so trying to use {} instead", DEFAULT_PORT);
            port = DEFAULT_PORT;
        }

        LOGGER.info("Setting Sinai HTTP port to: {}", port);
        return port;
    }

    /**
     * Sets the port that redirects to a secure port (only when https is configured).
     *
     * @param aConfig A JsonObject with configuration information
     * @throws ConfigurationException If there is trouble configuring Sinai
     */
    private int setRedirectPort(final JsonObject aConfig) throws ConfigurationException {
        int port;

        try {
            final Properties properties = System.getProperties();

            // We'll give command line properties first priority then fall back to our JSON configuration
            if (properties.containsKey(HTTP_PORT_REDIRECT_PROP)) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Found {} set in system properties", HTTP_PORT_REDIRECT_PROP);
                }

                port = Integer.parseInt(properties.getProperty(HTTP_PORT_REDIRECT_PROP));
            } else {
                port = aConfig.getInteger(HTTP_PORT_REDIRECT_PROP, DEFAULT_REDIRECT_PORT);
            }
        } catch (final NumberFormatException details) {
            LOGGER.warn("Supplied redirect port isn't valid so trying to use {} instead", DEFAULT_REDIRECT_PORT);
            port = DEFAULT_REDIRECT_PORT;
        }

        LOGGER.info("Setting Sinai HTTP redirect port to: {}", port);
        return port;
    }

    private File setTempDir(final JsonObject aConfig) throws ConfigurationException {
        final Properties properties = System.getProperties();
        final String defaultUploadDirPath = DEFAULT_TEMP_DIR.getAbsolutePath();
        final File tempDir;

        // First, clean up the default uploads directory that is automatically created
        new File(BodyHandler.DEFAULT_UPLOADS_DIRECTORY).delete();

        // Then get the uploads directory we want to use
        if (properties.containsKey(TEMP_DIR_PROP)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Found {} set in system properties", TEMP_DIR_PROP);
            }

            tempDir = checkTmpDir(properties.getProperty(TEMP_DIR_PROP, defaultUploadDirPath));
        } else {
            tempDir = checkTmpDir(aConfig.getString(TEMP_DIR_PROP, defaultUploadDirPath));
        }

        LOGGER.info("Setting Sinai file uploads directory to: {}", tempDir);
        return tempDir;
    }

    private File checkTmpDir(final String aDirPath) throws ConfigurationException {
        File uploadsDir;

        if (aDirPath.equalsIgnoreCase("java.io.tmpdir") || aDirPath.trim().equals("")) {
            uploadsDir = DEFAULT_TEMP_DIR;

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Using a temporary directory {} for file uploads", uploadsDir);
            }
        } else {
            uploadsDir = new File(aDirPath);

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Using a user supplied file uploads directory: {}", uploadsDir);
            }
        }

        if (uploadsDir.exists()) {
            if (!uploadsDir.canWrite()) {
                throw new ConfigurationException(LOGGER.getMessage("{}", uploadsDir));
            }
        } else if (!uploadsDir.mkdirs()) {
            throw new ConfigurationException(LOGGER.getMessage("{}", uploadsDir));
        }

        return uploadsDir;
    }
}
