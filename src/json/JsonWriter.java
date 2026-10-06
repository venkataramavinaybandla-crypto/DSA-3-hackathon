package json;

/**
 * Hand-rolled JSON serializer — Build Plan Phase 7.
 *
 * <p>A tiny builder scoped to this project's model types. No reflection,
 * no generic object-graph walking: the caller renders exactly the fields
 * a {@code Paper}, {@code Citation} or report row has.
 *
 * <p>{@link #escape(String)} handles quotes, backslashes, control
 * characters and every non-ASCII code point (as {@code \\uXXXX}), so the
 * output is pure ASCII and safe for any consumer.
 *
 * <p>Usage:
 * <pre>
 *   JsonWriter cites = JsonWriter.array().value("P101").value("P104").end();
 *   String json = JsonWriter.object()
 *       .field("id", paper.getId())
 *       .field("year", paper.getYear())
 *       .field("cites", cites)
 *       .end()
 *       .toString();
 * </pre>
 */
public final class JsonWriter {

    private final StringBuilder out;
    private final char closer;
    private boolean first = true;

    private JsonWriter(char opener, char closer) {
        this.out = new StringBuilder();
        this.out.append(opener);
        this.closer = closer;
    }

    /** Starts a JSON object ({@code {}). */
    public static JsonWriter object() {
        return new JsonWriter('{', '}');
    }

    /** Starts a JSON array ({@code []}). */
    public static JsonWriter array() {
        return new JsonWriter('[', ']');
    }

    public JsonWriter field(String name, String value) {
        comma();
        quoted(name);
        out.append(':');
        quoted(value);
        return this;
    }

    public JsonWriter field(String name, int value) {
        comma();
        quoted(name);
        out.append(':');
        out.append(value);
        return this;
    }

    public JsonWriter field(String name, boolean value) {
        comma();
        quoted(name);
        out.append(':');
        out.append(value);
        return this;
    }

    /** Embeds a nested builder's rendered JSON as the field value. */
    public JsonWriter field(String name, JsonWriter nested) {
        comma();
        quoted(name);
        out.append(':');
        out.append(nested.out);
        return this;
    }

    public JsonWriter value(String value) {
        comma();
        quoted(value);
        return this;
    }

    public JsonWriter value(int value) {
        comma();
        out.append(value);
        return this;
    }

    public JsonWriter value(boolean value) {
        comma();
        out.append(value);
        return this;
    }

    /** Embeds a nested builder's rendered JSON as an array element. */
    public JsonWriter value(JsonWriter nested) {
        comma();
        out.append(nested.out);
        return this;
    }

    /** Closes this object or array. */
    public JsonWriter end() {
        out.append(closer);
        return this;
    }

    private void comma() {
        if (first) {
            first = false;
        } else {
            out.append(',');
        }
    }

    private void quoted(String value) {
        out.append('"');
        out.append(escape(value));
        out.append('"');
    }

    /**
     * Escapes a string for JSON: {@code "} and {@code \} become {@code \"}
     * and {@code \\}, control characters use their short forms, and every
     * character outside printable ASCII becomes {@code \\uXXXX}. A {@code null}
     * input escapes to the empty string.
     */
    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                case '\b':
                    escaped.append("\\b");
                    break;
                case '\f':
                    escaped.append("\\f");
                    break;
                default:
                    if (c < 0x20 || c > 0x7E) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
            }
        }
        return escaped.toString();
    }

    @Override
    public String toString() {
        return out.toString();
    }
}
