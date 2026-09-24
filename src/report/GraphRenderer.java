package report;

import core.DynamicArray;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

/**
 * Renders a small directed graph (papers as nodes, citations as directed edges)
 * as compact terminal art.
 *
 * <p>The graph is drawn top-down in layers: the source sits on the first line,
 * every node's layer is its longest distance (in hops) from the source, and each
 * layer is a line of {@code [ID]} boxes. Edges are routed orthogonally - down,
 * across the free line under a layer, then into the destination box - and every
 * edge ends in an arrowhead, so a diamond-shaped citation network shows up as a
 * diamond and a chain shows up as a column of boxes.
 *
 * <p>Two glyph palettes are supported: Unicode box-drawing when the stdout
 * charset can encode it, plain ASCII otherwise, so the diagram never degrades
 * into question marks on a legacy Windows console.
 *
 * <p>Zero {@code java.util} imports; custom data structures only.
 */
public final class GraphRenderer {

    private GraphRenderer() {
        // static utility class, prevent instantiation
    }

    /** Maximum node count that still fits a legible diagram. */
    private static final int MAX_RENDER_NODES = 12;
    /** Maximum drawing width, in terminal columns. */
    private static final int MAX_CANVAS_WIDTH = 110;
    /** Maximum drawing height, in terminal lines. */
    private static final int MAX_CANVAS_HEIGHT = 25;
    /** Blank columns kept between two boxes sitting on the same layer. */
    private static final int COL_GAP = 4;
    /** Blank columns kept left of the leftmost box, so routing channels fit. */
    private static final int LEFT_MARGIN = 2;
    /** Blank columns kept right of the rightmost box, so routing channels fit. */
    private static final int RIGHT_MARGIN = 4;

    // Line direction bits, screen coordinates (row 0 = top).
    private static final int DIR_N = 1;
    private static final int DIR_S = 2;
    private static final int DIR_E = 4;
    private static final int DIR_W = 8;
    private static final int DIR_NE = 16;
    private static final int DIR_SE = 32;
    private static final int DIR_SW = 64;
    private static final int DIR_NW = 128;

    private static final int ARROW_E = 0;
    private static final int ARROW_W = 1;
    private static final int ARROW_S = 2;
    private static final int ARROW_N = 3;

    /**
     * Glyph set used to draw lines and arrowheads.
     *
     * <p>All non-ASCII glyphs are written as {@code \\uXXXX} escapes so the
     * source compiles identically regardless of the platform source encoding.
     */
    public static final class Palette {
        private final String horizontal;       // U+2500
        private final String vertical;         // U+2502
        private final String cross;            // U+253C
        private final String verticalRight;    // U+251C
        private final String verticalLeft;     // U+2524
        private final String horizontalUp;     // U+2534
        private final String horizontalDown;   // U+252C
        private final String cornerRightUp;    // U+2514
        private final String cornerLeftUp;     // U+2518
        private final String cornerRightDown;  // U+250C
        private final String cornerLeftDown;   // U+2510
        private final String slash;            // U+2571
        private final String backslash;        // U+2572
        private final String diagonalCross;    // U+2573
        private final String arrowRight;       // U+25B6
        private final String arrowLeft;        // U+25C0
        private final String arrowUp;          // U+25B2
        private final String arrowDown;        // U+25BC

        private Palette(String horizontal, String vertical, String cross,
                        String verticalRight, String verticalLeft,
                        String horizontalUp, String horizontalDown,
                        String cornerRightUp, String cornerLeftUp,
                        String cornerRightDown, String cornerLeftDown,
                        String slash, String backslash, String diagonalCross,
                        String arrowRight, String arrowLeft, String arrowUp, String arrowDown) {
            this.horizontal = horizontal;
            this.vertical = vertical;
            this.cross = cross;
            this.verticalRight = verticalRight;
            this.verticalLeft = verticalLeft;
            this.horizontalUp = horizontalUp;
            this.horizontalDown = horizontalDown;
            this.cornerRightUp = cornerRightUp;
            this.cornerLeftUp = cornerLeftUp;
            this.cornerRightDown = cornerRightDown;
            this.cornerLeftDown = cornerLeftDown;
            this.slash = slash;
            this.backslash = backslash;
            this.diagonalCross = diagonalCross;
            this.arrowRight = arrowRight;
            this.arrowLeft = arrowLeft;
            this.arrowUp = arrowUp;
            this.arrowDown = arrowDown;
        }

