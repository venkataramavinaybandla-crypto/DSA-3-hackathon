package report;

import core.DynamicArray;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

/**
 * Terminal presentation layer for the console UI.
 *
 * <p>Provides colour, box-drawing frames, proportional bars and status-tag highlighting for the
 * interactive {@code main.Main} front end. Nothing here mutates application state: every method is
 * a pure function from text to (decorated) text, so the engine, the reports and the tests stay
 * exactly as they were.
 *
 * <p><b>Graceful degradation.</b> Two capabilities are auto-detected once, at class load:
 * <ul>
 *   <li><b>Colour</b> - ANSI escape codes are emitted only for a real terminal. Redirected output
 *       (pipes, files, CI logs) and legacy Windows consoles stay plain, so no escape garbage ever
 *       leaks into saved reports. Override with {@code NO_COLOR=1} (disable) or
 *       {@code CERBERUS_COLOR=always|never|auto} (force).</li>
 *   <li><b>Glyphs</b> - box-drawing characters are used when the stdout charset can actually
 *       encode them; otherwise every frame falls back to {@code +-|} ASCII art.</li>
 * </ul>
 *
 * <p>Zero {@code java.util} imports; JDK standard library only.
 */
public final class Style {

    private Style() {
        // static utility class, prevent instantiation
    }

    // ------------------------------------------------------------------ palette

    /** Escape-sequence introducer for ANSI SGR codes. */
    private static final String CSI = "\u001B[";

    /** Resets every active SGR attribute. */
    private static final String RESET = CSI + "0m";

    private static final String BOLD = CSI + "1m";
    private static final String DIM = CSI + "2m";

    private static final String RED = CSI + "91m";
    private static final String GREEN = CSI + "92m";
    private static final String YELLOW = CSI + "93m";
    private static final String BLUE = CSI + "94m";
    private static final String MAGENTA = CSI + "95m";
    private static final String CYAN = CSI + "96m";

    /**
     * Colour depth the current stdout can render.
     *
     * <ul>
     *   <li>{@link #TRUECOLOR} - 24-bit, declared by {@code COLORTERM}</li>
     *   <li>{@link #XTERM256} - the 256-colour cube, the safe default for any ANSI terminal</li>
     *   <li>{@link #NONE} - no colour at all (piped output, legacy console, {@code NO_COLOR})</li>
     * </ul>
     */
    public enum Depth {
        NONE, XTERM256, TRUECOLOR
    }

    /** Default frame width when {@code COLUMNS} is not exported by the shell. */
    private static final int DEFAULT_WIDTH = 78;
    private static final int MIN_WIDTH = 60;
    private static final int MAX_WIDTH = 100;

    private static final boolean COLORS = detectColors();
    private static final boolean UNICODE = detectUnicode();
    // Declared after COLORS on purpose: detectDepth() reads it during class initialisation.
    private static final Depth DEPTH = detectDepth();

    private static final String H = UNICODE ? "\u2500" : "-";
    private static final String BAR = UNICODE ? "\u258C" : "|";
    private static final String FULL = UNICODE ? "\u2588" : "#";
    private static final String EMPTY = UNICODE ? "\u2591" : ".";

    // ------------------------------------------------------------------ capabilities

    /** @return {@code true} when ANSI colour can be shown in the current output stream */
    public static boolean colors() {
        return COLORS;
    }

    /** @return {@code true} when box-drawing glyphs can be encoded by the current stdout */
    public static boolean unicode() {
        return UNICODE;
    }

    /** @return the colour depth of the current stdout */
    public static Depth depth() {
        return DEPTH;
    }

    /**
     * Builds a foreground-colour escape at the deepest depth the terminal supports.
     *
     * @return the escape sequence, or {@code ""} when colour is disabled
     */
    public static String fg(int r, int g, int b) {
        if (!COLORS) {
            return "";
        }
        return CSI + "38;" + colourSpec(r, g, b) + "m";
    }

    /**
     * Builds a background-colour escape at the deepest depth the terminal supports.
     *
     * @return the escape sequence, or {@code ""} when colour is disabled
     */
    public static String bg(int r, int g, int b) {
        if (!COLORS) {
            return "";
        }
        return CSI + "48;" + colourSpec(r, g, b) + "m";
    }

    /** @return the SGR reset sequence, or {@code ""} when colour is disabled */
    public static String reset() {
        return COLORS ? RESET : "";
    }

    private static String colourSpec(int r, int g, int b) {
        if (DEPTH == Depth.TRUECOLOR) {
            return "2;" + clamp255(r) + ";" + clamp255(g) + ";" + clamp255(b);
        }
        return "5;" + xterm256(r, g, b);
    }

    /** Packs an RGB triple into the nearest cell of the standard 6x6x6 colour cube. */
    private static int xterm256(int r, int g, int b) {
        int rc = clamp255(r) * 5 / 255;
        int gc = clamp255(g) * 5 / 255;
        int bc = clamp255(b) * 5 / 255;
        return 16 + 36 * rc + 6 * gc + bc;
    }

