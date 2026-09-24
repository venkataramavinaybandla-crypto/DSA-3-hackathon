package report;

import core.DynamicArray;

/**
 * Test suite for GraphRenderer (terminal-art diagram of the path subgraph).
 */
public class GraphRendererTest {

    private static int totalTests = 0;
    private static int passedTests = 0;

    public static void main(String[] args) {
        System.out.println("Running GraphRenderer Test Suite...\n");

        testDiamondIsFiveLines();
        testTriangleLayersByHops();
        testTwoNodeLink();
        testLabelsDrawnExactlyOnce();
        testArrowsForEveryEdge();
        testAsciiAndUnicodePalettes();
        testPathsOverloadDeduplicatesNodesAndEdges();
        testUnknownEdgeEndpointsAreSkipped();
        testEmptyInput();
        testTooManyNodesFallsBackToText();

        System.out.println("\n==========================================");
        System.out.println("GRAPHRENDERER TEST RESULTS: " + passedTests + " / " + totalTests + " PASSED");
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

    private static DynamicArray<String> nodes(String... ids) {
        DynamicArray<String> list = new DynamicArray<>();
        for (String id : ids) {
            list.add(id);
        }
        return list;
    }

    private static DynamicArray<String[]> edges(String... pairs) {
        DynamicArray<String[]> list = new DynamicArray<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            list.add(new String[]{pairs[i], pairs[i + 1]});
        }
        return list;
    }

    private static String[] lines(String art) {
        String trimmed = art.endsWith("\n") ? art.substring(0, art.length() - 1) : art;
        return trimmed.split("\n", -1);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = haystack.indexOf(needle);
        while (idx != -1) {
            count++;
            idx = haystack.indexOf(needle, idx + 1);
        }
        return count;
    }

    // ------------------------------------------------------------------ tests

    private static void testDiamondIsFiveLines() {
        System.out.println("--- diamond (4 nodes, 4 edges) ---");
        DynamicArray<String> n = nodes("A", "B", "C", "D");
        DynamicArray<String[]> e = edges("A", "B", "A", "C", "B", "D", "C", "D");
        String art = GraphRenderer.renderSubgraph(n, e, GraphRenderer.Palette.ASCII);
        System.out.println(art);

        String[] l = lines(art);
        // header line + 5 drawing lines (node row, gap, node row, gap, node row)
        assertEquals("Diamond draws 5 diagram lines for 4 nodes", 6, l.length);
        assertTrue("Diamond keeps all four labels", art.contains("[A]") && art.contains("[B]")
                && art.contains("[C]") && art.contains("[D]"));
        assertTrue("Diamond header reports 4 nodes", art.contains("4 nodes"));
        assertTrue("Diamond header reports 4 edges", art.contains("4 directed edges"));
    }

    private static void testTriangleLayersByHops() {
        System.out.println("\n--- triangle, longest path A -> C -> B (3 nodes, 3 edges) ---");
        DynamicArray<String> n = nodes("A", "B", "C");
        DynamicArray<String[]> e = edges("A", "B", "A", "C", "C", "B");
        String art = GraphRenderer.renderSubgraph(n, e, GraphRenderer.Palette.ASCII);
        System.out.println(art);

        // A on layer 0, C on layer 1, B on layer 2 -> three node lines plus two corridors
        assertEquals("Triangle spans as many layers as its longest path", 6, lines(art).length);
        int rowOfA = rowOf(art, "[A]");
        int rowOfC = rowOf(art, "[C]");
        int rowOfB = rowOf(art, "[B]");
        assertTrue("Source is drawn above every other node", rowOfA < rowOfC && rowOfA < rowOfB);
        assertTrue("Deeper node is drawn below its parent", rowOfC < rowOfB);
    }

    private static int rowOf(String art, String label) {
        String[] l = lines(art);
        for (int i = 0; i < l.length; i++) {
            if (l[i].contains(label)) {
                return i;
            }
        }
        return -1;
    }

    private static void testTwoNodeLink() {
        System.out.println("\n--- two nodes (1 edge) ---");
        DynamicArray<String> n = nodes("P101", "P102");
        DynamicArray<String[]> e = edges("P101", "P102");
        String art = GraphRenderer.renderSubgraph(n, e, GraphRenderer.Palette.ASCII);
        System.out.println(art);

        assertTrue("Two-node diagram shows both boxes", art.contains("[P101]") && art.contains("[P102]"));
        assertTrue("Two-node diagram has exactly one arrow", countOccurrences(art, "v") == 1);
        assertEquals("Two-node header uses singular edge wording", 1, countOccurrences(art, "1 directed edge "));
    }

    private static void testLabelsDrawnExactlyOnce() {
        DynamicArray<String> n = nodes("A", "B", "C", "D");
        DynamicArray<String[]> e = edges("A", "B", "A", "C", "B", "D", "C", "D");
        String art = GraphRenderer.renderSubgraph(n, e, GraphRenderer.Palette.ASCII);

        assertTrue("Label [A] appears exactly once", countOccurrences(art, "[A]") == 1);
        assertTrue("Label [B] appears exactly once", countOccurrences(art, "[B]") == 1);
        assertTrue("Label [C] appears exactly once", countOccurrences(art, "[C]") == 1);
        assertTrue("Label [D] appears exactly once", countOccurrences(art, "[D]") == 1);
    }

