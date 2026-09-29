package report;

import core.DynamicArray;

/**
 * Draws citation paths as terminal flowcharts: one rounded box per paper, joined by arrows.
 *
 * <pre>
 *   &#x256D;&#x2500;&#x2500;&#x2500;&#x2500;&#x2500;&#x2500;&#x256E;     &#x256D;&#x2500;&#x2500;&#x2500;&#x2500;&#x2500;&#x2500;&#x256E;     &#x256D;&#x2500;&#x2500;&#x2500;&#x2500;&#x2500;&#x2500;&#x256E;
 *   &#x2502; P102 &#x2502; &#x2500;&#x2500;&#x25B6; &#x2502; P101 &#x2502; &#x2500;&#x2500;&#x25B6; &#x2502; P104 &#x2502;
 *   &#x2570;&#x2500;&#x2500;&#x2500;&#x2500;&#x2500;&#x2500;&#x256F;     &#x2570;&#x2500;&#x2500;&#x2500;&#x2500;&#x2500;&#x2500;&#x256F;     &#x2570;&#x2500;&#x2500;&#x2500;&#x2500;&#x2500;&#x2500;&#x256F;
 * </pre>
 *
 * <p>Boxes are padded to one shared width so a stack of paths reads as a column of aligned
 * diagrams. Chains longer than the terminal are wrapped across lines, repeating the joining paper
 * at the start of the next row so continuity is never ambiguous. When the stdout cannot carry
 * box-drawing glyphs the same layout is drawn with {@code +-|>} instead.
 *
 * <p>Zero {@code java.util} imports; JDK standard library only.
 */
public final class FlowRenderer {

    private FlowRenderer() {
        // static utility class, prevent instantiation
    }

    /** Blank columns reserved between two boxes, filled by the connector. */
    private static final int GAP = 5;
    /** Blank columns kept left of the diagram. */
    private static final int INDENT = 4;
    /** Smallest inner width of a node box, so short IDs still get a proper box. */
    private static final int MIN_INNER = 6;

    private static final boolean UNICODE = Style.unicode();

    private static final String TOP_LEFT = UNICODE ? "\u256D" : "+";
    private static final String TOP_RIGHT = UNICODE ? "\u256E" : "+";
    private static final String BOTTOM_LEFT = UNICODE ? "\u2570" : "+";
    private static final String BOTTOM_RIGHT = UNICODE ? "\u256F" : "+";
    private static final String HORIZONTAL = UNICODE ? "\u2500" : "-";
    private static final String VERTICAL = UNICODE ? "\u2502" : "|";
    private static final String ARROW = UNICODE ? "\u25B6" : ">";
    private static final String CONTINUES = UNICODE ? "\u21B3" : "\\";

    /** @return the shared box width for a set of paths, so every diagram lines up */
    public static int boxWidth(DynamicArray<DynamicArray<String>> paths) {
        int longest = 0;
        if (paths != null) {
            for (int p = 0; p < paths.size(); p++) {
                longest = Math.max(longest, longestLabel(paths.get(p)));
            }
        }
        return Math.max(MIN_INNER, longest + 2) + 2;
    }

    private static int longestLabel(DynamicArray<String> path) {
        int longest = 0;
        if (path != null) {
            for (int i = 0; i < path.size(); i++) {
                String id = path.get(i);
                if (id != null) {
                    longest = Math.max(longest, id.length());
                }
            }
        }
        return longest;
    }

    /**
     * Renders every path as a flowchart.
     *
     * @param paths all simple paths between a source and a target paper
     * @return ready-to-print text, one block per path
     */
    public static String render(DynamicArray<DynamicArray<String>> paths) {
        if (paths == null || paths.size() == 0) {
            return Style.muted("  (nothing to draw)") + "\n";
        }
        StringBuilder sb = new StringBuilder();
        int width = boxWidth(paths);
        for (int p = 0; p < paths.size(); p++) {
            sb.append(renderPath(paths.get(p), p + 1, false, width));
        }
        return sb.toString();
    }

    /**
     * Renders one path: a header line, then the flowchart itself.
     *
     * @param path        the papers in visit order
     * @param index       1-based path number used in the header
     * @param hamiltonian whether the path visits every paper in the graph
     * @param boxWidth    shared box width from {@link #boxWidth(DynamicArray)}
     * @return ready-to-print text
     */
    public static String renderPath(DynamicArray<String> path, int index, boolean hamiltonian,
                                    int boxWidth) {
        StringBuilder sb = new StringBuilder();
        int hops = (path == null || path.size() == 0) ? 0 : path.size() - 1;
        sb.append("  ").append(Style.muted("Path " + index + " (hops=" + hops + ")"))
                .append(hamiltonian ? "  " + Style.good("[HAMILTONIAN]") : "")
                .append('\n');

        if (path == null || path.size() == 0) {
            sb.append(Style.muted("    (empty path)")).append('\n');
            return sb.toString();
        }

        sb.append(sequence(path, boxWidth));
        return sb.toString();
    }

