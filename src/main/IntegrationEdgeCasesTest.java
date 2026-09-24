package main;

import algo.FuzzyMatcher;
import algo.GraphTraversal;
import algo.KMPMatcher;
import algo.MergeSort;
import core.DynamicArray;
import core.Graph;
import core.Paper;
import io.CsvHandler;
import report.ReportGenerator;

import java.io.File;
import java.io.IOException;

/**
 * Edge case test suite verifying edge conditions required for Phase 9:
 * - Empty graph
 * - Self-citation
 * - Duplicate titles
 * - Disconnected clusters
 * - Invalid CSV rows
 */
public class IntegrationEdgeCasesTest {

    private static int totalTests = 0;
    private static int passedTests = 0;

    public static void main(String[] args) {
        System.out.println("Running Phase 9 Integration & Edge Cases Test Suite...\n");

        testEmptyGraphEdgeCases();
        testSelfCitationEdgeCases();
        testDuplicateTitlesAndAuthors();
        testDisconnectedClusters();
        testInvalidCsvRowsAndEscaping();

        System.out.println("\n==========================================");
        System.out.println("EDGE CASES TEST RESULTS: " + passedTests + " / " + totalTests + " PASSED");
        System.out.println("==========================================");

        if (passedTests != totalTests) {
            System.exit(1);
        }
    }

    private static void assertTrue(String name, boolean cond) {
        totalTests++;
        if (cond) {
            passedTests++;
            System.out.println("[PASS] " + name);
        } else {
            System.err.println("[FAIL] " + name);
            throw new AssertionError("FAILED: " + name);
        }
    }

    private static void assertEquals(String name, Object expected, Object actual) {
        totalTests++;
        boolean eq = (expected == null) ? (actual == null) : expected.equals(actual);
        if (eq) {
            passedTests++;
            System.out.println("[PASS] " + name);
        } else {
            System.err.println("[FAIL] " + name + " | Expected=" + expected + " Actual=" + actual);
            throw new AssertionError("FAILED: " + name);
        }
    }

    private static void testEmptyGraphEdgeCases() {
        System.out.println("--- Empty Graph Edge Cases ---");
        Graph empty = new Graph();
        assertEquals("Empty graph vertexCount == 0", 0, empty.vertexCount());
        assertEquals("Empty graph edgeCount == 0", 0, empty.edgeCount());
        assertEquals("Find non-existent ID returns -1", -1, empty.findIndexById("NO_ID"));

        DynamicArray<Paper> topPapers = ReportGenerator.getTopCitedPapers(empty, 10);
        assertEquals("Top papers on empty graph is empty", 0, topPapers.size());

        DynamicArray<ReportGenerator.AuthorStats> topAuthors = ReportGenerator.getTopAuthors(empty, 10);
        assertEquals("Top authors on empty graph is empty", 0, topAuthors.size());

        DynamicArray<ReportGenerator.YearTrend> trends = ReportGenerator.getCitationTrends(empty);
        assertEquals("Trends on empty graph is empty", 0, trends.size());
    }

    private static void testSelfCitationEdgeCases() {
        System.out.println("\n--- Self-Citation Edge Cases ---");
        Graph g = new Graph();
        Paper p = new Paper("S1", "Self-Citing Work", "Solo Author", 2022);
        g.addVertex(p);

        // Add self-citation S1 -> S1
        g.addCitation("S1", "S1");
        assertEquals("Edge count is 1 after self-citation", 1, g.edgeCount());
        assertEquals("Neighbors of S1 contains index 0", 1, g.getNeighbors(0).size());
        assertEquals("Neighbor index is 0", 0, (int) g.getNeighbors(0).get(0));

        // BFS/DFS from self-citing vertex terminates without infinite loop
        DynamicArray<Integer> bfs = GraphTraversal.bfs(g, 0);
        assertEquals("BFS on self-loop terminates with size 1", 1, bfs.size());

        DynamicArray<Integer> dfs = GraphTraversal.dfs(g, 0);
        assertEquals("DFS on self-loop terminates with size 1", 1, dfs.size());
    }

