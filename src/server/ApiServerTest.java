package server;

import core.Graph;
import core.Paper;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Phase 11 — end-to-end tests for the {@link ApiServer} REST backend.
 *
 * <p>Starts the server on an ephemeral port against a fixture graph and a
 * temporary static root, then exercises every endpoint in the locked API
 * contract (Phase 8): happy paths, validation failures (400), missing
 * resources (404), wrong methods (405), duplicate papers (409), static file
 * serving, path-traversal rejection, in-memory mutations, and an empty-graph
 * server.
 *
 * <p>Plain {@code main()} style with pass/fail counters — exits 1 on any
 * failure.
 */
public final class ApiServerTest {

    private static int totalTests = 0;
    private static int passedTests = 0;

    public static void main(String[] args) throws Exception {
        Graph graph = fixtureGraph();
        File staticRoot = Files.createTempDirectory("cas-apitest").toFile();
        Files.write(new File(staticRoot, "index.html").toPath(),
                "<html><body>static-ok</body></html>".getBytes(StandardCharsets.UTF_8));

        ApiServer server = new ApiServer(graph, 0, staticRoot);
        server.start();
        ApiServer emptyServer = new ApiServer(new Graph(), 0, staticRoot);
        emptyServer.start();

        try {
            check("server binds an ephemeral port", true, server.getPort() > 0);
            String base = "http://127.0.0.1:" + server.getPort();
            HttpClient client = HttpClient.newHttpClient();

            // Read-only phases first — mutations below change the graph.
            testListPapers(client, base);
            testSearch(client, base);
            testTraverse(client, base);
            testReports(client, base);
            testRoutingAndErrors(client, base);
            testStatic(client, base);
            testMutations(client, base);
            testEmptyGraph(client, "http://127.0.0.1:" + emptyServer.getPort());
        } finally {
            server.stop();
            emptyServer.stop();
            new File(staticRoot, "index.html").delete();
            staticRoot.delete();
        }

        System.out.println("==========================================");
        System.out.println("APISERVER TEST RESULTS: " + passedTests + " / " + totalTests + " PASSED");
        System.out.println("==========================================");
        if (passedTests != totalTests) {
            System.exit(1);
        }
    }

    // --------------------------------------------------------------- fixtures

    private static Graph fixtureGraph() {
        Graph graph = new Graph();
        graph.addVertex(new Paper("T1", "Attention Mechanisms", "Alice Smith", 2017));
        graph.addVertex(new Paper("T2", "Graph Neural Networks", "Bob Jones", 2019));
        graph.addVertex(new Paper("T3", "Attention Is All You Need Again", "Alice Smith", 2021));
        graph.addCitation("T2", "T1");
        graph.addCitation("T3", "T1");
        return graph;
    }

