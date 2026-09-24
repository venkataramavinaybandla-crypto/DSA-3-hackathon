package report;

import core.DynamicArray;
import core.Graph;
import core.Paper;

/**
 * Test suite for ReportGenerator (Phase 7).
 */
public class ReportTest {

    private static int totalTests = 0;
    private static int passedTests = 0;

    public static void main(String[] args) {
        System.out.println("Running Phase 7 ReportGenerator Test Suite...\n");

        testTopCitedPapers();
        testTopAuthors();
        testCitationTrends();
        testEmptyGraph();
        testReadOnlyGraphInvariant();

        System.out.println("\n==========================================");
        System.out.println("REPORT TEST RESULTS: " + passedTests + " / " + totalTests + " PASSED");
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

    private static Graph buildSampleGraph() {
        Graph g = new Graph();
        // Vaswani: 1 paper (P1), will get 3 citations
        Paper p1 = new Paper("P1", "Attention Is All You Need", "Vaswani", 2017);
        // Devlin: 2 papers (P2, P4), will get 2 citations + 1 citation = 3 citations total
        Paper p2 = new Paper("P2", "BERT: Pre-training", "Devlin", 2018);
        Paper p4 = new Paper("P4", "RoBERTa: Robustly Optimized", "Devlin", 2019);
        // Brown: 1 paper (P3), 0 citations
        Paper p3 = new Paper("P3", "GPT-3", "Brown", 2020);
        // LeCun: 1 paper (P5), 1 citation
        Paper p5 = new Paper("P5", "CNN Basics", "LeCun", 2015);

        g.addVertex(p1);
        g.addVertex(p2);
        g.addVertex(p3);
        g.addVertex(p4);
        g.addVertex(p5);

        // Citations:
        // P2 cites P1
        // P3 cites P1
        // P4 cites P1 -> P1 has 3 citations
        g.addCitation("P2", "P1");
        g.addCitation("P3", "P1");
        g.addCitation("P4", "P1");

        // P3 cites P2
        // P4 cites P2 -> P2 has 2 citations
        g.addCitation("P3", "P2");
        g.addCitation("P4", "P2");

        // P3 cites P4 -> P4 has 1 citation
        g.addCitation("P3", "P4");

        // P1 cites P5 -> P5 has 1 citation
        g.addCitation("P1", "P5");

        return g;
    }

    private static void testTopCitedPapers() {
        System.out.println("--- Top N Cited Papers Tests ---");
        Graph g = buildSampleGraph();

        DynamicArray<Paper> top3 = ReportGenerator.getTopCitedPapers(g, 3);
        assertEquals("Top 3 papers size is 3", 3, top3.size());
        assertEquals("Rank 1 is P1 (3 citations)", "P1", top3.get(0).getId());
        assertEquals("Rank 1 citations is 3", 3, top3.get(0).getCitationCount());

        assertEquals("Rank 2 is P2 (2 citations)", "P2", top3.get(1).getId());
        assertEquals("Rank 2 citations is 2", 2, top3.get(1).getCitationCount());

        // Ties between P4 and P5 (each 1 citation), stable order preserves insertion (P4 before P5)
        assertEquals("Rank 3 is P4 (1 citation)", "P4", top3.get(2).getId());
        assertEquals("Rank 3 citations is 1", 1, top3.get(2).getCitationCount());

        // Check formatting table output
        String formatted = ReportGenerator.formatTopPapers(g, 3);
        assertTrue("Formatted table contains header", formatted.contains("Rank"));
        assertTrue("Formatted table contains P1", formatted.contains("P1"));
    }

    private static void testTopAuthors() {
        System.out.println("\n--- Top Authors by Total Citations Tests ---");
        Graph g = buildSampleGraph();

        // Devlin: P2 (2 citations) + P4 (1 citation) = 3 citations across 2 papers
        // Vaswani: P1 (3 citations) across 1 paper
        // LeCun: P5 (1 citation) across 1 paper
        // Brown: P3 (0 citations) across 1 paper

        DynamicArray<ReportGenerator.AuthorStats> topAuthors = ReportGenerator.getTopAuthors(g, 2);
        assertEquals("Top 2 authors size is 2", 2, topAuthors.size());

        // Vaswani and Devlin both have 3 total citations.
        // Vaswani's paper appeared first, so stable sort preserves Vaswani at rank 1, Devlin at rank 2
        assertEquals("Rank 1 author is Vaswani", "Vaswani", topAuthors.get(0).getAuthor());
        assertEquals("Vaswani total citations is 3", 3, topAuthors.get(0).getTotalCitations());
        assertEquals("Vaswani paper count is 1", 1, topAuthors.get(0).getPaperCount());

        assertEquals("Rank 2 author is Devlin", "Devlin", topAuthors.get(1).getAuthor());
        assertEquals("Devlin total citations is 3", 3, topAuthors.get(1).getTotalCitations());
        assertEquals("Devlin paper count is 2", 2, topAuthors.get(1).getPaperCount());

        String formatted = ReportGenerator.formatTopAuthors(g, 2);
        assertTrue("Formatted authors table contains Devlin", formatted.contains("Devlin"));
        assertTrue("Formatted authors table contains Vaswani", formatted.contains("Vaswani"));
    }

    private static void testCitationTrends() {
        System.out.println("\n--- Yearly Citation Trends Tests ---");
        Graph g = buildSampleGraph();

        // Years present:
        // 2015: 1 paper (P5), 1 citation
        // 2017: 1 paper (P1), 3 citations
        // 2018: 1 paper (P2), 2 citations
        // 2019: 1 paper (P4), 1 citation
        // 2020: 1 paper (P3), 0 citations
        DynamicArray<ReportGenerator.YearTrend> trends = ReportGenerator.getCitationTrends(g);
        assertEquals("Total distinct years is 5", 5, trends.size());

        assertEquals("First year is 2015", 2015, trends.get(0).getYear());
        assertEquals("2015 papers is 1", 1, trends.get(0).getPaperCount());
        assertEquals("2015 citations is 1", 1, trends.get(0).getTotalCitations());

        assertEquals("Second year is 2017", 2017, trends.get(1).getYear());
        assertEquals("2017 citations is 3", 3, trends.get(1).getTotalCitations());

        assertEquals("Last year is 2020", 2020, trends.get(4).getYear());
        assertEquals("2020 citations is 0", 0, trends.get(4).getTotalCitations());

        String formatted = ReportGenerator.formatCitationTrends(g);
        assertTrue("Formatted trends table contains 2015", formatted.contains("2015"));
        assertTrue("Formatted trends table contains 2017", formatted.contains("2017"));
    }

    private static void testEmptyGraph() {
        System.out.println("\n--- Empty Graph Reports ---");
        Graph empty = new Graph();

        assertEquals("Empty graph top papers is empty", 0, ReportGenerator.getTopCitedPapers(empty, 5).size());
        assertEquals("Empty graph top authors is empty", 0, ReportGenerator.getTopAuthors(empty, 5).size());
        assertEquals("Empty graph trends is empty", 0, ReportGenerator.getCitationTrends(empty).size());

        assertTrue("Empty formatted papers message", ReportGenerator.formatTopPapers(empty, 5).contains("No papers available"));
        assertTrue("Empty formatted authors message", ReportGenerator.formatTopAuthors(empty, 5).contains("No author data"));
        assertTrue("Empty formatted trends message", ReportGenerator.formatCitationTrends(empty).contains("No trend data"));
    }

    private static void testReadOnlyGraphInvariant() {
        System.out.println("\n--- Read-Only Graph Invariant Verification ---");
        Graph g = buildSampleGraph();
        int initialV = g.vertexCount();
        int initialE = g.edgeCount();

        // Run all reports
        ReportGenerator.getTopCitedPapers(g, 10);
        ReportGenerator.getTopAuthors(g, 10);
        ReportGenerator.getCitationTrends(g);
        ReportGenerator.formatTopPapers(g, 10);
        ReportGenerator.formatTopAuthors(g, 10);
        ReportGenerator.formatCitationTrends(g);

        assertEquals("Vertex count unchanged after reports", initialV, g.vertexCount());
        assertEquals("Edge count unchanged after reports", initialE, g.edgeCount());
    }
}