        /** Full box-drawing set, for UTF-8 capable terminals. */
        public static final Palette UNICODE = new Palette(
                "\u2500", "\u2502", "\u253C", "\u251C", "\u2524",
                "\u2534", "\u252C", "\u2514", "\u2518", "\u250C", "\u2510",
                "\u2571", "\u2572", "\u2573", "\u25B6", "\u25C0", "\u25B2", "\u25BC");

        /** Plain ASCII set, for consoles that cannot encode box-drawing glyphs. */
        public static final Palette ASCII = new Palette(
                "-", "|", "+", "+", "+", "+", "+", "+", "+", "+", "+",
                "/", "\\", "X", ">", "<", "^", "v");

        /**
         * Picks the best palette the current stdout can actually display.
         *
         * @return {@link #UNICODE} when the stdout charset encodes the glyph set, else {@link #ASCII}
         */
        public static Palette auto() {
            return canEncodeUnicode() ? UNICODE : ASCII;
        }

        private static boolean canEncodeUnicode() {
            try {
                Charset charset = stdoutCharset();
                if (charset == null) {
                    return false;
                }
                CharsetEncoder encoder = charset.newEncoder();
                return encoder.canEncode("\u2500\u2502\u253C\u251C\u2524\u2534\u252C"
                        + "\u2514\u2518\u250C\u2510\u2571\u2572\u2573\u25B6\u25C0\u25B2\u25BC");
            } catch (RuntimeException e) {
                return false;
            }
        }

        private static Charset stdoutCharset() {
            String name = System.getProperty("stdout.encoding");
            if (name == null) {
                name = System.getProperty("sun.stdout.encoding");
            }
            if (name == null) {
                name = System.getProperty("file.encoding");
            }
            if (name == null) {
                return Charset.defaultCharset();
            }
            try {
                return Charset.forName(name);
            } catch (RuntimeException e) {
                return Charset.defaultCharset();
            }
        }
    }

    /**
     * Convenience entry point: derives the subgraph visited by the given paths and
     * draws it using the auto-detected palette.
     *
     * @param paths all simple paths between a source and a target paper
     * @return a multi-line diagram, ready to print
     */
    public static String render(DynamicArray<DynamicArray<String>> paths) {
        return render(paths, Palette.auto());
    }

    /**
     * Derives the subgraph visited by the given paths and draws it.
     *
     * <p>Nodes are deduplicated in first-seen order; edges are the deduplicated
     * consecutive pairs appearing in any path. The first node of the first path is
     * treated as the source, i.e. the top of the diagram.
     *
     * @param paths   all simple paths between a source and a target paper
     * @param palette glyph set to draw with
     * @return a multi-line diagram, ready to print
     */
    public static String render(DynamicArray<DynamicArray<String>> paths, Palette palette) {
        DynamicArray<String> nodes = new DynamicArray<>();
        DynamicArray<String[]> edges = new DynamicArray<>();

        if (paths != null) {
            for (int p = 0; p < paths.size(); p++) {
                DynamicArray<String> path = paths.get(p);
                if (path == null) {
                    continue;
                }
                for (int k = 0; k < path.size(); k++) {
                    String id = path.get(k);
                    if (id == null) {
                        continue;
                    }
                    if (!nodes.contains(id)) {
                        nodes.add(id);
                    }
                    if (k + 1 < path.size()) {
                        String next = path.get(k + 1);
                        if (next == null) {
                            continue;
                        }
                        if (!nodes.contains(next)) {
                            nodes.add(next);
                        }
                        if (!hasEdge(edges, id, next)) {
                            edges.add(new String[]{id, next});
                        }
                    }
                }
            }
        }

        return renderSubgraph(nodes, edges, palette);
    }

