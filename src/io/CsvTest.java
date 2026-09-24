package io;

import core.DynamicArray;
import core.Graph;
import core.Paper;

import java.io.File;
import java.io.IOException;

/**
 * Test suite for CsvHandler persistence module (Phase 7).
 */
public class CsvTest {

    private static int totalTests = 0;
    private static int passedTests = 0;

    public static void main(String[] args) {
        System.out.println("Running Phase 7 CSV Persistence Test Suite...\n");

        testCsvLineParser();
        testCsvEscaping();
        testSaveAndLoadGraph();
        testCorruptedOrMalformedCsvHandling();

        System.out.println("\n==========================================");
        System.out.println("CSV TEST RESULTS: " + passedTests + " / " + totalTests + " PASSED");
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

    private static void testCsvLineParser() {
        System.out.println("--- CSV Line Parser Tests ---");
        DynamicArray<String> simple = CsvHandler.parseCsvLine("P1,Attention Is All You Need,Vaswani,2017,100");
        assertEquals("Parsed 5 tokens", 5, simple.size());
        assertEquals("Token 0 is P1", "P1", simple.get(0));
        assertEquals("Token 1 is Attention Is All You Need", "Attention Is All You Need", simple.get(1));
        assertEquals("Token 4 is 100", "100", simple.get(4));

        // Quoted tokens with commas
        DynamicArray<String> quoted = CsvHandler.parseCsvLine("P2,\"BERT: Pre-training of Deep Bidirectional Transformers, etc.\",\"Devlin, J.\",2018,50");
        assertEquals("Quoted field parsed 5 tokens", 5, quoted.size());
        assertEquals("Title with embedded comma", "BERT: Pre-training of Deep Bidirectional Transformers, etc.", quoted.get(1));
        assertEquals("Author with embedded comma", "Devlin, J.", quoted.get(2));

        // Quoted tokens with escaped inner quotes
        DynamicArray<String> escaped = CsvHandler.parseCsvLine("P3,\"The \"\"Special\"\" Paper\",Author,2022,5");
        assertEquals("Escaped quotes parsed title", "The \"Special\" Paper", escaped.get(1));
    }

    private static void testCsvEscaping() {
        System.out.println("\n--- CSV Escaping Tests ---");
        assertEquals("Plain string unchanged", "Hello World", CsvHandler.escapeCsv("Hello World"));
        assertEquals("String with comma wrapped in quotes", "\"Hello, World\"", CsvHandler.escapeCsv("Hello, World"));
        assertEquals("String with quote escaped and wrapped", "\"Hello \"\"World\"\"\"", CsvHandler.escapeCsv("Hello \"World\""));
    }

    private static void testSaveAndLoadGraph() {
        System.out.println("\n--- Save and Load Graph End-to-End ---");
        Graph gOriginal = new Graph();
        Paper p1 = new Paper("P1", "Attention Is All You Need", "Vaswani et al.", 2017);
        Paper p2 = new Paper("P2", "BERT: Pre-training", "Devlin et al.", 2018);
        Paper p3 = new Paper("P3", "GPT-3: Language Models", "Brown et al.", 2020);

        gOriginal.addVertex(p1);
        gOriginal.addVertex(p2);
        gOriginal.addVertex(p3);

        // P2 cites P1, P3 cites P1, P3 cites P2
        gOriginal.addCitation("P2", "P1");
        gOriginal.addCitation("P3", "P1");
        gOriginal.addCitation("P3", "P2");

        String tempFilePath = "temp_test_graph.csv";
        File tempFile = new File(tempFilePath);
        try {
            CsvHandler.save(gOriginal, tempFilePath);
            assertTrue("CSV file created successfully", tempFile.exists());

            Graph gLoaded = CsvHandler.load(tempFilePath);
            assertEquals("Loaded graph vertex count is 3", 3, gLoaded.vertexCount());
            assertEquals("Loaded graph edge count is 3", 3, gLoaded.edgeCount());

            // Check papers loaded
            int idx1 = gLoaded.findIndexById("P1");
            int idx2 = gLoaded.findIndexById("P2");
            int idx3 = gLoaded.findIndexById("P3");

            assertTrue("P1 exists in loaded graph", idx1 != -1);
            assertTrue("P2 exists in loaded graph", idx2 != -1);
            assertTrue("P3 exists in loaded graph", idx3 != -1);

            Paper loadedP1 = gLoaded.getPaper(idx1);
            assertEquals("P1 title matches", "Attention Is All You Need", loadedP1.getTitle());
            assertEquals("P1 citationCount is 2 (cited by P2 and P3)", 2, loadedP1.getCitationCount());

            Paper loadedP2 = gLoaded.getPaper(idx2);
            assertEquals("P2 citationCount is 1 (cited by P3)", 1, loadedP2.getCitationCount());

            Paper loadedP3 = gLoaded.getPaper(idx3);
            assertEquals("P3 citationCount is 0", 0, loadedP3.getCitationCount());

            // Check citations
            assertTrue("P2 cites P1", gLoaded.getNeighbors(idx2).contains(idx1));
            assertTrue("P3 cites P1", gLoaded.getNeighbors(idx3).contains(idx1));
            assertTrue("P3 cites P2", gLoaded.getNeighbors(idx3).contains(idx2));

        } catch (IOException e) {
            throw new AssertionError("Save/Load threw IOException: " + e.getMessage());
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    private static void testCorruptedOrMalformedCsvHandling() {
        System.out.println("\n--- Malformed CSV Rows Handling ---");
        String tempFilePath = "temp_malformed_test.csv";
        File tempFile = new File(tempFilePath);
        try {
            // Write malformed CSV file manually
            java.io.BufferedWriter bw = new java.io.BufferedWriter(new java.io.FileWriter(tempFile));
            bw.write("# PAPERS\n");
            bw.write("id,title,author,year\n");
            bw.write("M1,Good Paper,Good Author,2021\n");
            bw.write("bad_row_with_too_few_columns\n");
            bw.write("M2,Another Paper,Author Two,not_a_valid_year\n");
            bw.write("# CITATIONS\n");
            bw.write("citingPaperId,citedPaperId\n");
            bw.write("M2,M1\n");
            bw.write("NON_EXISTENT,M1\n"); // invalid citing id
            bw.close();

            Graph loaded = CsvHandler.load(tempFilePath);
            assertEquals("Loaded valid papers (M1, M2)", 2, loaded.vertexCount());
            int m1Idx = loaded.findIndexById("M1");
            assertTrue("M1 exists", m1Idx != -1);
            assertEquals("M1 citation count is 1 from M2", 1, loaded.getPaper(m1Idx).getCitationCount());
            assertEquals("Valid citation edge count is 1", 1, loaded.edgeCount());

        } catch (IOException e) {
            throw new AssertionError("Malformed CSV load threw IOException: " + e.getMessage());
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }
}
