package algo;

import core.DynamicArray;
import core.HashTable;

/**
 * Phase 4 test suite: KMPMatcher, RabinKarpMatcher, FuzzyMatcher.
 * Self-contained runner in the same style as DataStructuresTest / GraphTest /
 * HashTableTest -- no JUnit dependency, plain main() with pass/fail tally.
 */
public class StringMatchingTest {
    private static int pass = 0;
    private static int fail = 0;

    private static void check(String name, boolean condition) {
        if (condition) {
            pass++;
            System.out.println("[PASS] " + name);
        } else {
            fail++;
            System.out.println("[FAIL] " + name);
        }
    }

    public static void main(String[] args) {
        System.out.println("Running Phase 4 String Matching Test Suite...\n");

        testKmpLpsArray();
        testKmpSearch();
        testRabinKarpSearch();
        testRabinKarpSearchMany();
        testEditDistance();
        testFuzzySearch();

        System.out.println("\n==========================================");
        System.out.println("STRING MATCHING TEST RESULTS: " + pass + " / " + (pass + fail) + " PASSED");
        System.out.println("==========================================");

        if (fail > 0) {
            System.exit(1);
        }
    }

    // ---------------------------------------------------------------
    // KMP: LPS array -- the highest-risk piece of this entire phase
    // ---------------------------------------------------------------
    private static void testKmpLpsArray() {
        System.out.println("--- KMP LPS Array Tests (hand-traced) ---");

        // Hand-traced in KMPMatcher's javadoc: "ABABC" -> [0,0,1,2,0]
        int[] lps1 = KMPMatcher.buildLpsArray("ABABC");
        check("LPS('ABABC') == [0,0,1,2,0]",
              lps1[0] == 0 && lps1[1] == 0 && lps1[2] == 1 && lps1[3] == 2 && lps1[4] == 0);

        // "AAAA" -> every position after the first extends the run: [0,1,2,3]
        int[] lps2 = KMPMatcher.buildLpsArray("AAAA");
        check("LPS('AAAA') == [0,1,2,3]",
              lps2[0] == 0 && lps2[1] == 1 && lps2[2] == 2 && lps2[3] == 3);

        // "AABAACAABAA" is the textbook worked example, lps = [0,1,0,1,2,0,1,2,3,4,5]
        int[] lps3 = KMPMatcher.buildLpsArray("AABAACAABAA");
        int[] expected3 = {0, 1, 0, 1, 2, 0, 1, 2, 3, 4, 5};
        boolean match3 = true;
        for (int i = 0; i < expected3.length; i++) {
            if (lps3[i] != expected3[i]) match3 = false;
        }
        check("LPS('AABAACAABAA') matches textbook expected array", match3);

        // single character has no proper prefix
        int[] lps4 = KMPMatcher.buildLpsArray("X");
        check("LPS single char == [0]", lps4.length == 1 && lps4[0] == 0);

        // empty pattern -> empty array, must not throw
        int[] lps5 = KMPMatcher.buildLpsArray("");
        check("LPS empty pattern == empty array", lps5.length == 0);
    }

    // ---------------------------------------------------------------
    // KMP: search correctness
    // ---------------------------------------------------------------
    private static void testKmpSearch() {
        System.out.println("\n--- KMP Search Tests ---");

        DynamicArray<Integer> r1 = KMPMatcher.search("ABABABABC", "ABABC");
        check("KMP finds single match at correct index", r1.size() == 1 && r1.get(0) == 4);

        // overlapping matches: "AAAA" contains "AA" starting at 0, 1, 2
        DynamicArray<Integer> r2 = KMPMatcher.search("AAAA", "AA");
        check("KMP finds all overlapping matches",
              r2.size() == 3 && r2.get(0) == 0 && r2.get(1) == 1 && r2.get(2) == 2);

        DynamicArray<Integer> r3 = KMPMatcher.search("Deep Learning for Vision", "xyz");
        check("KMP returns empty for no match", r3.isEmpty());

        DynamicArray<Integer> r4 = KMPMatcher.search("short", "muchlongerpattern");
        check("KMP returns empty when pattern longer than text", r4.isEmpty());

        DynamicArray<Integer> r5 = KMPMatcher.search("", "x");
        check("KMP handles empty text without throwing", r5.isEmpty());

        DynamicArray<Integer> r6 = KMPMatcher.search("Neural Networks for NLP", "Neural Networks for NLP");
        check("KMP matches pattern equal to entire text", r6.size() == 1 && r6.get(0) == 0);

        check("KMP contains() convenience method works",
              KMPMatcher.contains("Graph Attention Networks", "Attention"));
        check("KMP contains() correctly returns false",
              !KMPMatcher.contains("Graph Attention Networks", "Transformer"));
    }