    /**
     * Draws just the boxes and arrows for a sequence, with no path header - used where the caller
     * already printed its own caption (for example the optimal route).
     *
     * @param path     the papers in visit order
     * @param boxWidth shared box width from {@link #boxWidth(DynamicArray)}
     * @return ready-to-print text
     */
    public static String renderSequence(DynamicArray<String> path, int boxWidth) {
        if (path == null || path.size() == 0) {
            return Style.muted("  (empty path)") + "\n";
        }
        return sequence(path, boxWidth);
    }

    private static String sequence(DynamicArray<String> path, int boxWidth) {
        StringBuilder sb = new StringBuilder();

        int perRow = (Style.width() - INDENT + GAP) / (boxWidth + GAP);
        if (perRow < 2) {
            perRow = 2;
        }

        int start = 0;
        boolean continuation = false;
        while (start < path.size()) {
            int end = Math.min(start + perRow, path.size());
            if (continuation) {
                sb.append('\n');
            }
            DynamicArray<String> chunk = new DynamicArray<>();
            for (int i = start; i < end; i++) {
                chunk.add(path.get(i));
            }
            sb.append(block(chunk, boxWidth, continuation));
            continuation = true;
            start = end - 1;                 // repeat the joining paper on the next row
            if (start >= path.size() - 1) {
                break;
            }
        }
        return sb.toString();
    }

    /**
     * Extracts the ordered paper IDs from a {@link algo.GraphTraversal} optimal-path description.
     *
     * <p>Returns an empty array whenever the text is not a computed path - a "no valid path"
     * message, an unknown paper ID notice, or anything else - so the caller can print that message
     * verbatim instead of inventing a diagram for it.
     *
     * @param description the string returned by {@code optimalCitationPath}
     * @return the papers in visit order, or an empty array when there is no path to draw
     */
    public static DynamicArray<String> parseIds(String description) {
        DynamicArray<String> ids = new DynamicArray<>();
        String prefix = "Minimum-cost citation path (hops=";
        if (description == null || !description.startsWith(prefix)) {
            return ids;
        }
        int colon = description.indexOf(": ");
        if (colon < 0) {
            return ids;
        }
        String tail = description.substring(colon + 2).trim();
        if (tail.isEmpty()) {
            return ids;
        }
        if (!tail.contains(" -> ")) {
            ids.add(tail);
            return ids;
        }
        int start = 0;
        while (true) {
            int arrow = tail.indexOf(" -> ", start);
            if (arrow < 0) {
                ids.add(tail.substring(start).trim());
                return ids;
            }
            ids.add(tail.substring(start, arrow).trim());
            start = arrow + 4;
        }
    }

    /** Draws one row of connected boxes. */
    private static String block(DynamicArray<String> labels, int boxWidth, boolean continuation) {
        int inner = boxWidth - 2;
        StringBuilder top = new StringBuilder();
        StringBuilder middle = new StringBuilder();
        StringBuilder bottom = new StringBuilder();

        for (int i = 0; i < labels.size(); i++) {
            if (i > 0) {
                top.append(" ".repeat(GAP));
                bottom.append(" ".repeat(GAP));
                middle.append(connector());
            }
            top.append(Style.muted(TOP_LEFT + HORIZONTAL.repeat(boxWidth - 2) + TOP_RIGHT));
            bottom.append(Style.muted(BOTTOM_LEFT + HORIZONTAL.repeat(boxWidth - 2) + BOTTOM_RIGHT));
            String label = labels.get(i);
            middle.append(Style.muted(VERTICAL))
                    .append(' ')
                    .append(Style.strong(Style.padRight(label == null ? "?" : label, inner - 2)))
                    .append(' ')
                    .append(Style.muted(VERTICAL));
        }

        String margin = continuation ? "  " + Style.muted(CONTINUES) + " " : " ".repeat(INDENT);
        return margin + top + "\n" + margin + middle + "\n" + margin + bottom + "\n";
    }

    private static String connector() {
        return " " + Style.muted(HORIZONTAL + HORIZONTAL) + Style.note(ARROW) + " ";
    }
}
