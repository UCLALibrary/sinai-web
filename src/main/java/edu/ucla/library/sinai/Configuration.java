
package edu.ucla.library.sinai;

import static edu.ucla.library.sinai.Constants.CONFIG_KEY;
import static edu.ucla.library.sinai.Constants.HTTP_HOST_PROP;
import static edu.ucla.library.sinai.Constants.HTTP_PORT_PROP;
import static edu.ucla.library.sinai.Constants.HTTP_PORT_REDIRECT_PROP;
import static edu.ucla.library.sinai.Constants.IMAGE_SERVER_PROP;
import static edu.ucla.library.sinai.Constants.KATIKON_DATABASE;
import static edu.ucla.library.sinai.Constants.KATIKON_HOST;
import static edu.ucla.library.sinai.Constants.KATIKON_PASSWORD;
import static edu.ucla.library.sinai.Constants.KATIKON_PORT;
import static edu.ucla.library.sinai.Constants.KATIKON_SSL;
import static edu.ucla.library.sinai.Constants.KATIKON_SSLFACTORY;
import static edu.ucla.library.sinai.Constants.KATIKON_USER;
import static edu.ucla.library.sinai.Constants.MESSAGES;
import static edu.ucla.library.sinai.Constants.SHARED_DATA_KEY;
import static edu.ucla.library.sinai.Constants.SOLR_SERVER_PROP;
import static edu.ucla.library.sinai.Constants.TEMP_DIR_PROP;
import static edu.ucla.library.sinai.Constants.URL_SCHEME_PROP;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import javax.naming.ConfigurationException;

import org.apache.solr.client.solrj.impl.HttpSolrServer;

import com.fasterxml.jackson.core.JsonProcessingException;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
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

    public static final long DEFAULT_SESSION_TIMEOUT = Long.MAX_VALUE; // 7200000L; // two hours

    public static final long DEFAULT_METADATA_HARVEST_INTERVAL = 1000 * 60 * 60 * 24; // Daily

    private final Logger LOGGER = LoggerFactory.getLogger(Configuration.class, MESSAGES);

    private final int myPort;

    private final int myRedirectPort;

    private final String myHost;

    private final File myTempDir;

    private HttpSolrServer mySolrServer;

    private JsonObject myPostgreSQLProperties;

    private final String myURLScheme;

    private String myImageServer;

    private long myMetadataHarvestInterval;

    /**
     * Creates a new Sinai configuration object, which simplifies accessing configuration information.
     *
     * @param aConfig A JSON configuration
     * @throws ConfigurationException If there is trouble reading or setting a configuration option
     */
    public Configuration(final JsonObject aConfig, final Vertx aVertx,
            final Handler<AsyncResult<Configuration>> aHandler) throws ConfigurationException, IOException,
            JsonProcessingException {
        final Future<Configuration> result = Future.future();

        myTempDir = setTempDir(aConfig);
        myPort = setPort(aConfig);
        myRedirectPort = setRedirectPort(aConfig);
        myHost = setHost(aConfig);
        myURLScheme = setURLScheme(aConfig);
        myPostgreSQLProperties = setPostgreSQLProperties(aConfig);

        setMetadataHarvestInterval();

        if (aHandler != null) {
            result.setHandler(aHandler);

            setImageServer(imageServerHandler -> {
                if (imageServerHandler.failed()) {
                    result.fail(imageServerHandler.cause());
                }
                setSolrServer(solrServerHandler -> {
                    if (solrServerHandler.failed()) {
                        result.fail(solrServerHandler.cause());
                    }
                    aVertx.sharedData().getLocalMap(SHARED_DATA_KEY).put(CONFIG_KEY, this);
                    result.complete(this);
                });
            });
        }
    }

    private void setImageServer(final Handler<AsyncResult<Configuration>> aHandler) {
        final Properties properties = System.getProperties();
        final Future<Configuration> result = Future.future();

        final String errorMessage;

        if (aHandler != null) {
            result.setHandler(aHandler);

            if (properties.containsKey(IMAGE_SERVER_PROP)) {
                myImageServer = properties.getProperty(IMAGE_SERVER_PROP);
                LOGGER.debug("Found {} in system properties", IMAGE_SERVER_PROP);
                result.complete(this);
            } else {
                errorMessage = IMAGE_SERVER_PROP + " is not set in system properties";
                result.fail(new ConfigurationException(errorMessage));
            }
        } else {
            errorMessage = "No handler was passed to setImageServer";
            result.fail(new ConfigurationException(errorMessage));
        }
    }

    public String getImageServer() {
        return myImageServer;
    }

    /**
     * Reads the PostgreSQL properties from a JSON configuration file.
     * @param aConfig
     * @return The JsonObject to be used by MetadataHarvestHandler
     */
    private JsonObject setPostgreSQLProperties(JsonObject aConfig) {
        final JsonObject props = new JsonObject();
        props.put("host", aConfig.getString(KATIKON_HOST));
        props.put("port", String.valueOf(aConfig.getInteger(KATIKON_PORT)));
        props.put("database", aConfig.getString(KATIKON_DATABASE));
        props.put("user", aConfig.getString(KATIKON_USER));
        props.put("password", aConfig.getString(KATIKON_PASSWORD));
        props.put("ssl", String.valueOf(aConfig.getBoolean(KATIKON_SSL)));
        props.put("sslfactory", aConfig.getString(KATIKON_SSLFACTORY));

        return props;
    }

    public JsonObject getPostgreSQLProperties() {
        return myPostgreSQLProperties;
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
    public HttpSolrServer getSolrServer() {
        return mySolrServer;
    }

    private void setSolrServer(final Handler<AsyncResult<Configuration>> aHandler) {
        final Properties properties = System.getProperties();
        final Future<Configuration> result = Future.future();

        final String errorMessage;

        if (aHandler != null) {
            result.setHandler(aHandler);

            if (properties.containsKey(SOLR_SERVER_PROP)) {
                mySolrServer = new HttpSolrServer(properties.getProperty(SOLR_SERVER_PROP));
                LOGGER.debug("Found {} in system properties", SOLR_SERVER_PROP);
                result.complete(this);
            } else {
                errorMessage = SOLR_SERVER_PROP + " is not set in system properties";
                result.fail(new ConfigurationException(errorMessage));
            }
        } else {
            errorMessage = "No handler was passed to setSolrServer";
            result.fail(new ConfigurationException(errorMessage));
        }
    }

    /**
     * Gets the metadata harvest interval.
     *
     * @return The metadata harvest interval
     */
    public long getMedatadaHarvestInterval() {
        return myMetadataHarvestInterval;
    }

    /**
     * Sets the metadata harvest interval.
     */
    private void setMetadataHarvestInterval() {
        try {
            myMetadataHarvestInterval =
                    Long.parseLong(System.getProperties().getProperty(Constants.METATADA_HARVEST_INTERVAL));
        } catch (final Exception details) {
            myMetadataHarvestInterval = DEFAULT_METADATA_HARVEST_INTERVAL;
        }
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
