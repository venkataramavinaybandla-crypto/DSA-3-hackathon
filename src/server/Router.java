package server;

import com.sun.net.httpserver.HttpExchange;
import core.DynamicArray;
import core.HashTable;
import json.JsonWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Shared routing helper for the JDK {@code HttpServer} backend — Build
 * Plan Phase 8. Registers routes, dispatches by HTTP method, and provides
 * the request-parsing primitives every handler needs: query parameters
 * and JSON request bodies.
 *
 * <p>Also defines the uniform JSON error envelope
 * {@code {"ok":false,"error":"..."}} used for 400/404/405/409/500
 * responses, so API consumers always see the same shape.
 */
public final class Router {

    /** One registered route. */
    private static final class Route {
        final String method;
        final String path;
        final Handler handler;

        Route(String method, String path, Handler handler) {
            this.method = method;
            this.path = path;
            this.handler = handler;
        }
    }

    /** Callback that fully handles a matched exchange. */
    public interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private final DynamicArray<Route> routes = new DynamicArray<>();

    public void get(String path, Handler handler) {
        add("GET", path, handler);
    }

    public void post(String path, Handler handler) {
        add("POST", path, handler);
    }

    private void add(String method, String path, Handler handler) {
        routes.add(new Route(method, path, handler));
    }

    /**
     * Dispatches an exchange to the first route matching path and method.
     *
     * @return true if a route matched (or a 405 was sent for a known path
     *         with the wrong method); false when no route knows the path
     */
    public boolean dispatch(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        for (int i = 0; i < routes.size(); i++) {
            Route route = routes.get(i);
            if (route.path.equals(path) && route.method.equals(method)) {
                route.handler.handle(exchange);
                return true;
            }
        }
        for (int i = 0; i < routes.size(); i++) {
            if (routes.get(i).path.equals(path)) {
                sendError(exchange, 405, "Method " + method + " is not allowed on " + path);
                return true;
            }
        }
        return false;
    }

    /** Sends the uniform JSON error envelope with the given status code. */
    public static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        String body = JsonWriter.object()
                .field("ok", false)
                .field("error", message)
                .end()
                .toString();
        sendJson(exchange, status, body);
    }

    /** Writes a JSON response body with the given status code. */
    public static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** Parses the raw query string into decoded key/value pairs (last value wins). */
    public static HashTable<String> queryParams(HttpExchange exchange) {
        HashTable<String> params = new HashTable<>();
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null || raw.isEmpty()) {
            return params;
        }
        int start = 0;
        for (int i = 0; i <= raw.length(); i++) {
            if (i == raw.length() || raw.charAt(i) == '&') {
                if (i > start) {
                    String pair = raw.substring(start, i);
                    int eq = pair.indexOf('=');
                    String key = eq == -1 ? pair : pair.substring(0, eq);
                    String value = eq == -1 ? "" : pair.substring(eq + 1);
                    params.put(decode(key), decode(value));
                }
                start = i + 1;
            }
        }
        return params;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            return value; // UTF-8 is always supported
        }
    }

    /** Reads the full request body as a UTF-8 string (empty when absent). */
    public static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
