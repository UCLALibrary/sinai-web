
package edu.ucla.library.sinai;

public interface RoutePatterns {

    /**
     * The base URI for an image request; it redirects to an image info document for the image.
     */
    public static final String BASE_URI_RE = "\\{}\\/([^\\/]+\\/?)";

    /**
     * A catch-all path for the administrative statistics page.
     */
    public static final String METRICS_RE = "\\/metrics\\/?";

    /**
     * A path for logins to the administrative interface.
     */
    public static final String LOGIN = "/login";

    /**
     * A path for administrative interface login responses.
     */
    public static final String LOGIN_RESPONSE_RE = "\\/login-response";

    /**
     * A root path.
     */
    public static final String ROOT = "/";

    /**
     * A path for the browse page.
     */
    public static final String BROWSE = "/browse";

    /**
     * A path for managing user accounts and other administrative tasks.
     */
    public static final String ADMIN = "/admin";

    /**
     * A generic path for Web application metrics.
     */
    public static final String STATUS = "/status/*";

    /**
     * A generic path for the Mirador viewer.
     */
    public static final String VIEWER_RE = "/viewer/.*";

    /**
     * A route pattern for serving static files.
     */
    public static final String STATIC_FILES_RE =
            ".*(\\.txt|\\.js|\\.css|\\.ico|\\.png|\\.gif|\\.ttf|\\.eot|\\.svg|\\.woff|\\.woff2|\\.jpg|translation\\.json|\\.map)$";

    /**
     * A regex that matches route patterns that are to be placed behind an authentication check.
     */
    public static final String AUTHENTICATION_CHECK_RE = METRICS_RE + "|" + STATUS + "|" + BROWSE + "|" + ADMIN +
            "|" + VIEWER_RE;
}
