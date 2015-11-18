
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
     * A path for logouts from the administrative interface.
     */
    public static final String LOGOUT = "/logout";

    /**
     * A generic path for the root of the Web application.
     */
    public static final String ROOT = "/";

    /**
     * A generic path for Web application metrics.
     */
    public static final String STATUS = "/status/*";

    /**
     * A route pattern for serving static files.
     */
    public static final String STATIC_FILES_RE =
            ".*(\\.js|\\.css|\\.ico|\\.png|\\.gif|\\.ttf|\\.eot|\\.svg|\\.woff|\\.woff2|\\.jpg)$";
}