    private static HttpResponse<String> get(HttpClient client, String url)
            throws IOException, InterruptedException {
        return client.send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(HttpClient client, String url, String body)
            throws IOException, InterruptedException {
        return client.send(
                HttpRequest.newBuilder(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    // ------------------------------------------------------------ GET /api/papers

    private static void testListPapers(HttpClient client, String base) throws Exception {
        HttpResponse<String> r = get(client, base + "/api/papers");
        check("GET /api/papers -> 200", 200, r.statusCode());
        String body = r.body();
        check("GET /api/papers returns all fixture papers",
                body.contains("\"id\":\"T1\"")
                    && body.contains("\"id\":\"T2\"")
                    && body.contains("\"id\":\"T3\""));

        r = get(client, base + "/api/papers?limit=1");
        check("GET /api/papers?limit=1 -> 200", 200, r.statusCode());
        check("limit=1 returns only the most-cited paper",
                !r.body().contains("Graph Neural Networks"));

        r = get(client, base + "/api/papers?q=attention");
        check("GET /api/papers?q=attention -> 200", 200, r.statusCode());
        check("exact query matches both attention titles",
                r.body().contains("Attention Mechanisms")
                    && r.body().contains("Attention Is All You Need Again")
                    && !r.body().contains("Graph Neural Networks"));

        r = get(client, base + "/api/papers?q=atention");
        check("exact search for a typo finds nothing",
                r.body().contains("\"results\":[]"));

        r = get(client, base + "/api/papers?q=atention&fuzzy=true");
        check("fuzzy search tolerates the typo",
                r.body().contains("\"fuzzy\":true") && r.body().contains("\"id\":\"T1\""));

        r = get(client, base + "/api/papers?fuzzy=maybe");
        check("fuzzy parameter rejects non-boolean values -> 400", 400, r.statusCode());
        check("validation failure uses the error envelope", true, r.body().contains("\"ok\":false"));
    }

    // -------------------------------------------------------------- GET /api/search

    private static void testSearch(HttpClient client, String base) throws Exception {
        HttpResponse<String> r = get(client, base + "/api/search?q=graph");
        check("GET /api/search -> 200", 200, r.statusCode());
        check("search finds the graph paper", true, r.body().contains("Graph Neural Networks"));

        r = get(client, base + "/api/search");
        check("search without q -> 400", 400, r.statusCode());
        check("missing-query error envelope", true, r.body().contains("\"ok\":false"));

        r = get(client, base + "/api/search?q=atention&fuzzy=true");
        check("fuzzy search endpoint tolerates the typo", true, r.body().contains("\"id\":\"T1\""));
    }

    // ------------------------------------------------------------ GET /api/traverse

    private static void testTraverse(HttpClient client, String base) throws Exception {
        HttpResponse<String> r = get(client, base + "/api/traverse?from=T2&mode=bfs");
        check("GET /api/traverse bfs -> 200", 200, r.statusCode());
        check("bfs from T2 reaches T2 and T1", true, r.body().contains("\"reached\":2"));

        r = get(client, base + "/api/traverse?from=T2&mode=dfs");
        check("GET /api/traverse dfs -> 200", 200, r.statusCode());
        check("dfs mode is echoed back", true, r.body().contains("\"mode\":\"dfs\""));

        r = get(client, base + "/api/traverse?from=T2&mode=teleport");
        check("invalid traversal mode -> 400", 400, r.statusCode());

        r = get(client, base + "/api/traverse?from=NOPE");
        check("unknown source paper -> 404", 404, r.statusCode());

        r = get(client, base + "/api/traverse");
        check("missing source paper -> 400", 400, r.statusCode());

        r = get(client, base + "/api/traverse?source=T2&mode=bfs");
        check("source parameter works as an alias for from", 200, r.statusCode());
    }

    // ------------------------------------------------------------- GET /api/report

    private static void testReports(HttpClient client, String base) throws Exception {
        HttpResponse<String> r = get(client, base + "/api/report?type=top-authors");
        check("GET top-authors report -> 200", 200, r.statusCode());
        check("top-authors names the fixture author", true, r.body().contains("Alice Smith"));

        r = get(client, base + "/api/report?type=top-papers");
        check("GET top-papers report -> 200", 200, r.statusCode());
        check("top-papers ranks the twice-cited paper first", true, r.body().contains("\"id\":\"T1\""));

        r = get(client, base + "/api/report?type=trends");
        check("GET trends report -> 200", 200, r.statusCode());
        check("trends includes the 2017 bucket", true, r.body().contains("\"year\":2017"));

        r = get(client, base + "/api/report?type=bogus");
        check("unknown report type -> 400", 400, r.statusCode());

        r = get(client, base + "/api/report?type=top-authors&limit=0");
        check("report limit=0 -> 400", 400, r.statusCode());
    }

    // ----------------------------------------------- routing & error-shape checks

    private static void testRoutingAndErrors(HttpClient client, String base) throws Exception {
        HttpResponse<String> r = post(client, base + "/api/search", "{}");
        check("POST on a GET-only route -> 405", 405, r.statusCode());
        check("405 uses the error envelope", true, r.body().contains("\"ok\":false"));

        r = get(client, base + "/api/does-not-exist");
        check("unknown API path falls through to static 404", 404, r.statusCode());
    }

    // --------------------------------------------------------- static file serving

    private static void testStatic(HttpClient client, String base) throws Exception {
        HttpResponse<String> r = get(client, base + "/");
        check("GET / serves index.html -> 200", 200, r.statusCode());
        check("index.html body is served", true, r.body().contains("static-ok"));
        String contentType = r.headers().firstValue("Content-Type").orElse("");
        check("index.html is text/html", true, contentType.startsWith("text/html"));

        r = get(client, base + "/missing.css");
        check("missing static file -> 404", 404, r.statusCode());

        r = get(client, base + "/%2e%2e/secret.txt");
        check("path traversal is rejected -> 400", 400, r.statusCode());
    }

    // ------------------------------------------------------- in-memory mutations

    private static void testMutations(HttpClient client, String base) throws Exception {
        HttpResponse<String> r = post(client, base + "/api/papers",
                "{\"id\":\"T4\",\"title\":\"Fuzzy Matching in Practice\","
                + "\"author\":\"Carol White\",\"year\":2023}");
        check("POST /api/papers -> 200", 200, r.statusCode());
        check("created paper is echoed back", true, r.body().contains("\"id\":\"T4\""));
        check("create response closes with exactly one brace",
                r.body().endsWith("\"citedBy\":[]}"));

        r = post(client, base + "/api/papers",
                "{\"id\":\"T4\",\"title\":\"Duplicate\","
                + "\"author\":\"Carol White\",\"year\":2023}");
        check("duplicate paper ID -> 409", 409, r.statusCode());
        check("conflict uses the error envelope", true, r.body().contains("\"ok\":false"));

        r = post(client, base + "/api/papers",
                "{\"id\":\"T5\",\"author\":\"X\",\"year\":2020}");
        check("paper without a title -> 400", 400, r.statusCode());

        r = post(client, base + "/api/papers",
                "{\"id\":\"T5\",\"title\":\"X\",\"author\":\"Y\",\"year\":3000}");
        check("paper with an impossible year -> 400", 400, r.statusCode());

        r = post(client, base + "/api/papers", "oops-not-json");
        check("malformed JSON body -> 400", 400, r.statusCode());

        r = get(client, base + "/api/papers?q=fuzzy");
        check("GET /api/papers sees the newly created paper",
                200, r.statusCode());
        check("new paper appears in exact search", true, r.body().contains("\"id\":\"T4\""));

        r = post(client, base + "/api/citations", "{\"citing\":\"T4\",\"cited\":\"T1\"}");
        check("POST /api/citations -> 200", 200, r.statusCode());
        check("new citation reports added=true", true, r.body().contains("\"added\":true"));

        r = post(client, base + "/api/citations", "{\"citing\":\"T4\",\"cited\":\"T1\"}");
        check("repeating a citation keeps 200", 200, r.statusCode());
        check("repeated citation reports added=false", true, r.body().contains("\"added\":false"));

        r = post(client, base + "/api/citations", "{\"citing\":\"NOPE\",\"cited\":\"T1\"}");
        check("unknown citing paper -> 404", 404, r.statusCode());

        r = post(client, base + "/api/citations", "{\"citing\":\"T1\",\"cited\":\"NOPE\"}");
        check("unknown cited paper -> 404", 404, r.statusCode());

        r = post(client, base + "/api/citations", "{\"citing\":\"T4\",\"cited\":\"T4\"}");
        check("self-citation is accepted", 200, r.statusCode());

        r = get(client, base + "/api/papers?q=attention");
        check("T1 citation count reflects the new in-edge",
                true, r.body().contains("\"citationCount\":3"));
    }

    // -------------------------------------------------- empty-graph edge cases

    private static void testEmptyGraph(HttpClient client, String base) throws Exception {
        HttpResponse<String> r = get(client, base + "/api/papers");
        check("empty graph lists papers -> 200", 200, r.statusCode());
        check("empty graph returns no papers", true, r.body().contains("\"results\":[]"));

        r = get(client, base + "/api/traverse?from=ANY");
        check("traversal on an empty graph -> 404", 404, r.statusCode());

        r = get(client, base + "/api/report?type=trends");
        check("trends on an empty graph -> 200", 200, r.statusCode());
        check("empty graph has no trend buckets", true, r.body().contains("\"trends\":[]"));

        r = post(client, base + "/api/citations", "{\"citing\":\"A\",\"cited\":\"B\"}");
        check("citation on an empty graph -> 404", 404, r.statusCode());
    }

    // ------------------------------------------------------------------ checks

    private static void check(String name, int expected, int actual) {
        totalTests++;
        if (expected == actual) {
            passedTests++;
            System.out.println("  [PASS] " + name);
        } else {
            System.out.println("  [FAIL] " + name);
            System.out.println("         expected: " + expected);
            System.out.println("         actual:   " + actual);
        }
    }

    private static void check(String name, String expected, String actual) {
        totalTests++;
        if (expected.equals(actual)) {
            passedTests++;
            System.out.println("  [PASS] " + name);
        } else {
            System.out.println("  [FAIL] " + name);
            System.out.println("         expected: " + expected);
            System.out.println("         actual:   " + actual);
        }
    }

    private static void check(String name, boolean expected, boolean actual) {
        totalTests++;
        if (expected == actual) {
            passedTests++;
            System.out.println("  [PASS] " + name);
        } else {
            System.out.println("  [FAIL] " + name);
            System.out.println("         expected: " + expected);
            System.out.println("         actual:   " + actual);
        }
    }

    private static void check(String name, boolean actual) {
        check(name, true, actual);
    }
}
