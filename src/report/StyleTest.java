package report;

/**
 * Test suite for Style (terminal presentation helpers).
 *
 * <p>Every assertion holds in both modes - colour on and colour off - because the helpers must
 * never change the printable text, only decorate it. That is the contract the interactive console
 * relies on: prompts, menu numbering and report data stay byte-for-byte identical when colour is
 * disabled (piped output, legacy consoles, {@code NO_COLOR}).
 */
public class StyleTest {

    private static int totalTests = 0;
    private static int passedTests = 0;

    public static void main(String[] args) {
        System.out.println("Running Style Test Suite...\n");

        testVisibleLengthIgnoresAnsi();
        testPadAndCenterIgnoreAnsi();
        testTruncateKeepsFramesAndColourIntact();
        testStatusColourisesOnlyKnownTags();
        testPanelLinesFitDisplayWidth();
        testBannerLayout();
        testRainbowTheme();
        testRuleSpansDisplayWidth();
        testBarScalesAndFills();
        testMenuLineKeepsWording();
        testColourDepthIsUsable();
        testFoldWrapsCaptions();
        testDiagramKeepsNodeLabelsIntact();

        System.out.println("\n==========================================");
        System.out.println("STYLE TEST RESULTS: " + passedTests + " / " + totalTests + " PASSED");
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

    /** Collapses every whitespace run to one space, so re-wrapped text can be compared. */
    private static String squash(String text) {
        StringBuilder sb = new StringBuilder();
        boolean spaced = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ' || c == '\n' || c == '\r') {
                if (!spaced) {
                    sb.append(' ');
                }
                spaced = true;
            } else {
                sb.append(c);
                spaced = false;
            }
        }
        return sb.toString().trim();
    }

    /** Strips ANSI escapes, so textual assertions read the same with colour on or off. */
    private static String plain(String text) {
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

    /** Splits a rendered panel into lines, ignoring the single trailing newline the helpers emit. */
    private static String[] lines(String panel) {
        String trimmed = panel.endsWith("\n") ? panel.substring(0, panel.length() - 1) : panel;
        return trimmed.split("\n", -1);
    }

    /** @return true when every ANSI escape in the text is complete, i.e. nothing was cut in half */
    private static boolean hasWholeEscapes(String text) {
        int i = 0;
        while (i < text.length()) {
            if (text.charAt(i) != '\u001B') {
                i++;
                continue;
            }
            if (i + 1 >= text.length() || text.charAt(i + 1) != '[') {
                return false;
            }
            int j = i + 2;
            while (j < text.length() && !(text.charAt(j) >= '@' && text.charAt(j) <= '~')) {
                j++;
            }
            if (j >= text.length()) {
                return false;
            }
            i = j + 1;
        }
        return true;
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

    private static void testVisibleLengthIgnoresAnsi() {
        System.out.println("--- visible length ---");
        assertEquals("Plain text length", 5, Style.visibleLength("hello"));
        assertEquals("Painted text keeps its visible length", 5, Style.visibleLength(Style.highlight("hello")));
        assertEquals("Nested paint keeps its visible length", 5, Style.visibleLength(Style.muted(Style.strong("hello"))));
        assertEquals("Null counts as zero", 0, Style.visibleLength(null));
    }

    private static void testPadAndCenterIgnoreAnsi() {
        System.out.println("\n--- padding ---");
        assertEquals("padRight reaches the target width", 10, Style.visibleLength(Style.padRight("abc", 10)));
        assertEquals("padLeft reaches the target width", 10, Style.visibleLength(Style.padLeft("abc", 10)));
        assertEquals("padRight on coloured text reaches the target width",
                10, Style.visibleLength(Style.padRight(Style.good("abc"), 10)));
        assertEquals("Padding never truncates", 20, Style.visibleLength(Style.padRight("abcdefghijklmnopqrst", 10)));
    }

    private static void testTruncateKeepsFramesAndColourIntact() {
        System.out.println("\n--- truncation ---");
        assertEquals("Short text is returned untouched", "abc", Style.truncate("abc", 10));
        assertEquals("Truncated text fits the budget", 6, Style.visibleLength(Style.truncate("abcdefghij", 6)));
        assertTrue("Truncation marks the cut", plain(Style.truncate("abcdefghij", 6)).endsWith("..."));
        assertEquals("Coloured text truncates to the same visible width",
                6, Style.visibleLength(Style.truncate(Style.highlight("abcdefghij"), 6)));
        assertTrue("Coloured truncation still reads as the original text",
                plain(Style.truncate(Style.highlight("abcdefghij"), 6)).startsWith("abc"));
        assertEquals("Null truncates to empty", "", Style.truncate(null, 5));
        assertEquals("A three-cell budget still yields dots", 3, Style.visibleLength(Style.truncate("abcde", 3)));

        String overloaded = Style.panel("TITLE", new String[]{"x".repeat(500)});
        boolean fits = true;
        for (String line : lines(overloaded)) {
            if (Style.visibleLength(line) > Style.width()) {
                fits = false;
            }
        }
        assertTrue("An over-long body row cannot break the layout", fits);
    }

    private static void testStatusColourisesOnlyKnownTags() {
        System.out.println("\n--- status tags ---");
        String error = Style.status("[Error] boom");
        assertTrue("Tagged message keeps its text", error.endsWith("boom"));
        assertEquals("Tag decoration adds no visible width",
                Style.visibleLength("[Error] boom"), Style.visibleLength(error));

        String unknown = "[Widget] boom";
        assertEquals("Unknown tag is left untouched", unknown, Style.status(unknown));
        assertEquals("Untagged message is left untouched", "plain text", Style.status("plain text"));
        assertEquals("Null message stays null", null, Style.status(null));
    }

    private static void testPanelLinesFitDisplayWidth() {
        System.out.println("\n--- open panel ---");
        String panel = Style.panel("TITLE", new String[]{"  hello", "second row"});
        String[] rows = lines(panel);

        assertEquals("Panel draws a title, a rule, a gap and two body rows", 5, rows.length);
        boolean fits = true;
        for (String line : rows) {
            if (Style.visibleLength(line) > Style.width()) {
                fits = false;
            }
        }
        assertTrue("No panel line exceeds the display width", fits);
        assertTrue("Panel keeps its content", panel.contains("hello") && panel.contains("second row"));
        assertTrue("Panel keeps its title", plain(panel).contains("TITLE"));
        assertTrue("Untitled panel still renders", Style.panel(null, new String[]{"x"}).contains("x"));
        assertTrue("An open panel draws no borders",
                !panel.contains("\u2502") && !panel.contains("\u256D") && !panel.contains("+"));
        assertTrue("No rendered line carries trailing blanks",
                !panel.contains(" \n") && !panel.endsWith(" \n"));
    }

    private static void testBannerLayout() {
        System.out.println("\n--- banner ---");
        String[] info = {"row one", "row two"};
        String banner = Style.banner("TITLE", "subtitle line", info);

        boolean fits = true;
        for (String line : lines(banner)) {
            if (Style.visibleLength(line) > Style.width()) {
                fits = false;
            }
        }
        assertTrue("No banner line exceeds the display width", fits);

        String flattened = plain(banner);
        assertTrue("Banner keeps the title", flattened.contains("TITLE"));
        assertTrue("Banner keeps the subtitle", flattened.contains("subtitle line"));
        assertTrue("Banner keeps the information rows",
                flattened.contains("row one") && flattened.contains("row two"));

        String bare = Style.banner("ONLY A TITLE", null, null);
        boolean bareFits = true;
        for (String line : lines(bare)) {
            if (Style.visibleLength(line) > Style.width()) {
                bareFits = false;
            }
        }
        assertTrue("A banner without subtitle or info still lays out correctly", bareFits);
        assertTrue("The splash is unframed", !bare.contains("\u2502") && !bare.contains("+"));
    }

    private static void testRainbowTheme() {
        System.out.println("\n--- rainbow theme ---");
        String plainText = "CERBERUS SYSTEM";
        String rainbow = Style.rainbow(plainText);
        String words = Style.rainbowWords("Rank ID Title");

        assertEquals("Rainbow adds no visible width", Style.visibleLength(plainText),
                Style.visibleLength(rainbow));
        assertEquals("Word rainbow adds no visible width", Style.visibleLength("Rank ID Title"),
                Style.visibleLength(words));
        assertEquals("Word rainbow keeps every word intact",
                plainText.length(), Style.visibleLength(Style.rainbowWords(plainText)));
        assertTrue("Word rainbow keeps words searchable", words.contains("Rank") && words.contains("Title"));
        assertEquals("Empty text is returned untouched", "", Style.rainbow(""));
        assertEquals("Null text is tolerated", null, Style.rainbow(null));
        assertEquals("A zero-length sweep is tolerated", "", Style.rainbowSweep(""));

        if (Style.colors()) {
            assertTrue("Rainbow emits colour", rainbow.indexOf('\u001B') >= 0);
            assertTrue("Each rainbow character is closed",
                    rainbow.indexOf('\u001B') < rainbow.indexOf('C'));
            assertTrue("Menu numbering is painted per entry",
                    plain(Style.menuLine("  1. one", 0)).contains("1."));
            assertEquals("Rainbow menu numbering stays on one column",
                    plain(Style.menuLine("  1. one", 0)).indexOf('.'),
                    plain(Style.menuLine(" 10. ten", 1)).indexOf('.'));
        } else {
            assertEquals("Without colour the text is unchanged", plainText, rainbow);
            assertEquals("Without colour words are unchanged", "Rank ID Title", words);
        }
    }

    private static void testRuleSpansDisplayWidth() {
        System.out.println("\n--- rules ---");
        assertEquals("Plain rule spans the display width", Style.width(), Style.visibleLength(Style.rule()));
        assertEquals("Labelled rule spans the display width", Style.width(), Style.visibleLength(Style.rule("TITLE")));
        assertTrue("Labelled rule keeps its label", Style.rule("TITLE").contains("TITLE"));
    }

    private static void testBarScalesAndFills() {
        System.out.println("\n--- bars ---");
        String full = Style.unicode() ? "\u2588" : "#";
        String empty = Style.unicode() ? "\u2591" : ".";

        String maxBar = Style.bar(10, 10, 8);
        assertEquals("Bar length matches the requested cell count", 8, Style.visibleLength(maxBar));
        assertEquals("A maxed-out bar is fully filled", 8, countOccurrences(maxBar, full));
        assertEquals("A maxed-out bar has no empty cells", 0, countOccurrences(maxBar, empty));

        String zeroBar = Style.bar(0, 10, 8);
        assertEquals("A zero bar is empty", 0, countOccurrences(zeroBar, full));
        assertEquals("A zero bar keeps its cell count", 8, Style.visibleLength(zeroBar));

        String tinyBar = Style.bar(1, 1000, 8);
        assertEquals("A tiny non-zero value still shows one cell", 1, countOccurrences(tinyBar, full));

        assertEquals("A bar over a zero max stays empty", 0, countOccurrences(Style.bar(3, 0, 5), full));
    }

    private static void testMenuLineKeepsWording() {
        System.out.println("\n--- menu lines ---");
        String single = plain(Style.menuLine("  1. Add a paper"));
        String double_ = plain(Style.menuLine(" 10. Display All Paths (Hamiltonian Check)"));

        assertTrue("Single-digit entry keeps its wording", single.endsWith("1. Add a paper"));
        assertTrue("Two-digit entry keeps its wording",
                double_.endsWith("10. Display All Paths (Hamiltonian Check)"));
        assertEquals("Numbering is right-aligned to one column", single.indexOf('.'), double_.indexOf('.'));
        assertEquals("Non-menu text is untouched", "no number here", Style.menuLine("no number here"));
        assertTrue("Menu box keeps entry order",
                plain(Style.menuBox("MENU", new String[]{"  1. one", " 10. ten"})).indexOf("one")
                        < plain(Style.menuBox("MENU", new String[]{"  1. one", " 10. ten"})).indexOf("ten"));
    }

    private static void testFoldWrapsCaptions() {
        System.out.println("\n--- caption folding ---");
        assertEquals("Short captions are not folded", "short", Style.fold("short", 20));
        assertEquals("Null captions are tolerated", null, Style.fold(null, 20));

        String longCaption = "Subgraph: 12 nodes, 40 directed edges (arrows point from citing paper to cited paper)";
        String folded = Style.fold(longCaption, 40);
        assertTrue("A long caption is folded", folded.indexOf('\n') > 0);

        boolean foldedFits = true;
        for (String line : folded.split("\n", -1)) {
            if (Style.visibleLength(line) > 40) {
                foldedFits = false;
            }
        }
        assertTrue("Every folded line fits the width", foldedFits);
        assertEquals("Folding keeps every word, in order", squash(longCaption), squash(folded));
        String hardCut = Style.fold("abcdefghijklmnop", 10);
        boolean cutFits = true;
        for (String line : hardCut.split("\n", -1)) {
            if (Style.visibleLength(line) > 10) {
                cutFits = false;
            }
        }
        assertTrue("A word longer than the line is cut to the width", cutFits);
        assertTrue("Hard cutting marks a new line", hardCut.indexOf('\n') > 0);

        // Folding must measure printable width, so a coloured line never splits an escape code.
        String coloured = Style.muted("Graph loaded with ") + Style.emphasis("75")
                + Style.muted(" research papers and ") + Style.emphasis("91")
                + Style.muted(" citation edges.");
        String foldedColour = Style.fold(coloured, 40);
        assertTrue("Folding never cuts an escape sequence in half", hasWholeEscapes(foldedColour));
        boolean colouredFits = true;
        for (String line : foldedColour.split("\n", -1)) {
            if (Style.visibleLength(line) > 40) {
                colouredFits = false;
            }
        }
        assertTrue("Every folded coloured line fits the width", colouredFits);
        assertEquals("Folding a coloured line keeps its wording",
                squash("Graph loaded with 75 research papers and 91 citation edges."),
                squash(plain(foldedColour)));
    }

    private static void testColourDepthIsUsable() {
        System.out.println("\n--- colour depth ---");
        Style.Depth depth = Style.depth();
        assertTrue("A valid depth is reported",
                depth == Style.Depth.NONE || depth == Style.Depth.XTERM256 || depth == Style.Depth.TRUECOLOR);

        if (!Style.colors()) {
            assertEquals("No colour support reports no depth", Style.Depth.NONE, depth);
            assertEquals("Foreground escapes are suppressed", "", Style.fg(10, 20, 30));
            assertEquals("Background escapes are suppressed", "", Style.bg(10, 20, 30));
            assertEquals("Reset is suppressed", "", Style.reset());
        } else {
            assertTrue("Colour support reports a real depth", depth != Style.Depth.NONE);
            assertTrue("Foreground escape is emitted", Style.fg(10, 20, 30).contains("[38;"));
            assertTrue("Background escape is emitted", Style.bg(10, 20, 30).contains("[48;"));
            assertEquals("Reset closes the sequence", "\u001B[0m", Style.reset());
        }
    }

    private static void testDiagramKeepsNodeLabelsIntact() {
        System.out.println("\n--- diagram ---");
        String art = "Header line\n  [P101]\n     v\n  [P102]\n";
        String styled = Style.diagram(art);

        assertTrue("Diagram keeps every node label",
                styled.contains("[P101]") && styled.contains("[P102]"));
        assertTrue("Diagram keeps the arrow", styled.contains("v"));
        assertEquals("Diagram decoration adds no visible width",
                Style.visibleLength(art), Style.visibleLength(styled));
        assertEquals("Renderer notices are left alone", "[Graph] Nothing to draw.\n",
                Style.diagram("[Graph] Nothing to draw.\n"));
        assertEquals("Null art is tolerated", null, Style.diagram(null));
    }
}