    private static void testDuplicateTitlesAndAuthors() {
        System.out.println("\n--- Duplicate Titles and Multi-Paper Authors ---");
        Graph g = new Graph();
        // Different IDs, but identical titles and authors (e.g. revisions / editions)
        Paper p1 = new Paper("P1", "Machine Learning Survey", "Alice Smith", 2019, 10);
        Paper p2 = new Paper("P2", "Machine Learning Survey", "Alice Smith", 2021, 25);
        Paper p3 = new Paper("P3", "Machine Learning Survey", "Bob Jones", 2023, 25);

        g.addVertex(p1);
        g.addVertex(p2);
        g.addVertex(p3);

        assertEquals("Graph has 3 distinct vertices", 3, g.vertexCount());

        // Exact search by title returns all matching papers
        DynamicArray<Integer> matches = new DynamicArray<>();
        for (int i = 0; i < g.vertexCount(); i++) {
            if (KMPMatcher.contains(g.getPaper(i).getTitle(), "Machine Learning Survey")) {
                matches.add(i);
            }
        }
        assertEquals("Exact title match found 3 papers", 3, matches.size());

        // Stable sort: P2 (25) and P3 (25) tie, P2 must precede P3 because it was inserted earlier
        DynamicArray<Paper> papers = new DynamicArray<>();
        papers.add(p1);
        papers.add(p2);
        papers.add(p3);
        MergeSort.sort(papers);

        assertEquals("Rank 0 is P2 (25)", "P2", papers.get(0).getId());
        assertEquals("Rank 1 is P3 (25, stable order)", "P3", papers.get(1).getId());
        assertEquals("Rank 2 is P1 (10)", "P1", papers.get(2).getId());

        // Author aggregation for Alice Smith: 10 + 25 = 35 citations across 2 papers
        DynamicArray<ReportGenerator.AuthorStats> authors = ReportGenerator.getTopAuthors(g, 5);
        assertEquals("Alice Smith is top author", "Alice Smith", authors.get(0).getAuthor());
        assertEquals("Alice total citations is 35", 35, authors.get(0).getTotalCitations());
        assertEquals("Alice paper count is 2", 2, authors.get(0).getPaperCount());
    }

    private static void testDisconnectedClusters() {
        System.out.println("\n--- Disconnected Clusters ---");
        // Cluster 1: A1 -> A2 -> A3
        // Cluster 2: B1 -> B2
        // Cluster 3: Isolated C1
        Graph g = new Graph();
        g.addVertex(new Paper("A1", "Cluster A1", "Author A", 2020));
        g.addVertex(new Paper("A2", "Cluster A2", "Author A", 2021));
        g.addVertex(new Paper("A3", "Cluster A3", "Author A", 2022));
        g.addVertex(new Paper("B1", "Cluster B1", "Author B", 2020));
        g.addVertex(new Paper("B2", "Cluster B2", "Author B", 2021));
        g.addVertex(new Paper("C1", "Isolated C1", "Author C", 2020));

        g.addCitation("A1", "A2");
        g.addCitation("A2", "A3");
        g.addCitation("B1", "B2");

        // BFS from A1 visits only A1, A2, A3 (3 papers)
        DynamicArray<Integer> bfsA1 = GraphTraversal.bfs(g, g.findIndexById("A1"));
        assertEquals("Cluster A BFS visit count is 3", 3, bfsA1.size());
        assertTrue("Does not reach B1", !bfsA1.contains(g.findIndexById("B1")));
        assertTrue("Does not reach C1", !bfsA1.contains(g.findIndexById("C1")));
    }

    private static void testInvalidCsvRowsAndEscaping() {
        System.out.println("\n--- Invalid CSV Rows and Complex Quoting ---");
        String testFile = "temp_edge_csv_test.csv";
        File f = new File(testFile);
        try {
            java.io.BufferedWriter bw = new java.io.BufferedWriter(new java.io.FileWriter(f));
            bw.write("# Some Header Comment\n");
            bw.write("\n"); // blank line
            bw.write("# PAPERS\n");
            bw.write("id,title,author,year,citationCount\n");
            bw.write("E1,\"Complex, \"\"Title\"\" with, Commas and Quotes\",\"Doe, John & Jane\",2020,4\n");
            bw.write("   \n"); // line with spaces
            bw.write("E2,Regular Title,Regular Author,notAnIntYear,1\n"); // malformed year handled gracefully
            bw.write("short_row\n"); // malformed row ignored
            bw.write("# CITATIONS\n");
            bw.write("citingPaperId,citedPaperId\n");
            bw.write("E1,E2\n");
            bw.write("NON_EXISTENT_1,E2\n"); // ignored gracefully
            bw.write("E1,NON_EXISTENT_2\n"); // ignored gracefully
            bw.close();

            Graph loaded = CsvHandler.load(testFile);
            assertEquals("2 valid papers loaded", 2, loaded.vertexCount());
            int idxE1 = loaded.findIndexById("E1");
            assertTrue("E1 exists", idxE1 != -1);
            Paper pE1 = loaded.getPaper(idxE1);
            assertEquals("E1 complex title parsed correctly", "Complex, \"Title\" with, Commas and Quotes", pE1.getTitle());
            assertEquals("E1 author parsed correctly", "Doe, John & Jane", pE1.getAuthor());

            int idxE2 = loaded.findIndexById("E2");
            assertTrue("E2 exists", idxE2 != -1);
            Paper pE2 = loaded.getPaper(idxE2);
            assertEquals("E2 year defaulted to 0 on parse error", 0, pE2.getYear());
            assertEquals("1 valid citation edge loaded", 1, loaded.edgeCount());

        } catch (IOException e) {
            throw new AssertionError("Edge CSV test failed: " + e.getMessage());
        } finally {
            if (f.exists()) {
                f.delete();
            }
        }
    }
}