    // ---------------------------------------------------------------
    // Rabin-Karp: search correctness + hash-collision verification safety
    // ---------------------------------------------------------------
    private static void testRabinKarpSearch() {
        System.out.println("\n--- Rabin-Karp Search Tests ---");

        DynamicArray<Integer> r1 = RabinKarpMatcher.search("ABABABABC", "ABABC");
        check("Rabin-Karp finds single match at correct index", r1.size() == 1 && r1.get(0) == 4);

        DynamicArray<Integer> r2 = RabinKarpMatcher.search("AAAA", "AA");
        check("Rabin-Karp finds all overlapping matches",
              r2.size() == 3 && r2.get(0) == 0 && r2.get(1) == 1 && r2.get(2) == 2);

        DynamicArray<Integer> r3 = RabinKarpMatcher.search("Deep Learning for Vision", "xyz");
        check("Rabin-Karp returns empty for no match", r3.isEmpty());

        // Cross-verify against KMP on a longer, denser text to catch any
        // hash-collision handling bug that only a false-positive would reveal
        String text = "abababababababababababcababab";
        String pattern = "abababc";
        DynamicArray<Integer> kmpResult = KMPMatcher.search(text, pattern);
        DynamicArray<Integer> rkResult = RabinKarpMatcher.search(text, pattern);
        boolean sameResults = kmpResult.size() == rkResult.size();
        if (sameResults) {
            for (int i = 0; i < kmpResult.size(); i++) {
                if (!kmpResult.get(i).equals(rkResult.get(i))) {
                    sameResults = false;
                    break;
                }
            }
        }
        check("Rabin-Karp results match KMP exactly on dense repetitive text", sameResults);

        check("Rabin-Karp contains() convenience method works",
              RabinKarpMatcher.contains("Graph Attention Networks", "Attention"));
    }

    private static void testRabinKarpSearchMany() {
        System.out.println("\n--- Rabin-Karp Multi-Title Scan Tests ---");

        DynamicArray<String> titles = new DynamicArray<>();
        titles.add("Attention Is All You Need");
        titles.add("Deep Residual Learning for Image Recognition");
        titles.add("Graph Attention Networks");
        titles.add("BERT: Pre-training of Deep Bidirectional Transformers");

        DynamicArray<Integer> hits = RabinKarpMatcher.searchMany(titles, "Attention");
        check("searchMany finds titles 0 and 2 containing 'Attention'",
              hits.size() == 2 && hits.get(0) == 0 && hits.get(1) == 2);

        DynamicArray<Integer> noHits = RabinKarpMatcher.searchMany(titles, "Reinforcement");
        check("searchMany returns empty when no title matches", noHits.isEmpty());
    }

    // ---------------------------------------------------------------
    // Wagner-Fischer edit distance
    // ---------------------------------------------------------------
    private static void testEditDistance() {
        System.out.println("\n--- Wagner-Fischer Edit Distance Tests ---");

        // the canonical textbook example
        check("editDistance('kitten','sitting') == 3",
              FuzzyMatcher.editDistance("kitten", "sitting") == 3);

        check("editDistance identical strings == 0",
              FuzzyMatcher.editDistance("Dijkstra", "Dijkstra") == 0);

        check("editDistance one substitution == 1",
              FuzzyMatcher.editDistance("Dijkstra", "DijkstrA") == 1);

        check("editDistance one insertion == 1",
              FuzzyMatcher.editDistance("Dijkstra", "Dijkstraa") == 1);

        check("editDistance one deletion == 1",
              FuzzyMatcher.editDistance("Dijkstra", "Dijkstr") == 1);

        check("editDistance against empty string == length of other string",
              FuzzyMatcher.editDistance("", "hello") == 5
              && FuzzyMatcher.editDistance("hello", "") == 5);

        check("editDistance is symmetric",
              FuzzyMatcher.editDistance("kitten", "sitting")
              == FuzzyMatcher.editDistance("sitting", "kitten"));

        check("editDistance realistic typo ('Dijkstar' vs 'Dijkstra') == 2",
              FuzzyMatcher.editDistance("Dijkstar", "Dijkstra") == 2);
    }

    // ---------------------------------------------------------------
    // Fuzzy search: pre-filter correctness
    // ---------------------------------------------------------------
    private static void testFuzzySearch() {
        System.out.println("\n--- Fuzzy Search Pre-Filter Tests ---");

        DynamicArray<String> titles = new DynamicArray<>();
        titles.add("Dijkstra Shortest Path Algorithm");   // index 0
        titles.add("Attention Is All You Need");           // index 1
        titles.add("Deep Residual Learning");               // index 2
        titles.add("Dinic Maximum Flow Algorithm");          // index 3

        HashTable<DynamicArray<Integer>> index = FuzzyMatcher.buildFirstLetterIndex(titles);
        DynamicArray<Integer> dBucket = index.get("d");
        check("first-letter index buckets all 3 'D' titles together",
              dBucket != null && dBucket.size() == 3);

        // typo in the middle of the word, first letter still correct
        DynamicArray<Integer> results = FuzzyMatcher.fuzzySearch(titles, "Dijkstar Shortest Path Algorithm", 3);
        boolean found0 = false;
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i) == 0) found0 = true;
        }
        check("fuzzySearch finds typo'd title within same first-letter bucket", found0);

        // length-difference pre-filter: wildly different length must be excluded
        // even if it happens to share a first letter (correctness, not just speed)
        DynamicArray<Integer> tightResults = FuzzyMatcher.fuzzySearch(titles, "Dijkstra Shortest Path Algorithm", 0);
        check("fuzzySearch with maxDistance=0 finds only the exact match",
              tightResults.size() == 1 && tightResults.get(0) == 0);

        // documented limitation: a first-letter typo will NOT be found, since
        // the pre-filter buckets strictly by first letter
        DynamicArray<Integer> firstLetterTypo = FuzzyMatcher.fuzzySearch(titles, "Xijkstra Shortest Path Algorithm", 3);
        check("fuzzySearch documented limitation: first-letter typo yields no match",
              firstLetterTypo.isEmpty());

        DynamicArray<Integer> noMatch = FuzzyMatcher.fuzzySearch(titles, "Zzzzzzzzz", 2);
        check("fuzzySearch returns empty for unrelated query with no bucket", noMatch.isEmpty());
    }
}