    /**
     * Draws an explicit node/edge set.
     *
     * @param nodeIds paper IDs; the first entry is treated as the source (top row)
     * @param edges   directed edges, each a two-element array {citingId, citedId}
     * @param palette glyph set to draw with
     * @return a multi-line diagram, ready to print
     */
    public static String renderSubgraph(DynamicArray<String> nodeIds, DynamicArray<String[]> edges,
                                        Palette palette) {
        int nodeCount = (nodeIds == null) ? 0 : nodeIds.size();
        int edgeCount = (edges == null) ? 0 : edges.size();

        if (nodeCount == 0) {
            return "[Graph] Nothing to draw.\n";
        }
        if (nodeCount > MAX_RENDER_NODES) {
            return "[Graph] " + nodeCount + " nodes is too many to draw as a diagram (limit "
                    + MAX_RENDER_NODES + ") - showing the path list only.\n";
        }

        int boxW = 0;
        for (int i = 0; i < nodeCount; i++) {
            boxW = Math.max(boxW, nodeIds.get(i).length() + 2);
        }
        int halfBox = boxW / 2;

        // 1. Layer every node by its longest hop distance from the source.
        int[] depth = computeDepths(nodeIds, edges, nodeCount);
        int maxDepth = 0;
        for (int i = 0; i < nodeCount; i++) {
            maxDepth = Math.max(maxDepth, depth[i]);
        }
        int[] row = new int[nodeCount];
        for (int i = 0; i < nodeCount; i++) {
            row[i] = 2 * depth[i];
        }
        int height = 2 * maxDepth + 1;

        // 2. Place each layer on one line, ordering it under its parents.
        int[] center = new int[nodeCount];
        int[] left = new int[nodeCount];
        placeLayers(nodeIds, edges, nodeCount, boxW, halfBox, depth, center, left);

        int minLeft = Integer.MAX_VALUE;
        int maxRight = Integer.MIN_VALUE;
        for (int i = 0; i < nodeCount; i++) {
            minLeft = Math.min(minLeft, left[i]);
            maxRight = Math.max(maxRight, left[i] + boxW - 1);
        }
        int shift = LEFT_MARGIN - minLeft;
        for (int i = 0; i < nodeCount; i++) {
            left[i] += shift;
            center[i] += shift;
        }
        int width = (maxRight + shift) + 1 + RIGHT_MARGIN;

        if (width > MAX_CANVAS_WIDTH || height > MAX_CANVAS_HEIGHT) {
            return "[Graph] Diagram would be " + width + "x" + height
                    + " characters - too large to draw. Showing the path list only.\n";
        }

        char[][] grid = new char[height][width];
        int[][] mask = new int[height][width];
        boolean[][] boxCell = new boolean[height][width];
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                grid[r][c] = ' ';
            }
        }
        for (int i = 0; i < nodeCount; i++) {
            for (int c = left[i]; c < left[i] + boxW; c++) {
                if (c >= 0 && c < width) {
                    boxCell[row[i]][c] = true;
                }
            }
        }

        // 3. Route every edge: down, across the free line beside the layer, into the box.
        DynamicArray<int[]> arrows = new DynamicArray<>();
        int[] entryCount = new int[nodeCount];
        for (int e = 0; e < edgeCount; e++) {
            String[] edge = edges.get(e);
            if (edge == null || edge.length < 2) {
                continue;
            }
            int from = nodeIds.indexOf(edge[0]);
            int to = nodeIds.indexOf(edge[1]);
            if (from == -1 || to == -1 || from == to) {
                continue;
            }
            routeEdge(from, to, center, left, row, boxW, halfBox, mask, boxCell,
                    height, width, entryCount, arrows);
        }

        // 4. Resolve accumulated directions into glyphs.
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                grid[r][c] = glyph(mask[r][c], palette);
            }
        }

        // 5. Stamp node boxes on top of any line that passes under them.
        for (int i = 0; i < nodeCount; i++) {
            String label = "[" + nodeIds.get(i) + "]";
            for (int k = 0; k < label.length(); k++) {
                int c = left[i] + k;
                if (c >= 0 && c < width) {
                    grid[row[i]][c] = label.charAt(k);
                }
            }
        }

        // 6. Arrowheads, one cell outside the destination box.
        for (int a = 0; a < arrows.size(); a++) {
            int[] arrow = arrows.get(a);
            int r = arrow[0];
            int c = arrow[1];
            if (r < 0 || r >= height || c < 0 || c >= width || boxCell[r][c]) {
                continue;
            }
            grid[r][c] = arrowGlyph(arrow[2], palette);
        }

        StringBuilder out = new StringBuilder();
        out.append("Subgraph: ").append(nodeCount)
           .append(nodeCount == 1 ? " node, " : " nodes, ")
           .append(edgeCount)
           .append(edgeCount == 1 ? " directed edge" : " directed edges")
           .append(" (arrows point from citing paper to cited paper)\n");
        for (int r = 0; r < height; r++) {
            int last = -1;
            for (int c = 0; c < width; c++) {
                if (grid[r][c] != ' ') {
                    last = c;
                }
            }
            for (int c = 0; c <= last; c++) {
                out.append(grid[r][c]);
            }
            out.append('\n');
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ layout

    /** Longest hop distance from the source for every node. */
    private static int[] computeDepths(DynamicArray<String> nodeIds, DynamicArray<String[]> edges,
                                       int nodeCount) {
        int[] depth = new int[nodeCount];
        for (int pass = 0; pass < nodeCount; pass++) {
            boolean changed = false;
            for (int e = 0; e < edges.size(); e++) {
                String[] edge = edges.get(e);
                int from = nodeIds.indexOf(edge[0]);
                int to = nodeIds.indexOf(edge[1]);
                if (from == -1 || to == -1 || from == to) {
                    continue;
                }
                if (depth[to] < depth[from] + 1) {
                    depth[to] = depth[from] + 1;
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }
        return depth;
    }

    /**
     * Lays every layer out on its own line, ordering each layer by the average
     * column of its parents (a single barycenter pass) and keeping boxes apart.
     */
    private static void placeLayers(DynamicArray<String> nodeIds, DynamicArray<String[]> edges,
                                    int nodeCount, int boxW, int halfBox,
                                    int[] depth, int[] center, int[] left) {
        int maxDepth = 0;
        for (int i = 0; i < nodeCount; i++) {
            maxDepth = Math.max(maxDepth, depth[i]);
        }

        for (int d = 0; d <= maxDepth; d++) {
            int count = 0;
            for (int i = 0; i < nodeCount; i++) {
                if (depth[i] == d) {
                    count++;
                }
            }
            if (count == 0) {
                continue;
            }

            int[] layer = new int[count];
            double[] desired = new double[count];
            int k = 0;
            for (int i = 0; i < nodeCount; i++) {
                if (depth[i] != d) {
                    continue;
                }
                layer[k] = i;
                desired[k] = desiredColumn(nodeIds, edges, i, depth, center, d);
                k++;
            }

            for (int a = 1; a < count; a++) {
                int keyNode = layer[a];
                double keyDesired = desired[a];
                int b = a - 1;
                while (b >= 0 && desired[b] > keyDesired) {
                    layer[b + 1] = layer[b];
                    desired[b + 1] = desired[b];
                    b--;
                }
                layer[b + 1] = keyNode;
                desired[b + 1] = keyDesired;
            }

            int prevRight = Integer.MIN_VALUE;
            for (int a = 0; a < count; a++) {
                int idx = layer[a];
                int c = (int) Math.round(desired[a]);
                if (prevRight != Integer.MIN_VALUE) {
                    c = Math.max(c, prevRight + COL_GAP + halfBox + 1);
                }
                center[idx] = c;
                left[idx] = c - halfBox;
                prevRight = left[idx] + boxW - 1;
            }
        }
    }

    /** Average column of a node's parents, or 0 when the node has none. */
    private static double desiredColumn(DynamicArray<String> nodeIds, DynamicArray<String[]> edges,
                                        int index, int[] depth, int[] center, int layerDepth) {
        String id = nodeIds.get(index);
        double sum = 0;
        int count = 0;
        for (int e = 0; e < edges.size(); e++) {
            String[] edge = edges.get(e);
            if (!edge[1].equals(id)) {
                continue;
            }
            int parent = nodeIds.indexOf(edge[0]);
            if (parent == -1 || parent == index || depth[parent] >= layerDepth) {
                continue;
            }
            sum += center[parent];
            count++;
        }
        return (count == 0) ? 0.0 : sum / count;
    }

    // ----------------------------------------------------------------- routing

    /**
     * Routes one directed edge as: down/up out of the source box, across the free
     * line beside the layer (via a clear vertical channel when the layers are not
     * adjacent), then into the destination box with an arrowhead.
     */
    private static void routeEdge(int from, int to, int[] center, int[] left, int[] row,
                                  int boxW, int halfBox, int[][] mask, boolean[][] boxCell,
                                  int height, int width, int[] entryCount,
                                  DynamicArray<int[]> arrows) {
        int cu = center[from];
        int cv = center[to];
        int ru = row[from];
        int rv = row[to];
        int entryCol = entryColumn(entryCount, to, center, left, boxW);

        if (rv == ru) {
            // Same layer (only reachable with a cycle): link sideways on the node's own line.
            boolean toRight = cv >= cu;
            int x0 = toRight ? left[from] + boxW : left[from] - 1;
            int x1 = toRight ? left[to] - 1 : left[to] + boxW;
            drawLine(mask, height, width, x0, ru, x1, ru);
            arrows.add(new int[]{ru, x1, toRight ? ARROW_E : ARROW_W});
            return;
        }

        boolean downward = rv > ru;
        int step = downward ? 1 : -1;
        int exitCorridor = ru + step;
        int entryCorridor = rv - step;

        if (exitCorridor == entryCorridor) {
            // Adjacent layers: one corridor line does the whole job.
            mark(mask, height, width, exitCorridor, cu, downward ? DIR_N : DIR_S);
            drawLine(mask, height, width, cu, exitCorridor, entryCol, exitCorridor);
            arrows.add(new int[]{exitCorridor, entryCol, downward ? ARROW_S : ARROW_N});
            return;
        }

        // Layers further apart: drop down a channel that is clear of every box in between.
        int channel = findChannel(cu, cv, exitCorridor, entryCorridor, boxCell, width);
        mark(mask, height, width, exitCorridor, cu, downward ? DIR_N : DIR_S);
        drawLine(mask, height, width, cu, exitCorridor, channel, exitCorridor);
        drawLine(mask, height, width, channel, exitCorridor, channel, entryCorridor);
        drawLine(mask, height, width, channel, entryCorridor, entryCol, entryCorridor);
        arrows.add(new int[]{entryCorridor, entryCol, downward ? ARROW_S : ARROW_N});
    }

    /**
     * Finds a column that no box occupies on any node line strictly between the
     * two corridors, preferring columns closest to the destination.
     */
    private static int findChannel(int sourceCol, int targetCol, int fromRow, int toRow,
                                   boolean[][] boxCell, int width) {
        int low = Math.min(fromRow, toRow);
        int high = Math.max(fromRow, toRow);
        for (int offset = 0; offset < width; offset++) {
            for (int sign = 0; sign < 2; sign++) {
                int candidate = targetCol + ((sign == 0) ? offset : -offset);
                if (candidate < 0 || candidate >= width) {
                    continue;
                }
                boolean clear = true;
                for (int r = low; r <= high; r++) {
                    // Only node lines can hold boxes; corridor lines are always free.
                    if (r % 2 == 0 && boxCell[r][candidate]) {
                        clear = false;
                        break;
                    }
                }
                if (clear) {
                    return candidate;
                }
            }
        }
        return Math.max(0, Math.min(width - 1, sourceCol));
    }

    /** Picks a distinct entry cell under (or above) the destination box per edge. */
    private static int entryColumn(int[] entryCount, int node, int[] center, int[] left, int boxW) {
        int slot = entryCount[node]++;
        int delta = 0;
        if (slot > 0) {
            delta = ((slot + 1) / 2) * (((slot % 2) == 1) ? -1 : 1);
        }
        int col = center[node] + delta;
        int min = left[node];
        int max = left[node] + boxW - 1;
        return Math.max(min, Math.min(max, col));
    }

    /** Draws a straight line between two cells, accumulating direction bits. */
    private static void drawLine(int[][] mask, int height, int width, int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = (x0 < x1) ? 1 : -1;
        int sy = (y0 < y1) ? 1 : -1;
        int err = dx + dy;
        int x = x0;
        int y = y0;
        int guard = 4 * (height + width) + 8;

        while ((x != x1 || y != y1) && guard-- > 0) {
            int e2 = 2 * err;
            int nx = x;
            int ny = y;
            if (e2 >= dy) {
                err += dy;
                nx += sx;
            }
            if (e2 <= dx) {
                err += dx;
                ny += sy;
            }
            if (inBounds(y, x, height, width)) {
                mask[y][x] |= directionBits(x, y, nx, ny);
            }
            if (inBounds(ny, nx, height, width)) {
                mask[ny][nx] |= directionBits(nx, ny, x, y);
            }
            x = nx;
            y = ny;
        }
    }

    private static void mark(int[][] mask, int height, int width, int row, int col, int bits) {
        if (inBounds(row, col, height, width)) {
            mask[row][col] |= bits;
        }
    }

    private static int directionBits(int fromX, int fromY, int toX, int toY) {
        int dx = Integer.compare(toX, fromX);
        int dy = Integer.compare(toY, fromY);
        if (dx == 0 && dy == -1) return DIR_N;
        if (dx == 0 && dy == 1) return DIR_S;
        if (dx == 1 && dy == 0) return DIR_E;
        if (dx == -1 && dy == 0) return DIR_W;
        if (dx == 1 && dy == -1) return DIR_NE;
        if (dx == 1 && dy == 1) return DIR_SE;
        if (dx == -1 && dy == -1) return DIR_NW;
        if (dx == -1 && dy == 1) return DIR_SW;
        return 0;
    }

    /** Maps a set of line directions onto the matching box-drawing glyph. */
    private static char glyph(int mask, Palette p) {
        boolean n = (mask & DIR_N) != 0;
        boolean s = (mask & DIR_S) != 0;
        boolean e = (mask & DIR_E) != 0;
        boolean w = (mask & DIR_W) != 0;
        boolean slashFwd = (mask & DIR_NE) != 0 || (mask & DIR_SW) != 0;
        boolean slashBack = (mask & DIR_SE) != 0 || (mask & DIR_NW) != 0;

        if (n || s || e || w) {
            if (n && s && e && w) return first(p.cross);
            if (n && s) return first(e ? p.verticalRight : (w ? p.verticalLeft : p.vertical));
            if (e && w) return first(s ? p.horizontalDown : (n ? p.horizontalUp : p.horizontal));
            if (n && e) return first(p.cornerRightUp);
            if (n && w) return first(p.cornerLeftUp);
            if (s && e) return first(p.cornerRightDown);
            if (s && w) return first(p.cornerLeftDown);
            if (n || s) return first(p.vertical);
            return first(p.horizontal);
        }
        if (slashFwd && slashBack) return first(p.diagonalCross);
        if (slashFwd) return first(p.slash);
        if (slashBack) return first(p.backslash);
        return ' ';
    }

    private static char arrowGlyph(int dir, Palette p) {
        switch (dir) {
            case ARROW_E: return first(p.arrowRight);
            case ARROW_W: return first(p.arrowLeft);
            case ARROW_S: return first(p.arrowDown);
            default: return first(p.arrowUp);
        }
    }

    private static char first(String glyph) {
        return glyph.isEmpty() ? ' ' : glyph.charAt(0);
    }

    private static boolean hasEdge(DynamicArray<String[]> edges, String from, String to) {
        for (int i = 0; i < edges.size(); i++) {
            String[] edge = edges.get(i);
            if (edge[0].equals(from) && edge[1].equals(to)) {
                return true;
            }
        }
        return false;
    }

    private static boolean inBounds(int row, int col, int height, int width) {
        return row >= 0 && row < height && col >= 0 && col < width;
    }
}
