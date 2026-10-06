package server;

import algo.FuzzyMatcher;
import algo.GraphTraversal;
import algo.KMPMatcher;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import core.DynamicArray;
import core.Graph;
import core.HashTable;
import core.Paper;
import json.JsonReader;
import json.JsonWriter;
import report.ReportGenerator;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * JDK {@code com.sun.net.httpserver} backend — Build Plan Phase 8.
 *
 * <p>Locked endpoint contract (mirrors the Python FastAPI backend so the
 * single web dashboard works against either server):
 * <ul>
 *   <li>{@code GET  /api/papers}            — ranked paper list / substring search</li>
 *   <li>{@code POST /api/papers}            — add a paper (in memory)</li>
 *   <li>{@code POST /api/citations}         — add a citation edge (in memory)</li>
 *   <li>{@code GET  /api/search?q=&fuzzy=}</li>
 *   <li>{@code GET  /api/traverse?from=&mode=bfs|dfs}</li>
 *   <li>{@code GET  /api/report?type=top-authors|top-papers|trends}</li>
 *   <li>static files from {@code web/static/} on any other path</li>
 * </ul>
 *
 * <p>Errors use the uniform JSON envelope {@code {"ok":false,"error":"..."}}
 * with 400 (bad request), 404 (not found), 405 (wrong method),
 * 409 (duplicate) or 500 (unexpected).
 *
 * <p>Like the interactive console, mutations are in memory only — the CSV
 * on disk stays the source of truth until the caller persists it
 * (Main's {@code --serve} mode flushes on graceful shutdown).
 */
public final class ApiServer {

    /** Request-validation failure — mapped to HTTP 400. */
    private static final class BadRequest extends RuntimeException {
        BadRequest(String message) {
            super(message);
        }
    }

    /** Missing resource — mapped to HTTP 404. */
    private static final class NotFoundException extends RuntimeException {
        NotFoundException(String message) {
            super(message);
        }
    }

    /** Duplicate resource — mapped to HTTP 409. */
    private static final class ConflictException extends RuntimeException {
        ConflictException(String message) {
            super(message);
        }
    }

    /** Per-request derived graph data: in-degrees and reverse adjacency. */
    private static final class GraphIndex {
        final Graph graph;
        final int[] inDegrees;
        final DynamicArray<DynamicArray<String>> citedBy;

        GraphIndex(Graph graph) {
            this.graph = graph;
            int v = graph.vertexCount();
            this.inDegrees = new int[v];
            this.citedBy = new DynamicArray<>();
            for (int i = 0; i < v; i++) {
                this.citedBy.add(new DynamicArray<>());
            }
            for (int i = 0; i < v; i++) {
                DynamicArray<Integer> neighbors = graph.getNeighbors(i);
                for (int j = 0; j < neighbors.size(); j++) {
                    int to = neighbors.get(j);
                    if (to >= 0 && to < v) {
                        this.inDegrees[to]++;
                        this.citedBy.get(to).add(graph.getPaper(i).getId());
                    }
                }
            }
        }

        /** Citation count = max(stored count, in-degree), like CsvHandler.syncCitationCounts. */
        int citationCount(int index) {
            Paper paper = graph.getPaper(index);
            return Math.max(paper.getCitationCount(), inDegrees[index]);
        }
    }

    private final HttpServer server;
    private final Graph graph;
    private final File staticRoot;

    /** Serves the static dashboard from {@code web/static/} relative to the working directory. */
    public ApiServer(Graph graph, int port) throws IOException {
        this(graph, port, new File("web/static"));
    }

    public ApiServer(Graph graph, int port, File staticRoot) throws IOException {
        this.graph = graph;
        this.staticRoot = staticRoot;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);

        Router router = new Router();
        router.get("/api/papers", this::listPapers);
        router.post("/api/papers", this::createPaper);
        router.post("/api/citations", this::createCitation);
        router.get("/api/search", this::search);
        router.get("/api/traverse", this::traverse);
        router.get("/api/report", this::report);

        this.server.createContext("/", exchange -> {
            try {
                if (!router.dispatch(exchange)) {
                    serveStatic(exchange);
                }
            } catch (BadRequest e) {
                safeError(exchange, 400, e.getMessage());
            } catch (NotFoundException e) {
                safeError(exchange, 404, e.getMessage());
            } catch (ConflictException e) {
                safeError(exchange, 409, e.getMessage());
            } catch (IOException | RuntimeException e) {
                safeError(exchange, 500, "Internal server error: " + e.getMessage());
            }
        });
        this.server.setExecutor(null);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    private static void safeError(HttpExchange exchange, int status, String message) {
        try {
            Router.sendError(exchange, status, message);
        } catch (IOException ignored) {
            // Response was already committed before the failure.
        }
    }

    // ------------------------------------------------------------- GET /api/papers

    private void listPapers(HttpExchange exchange) throws IOException {
        HashTable<String> params = Router.queryParams(exchange);
        String query = value(params, "q", "").trim();
        boolean fuzzy = boolParam(params, "fuzzy");
        int limit = intParam(params, "limit", 20, 1, 200);

        GraphIndex index = new GraphIndex(graph);
        DynamicArray<Paper> results;
        if (query.isEmpty()) {
            results = topPapers(limit, index);
        } else {
            results = searchPapers(query, fuzzy, limit);
        }

        JsonWriter array = JsonWriter.array();
        for (int i = 0; i < results.size(); i++) {
            array.value(paperObject(results.get(i), index));
        }
        array.end();
        Router.sendJson(exchange, 200, JsonWriter.object()
                .field("query", query)
                .field("results", array)
                .field("fuzzy", fuzzy)
                .end()
                .toString());
    }

    // ----------------------------------------------------------- POST /api/papers

    private void createPaper(HttpExchange exchange) throws IOException {
        HashTable<String> fields = parseBody(exchange);
        String id = stringField(fields, "id");
        String title = stringField(fields, "title");
        String author = stringField(fields, "author");

        if (id.isEmpty()) {
            throw new BadRequest("Paper ID cannot be empty");
        }
        if (title.isEmpty()) {
            throw new BadRequest("Paper title cannot be empty");
        }
        if (author.isEmpty()) {
            throw new BadRequest("Author name cannot be empty");
        }

        String yearRaw = fields.get("year");
        int year;
        try {
            year = (yearRaw == null || yearRaw.isEmpty())
                    ? Integer.MIN_VALUE
                    : Integer.parseInt(yearRaw.trim());
        } catch (NumberFormatException e) {
            throw new BadRequest("Publication year must be between 1500 and 2100");
        }
        if (year < 1500 || year > 2100) {
            throw new BadRequest("Publication year must be between 1500 and 2100");
        }
        if (graph.findIndexById(id) != -1) {
            throw new ConflictException("A paper with ID '" + id + "' already exists");
        }

        Paper paper = new Paper(id, title, author, year);
        graph.addVertex(paper);
        GraphIndex index = new GraphIndex(graph);
        Router.sendJson(exchange, 200, paperObject(paper, index).toString());
    }

    // -------------------------------------------------------- POST /api/citations

    private void createCitation(HttpExchange exchange) throws IOException {
        HashTable<String> fields = parseBody(exchange);
        String citing = stringField(fields, "citing");
        String cited = stringField(fields, "cited");

        int fromIndex = graph.findIndexById(citing);
        if (fromIndex == -1) {
            throw new NotFoundException("Citing paper ID not found in graph: " + citing);
        }
        int toIndex = graph.findIndexById(cited);
        if (toIndex == -1) {
            throw new NotFoundException("Cited paper ID not found in graph: " + cited);
        }

        boolean alreadyExists = false;
        DynamicArray<Integer> neighbors = graph.getNeighbors(fromIndex);
        for (int i = 0; i < neighbors.size(); i++) {
            if (neighbors.get(i) == toIndex) {
                alreadyExists = true;
                break;
            }
        }
        graph.addCitation(citing, cited);

        GraphIndex index = new GraphIndex(graph);
        int[] most = mostCited(index);
        Router.sendJson(exchange, 200, JsonWriter.object()
                .field("added", !alreadyExists)
                .field("citing", citing)
                .field("cited", cited)
                .field("papers", graph.vertexCount())
                .field("edges", graph.edgeCount())
                .field("most_cited", most[0] == -1 ? "" : graph.getPaper(most[0]).getId())
                .field("most_cited_count", most[1])
                .end()
                .toString());
    }

    // ----------------------------------------------------------- GET /api/search

    private void search(HttpExchange exchange) throws IOException {
        HashTable<String> params = Router.queryParams(exchange);
        String query = value(params, "q", "").trim();
        if (query.isEmpty()) {
            throw new BadRequest("Search query cannot be empty");
        }
        boolean fuzzy = boolParam(params, "fuzzy");
        int limit = intParam(params, "limit", 20, 1, 200);

        GraphIndex index = new GraphIndex(graph);
        DynamicArray<Paper> results = searchPapers(query, fuzzy, limit);
        JsonWriter array = JsonWriter.array();
        for (int i = 0; i < results.size(); i++) {
            array.value(paperObject(results.get(i), index));
        }
        array.end();
        Router.sendJson(exchange, 200, JsonWriter.object()
                .field("query", query)
                .field("results", array)
                .field("fuzzy", fuzzy)
                .end()
                .toString());
    }

    // ---------------------------------------------------------- GET /api/traverse

    private void traverse(HttpExchange exchange) throws IOException {
        HashTable<String> params = Router.queryParams(exchange);
        String source = value(params, "from", value(params, "source", "")).trim();
        if (source.isEmpty()) {
            throw new BadRequest("Source paper ID cannot be empty");
        }
        String mode = value(params, "mode", "bfs").trim().toLowerCase();
        if (!mode.equals("bfs") && !mode.equals("dfs")) {
            throw new BadRequest("Traversal mode must be 'bfs' or 'dfs'");
        }

        int startIndex = graph.findIndexById(source);
        if (startIndex == -1) {
            throw new NotFoundException("Paper ID '" + source + "' not found in the graph.");
        }

        DynamicArray<Integer> order = mode.equals("dfs")
                ? GraphTraversal.dfs(graph, startIndex)
                : GraphTraversal.bfs(graph, startIndex);

        GraphIndex index = new GraphIndex(graph);
        JsonWriter orderJson = JsonWriter.array();
        for (int i = 0; i < order.size(); i++) {
            int vertex = order.get(i);
            orderJson.value(nodeObject(graph.getPaper(vertex), index.citationCount(vertex)));
        }
        orderJson.end();
        Router.sendJson(exchange, 200, JsonWriter.object()
                .field("found", true)
                .field("mode", mode)
                .field("source", source)
                .field("reached", order.size())
                .field("order", orderJson)
                .end()
                .toString());
    }

    // ----------------------------------------------------------- GET /api/report

    private void report(HttpExchange exchange) throws IOException {
        HashTable<String> params = Router.queryParams(exchange);
        String type = value(params, "type", "").trim().toLowerCase();
        int limit = intParam(params, "limit", 10, 1, 100);
        switch (type) {
            case "top-authors":
                reportAuthors(exchange, limit);
                break;
            case "top-papers":
                reportTopPapers(exchange, limit);
                break;
            case "trends":
                reportTrends(exchange);
                break;
            default:
                throw new BadRequest("Report type must be one of: top-authors, top-papers, trends");
        }
    }

    private void reportAuthors(HttpExchange exchange, int limit) throws IOException {
        DynamicArray<ReportGenerator.AuthorStats> authors = ReportGenerator.getTopAuthors(graph, limit);
        JsonWriter array = JsonWriter.array();
        for (int i = 0; i < authors.size(); i++) {
            ReportGenerator.AuthorStats author = authors.get(i);
            array.value(JsonWriter.object()
                    .field("author", author.getAuthor())
                    .field("papers", author.getPaperCount())
                    .field("totalCitations", author.getTotalCitations())
                    .end());
        }
        array.end();
        Router.sendJson(exchange, 200,
                JsonWriter.object().field("authors", array).end().toString());
    }

    private void reportTopPapers(HttpExchange exchange, int limit) throws IOException {
        GraphIndex index = new GraphIndex(graph);
        DynamicArray<Paper> top = topPapers(limit, index);
        JsonWriter array = JsonWriter.array();
        for (int i = 0; i < top.size(); i++) {
            Paper paper = top.get(i);
            array.value(JsonWriter.object()
                    .field("id", paper.getId())
                    .field("title", paper.getTitle())
                    .field("author", paper.getAuthor())
                    .field("year", paper.getYear())
                    .field("citationCount", index.citationCount(graph.findIndexById(paper.getId())))
                    .end());
        }
        array.end();
        Router.sendJson(exchange, 200, JsonWriter.object()
                .field("papers", array)
                .field("count", top.size())
                .end()
                .toString());
    }

    private void reportTrends(HttpExchange exchange) throws IOException {
        DynamicArray<ReportGenerator.YearTrend> trends = ReportGenerator.getCitationTrends(graph);
        JsonWriter array = JsonWriter.array();
        for (int i = 0; i < trends.size(); i++) {
            ReportGenerator.YearTrend trend = trends.get(i);
            array.value(JsonWriter.object()
                    .field("year", trend.getYear())
                    .field("papers", trend.getPaperCount())
                    .field("totalCitations", trend.getTotalCitations())
                    .end());
        }
        array.end();
        Router.sendJson(exchange, 200,
                JsonWriter.object().field("trends", array).end().toString());
    }

    // -------------------------------------------------------------- static files

    private void serveStatic(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/")) {
            path = "/index.html";
        }
        if (path.contains("..")) {
            Router.sendError(exchange, 400, "Invalid file path");
            return;
        }

        File file = new File(staticRoot, path.substring(1).replace('/', File.separatorChar));
        String rootCanonical = staticRoot.getCanonicalPath();
        String fileCanonical = file.getCanonicalPath();
        if (!file.isFile()
                || (!fileCanonical.equals(rootCanonical)
                    && !fileCanonical.startsWith(rootCanonical + File.separator))) {
            Router.sendError(exchange, 404, "Not found: " + path);
            return;
        }

        byte[] bytes = Files.readAllBytes(file.toPath());
        exchange.getResponseHeaders().set("Content-Type", contentType(path));
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String contentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (path.endsWith(".json")) return "application/json; charset=utf-8";
        if (path.endsWith(".svg")) return "image/svg+xml";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".ico")) return "image/x-icon";
        if (path.endsWith(".txt")) return "text/plain; charset=utf-8";
        return "application/octet-stream";
    }

    // ------------------------------------------------------------------ helpers

    private static HashTable<String> parseBody(HttpExchange exchange) throws IOException {
        String body = Router.readBody(exchange).trim();
        if (body.isEmpty()) {
            throw new BadRequest("Request body must be a JSON object");
        }
        try {
            return JsonReader.parseObject(body);
        } catch (IllegalArgumentException e) {
            throw new BadRequest(e.getMessage());
        }
    }

    private static String stringField(HashTable<String> fields, String key) {
        String value = fields.get(key);
        return value == null ? "" : value.trim();
    }

    private static String value(HashTable<String> params, String key, String fallback) {
        String value = params.get(key);
        return value == null ? fallback : value;
    }

    private static int intParam(HashTable<String> params, String key, int fallback, int min, int max) {
        String raw = params.get(key);
        if (raw == null || raw.isEmpty()) {
            return fallback;
        }
        int parsed;
        try {
            parsed = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new BadRequest("Parameter '" + key + "' must be an integer");
        }
        if (parsed < min || parsed > max) {
            throw new BadRequest("Parameter '" + key + "' must be between " + min + " and " + max);
        }
        return parsed;
    }

    private static boolean boolParam(HashTable<String> params, String key) {
        String raw = value(params, key, "false").trim().toLowerCase();
        if (raw.equals("true") || raw.equals("1")) {
            return true;
        }
        if (raw.equals("false") || raw.equals("0") || raw.isEmpty()) {
            return false;
        }
        throw new BadRequest("Parameter '" + key + "' must be true or false");
    }

    /** Full paper record: identity fields plus in/out citation id lists. */
    private JsonWriter paperObject(Paper paper, GraphIndex index) {
        int vertex = graph.findIndexById(paper.getId());
        DynamicArray<Integer> neighbors = graph.getNeighbors(vertex);
        JsonWriter cites = JsonWriter.array();
        for (int i = 0; i < neighbors.size(); i++) {
            cites.value(graph.getPaper(neighbors.get(i)).getId());
        }
        cites.end();
        DynamicArray<String> citers = index.citedBy.get(vertex);
        JsonWriter citedBy = JsonWriter.array();
        for (int i = 0; i < citers.size(); i++) {
            citedBy.value(citers.get(i));
        }
        citedBy.end();
        return JsonWriter.object()
                .field("id", paper.getId())
                .field("title", paper.getTitle())
                .field("author", paper.getAuthor())
                .field("year", paper.getYear())
                .field("citationCount", index.citationCount(vertex))
                .field("cites", cites)
                .field("citedBy", citedBy)
                .end();
    }

    /** Traversal node: identity fields without the citation lists. */
    private JsonWriter nodeObject(Paper paper, int citationCount) {
        return JsonWriter.object()
                .field("id", paper.getId())
                .field("title", paper.getTitle())
                .field("year", paper.getYear())
                .field("citationCount", citationCount)
                .end();
    }

    /**
     * All papers ranked by citation count (desc), then id (asc) — the same
     * ordering the Python backend's ranked list uses — capped at {@code limit}.
     */
    private DynamicArray<Paper> topPapers(int limit, GraphIndex index) {
        DynamicArray<Paper> ranked = new DynamicArray<>();
        int v = graph.vertexCount();
        boolean[] taken = new boolean[v];
        for (int pick = 0; pick < v; pick++) {
            int best = -1;
            for (int i = 0; i < v; i++) {
                if (taken[i]) {
                    continue;
                }
                if (best == -1 || ranksBefore(i, best, index)) {
                    best = i;
                }
            }
            taken[best] = true;
            ranked.add(graph.getPaper(best));
        }
        DynamicArray<Paper> result = new DynamicArray<>();
        for (int i = 0; i < Math.min(limit, ranked.size()); i++) {
            result.add(ranked.get(i));
        }
        return result;
    }

    private boolean ranksBefore(int a, int b, GraphIndex index) {
        int countA = index.citationCount(a);
        int countB = index.citationCount(b);
        if (countA != countB) {
            return countA > countB;
        }
        return graph.getPaper(a).getId().compareTo(graph.getPaper(b).getId()) < 0;
    }

    /** Index of the most-cited paper and its count (max count, smallest id on ties). */
    private int[] mostCited(GraphIndex index) {
        int best = -1;
        int bestCount = -1;
        for (int i = 0; i < graph.vertexCount(); i++) {
            int count = index.citationCount(i);
            if (best == -1
                    || count > bestCount
                    || (count == bestCount
                        && graph.getPaper(i).getId().compareTo(graph.getPaper(best).getId()) < 0)) {
                best = i;
                bestCount = count;
            }
        }
        return new int[] {best, Math.max(bestCount, 0)};
    }

    /**
     * Exact substring search (KMP across id/title/author) or typo-tolerant
     * fuzzy search (Wagner-Fischer, port of {@code Main.handleSearchPaper}):
     * distance &le; 2, or &le; 3 for queries of 8+ characters, with the
     * same title-word pre-filter the Java console applies.
     */
    private DynamicArray<Paper> searchPapers(String query, boolean fuzzy, int limit) {
        DynamicArray<Paper> hits = new DynamicArray<>();
        String q = query.toLowerCase();
        int maxDist = q.length() >= 8 ? 3 : 2;
        for (int i = 0; i < graph.vertexCount(); i++) {
            Paper paper = graph.getPaper(i);
            boolean matched;
            if (fuzzy) {
                matched = FuzzyMatcher.editDistance(q, paper.getTitle().toLowerCase()) <= maxDist;
                if (!matched) {
                    String[] words = paper.getTitle().toLowerCase()
                            .split("[\\s,.:;!?()\\[\\]\\-]+");
                    for (String word : words) {
                        if (!word.isEmpty()
                                && Math.abs(word.length() - q.length()) <= maxDist
                                && FuzzyMatcher.editDistance(q, word) <= maxDist) {
                            matched = true;
                            break;
                        }
                    }
                }
            } else {
                matched = KMPMatcher.contains(paper.getTitle().toLowerCase(), q)
                        || KMPMatcher.contains(paper.getAuthor().toLowerCase(), q)
                        || KMPMatcher.contains(paper.getId().toLowerCase(), q);
            }
            if (matched) {
                hits.add(paper);
                if (hits.size() >= limit) {
                    break;
                }
            }
        }
        return hits;
    }
}
