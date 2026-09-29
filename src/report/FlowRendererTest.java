package report;

import core.DynamicArray;

/**
 * Test suite for FlowRenderer (boxed flowchart rendering of citation paths).
 *
 * <p>Checks the geometry rather than the pixels: every row of a diagram must have identical width,
 * the shared box width must follow the longest paper ID, long chains must wrap without losing a
 * paper, and the ASCII palette must kick in when box-drawing glyphs cannot be encoded.
 */
public class FlowRendererTest {

    private static int totalTests = 0;
    private static int passedTests = 0;

    public static void main(String[] args) {
        System.out.println("Running FlowRenderer Test Suite...\n");

        testSinglePath();
        testSharedBoxWidth();
        testRowGeometry();
        testWrappingKeepsEveryPaper();
        testEmptyInput();
        testAsciiFallback();
        testHamiltonianBadge();
        testParsesOptimalPath();
        testRejectsNonPathText();

        System.out.println("\n==========================================");
        System.out.println("FLOWRENDERER TEST RESULTS: " + passedTests + " / " + totalTests + " PASSED");
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

    private static String stripAnsi(String text) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\u001B' && i + 1 < text.length() && text.charAt(i + 1) == '[') {
                int j = i + 2;
                while (j < text.length() && !(text.charAt(j) >= '@' && text.charAt(j) <= '~')) {
                    j++;
                }
                i = j + 1;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    private static DynamicArray<String> path(String... ids) {
        DynamicArray<String> list = new DynamicArray<>();
        for (String id : ids) {
            list.add(id);
        }
        return list;
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

    /**
     * Counts the drawn node boxes in a palette-aware way: the rounded palette marks every box with
     * one top-left corner, while the ASCII palette draws four plain {@code +} corners per box.
     */
    private static int countBoxes(String art) {
        String plainArt = stripAnsi(art);
        if (Style.unicode()) {
            return countOccurrences(plainArt, "\u256D");
        }
        return countOccurrences(plainArt, "+") / 4;
    }

    private static boolean allRowsFit(String art) {
        for (String line : art.split("\n", -1)) {
            if (Style.visibleLength(line) > Style.width()) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ tests

    private static void testSinglePath() {
        System.out.println("--- single path ---");
        DynamicArray<DynamicArray<String>> paths = new DynamicArray<>();
        paths.add(path("P102", "P101", "P104"));

        String art = FlowRenderer.render(paths);
        System.out.println(stripAnsi(art));

        assertTrue("Every paper appears", art.contains("P102") && art.contains("P101") && art.contains("P104"));
        assertTrue("Header names the path and hop count", stripAnsi(art).contains("Path 1 (hops=2)"));
        assertTrue("A three-paper chain draws three boxes", countBoxes(art) == 3);
        assertTrue("Two connectors join three boxes", countOccurrences(art, arrow()) == 2);
    }

    private static String arrow() {
        return Style.unicode() ? "\u25B6" : ">";
    }

    private static void testSharedBoxWidth() {
        System.out.println("\n--- shared box width ---");
        DynamicArray<DynamicArray<String>> paths = new DynamicArray<>();
        paths.add(path("P1", "P2"));
        paths.add(path("ARXIV-2024-0001", "P2"));

        String longest = "ARXIV-2024-0001";
        int width = FlowRenderer.boxWidth(paths);
        assertEquals("Box width follows the longest ID plus padding and borders",
                longest.length() + 4, width);
    }

    private static void testRowGeometry() {
        System.out.println("\n--- row geometry ---");
        DynamicArray<DynamicArray<String>> paths = new DynamicArray<>();
        paths.add(path("P102", "P101", "P104"));

        String art = FlowRenderer.render(paths);
        String[] lines = art.split("\n", -1);
        int top = 1;
        int middle = 2;
        int bottom = 3;

        assertEquals("Top, label and bottom rows line up", Style.visibleLength(lines[top]),
                Style.visibleLength(lines[middle]));
        assertEquals("Bottom row matches the top row", Style.visibleLength(lines[top]),
                Style.visibleLength(lines[bottom]));
        assertTrue("No row overflows the display width", allRowsFit(art));
    }

    private static void testWrappingKeepsEveryPaper() {
        System.out.println("\n--- wrapping ---");
        DynamicArray<String> longPath = new DynamicArray<>();
        for (int i = 0; i < 40; i++) {
            longPath.add("N" + (100 + i));
        }
        DynamicArray<DynamicArray<String>> paths = new DynamicArray<>();
        paths.add(longPath);

        String art = FlowRenderer.render(paths);
        assertTrue("Wrapped diagram stays inside the display width", allRowsFit(art));
        assertEquals("Header is printed once", 1, countOccurrences(stripAnsi(art), "Path 1"));
        int missing = 0;
        for (int i = 0; i < 40; i++) {
            if (!art.contains("N" + (100 + i))) {
                missing++;
            }
        }
        assertEquals("Every paper of the wrapped chain is drawn", 0, missing);
        assertEquals("The joining paper is repeated between rows", 2, countOccurrences(art, "N105"));
        assertTrue("More than one box row was drawn",
                countOccurrences(art, Style.unicode() ? "\u21B3" : "\\") > 0);
    }

    private static void testEmptyInput() {
        System.out.println("\n--- empty input ---");
        DynamicArray<DynamicArray<String>> empty = new DynamicArray<>();
        assertTrue("No paths reports a notice", FlowRenderer.render(empty).contains("nothing to draw"));
        assertTrue("Null paths reports a notice", FlowRenderer.render(null).contains("nothing to draw"));

        DynamicArray<String> single = path("P101");
        String art = FlowRenderer.renderPath(single, 1, false, FlowRenderer.boxWidth(null));
        assertTrue("A one-paper path has zero hops", stripAnsi(art).contains("hops=0"));
        assertEquals("A one-paper path draws one box", 1, countBoxes(art));
    }

    private static void testAsciiFallback() {
        System.out.println("\n--- palette ---");
        DynamicArray<DynamicArray<String>> paths = new DynamicArray<>();
        paths.add(path("P1", "P2"));
        String art = FlowRenderer.render(paths);

        if (Style.unicode()) {
            assertTrue("Unicode palette uses rounded corners", art.contains("\u256D") && art.contains("\u256F"));
            assertTrue("Unicode palette uses a solid arrow", art.contains("\u25B6"));
            assertTrue("Unicode palette avoids ASCII corners", !art.contains("+--"));
        } else {
            assertTrue("ASCII palette uses plus corners", art.contains("+--"));
            assertTrue("ASCII palette uses an ASCII arrow", art.contains(">"));
            assertTrue("ASCII palette avoids box drawing", !art.contains("\u256D"));
        }
    }

    private static void testParsesOptimalPath() {
        System.out.println("\n--- optimal path parsing ---");
        DynamicArray<String> ids = FlowRenderer.parseIds(
                "Minimum-cost citation path (hops=2): P102 -> P101 -> P104");
        assertEquals("A three-paper route parses into three ids", 3, ids.size());
        assertEquals("Route start", "P102", ids.get(0));
        assertEquals("Route middle", "P101", ids.get(1));
        assertEquals("Route end", "P104", ids.get(2));

        DynamicArray<String> single = FlowRenderer.parseIds("Minimum-cost citation path (hops=0): P101");
        assertEquals("A one-paper route parses", 1, single.size());
        assertEquals("Single id", "P101", single.get(0));

        String art = FlowRenderer.renderSequence(ids, FlowRenderer.boxWidth(null));
        assertTrue("A parsed route draws one arrow per hop", countOccurrences(art, arrow()) == 2);
        assertTrue("The sequence renderer prints no path header", !stripAnsi(art).contains("Path 1"));
    }

    private static void testRejectsNonPathText() {
        System.out.println("\n--- non-path text ---");
        assertEquals("A no-path message is not parsed", 0, FlowRenderer.parseIds(
                "No valid path exists that visits all 3 specified papers using directed citation edges.").size());
        assertEquals("An unknown-id notice is not parsed", 0, FlowRenderer.parseIds(
                "No valid path exists: paper ID 'X' was not found in the graph.").size());
        assertEquals("Null text is tolerated", 0, FlowRenderer.parseIds(null).size());
        assertTrue("An empty sequence draws a notice",
                FlowRenderer.renderSequence(new DynamicArray<>(), 8).contains("empty path"));
    }

    private static void testHamiltonianBadge() {
        System.out.println("\n--- hamiltonian badge ---");
        DynamicArray<String> p = path("A", "B");
        String flagged = FlowRenderer.renderPath(p, 3, true, FlowRenderer.boxWidth(null));
        String plain = FlowRenderer.renderPath(p, 3, false, FlowRenderer.boxWidth(null));

        assertTrue("Flagged path is badged", stripAnsi(flagged).contains("HAMILTONIAN"));
        assertTrue("Unflagged path is not badged", !stripAnsi(plain).contains("HAMILTONIAN"));
        assertTrue("Path numbering comes from the caller", stripAnsi(flagged).contains("Path 3"));
    }
}
