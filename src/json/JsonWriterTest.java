package json;

/**
 * Test suite for the hand-rolled JSON serializer (Build Plan Phase 7).
 *
 * <p>Covers the escaping edge cases the citation dataset can produce:
 * quotes inside titles, unicode author names, backslashes, control
 * characters, empty arrays, and nested object/array composition.
 */
public class JsonWriterTest {

    private static int totalTests = 0;
    private static int passedTests = 0;

    public static void main(String[] args) {
        System.out.println("Running JsonWriter Test Suite...\n");

        testEmptyObject();
        testEmptyArray();
        testScalarFields();
        testStringEscaping();
        testQuotesInTitle();
        testUnicodeAuthorName();
        testControlCharacters();
        testNestedArrayInObject();
        testNestedObjectInArray();
        testDeeplyNestedStructure();
        testEscapeIsPureAscii();
        testCommaPlacement();

        System.out.println("\n==========================================");
        System.out.println("JSONWRITER TEST RESULTS: " + passedTests + " / " + totalTests + " PASSED");
        System.out.println("==========================================");

        if (passedTests != totalTests) {
            System.exit(1);
        }
    }

    private static void check(String name, String expected, String actual) {
        totalTests++;
        if (expected.equals(actual)) {
            passedTests++;
            System.out.println("  [PASS] " + name);
        } else {
            System.out.println("  [FAIL] " + name);
            System.out.println("         expected: " + expected);
            System.out.println("         actual:   " + actual);
        }
    }

    private static void check(String name, boolean expected, boolean actual) {
        totalTests++;
        if (expected == actual) {
            passedTests++;
            System.out.println("  [PASS] " + name);
        } else {
            System.out.println("  [FAIL] " + name);
            System.out.println("         expected: " + expected);
            System.out.println("         actual:   " + actual);
        }
    }

    private static void testEmptyObject() {
        check("empty object",
                "{}",
                JsonWriter.object().end().toString());
    }

    private static void testEmptyArray() {
        check("empty array (the empty-cites edge case)",
                "[]",
                JsonWriter.array().end().toString());
    }

    private static void testScalarFields() {
        check("string/int/bool fields",
                "{\"id\":\"P101\",\"year\":2017,\"verified\":true}",
                JsonWriter.object()
                        .field("id", "P101")
                        .field("year", 2017)
                        .field("verified", true)
                        .end()
                        .toString());
    }

    private static void testStringEscaping() {
        check("quote and backslash escaping",
                "{\"t\":\"a \\\"quoted\\\" \\\\ path\"}",
                JsonWriter.object()
                        .field("t", "a \"quoted\" \\ path")
                        .end()
                        .toString());
    }

    private static void testQuotesInTitle() {
        String title = "Attention: \"Is All You Need\"";
        String json = JsonWriter.object().field("title", title).end().toString();
        check("quotes inside a paper title round-trip visually",
                "{\"title\":\"Attention: \\\"Is All You Need\\\"\"}",
                json);
        check("inner title quotes appear only in escaped form",
                true,
                json.contains("\\\"Is All You Need\\\""));
    }

    private static void testUnicodeAuthorName() {
        check("unicode author name becomes \\uXXXX",
                "{\"author\":\"Jos\\u00e9 \\u00dcnal\"}",
                JsonWriter.object()
                        .field("author", "José Ünal")
                        .end()
                        .toString());
    }

    private static void testControlCharacters() {
        check("newline/tab/control escaping",
                "{\"t\":\"line1\\nline2\\ttab\\u0001\"}",
                JsonWriter.object()
                        .field("t", "line1\nline2\ttab\u0001")
                        .end()
                        .toString());
    }

    private static void testNestedArrayInObject() {
        JsonWriter cites = JsonWriter.array().value("P101").value("P104").end();
        check("array nested as field value",
                "{\"id\":\"P103\",\"cites\":[\"P101\",\"P104\"]}",
                JsonWriter.object()
                        .field("id", "P103")
                        .field("cites", cites)
                        .end()
                        .toString());
    }

    private static void testNestedObjectInArray() {
        JsonWriter paper = JsonWriter.object().field("id", "P101").end();
        check("object nested as array element",
                "[{\"id\":\"P101\"}]",
                JsonWriter.array().value(paper).end().toString());
    }

    private static void testDeeplyNestedStructure() {
        JsonWriter inner = JsonWriter.object().field("id", "P101").end();
        JsonWriter row = JsonWriter.object().field("paper", inner).end();
        JsonWriter rows = JsonWriter.array().value(row).end();
        check("three-level nesting",
                "{\"rows\":[{\"paper\":{\"id\":\"P101\"}}]}",
                JsonWriter.object().field("rows", rows).end().toString());
    }

    private static void testEscapeIsPureAscii() {
        String escaped = JsonWriter.escape("café — \u2014 done");
        boolean pureAscii = true;
        for (int i = 0; i < escaped.length(); i++) {
            if (escaped.charAt(i) > 0x7E) {
                pureAscii = false;
                break;
            }
        }
        totalTests++;
        if (pureAscii && escaped.contains("\\u00e9") && escaped.contains("\\u2014")) {
            passedTests++;
            System.out.println("  [PASS] escape() output is pure ASCII");
        } else {
            System.out.println("  [FAIL] escape() output is pure ASCII — got: " + escaped);
        }
    }

    private static void testCommaPlacement() {
        check("commas between fields, none after the last",
                "{\"a\":1,\"b\":2,\"c\":3}",
                JsonWriter.object()
                        .field("a", 1)
                        .field("b", 2)
                        .field("c", 3)
                        .end()
                        .toString());
        check("commas between array values, none after the last",
                "[1,2,3]",
                JsonWriter.array().value(1).value(2).value(3).end().toString());
    }
}