    private static int clamp255(int value) {
        return value < 0 ? 0 : (value > 255 ? 255 : value);
    }

    /**
     * Chooses the content width used by every rule, panel and frame body.
     *
     * <p>The window chrome always eats {@link #FRAME_OVERHEAD} columns, so what this returns is what
     * is left for the content. {@link #MIN_WIDTH} is a floor on the <em>whole window</em>, chrome
     * included - applying it to the content instead would push the frame past the terminal edge.
     *
     * @return the shell's reported {@code COLUMNS} less the chrome, clamped to a readable range,
     *         else {@value #DEFAULT_WIDTH} less the chrome
     */
    public static int width() {
        int columns = DEFAULT_WIDTH;
        String reported = env("COLUMNS");
        if (reported != null) {
            try {
                int parsed = Integer.parseInt(reported.trim());
                if (parsed > 0) {
                    columns = parsed;
                }
            } catch (NumberFormatException ignored) {
                // fall through to the default width
            }
        }
        // The window chrome eats FRAME_OVERHEAD columns, so the content width is what remains.
        int content = columns - FRAME_OVERHEAD;
        int minimumContent = MIN_WIDTH - FRAME_OVERHEAD;
        if (content < minimumContent) {
            return minimumContent;
        }
        return content > MAX_WIDTH ? MAX_WIDTH : content;
    }

    // ------------------------------------------------------------------ painting

    /**
     * Wraps text in an SGR sequence, unless colours are disabled (then the text is returned as-is).
     */
    public static String paint(String sgr, String text) {
        if (text == null || text.isEmpty() || !COLORS) {
            return text;
        }
        return sgr + text + RESET;
    }

    /** Citation keys and references, e.g. {@code [P101]} - Laser Pink, the action accent. */
    public static String highlight(String text) {
        return laser(text);
    }

    /** Emphasised labels and values - Ghost White, the readable body voice. */
    public static String strong(String text) {
        return ghost(text);
    }

    /** The most important number or word on a line - a metric, so Laser Pink. */
    public static String emphasis(String text) {
        return laser(text);
    }

    /** Secondary text that should recede. */
    public static String muted(String text) {
        return paint(DIM, text);
    }

    /** Positive outcome. */
    public static String good(String text) {
        return paint(GREEN, text);
    }

    /** Problem or failure. */
    public static String bad(String text) {
        return paint(RED, text);
    }

    /** Caution. */
    public static String warn(String text) {
        return paint(YELLOW, text);
    }

    /** Secondary identifiers: authors, categories, complexity tags - Cyber Purple. */
    public static String note(String text) {
        return cyber(text);
    }

    // ------------------------------------------------------------------ cyberpunk palette

    /**
     * The recognised Cyberpunk colour system. Every accent in the interface is drawn from these five
     * roles, so the console reads as one deliberate palette instead of a spectrum sweep:
     *
     * <ul>
     *   <li>{@link #OBSIDIAN} - 60%, the ink-dark canvas</li>
     *   <li>{@link #TECH} - 30%, frames, inner borders and the dashboard divider grid</li>
     *   <li>{@link #CYBER} - structural categories, secondary labels and data-structure types</li>
     *   <li>{@link #LASER} - 10%, reserved for metrics and interactive tokens</li>
     *   <li>{@link #GHOST} - the crisp, hyper-readable body text</li>
     * </ul>
     */
    private static final int[] OBSIDIAN = {11, 8, 19};
    private static final int[] TECH = {91, 15, 255};
    private static final int[] CYBER = {163, 46, 255};
    private static final int[] LASER = {255, 0, 127};
    private static final int[] GHOST = {245, 243, 247};

    /** Paints text in Tech Violet, bold: the structural voice of the interface. */
    public static String tech(String text) {
        return inkBold(TECH, text);
    }

    /** Paints text in Cyber Purple: secondary labels, categories and data-structure types. */
    public static String cyber(String text) {
        return ink(CYBER, text);
    }

    /** Paints text in Laser Pink, bold: reserved for metrics and interactive tokens. */
    public static String laser(String text) {
        return inkBold(LASER, text);
    }

    /** Paints text in Ghost White, bold: the readable body voice for data blocks. */
    public static String ghost(String text) {
        return inkBold(GHOST, text);
    }

    /**
     * Paints text as a two-tone sweep, Tech Violet shading into Laser Pink across the characters.
     * This is the project's title treatment: one deliberate duotone, never a rainbow.
     *
     * @param text the text to paint
     * @return the painted text, or the text unchanged when colour is unavailable
     */
    public static String duotone(String text) {
        return duotone(text, false);
    }

    /** As {@link #duotone(String)}, in bold. */
    public static String duotoneBold(String text) {
        return duotone(text, true);
    }

