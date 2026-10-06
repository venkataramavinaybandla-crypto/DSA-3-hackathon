package json;

import core.HashTable;

/**
 * Minimal hand-rolled JSON reader — the parsing half of Build Plan Phase 8's
 * "parses query params and JSON request bodies".
 *
 * <p>Parses exactly the shape the API accepts: flat objects whose values are
 * strings, numbers, booleans or {@code null}. String values are unescaped
 * (including {@code \\uXXXX}); numbers and booleans are returned verbatim for
 * the caller to convert. Nested objects and arrays are rejected — the request
 * model ({@code POST /api/papers}, {@code POST /api/citations}) has no
 * nested fields, so silently accepting them would be an unvalidated-input
 * hazard rather than a feature.
 */
public final class JsonReader {

    private final String text;
    private int pos;

    private JsonReader(String text) {
        this.text = text;
        this.pos = 0;
    }

    /**
     * Parses a flat JSON object into a string-valued table.
     *
     * @param json the request body
     * @return key -&gt; raw value (strings unescaped, numbers/bools verbatim)
     * @throws IllegalArgumentException on malformed or nested JSON
     */
    public static HashTable<String> parseObject(String json) {
        if (json == null) {
            throw new IllegalArgumentException("JSON body is empty");
        }
        JsonReader reader = new JsonReader(json.trim());
        reader.skipWhitespace();
        reader.expect('{');
        HashTable<String> values = new HashTable<>();
        reader.skipWhitespace();
        if (reader.peek() == '}') {
            reader.pos++;
            reader.finish();
            return values;
        }
        while (true) {
            reader.skipWhitespace();
            String key = reader.readString();
            reader.skipWhitespace();
            reader.expect(':');
            reader.skipWhitespace();
            values.put(key, reader.readValue());
            reader.skipWhitespace();
            char c = reader.peek();
            if (c == ',') {
                reader.pos++;
                continue;
            }
            if (c == '}') {
                reader.pos++;
                break;
            }
            throw new IllegalArgumentException(
                    "Malformed JSON: expected ',' or '}' at offset " + reader.pos);
        }
        reader.finish();
        return values;
    }

    private String readValue() {
        char c = peek();
        if (c == '"') {
            return readString();
        }
        if (c == '{' || c == '[') {
            throw new IllegalArgumentException(
                    "Nested JSON is not supported at offset " + pos);
        }
        int start = pos;
        while (pos < text.length()) {
            char ch = text.charAt(pos);
            if (ch == ',' || ch == '}' || ch == ']' || Character.isWhitespace(ch)) {
                break;
            }
            pos++;
        }
        if (pos == start) {
            throw new IllegalArgumentException("Malformed JSON: expected a value at offset " + pos);
        }
        return text.substring(start, pos);
    }

    private String readString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (pos < text.length()) {
            char c = text.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                if (pos >= text.length()) {
                    throw new IllegalArgumentException("Malformed JSON: dangling escape");
                }
                char escaped = text.charAt(pos++);
                switch (escaped) {
                    case '"':  sb.append('"');  break;
                    case '\\': sb.append('\\'); break;
                    case '/':  sb.append('/');  break;
                    case 'n':  sb.append('\n'); break;
                    case 'r':  sb.append('\r'); break;
                    case 't':  sb.append('\t'); break;
                    case 'b':  sb.append('\b'); break;
                    case 'f':  sb.append('\f'); break;
                    case 'u':
                        if (pos + 4 > text.length()) {
                            throw new IllegalArgumentException("Malformed JSON: truncated \\u escape");
                        }
                        String hex = text.substring(pos, pos + 4);
                        try {
                            sb.append((char) Integer.parseInt(hex, 16));
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException("Malformed JSON: bad \\u escape \\" + escaped + hex);
                        }
                        pos += 4;
                        break;
                    default:
                        throw new IllegalArgumentException("Malformed JSON: unknown escape \\" + escaped);
                }
            } else if (c < 0x20) {
                throw new IllegalArgumentException("Malformed JSON: raw control character in string");
            } else {
                sb.append(c);
            }
        }
        throw new IllegalArgumentException("Malformed JSON: unterminated string");
    }

    private char peek() {
        if (pos >= text.length()) {
            throw new IllegalArgumentException("Malformed JSON: unexpected end of input");
        }
        return text.charAt(pos);
    }

    private void expect(char c) {
        char found = peek();
        if (found != c) {
            throw new IllegalArgumentException(
                    "Malformed JSON: expected '" + c + "' but found '" + found + "' at offset " + pos);
        }
        pos++;
    }

    private void skipWhitespace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }

    private void finish() {
        skipWhitespace();
        if (pos < text.length()) {
            throw new IllegalArgumentException("Malformed JSON: trailing content at offset " + pos);
        }
    }
}
