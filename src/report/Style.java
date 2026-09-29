package report;

import core.DynamicArray;

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
    private static final String WHITE = CSI + "97m";

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
     * Chooses the display width used by every frame and rule.
     *
     * @return the shell's reported {@code COLUMNS}, clamped to a readable range, else {@value #DEFAULT_WIDTH}
     */
    public static int width() {
        String columns = env("COLUMNS");
        if (columns != null) {
            try {
                int parsed = Integer.parseInt(columns.trim());
                if (parsed > 0) {
                    return parsed < MIN_WIDTH ? MIN_WIDTH : (parsed > MAX_WIDTH ? MAX_WIDTH : parsed);
                }
            } catch (NumberFormatException ignored) {
                // fall through to the default width
            }
        }
        return DEFAULT_WIDTH;
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

    /** Section/file headings. */
    public static String highlight(String text) {
        return paint(BOLD + CYAN, text);
    }

    /** Emphasised labels and values. */
    public static String strong(String text) {
        return paint(BOLD + WHITE, text);
    }

    /** The most important number or word on a line. */
    public static String emphasis(String text) {
        return paint(BOLD + MAGENTA, text);
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

    /** Informational aside. */
    public static String note(String text) {
        return paint(CYAN, text);
    }

    // ------------------------------------------------------------------ rainbow theme

    /** Saturation and brightness of the rainbow sweep: vivid, but still readable on dark terminals. */
    private static final double RAINBOW_SATURATION = 0.85;
    private static final double RAINBOW_VALUE = 1.0;
    /** Hue advance, in degrees, per character for {@link #rainbow(String)}. */
    private static final double RAINBOW_STEP = 12.0;

    /**
     * Paints text with a rainbow sweep, advancing the hue at every character. This is the project's
     * text theme: titles, headings, menu numbers, table headers and rule captions all run through it.
     *
     * @param text the text to paint
     * @return the rainbow text, or the text unchanged when colour is unavailable
     */
    public static String rainbow(String text) {
        return rainbow(text, RAINBOW_STEP, false);
    }

    /** As {@link #rainbow(String)}, in bold. */
    public static String rainbowBold(String text) {
        return rainbow(text, RAINBOW_STEP, true);
    }

    /** Spreads exactly one full spectrum from red back to red, however long the text is. */
    public static String rainbowSweep(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return rainbow(text, 360.0 / text.length(), false);
    }

    /** As {@link #rainbowSweep(String)}, in bold. */
    public static String rainbowSweepBold(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return rainbow(text, 360.0 / text.length(), true);
    }

    /**
     * Paints text word by word, stepping the hue between words. Because each word is emitted as one
     * contiguous coloured run, the plain text stays intact and remains safe to search or assert on
     * (unlike {@link #rainbowSweep(String)}, which colours each character separately).
     *
     * @param text the text to paint
     * @return the painted text
     */
    public static String rainbowWords(String text) {
        if (text == null || text.isEmpty() || !COLORS) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length() * 20);
        int hue = 0;
        int i = 0;
        while (i < text.length()) {
            if (text.charAt(i) == ' ') {
                sb.append(' ');
                i++;
                continue;
            }
            int wordStart = i;
            while (i < text.length() && text.charAt(i) != ' ') {
                i++;
            }
            sb.append(hue(hue, text.substring(wordStart, i)));
            hue = (hue + 45) % 360;
        }
        return sb.toString();
    }

    /** Paints a single character in the hue for a given position, used for the menu numbering. */
    public static String hue(int degrees, String text) {
        int[] rgb = hsvToRgb(degrees, RAINBOW_SATURATION, RAINBOW_VALUE);
        return paint(BOLD + fg(rgb[0], rgb[1], rgb[2]), text);
    }

    private static String rainbow(String text, double degreesPerChar, boolean bold) {
        if (text == null || text.isEmpty() || !COLORS) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length() * 20);
        double hue = 0;
        for (int i = 0; i < text.length(); i++) {
            char glyph = text.charAt(i);
            if (glyph == ' ' || glyph == '\n') {
                sb.append(glyph);
            } else {
                int[] rgb = hsvToRgb(hue, RAINBOW_SATURATION, RAINBOW_VALUE);
                sb.append(fg(rgb[0], rgb[1], rgb[2]));
                if (bold) {
                    sb.append(BOLD);
                }
                sb.append(glyph).append(RESET);
            }
            hue += degreesPerChar;
        }
        return sb.toString();
    }

    /** Converts an HSV triple (hue in degrees, saturation and value in 0..1) to RGB bytes. */
    private static int[] hsvToRgb(double hue, double saturation, double value) {
        double degrees = ((hue % 360) + 360) % 360;
        double chroma = value * saturation;
        double sixth = degrees / 60.0;
        double second = chroma * (1 - Math.abs(sixth % 2 - 1));
        double red;
        double green;
        double blue;
        switch ((int) Math.floor(sixth) % 6) {
            case 0:
                red = chroma;
                green = second;
                blue = 0;
                break;
            case 1:
                red = second;
                green = chroma;
                blue = 0;
                break;
            case 2:
                red = 0;
                green = chroma;
                blue = second;
                break;
            case 3:
                red = 0;
                green = second;
                blue = chroma;
                break;
            case 4:
                red = second;
                green = 0;
                blue = chroma;
                break;
            default:
                red = chroma;
                green = 0;
                blue = second;
                break;
        }
        double match = value - chroma;
        return new int[]{
                (int) Math.round((red + match) * 255),
                (int) Math.round((green + match) * 255),
                (int) Math.round((blue + match) * 255)
        };
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

    /** Hue span of a full-width rule: one soft rainbow hairline across each section gap. */
    private static final int RULE_SWEEP = 359;
    /** Rules sit deliberately softer than the text, so they read as light rather than as ink. */
    private static final double RULE_SATURATION = 0.72;
    private static final double RULE_VALUE = 0.85;
    /** One step of nesting inside an open section - the whole console lines up on one edge. */
    private static final String INDENT = "  ";

    /** @return a full-width rule lit by the rainbow gradient (a hairline, not a border) */
    public static String rule() {
        return gradient(H.repeat(width()), 0, RULE_SWEEP);
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
        int mid = RULE_SWEEP * left / Math.max(1, width());
        return gradient(H.repeat(left), 0, mid)
                + " " + rainbowWords(label) + " "
                + gradient(H.repeat(right), mid, RULE_SWEEP);
    }

    /**
     * Paints a run of characters with a hue gradient. The characters themselves are untouched, so a
     * gradient run keeps its exact width however the hue walks.
     *
     * @param text    the characters to light up
     * @param fromHue starting hue, in degrees
     * @param toHue   ending hue, in degrees
     * @return the gradient run, or the plain text when colour is unavailable
     */
    private static String gradient(String text, int fromHue, int toHue) {
        if (text == null || text.isEmpty() || !COLORS) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length() * 20);
        int last = Math.max(1, text.length() - 1);
        for (int i = 0; i < text.length(); i++) {
            int degrees = fromHue + (toHue - fromHue) * i / last;
            int[] rgb = hsvToRgb(degrees, RULE_SATURATION, RULE_VALUE);
            sb.append(fg(rgb[0], rgb[1], rgb[2])).append(text.charAt(i));
        }
        return sb.append(RESET).toString();
    }

    /**
     * Draws an <em>open</em> section - there are no borders anywhere: an accent bar carrying the
     * rainbow title, a hairline rule, a breath of space, then the body rows indented beneath it.
     * Sections are separated by light and air instead of frames, which keeps the console clean and
     * legible at any terminal width.
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
     * Highlights the leading {@code N.} marker, walking the hue down the menu so the numbering reads
     * as a rainbow column while the wording stays exactly what the caller supplied.
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
            return hue(index * 30, padLeft(number, 2)) + entry.substring(dot);
        }
        return entry;
    }

    /**
     * Builds the startup splash: the rainbow system name centred on the page, a subtitle, a hairline
     * rule, then the environment information laid out in balanced columns. Nothing is framed, so the
     * splash reads as an open title page rather than a dialog box.
     *
     * @param title    the system name, painted with one full rainbow sweep
     * @param subtitle optional dim line under the title
     * @param info     information rows (already styled), or {@code null}
     * @return a multi-line splash (ends with a newline)
     */
    public static String banner(String title, String subtitle, String[] info) {
        int content = width() - 2 * INDENT.length();
        DynamicArray<String> rows = new DynamicArray<>();
        rows.add("");
        if (title != null && !title.isEmpty()) {
            rows.add(center(rainbowSweepBold(truncate(title, content)), content));
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
    private static String[] columns(String[] rows, int count) {
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

    /** Renders a section heading: an accent bar followed by the title in the rainbow theme. */
    public static String heading(String text) {
        return paint(BOLD + CYAN, BAR) + " " + rainbowSweepBold(text);
    }

    /** Renders a labelled value line, e.g. {@code Hop Count : 3}. */
    public static String field(String label, String value) {
        return paint(DIM, padRight(label, 18)) + paint(BOLD + WHITE, value);
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
        return paint(GREEN, FULL.repeat(filled)) + paint(DIM, EMPTY.repeat(cells - filled));
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
     * Turns every {@code [ID]} box in a diagram into a coloured node chip, walking the hue for each
     * node so a graph reads as a rainbow of papers rather than a wall of grey text. The characters
     * themselves are untouched, so the diagram keeps its exact width and layout.
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
        int hue = (index * 53) % 360;
        int[] ink = hsvToRgb(hue, 0.5, 1.0);
        int[] panel = hsvToRgb(hue, 0.75, 0.24);
        return fg(ink[0], ink[1], ink[2]) + bg(panel[0], panel[1], panel[2]) + BOLD
                + label + RESET;
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
            return encoder.canEncode("\u2588\u2591\u258C\u256D\u256E\u2570\u256F");
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