    private static String duotone(String text, boolean bold) {
        if (text == null || text.isEmpty() || !COLORS) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length() * 24);
        int last = Math.max(1, text.length() - 1);
        for (int i = 0; i < text.length(); i++) {
            char glyph = text.charAt(i);
            if (glyph == ' ') {
                sb.append(glyph);
                continue;
            }
            int[] rgb = mix(TECH, LASER, (double) i / last);
            sb.append(fg(rgb[0], rgb[1], rgb[2]));
            if (bold) {
                sb.append(BOLD);
            }
            sb.append(glyph).append(RESET);
        }
        return sb.toString();
    }

    /**
     * Paints a report table header in Tech Violet. The header is emitted as one contiguous run, so
     * the plain text stays intact and remains safe to search or assert on.
     *
     * @param text the header row
     * @return the painted header
     */
    public static String tableHeader(String text) {
        return inkBold(TECH, text);
    }

    /** Blends two palette roles; {@code t} runs 0 (all {@code from}) to 1 (all {@code to}). */
    private static int[] mix(int[] from, int[] to, double t) {
        double ratio = t < 0 ? 0 : (t > 1 ? 1 : t);
        return new int[]{
                (int) Math.round(from[0] + (to[0] - from[0]) * ratio),
                (int) Math.round(from[1] + (to[1] - from[1]) * ratio),
                (int) Math.round(from[2] + (to[2] - from[2]) * ratio)
        };
    }

    /** @return the bold foreground escape for a palette role, or {@code ""} without colour */
    private static String sgr(int[] role) {
        return BOLD + fg(role[0], role[1], role[2]);
    }

    private static String ink(int[] role, String text) {
        return paint(fg(role[0], role[1], role[2]), text);
    }

    private static String inkBold(int[] role, String text) {
        return paint(sgr(role), text);
    }

    // ------------------------------------------------------------------ measuring

    /** @return the number of printable characters in {@code text}, ignoring ANSI escapes */
    public static int visibleLength(String text) {
        if (text == null) {
            return 0;
        }
        int visible = 0;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\u001B' && i + 1 < text.length() && text.charAt(i + 1) == '[') {
                int j = i + 2;
                while (j < text.length() && !isSgrTerminator(text.charAt(j))) {
                    j++;
                }
                i = j + 1;
            } else {
                visible++;
                i++;
            }
        }
        return visible;
    }

    private static boolean isSgrTerminator(char c) {
        return c >= '@' && c <= '~';
    }

    /**
     * Shortens {@code text} to at most {@code max} printable characters, appending {@code ...}.
     * ANI escapes are preserved, so a truncated line never bleeds colour or breaks a frame.
     *
     * @param text the text to shorten
     * @param max  the printable width available
     * @return the text unchanged when it already fits, else a shortened copy
     */
    public static String truncate(String text, int max) {
        if (text == null || max <= 0) {
            return "";
        }
        if (visibleLength(text) <= max) {
            return text;
        }
        if (max <= 3) {
            return ".".repeat(max);
        }
        int budget = max - 3;
        StringBuilder sb = new StringBuilder();
        boolean styled = false;
        int visible = 0;
        int i = 0;
        while (i < text.length() && visible < budget) {
            char c = text.charAt(i);
            if (c == '\u001B' && i + 1 < text.length() && text.charAt(i + 1) == '[') {
                int j = i + 2;
                while (j < text.length() && !isSgrTerminator(text.charAt(j))) {
                    j++;
                }
                sb.append(text, i, j + 1);
                styled = true;
                i = j + 1;
            } else {
                sb.append(c);
                visible++;
                i++;
            }
        }
        sb.append("...");
        if (styled) {
            sb.append(RESET);
        }
        return sb.toString();
    }

    /** Pads {@code text} with trailing spaces up to {@code target} printable characters. */
    public static String padRight(String text, int target) {
        String value = (text == null) ? "" : text;
        int padding = target - visibleLength(value);
        return padding <= 0 ? value : value + " ".repeat(padding);
    }

    /** Pads {@code text} with leading spaces up to {@code target} printable characters. */
    public static String padLeft(String text, int target) {
        String value = (text == null) ? "" : text;
        int padding = target - visibleLength(value);
        return padding <= 0 ? value : " ".repeat(padding) + value;
    }

    /** Centres {@code text} inside {@code target} printable characters. */
    public static String center(String text, int target) {
        String value = (text == null) ? "" : text;
        int padding = target - visibleLength(value);
        if (padding <= 0) {
            return value;
        }
        int left = padding / 2;
        return " ".repeat(left) + value + " ".repeat(padding - left);
    }

    // ------------------------------------------------------------------ open sections

    /** One step of nesting inside an open section - the whole console lines up on one edge. */
    private static final String INDENT = "  ";

    /** @return a full-width Tech Violet rule: the divider grid between dashboard sections */
    public static String rule() {
        return paint(sgr(TECH), H.repeat(width()));
    }

    /** @return a full-width rule with a labelled centre, e.g. {@code TOP 5 PAPERS} */
    public static String rule(String label) {
        if (label == null || label.isEmpty()) {
            return rule();
        }
        String text = " " + label + " ";
        int fill = width() - visibleLength(text);
        if (fill < 0) {
            return rule();
        }
        int left = fill / 2;
        int right = fill - left;
        return paint(sgr(TECH), H.repeat(left))
                + " " + laser(label) + " "
                + paint(sgr(TECH), H.repeat(right));
    }

    /**
     * Draws a section inside the window frame: an accent bar carrying the title, a hairline rule, a
     * breath of space, then the body rows indented beneath it. The window chrome supplies the only
     * borders, so sections are separated by light and air and the console stays legible at any width.
     *
     * @param title optional caption placed above the rule; may be {@code null}
     * @param lines body rows, already decorated if desired
     * @return a multi-line block (ends with a newline)
     */
    public static String panel(String title, String[] lines) {
        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isEmpty()) {
            sb.append(trimEnd(truncate(heading(title), width()))).append('\n');
            sb.append(rule()).append('\n').append('\n');
        }
        if (lines != null) {
            for (String line : lines) {
                sb.append(trimEnd(truncate(INDENT + line, width()))).append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * Draws the numbered menu, colouring each leading {@code N.} token without altering the menu
     * text itself.
     */
    public static String menuBox(String title, String[] entries) {
        String[] lines = new String[entries.length];
        for (int i = 0; i < entries.length; i++) {
            lines[i] = menuLine(entries[i], i);
        }
        return panel(title, lines);
    }

    /** Highlights the leading {@code N.} marker of a menu entry, keeping the wording untouched. */
    public static String menuLine(String entry) {
        return menuLine(entry, 0);
    }

    /**
     * Highlights the leading {@code N.} marker in Laser Pink, so the numbering reads as one
     * interactive column while the wording stays exactly what the caller supplied.
     *
     * @param entry the menu text, e.g. {@code "  1. Add a paper"}
     * @param index zero-based position of the entry in the menu
     * @return the entry with its number painted
     */
    public static String menuLine(String entry, int index) {
        if (entry == null) {
            return null;
        }
        int start = 0;
        while (start < entry.length() && entry.charAt(start) == ' ') {
            start++;
        }
        int dot = entry.indexOf('.', start);
        if (dot > start && dot - start <= 2) {
            String number = entry.substring(start, dot);
            // A two-cell right-aligned number keeps every '.' on one column.
            return laser(padLeft(number, 2)) + entry.substring(dot);
        }
        return entry;
    }

    /**
     * Builds the startup splash: the system name centred on the page, a subtitle, a hairline rule,
     * then the environment information laid out in balanced columns. The splash itself is unframed -
     * the window chrome is drawn separately by {@link #openWindow(String)}.
     *
     * @param title    the system name, painted as a Tech Violet to Laser Pink duotone
     * @param subtitle optional dim line under the title
     * @param info     information rows (already styled), or {@code null}
     * @return a multi-line splash (ends with a newline)
     */
    public static String banner(String title, String subtitle, String[] info) {
        int content = width() - 2 * INDENT.length();
        DynamicArray<String> rows = new DynamicArray<>();
        rows.add("");
        if (title != null && !title.isEmpty()) {
            rows.add(center(duotoneBold(truncate(title, content)), content));
        }
        if (subtitle != null && !subtitle.isEmpty()) {
            rows.add(center(truncate(subtitle, content), content));
        }
        rows.add("");
        rows.add(rule());
        rows.add("");
        for (String line : columns(info, 2)) {
            rows.add(line);
        }
        rows.add("");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            sb.append(trimEnd(rows.get(i))).append('\n');
        }
        return sb.toString();
    }

    /**
     * Lays short rows out into balanced columns, each cell padded to one clean edge. A cell that does
     * not fit is truncated rather than left to wrap, so a block never spills past the display width.
     *
     * @param rows  the rows to distribute
     * @param count how many columns to use
     * @return one string per output line
     */
    public static String[] columns(String[] rows, int count) {
        if (rows == null || rows.length == 0) {
            return new String[0];
        }
        if (count < 2 || rows.length <= count) {
            String[] single = new String[rows.length];
            for (int i = 0; i < rows.length; i++) {
                single[i] = truncate(INDENT + rows[i], width());
            }
            return single;
        }
        int perColumn = (rows.length + count - 1) / count;
        int cell = (width() - INDENT.length()) / count;
        String[] out = new String[perColumn];
        for (int i = 0; i < perColumn; i++) {
            StringBuilder line = new StringBuilder();
            for (int c = 0; c < count; c++) {
                int index = i + c * perColumn;
                String text = index < rows.length ? INDENT + rows[index] : "";
                line.append(padRight(truncate(text, cell), cell));
            }
            out[i] = line.toString();
        }
        return out;
    }

    /** Drops trailing blanks, so piped output never carries invisible padding. */
    private static String trimEnd(String text) {
        if (text == null) {
            return null;
        }
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == ' ') {
            end--;
        }
        return text.substring(0, end);
    }

    // ------------------------------------------------------------------ content helpers

    /**
     * Colours a leading {@code [Tag]} status prefix and leaves the rest of the message alone.
     * Messages without a recognised tag are returned unchanged.
     */
    public static String status(String message) {
        if (message == null || message.isEmpty() || !COLORS || message.charAt(0) != '[') {
            return message;
        }
        int close = message.indexOf(']');
        if (close < 2 || close > 12) {
            return message;
        }
        String sgr = tagColor(message.substring(1, close).toLowerCase());
        if (sgr == null) {
            return message;
        }
        return paint(BOLD + sgr, message.substring(0, close + 1)) + message.substring(close + 1);
    }

    private static String tagColor(String name) {
        if (name.startsWith("error") || name.startsWith("fail")) {
            return RED;
        }
        if (name.startsWith("success") || name.startsWith("ok") || name.startsWith("pass")) {
            return GREEN;
        }
        if (name.startsWith("warn")) {
            return YELLOW;
        }
        if (name.startsWith("notice") || name.equals("+")) {
            return CYAN;
        }
        if (name.startsWith("info")) {
            return BLUE;
        }
        if (name.startsWith("result")) {
            return MAGENTA;
        }
        return null;
    }

    /** Renders a section heading: a Laser Pink accent bar followed by the title in Tech Violet. */
    public static String heading(String text) {
        return paint(sgr(LASER), BAR) + " " + tech(text);
    }

    /** Column the value of a {@link #field(String, String)} starts on. */
    private static final int FIELD_LABEL_WIDTH = 18;

    /**
     * Renders a labelled value line, e.g. {@code Hop Count : 3}. A value longer than the space left
     * beside the label is folded underneath it, so a long narrated chain cannot run past the window
     * frame.
     */
    public static String field(String label, String value) {
        String head = paint(sgr(CYBER), padRight(label, FIELD_LABEL_WIDTH));
        String folded = fold(paint(sgr(GHOST), value == null ? "" : value),
                Math.max(12, width() - FIELD_LABEL_WIDTH));
        return head + folded.replace("\n", "\n" + " ".repeat(FIELD_LABEL_WIDTH));
    }

    /**
     * Renders a proportional bar for report tables.
     *
     * @param value number to visualise
     * @param max   value that fills the whole bar
     * @param cells bar length in characters
     * @return a coloured filled/empty bar
     */
    public static String bar(int value, int max, int cells) {
        int filled = 0;
        if (max > 0 && value > 0) {
            filled = (int) Math.round((double) value * cells / max);
            if (filled < 1) {
                filled = 1;
            }
            if (filled > cells) {
                filled = cells;
            }
        }
        return paint(sgr(LASER), FULL.repeat(filled)) + paint(DIM, EMPTY.repeat(cells - filled));
    }

    /**
     * Folds prose onto extra lines at the last space, so a long caption is wrapped deliberately
     * instead of being left to the terminal. Continuation lines are indented by two spaces.
     *
     * @param text the text to fold
     * @param max  the printable width available
     * @return the text, possibly with newlines inserted
     */
    public static String fold(String text, int max) {
        if (text == null || max <= 6 || visibleLength(text) <= max) {
            return text;
        }
        StringBuilder out = new StringBuilder();
        String remaining = text;
        String indent = "";
        while (visibleLength(indent) + visibleLength(remaining) > max) {
            int room = max - indent.length();
            // Fold at the last space that fits; a word longer than the room is cut instead. Both cuts
            // are measured in printable characters, so a coloured line never splits an escape code.
            int cut = remaining.lastIndexOf(' ', Math.max(0, rawIndexAtWidth(remaining, room) - 1));
            if (cut <= 0) {
                cut = rawIndexAtWidth(remaining, room);
            }
            if (cut <= 0) {
                break;
            }
            out.append(indent).append(remaining, 0, cut).append('\n');
            remaining = remaining.substring(cut).trim();
            indent = "  ";
        }
        return out.append(indent).append(remaining).toString();
    }

    /**
     * @param text  text that may contain ANSI escapes
     * @param width a printable width
     * @return the raw index just past the {@code width}-th printable character, never inside an
     *         escape sequence
     */
    private static int rawIndexAtWidth(String text, int width) {
        int visible = 0;
        int i = 0;
        while (i < text.length() && visible < width) {
            char c = text.charAt(i);
            if (c == '\u001B' && i + 1 < text.length() && text.charAt(i + 1) == '[') {
                int j = i + 2;
                while (j < text.length() && !isSgrTerminator(text.charAt(j))) {
                    j++;
                }
                i = j + 1;
                continue;
            }
            visible++;
            i++;
        }
        return i;
    }

    /**
     * Colourises the {@code [ID]} node boxes inside a {@link GraphRenderer} diagram, leaving the
     * routing lines untouched. The renderer's caption line is folded to the display width first, and
     * the renderer's own {@code [Graph]} notices are never treated as node labels.
     */
    public static String diagram(String art) {
        if (art == null || art.isEmpty()) {
            return art;
        }
        String folded = foldCaption(art);
        if (!COLORS || art.startsWith("[Graph]")) {
            return folded;
        }
        return colourLabels(folded);
    }

    /** Folds only the first line of a diagram (the caption the renderer prints above the drawing). */
    private static String foldCaption(String art) {
        int breakAt = art.indexOf('\n');
        if (breakAt < 0) {
            return fold(art, width());
        }
        return fold(art.substring(0, breakAt), width()) + art.substring(breakAt);
    }

    /**
     * Turns every {@code [ID]} box in a diagram into a coloured node chip, alternating between the
     * Laser Pink and Cyber Purple roles so a graph reads as a set of nodes rather than a wall of grey
     * text. The characters themselves are untouched, so the diagram keeps its exact width and layout.
     */
    private static String colourLabels(String art) {
        StringBuilder sb = new StringBuilder(art.length() + 64);
        int i = 0;
        int node = 0;
        while (i < art.length()) {
            char c = art.charAt(i);
            if (c == '[') {
                int end = art.indexOf(']', i);
                if (end > i) {
                    sb.append(nodeChip(art.substring(i, end + 1), node));
                    node++;
                    i = end + 1;
                    continue;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    /**
     * Paints a node label as a chip: a bright tint of the node's own hue on a dark tint of the same
     * hue, so the chip stays readable on any terminal background.
     */
    private static String nodeChip(String label, int index) {
        int[] role = (index % 2 == 0) ? LASER : CYBER;
        return paint(sgr(role) + bg(OBSIDIAN[0], OBSIDIAN[1], OBSIDIAN[2]), label);
    }

    // ------------------------------------------------------------------ macbook window frame

    /** Columns the window chrome adds around the content: "| | " on each side of a line. */
    private static final int FRAME_OVERHEAD = 8;
    /** The window sheet is drawn in double-line box art: \u2554 \u2550 \u2557 on top, \u2551 down the sides. */
    private static final String OUTER_TOP_LEFT = UNICODE ? "\u2554" : "+";
    private static final String OUTER_TOP_RIGHT = UNICODE ? "\u2557" : "+";
    private static final String OUTER_BOTTOM_LEFT = UNICODE ? "\u255A" : "+";
    private static final String OUTER_BOTTOM_RIGHT = UNICODE ? "\u255D" : "+";
    private static final String OUTER_TEE_LEFT = UNICODE ? "\u2560" : "+";
    private static final String OUTER_TEE_RIGHT = UNICODE ? "\u2563" : "+";
    private static final String OUTER_VERTICAL = UNICODE ? "\u2551" : "|";
    /** The window sheet's horizontal stroke: \u2550 where the console can draw it, else {@code =}. */
    private static final String OUTER_HORIZONTAL = UNICODE ? "\u2550" : "=";
    /** The interface panel nested inside the sheet is deliberately sharp-cornered and single-line. */
    private static final String PANEL_TOP_LEFT = UNICODE ? "\u250C" : "+";
    private static final String PANEL_TOP_RIGHT = UNICODE ? "\u2510" : "+";
    private static final String PANEL_BOTTOM_LEFT = UNICODE ? "\u2514" : "+";
    private static final String PANEL_BOTTOM_RIGHT = UNICODE ? "\u2518" : "+";
    private static final String PANEL_VERTICAL = UNICODE ? "\u2502" : "|";
    /** The three window-management dots in the title bar. */
    private static final String DOT_GLYPH = UNICODE ? "\u25CF" : "o";

    /**
     * Prints the top of the macOS-style window sheet: a rounded Tech Violet frame with a title bar
     * carrying the three colour-coded window controls, then the sharp Tech Violet border of the
     * interface panel that holds the dashboard. Everything written afterwards belongs inside it,
     * so install the stream from {@link Window#stream()} once this returns.
     *
     * @param path the simulated shell path shown in the title bar
     */
    public static void openWindow(String path) {
        int inner = width();
        int outer = inner + FRAME_OVERHEAD;
        System.out.print(edge(OUTER_TOP_LEFT, OUTER_TOP_RIGHT, outer));
        System.out.print(titleBar(path, outer));
        System.out.print(edge(OUTER_TEE_LEFT, OUTER_TEE_RIGHT, outer));
        System.out.print(nestedEdge(PANEL_TOP_LEFT, PANEL_TOP_RIGHT, inner));
    }

    /** Closes the interface panel and the window sheet. Call once stdout is restored. */
    public static void closeWindow() {
        int inner = width();
        int outer = inner + FRAME_OVERHEAD;
        System.out.print(nestedEdge(PANEL_BOTTOM_LEFT, PANEL_BOTTOM_RIGHT, inner));
        System.out.print(edge(OUTER_BOTTOM_LEFT, OUTER_BOTTOM_RIGHT, outer));
    }

    /** @return one window-management dot, ringed in the given palette role */
    private static String windowDot(int[] role) {
        return paint(sgr(role), DOT_GLYPH);
    }

    /** @return a full-width border row: the two corners with the horizontal rule between them */
    private static String edge(String left, String right, int outer) {
        return paint(sgr(TECH), left + OUTER_HORIZONTAL.repeat(outer - 2) + right)
                + System.lineSeparator();
    }

    /** @return a border row of the interface panel, sitting one cell inside the window sheet */
    private static String nestedEdge(String left, String right, int inner) {
        String bar = paint(sgr(TECH), OUTER_VERTICAL);
        return bar + " " + paint(sgr(TECH), left + H.repeat(inner + 2) + right) + " " + bar
                + System.lineSeparator();
    }

    /** @return the title bar: three colour-coded dots, then the simulated shell path */
    private static String titleBar(String path, int outer) {
        int inner = outer - 2;
        String lead = " " + windowDot(LASER) + " " + windowDot(TECH) + " " + windowDot(CYBER) + "   ";
        int room = Math.max(0, inner - visibleLength(lead));
        String label = truncate(path == null ? "" : path, room);
        return paint(sgr(TECH), OUTER_VERTICAL) + lead + paint(sgr(GHOST), label)
                + " ".repeat(Math.max(0, room - visibleLength(label)))
                + paint(sgr(TECH), OUTER_VERTICAL) + System.lineSeparator();
    }

    /**
     * A stdout replacement that draws every complete line inside the window's interface panel. The
     * left border is written as soon as a line starts, so an interactive prompt stays visibly inside
     * the frame; the right border is completed when the line ends.
     *
     * <p>Bytes are forwarded as they arrive so an interactive prompt is on screen before the user is
     * asked to type. A consequence is that a line wider than the panel has already been written by
     * the time the line ends, so it cannot be retroactively clipped: such a row keeps its data and
     * runs past the right border instead of being silently truncated. Every table the console builds
     * sizes its own columns to {@link Style#width()}, so this only happens for genuinely oversized
     * content on a very narrow terminal.
     */
    public static final class Window extends OutputStream {

        private final OutputStream sink;
        private final Charset charset;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final PrintStream stream;
        private boolean started;

        public Window(OutputStream sink) {
            this.sink = sink;
            this.charset = stdoutCharset();
            this.stream = new PrintStream(this, true, this.charset);
        }

        /** @return the replacement for {@code System.out} */
        public PrintStream stream() {
            return stream;
        }

        /** Completes the current line, if one is open. Safe to call at any time. */
        public void endLine() {
            try {
                if (started) {
                    closeLine();
                }
            } catch (IOException ignored) {
                // a broken pipe is not worth aborting the session for
            }
        }

        @Override
        public void write(int b) throws IOException {
            if (b == '\n') {
                closeLine();
                return;
            }
            if (b == '\r') {
                return;
            }
            if (!started) {
                openLine();
            }
            sink.write(b);
            buffer.write(b);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            for (int i = 0; i < length; i++) {
                write(bytes[offset + i]);
            }
            // An interactive prompt is printed without a newline; flush so it is visible before
            // the user is asked to type.
            sink.flush();
        }

        @Override
        public void flush() throws IOException {
            sink.flush();
        }

        private void openLine() throws IOException {
            sink.write((paint(sgr(TECH), OUTER_VERTICAL) + " "
                    + paint(sgr(TECH), PANEL_VERTICAL) + " ").getBytes(charset));
            started = true;
        }

        private void closeLine() throws IOException {
            if (!started) {
                openLine();
            }
            String content = buffer.toString(charset);
            buffer.reset();
            int pad = Math.max(0, width() - visibleLength(content));
            String tail = " ".repeat(pad)
                    + " " + paint(sgr(TECH), PANEL_VERTICAL) + " " + paint(sgr(TECH), OUTER_VERTICAL)
                    + System.lineSeparator();
            sink.write(tail.getBytes(charset));
            sink.flush();
            started = false;
        }
    }

    // ------------------------------------------------------------------ wordmark

    /**
     * Renders the project wordmark above the window: CERBERUS set in heavy five-row block lettering
     * shaded Tech Violet into Laser Pink, with SYSTEM letterspaced beneath and a Tech Violet rule.
     *
     * <p>A terminal cannot be told to load a font file, so the display face is approximated with a
     * block wordmark. That is the closest a console gets to the typographic treatment the browser
     * build renders with Orbitron.
     *
     * @return the multi-line wordmark, centred in the full terminal width (ends with a newline)
     */
    public static String wordmark() {
        String word = "CERBERUS";
        int rows = 5;
        StringBuilder out = new StringBuilder();
        out.append(System.lineSeparator());
        for (int r = 0; r < rows; r++) {
            StringBuilder line = new StringBuilder();
            for (int g = 0; g < word.length(); g++) {
                if (g > 0) {
                    line.append(' ');
                }
                String[] glyph = glyph(word.charAt(g));
                line.append(glyph == null ? "    " : glyph[r]);
            }
            out.append(center(duotone(line.toString()), fullWidth())).append(System.lineSeparator());
        }
        out.append(center(letterspace("SYSTEM"), fullWidth())).append(System.lineSeparator());
        out.append(center(paint(sgr(TECH), H.repeat(Math.min(fullWidth(), 44))), fullWidth()))
                .append(System.lineSeparator()).append(System.lineSeparator());
        return out.toString();
    }

    /** @return the whole terminal width available to the wordmark, chrome included */
    private static int fullWidth() {
        return width() + FRAME_OVERHEAD;
    }

    /** @return {@code text} with one space between every character */
    private static String letterspace(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length() * 2);
        for (int i = 0; i < text.length(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(text.charAt(i));
        }
        return paint(sgr(CYBER), sb.toString());
    }

    /** @return the five rows of a block glyph, four cells wide, or {@code null} when unsupported */
    private static String[] glyph(char c) {
        String[] pattern;
        switch (c) {
            case 'C':
                pattern = new String[]{" ###", "##  ", "##  ", "##  ", " ###"};
                break;
            case 'E':
                pattern = new String[]{"####", "### ", "### ", "##  ", "####"};
                break;
            case 'R':
                pattern = new String[]{"####", "## #", "####", "## #", "## #"};
                break;
            case 'B':
                pattern = new String[]{"####", "## #", "### ", "## #", "####"};
                break;
            case 'U':
                pattern = new String[]{"## #", "## #", "## #", "## #", " ###"};
                break;
            case 'S':
                pattern = new String[]{" ###", "##  ", " ## ", "  ##", "### "};
                break;
            case 'Y':
                pattern = new String[]{"## #", "## #", " ###", "  ##", "  ##"};
                break;
            case 'T':
                pattern = new String[]{"####", " ## ", " ## ", " ## ", " ## "};
                break;
            case 'M':
                pattern = new String[]{"## #", "####", "# ##", "## #", "## #"};
                break;
            default:
                return null;
        }
        String[] out = new String[pattern.length];
        for (int i = 0; i < pattern.length; i++) {
            out[i] = pattern[i].replace("#", FULL);
        }
        return out;
    }

    // ------------------------------------------------------------------ detection

    private static boolean detectColors() {
        String override = env("CERBERUS_COLOR");
        if (override != null) {
            String mode = override.trim().toLowerCase();
            if (mode.equals("never") || mode.equals("off") || mode.equals("0") || mode.equals("false")) {
                return false;
            }
            if (mode.equals("always") || mode.equals("force") || mode.equals("on")
                    || mode.equals("1") || mode.equals("true")) {
                return true;
            }
        }
        if (env("NO_COLOR") != null) {
            return false;
        }
        String term = env("TERM");
        if (term != null && term.equalsIgnoreCase("dumb")) {
            return false;
        }

        // A positive signal that the thing on the other end understands ANSI.
        boolean terminal = env("WT_SESSION") != null
                || env("ANSICON") != null
                || env("ConEmuANSI") != null
                || env("TERM_PROGRAM") != null
                || hasText(env("COLORTERM"))
                || (term != null && (term.contains("xterm") || term.contains("color") || term.contains("ansi")));

        if (isWindows()) {
            // Windows consoles only honour ANSI once VT processing is enabled, which the JVM does
            // not do for us - so require a positive terminal signal instead of guessing.
            return terminal;
        }
        return System.console() != null || terminal;
    }

    private static Depth detectDepth() {
        if (!COLORS) {
            return Depth.NONE;
        }
        String colorterm = env("COLORTERM");
        if (colorterm != null) {
            String mode = colorterm.toLowerCase();
            if (mode.contains("truecolor") || mode.contains("24bit")) {
                return Depth.TRUECOLOR;
            }
        }
        return Depth.XTERM256;
    }

    private static boolean detectUnicode() {
        return GraphRenderer.Palette.auto() == GraphRenderer.Palette.UNICODE && canEncodeExtras();
    }

    /** Verifies the block/shade glyphs that GraphRenderer's palette does not probe for. */
    private static boolean canEncodeExtras() {
        try {
            Charset charset = stdoutCharset();
            if (charset == null) {
                return false;
            }
            CharsetEncoder encoder = charset.newEncoder();
            return encoder.canEncode("\u2588\u2591\u258C\u2550\u2551\u2554\u2557\u255A\u255D\u2560\u2563"
                    + "\u2502\u250C\u2510\u2514\u2518\u25CF");
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

    private static boolean isWindows() {
        String os = System.getProperty("os.name");
        return os != null && os.toLowerCase().contains("win");
    }

    private static String env(String name) {
        try {
            return System.getenv(name);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