    private static void testArrowsForEveryEdge() {
        DynamicArray<String> n = nodes("A", "B", "C", "D");
        DynamicArray<String[]> e = edges("A", "B", "A", "C", "B", "D", "C", "D");
        String art = GraphRenderer.renderSubgraph(n, e, GraphRenderer.Palette.ASCII);

        int arrows = countOccurrences(art, ">") + countOccurrences(art, "<")
                + countOccurrences(art, "^") + countOccurrences(art, "v");
        assertEquals("Every edge ends in an arrowhead", 4, arrows);
    }

    private static void testAsciiAndUnicodePalettes() {
        DynamicArray<String> n = nodes("A", "B", "C", "D");
        DynamicArray<String[]> e = edges("A", "B", "A", "C", "B", "D", "C", "D");
        String ascii = GraphRenderer.renderSubgraph(n, e, GraphRenderer.Palette.ASCII);
        String unicode = GraphRenderer.renderSubgraph(n, e, GraphRenderer.Palette.UNICODE);

        assertTrue("ASCII palette uses no box-drawing glyphs",
                !ascii.contains("\u2500") && !ascii.contains("\u2502") && !ascii.contains("\u25B6"));
        assertTrue("Unicode palette uses box-drawing glyphs",
                unicode.contains("\u2500") && unicode.contains("\u25BC"));
        assertEquals("Both palettes produce the same number of lines",
                lines(ascii).length, lines(unicode).length);
        assertTrue("Palette.auto() returns a usable palette",
                GraphRenderer.Palette.auto() == GraphRenderer.Palette.ASCII
                        || GraphRenderer.Palette.auto() == GraphRenderer.Palette.UNICODE);
    }

    private static void testPathsOverloadDeduplicatesNodesAndEdges() {
        DynamicArray<DynamicArray<String>> paths = new DynamicArray<>();
        paths.add(nodes("P101", "P102", "P104"));
        paths.add(nodes("P101", "P104"));
        paths.add(nodes("P101", "P102", "P104")); // duplicate path, must not add nodes twice

        String art = GraphRenderer.render(paths, GraphRenderer.Palette.ASCII);
        System.out.println("\n--- render(paths) ---");
        System.out.println(art);

        assertTrue("Paths overload reports 3 unique nodes", art.contains("3 nodes"));
        assertTrue("Paths overload reports 3 unique edges", art.contains("3 directed edges"));
        assertTrue("Duplicate path does not duplicate node boxes",
                countOccurrences(art, "[P101]") == 1 && countOccurrences(art, "[P104]") == 1);
    }

    private static void testUnknownEdgeEndpointsAreSkipped() {
        DynamicArray<String> n = nodes("A", "B");
        DynamicArray<String[]> e = edges("A", "B", "A", "GHOST", "GHOST", "B", "A", "A");
        String art = GraphRenderer.renderSubgraph(n, e, GraphRenderer.Palette.ASCII);

        assertTrue("Edges touching unknown nodes are dropped", !art.contains("GHOST"));
        assertTrue("Self-loop edge is dropped",
                countOccurrences(art, "[A]") == 1);
    }

    private static void testEmptyInput() {
        DynamicArray<String> empty = new DynamicArray<>();
        DynamicArray<String[]> noEdges = new DynamicArray<>();
        String art = GraphRenderer.renderSubgraph(empty, noEdges, GraphRenderer.Palette.ASCII);
        assertTrue("Empty subgraph returns a notice", art.contains("Nothing to draw"));

        DynamicArray<DynamicArray<String>> noPaths = new DynamicArray<>();
        String fromPaths = GraphRenderer.render(noPaths, GraphRenderer.Palette.ASCII);
        assertTrue("No paths returns a notice", fromPaths.contains("Nothing to draw"));

        DynamicArray<DynamicArray<String>> nullPaths = null;
        String fromNull = GraphRenderer.render(nullPaths, GraphRenderer.Palette.ASCII);
        assertTrue("Null paths returns a notice without throwing", fromNull.contains("Nothing to draw"));
    }

    private static void testTooManyNodesFallsBackToText() {
        DynamicArray<String> many = new DynamicArray<>();
        for (int i = 0; i < 20; i++) {
            many.add("N" + i);
        }
        DynamicArray<String[]> noEdges = new DynamicArray<>();
        String art = GraphRenderer.renderSubgraph(many, noEdges, GraphRenderer.Palette.ASCII);

        assertTrue("Oversized graph falls back to a notice", art.contains("too many to draw"));
        assertTrue("Oversized graph prints no node boxes", !art.contains("[N0]"));

        // 12 nodes is the largest graph that must still draw
        DynamicArray<String> twelve = new DynamicArray<>();
        for (int i = 0; i < 12; i++) {
            twelve.add("N" + i);
        }
        String drawn = GraphRenderer.renderSubgraph(twelve, noEdges, GraphRenderer.Palette.ASCII);
        System.out.println("\n--- 12 nodes (upper limit) ---");
        System.out.println(drawn);
        assertTrue("12-node graph still draws a diagram", drawn.contains("[N0]") && drawn.contains("[N11]"));
    }
}
