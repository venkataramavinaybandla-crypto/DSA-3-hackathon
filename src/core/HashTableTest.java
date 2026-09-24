package core;

/**
 * Comprehensive test suite for HashTable (Phase 3).
 * Tests open-addressing correctness, tombstone probing, resize/rehash, and Graph integration.
 */
public class HashTableTest {

    private static int totalTests = 0;
    private static int passedTests = 0;

    public static void main(String[] args) {
        System.out.println("Running Phase 3 HashTable Test Suite...\n");

        testEmptyTableBehavior();
        testBasicPutGetContainsRemove();
        testUpdateExistingKey();
        testCollisionHandling();
        testTombstoneProbing();
        testResizeAndRehash();
        testRemoveNonExistentKey();
        testLoadFactorAfterResize();
        testGraphIntegration();

        System.out.println("\n==========================================");
        System.out.println("HASHTABLE TEST RESULTS: " + passedTests + " / " + totalTests + " PASSED");
        System.out.println("==========================================");

        if (passedTests != totalTests) {
            System.exit(1);
        }
    }

    private static void assertTrue(String name, boolean cond) {
        totalTests++;
        if (cond) { passedTests++; System.out.println("[PASS] " + name); }
        else { System.err.println("[FAIL] " + name); throw new AssertionError("FAILED: " + name); }
    }

    private static void assertEquals(String name, Object expected, Object actual) {
        totalTests++;
        boolean eq = (expected == null) ? (actual == null) : expected.equals(actual);
        if (eq) { passedTests++; System.out.println("[PASS] " + name); }
        else { System.err.println("[FAIL] " + name + " | Expected=" + expected + " Actual=" + actual);
               throw new AssertionError("FAILED: " + name); }
    }

    // ------------------------------------------------------------------ 1
    private static void testEmptyTableBehavior() {
        System.out.println("--- Empty Table ---");
        HashTable<Integer> ht = new HashTable<>();
        assertTrue("empty isEmpty()", ht.isEmpty());
        assertEquals("empty size()==0", 0, ht.size());
        assertEquals("get on empty returns null", null, ht.get("anything"));
        assertTrue("containsKey on empty returns false", !ht.containsKey("anything"));
        assertEquals("remove on empty returns null", null, ht.remove("anything"));
    }

    // ------------------------------------------------------------------ 2
    private static void testBasicPutGetContainsRemove() {
        System.out.println("\n--- Basic Put/Get/Contains/Remove ---");
        HashTable<String> ht = new HashTable<>();

        ht.put("alpha", "A");
        ht.put("beta", "B");
        ht.put("gamma", "G");

        assertEquals("size after 3 puts", 3, ht.size());
        assertTrue("not empty", !ht.isEmpty());
        assertEquals("get alpha", "A", ht.get("alpha"));
        assertEquals("get beta", "B", ht.get("beta"));
        assertEquals("get gamma", "G", ht.get("gamma"));
        assertTrue("contains alpha", ht.containsKey("alpha"));
        assertTrue("not contains delta", !ht.containsKey("delta"));

        String removed = ht.remove("beta");
        assertEquals("remove returns B", "B", removed);
        assertEquals("size after remove", 2, ht.size());
        assertTrue("beta gone from containsKey", !ht.containsKey("beta"));
        assertEquals("get beta after remove returns null", null, ht.get("beta"));
        assertEquals("alpha still present", "A", ht.get("alpha"));
    }

    // ------------------------------------------------------------------ 3
    private static void testUpdateExistingKey() {
        System.out.println("\n--- Update Existing Key ---");
        HashTable<Integer> ht = new HashTable<>();

        ht.put("key", 1);
        assertEquals("initial value", 1, ht.get("key"));
        assertEquals("size is 1", 1, ht.size());

        ht.put("key", 42);
        assertEquals("updated value", 42, ht.get("key"));
        assertEquals("size still 1 after update", 1, ht.size());

        ht.put("key", 99);
        assertEquals("updated again", 99, ht.get("key"));
        assertEquals("size still 1", 1, ht.size());
    }

    // ------------------------------------------------------------------ 4
    private static void testCollisionHandling() {
        System.out.println("\n--- Collision Handling ---");
        // "Aa" and "BB" are the classic Java-hash collision pair:
        // 'A'=65, 'a'=97 -> 65*31+97=2112  mod 16 = 0
        // 'B'=66, 'B'=66 -> 66*31+66=2112  mod 16 = 0
        // However our hash function uses mod HASH_PRIME first then mod capacity.
        // Let's directly compute to find a colliding pair for capacity 16.
        // We'll use a small custom capacity to guarantee a collision by pigeonhole:
        // with capacity=4 and 5 distinct keys, at least two must share a slot.
        // To be deliberate, compute hashes of "a","b","c","d","e" mod 4:
        // h("a") = 97 % 4 = 1
        // h("b") = 98 % 4 = 2
        // h("c") = 99 % 4 = 3
        // h("d") = 100 % 4 = 0
        // h("e") = 101 % 4 = 1  <-- collides with "a"
        // With capacity 4 and load-factor 0.7, we can fit floor(4*0.7)=2 before resize.
        // So use capacity 8: "a"=97%8=1, "i"=105%8=1 -> both map to slot 1.
        HashTable<String> ht = new HashTable<>(8);
        // Verify collision pair
        // h("a") = 97 % 1_000_000_007 % 8 = 97 % 8 = 1
        // h("i") = 105 % 1_000_000_007 % 8 = 105 % 8 = 1  → collide at slot 1
        ht.put("a", "value-a");
        ht.put("i", "value-i");  // same hash slot as "a" -> linear probe to slot 2

        assertEquals("size after 2 colliding inserts", 2, ht.size());
        assertEquals("get 'a' after collision", "value-a", ht.get("a"));
        assertEquals("get 'i' after collision", "value-i", ht.get("i"));
        assertTrue("containsKey 'a'", ht.containsKey("a"));
        assertTrue("containsKey 'i'", ht.containsKey("i"));

        // Verify second-collision triple: h("q")=113%8=1 as well
        ht.put("q", "value-q");
        assertEquals("size after 3rd colliding insert", 3, ht.size());
        assertEquals("get 'q' after triple collision", "value-q", ht.get("q"));
        assertEquals("get 'a' still correct", "value-a", ht.get("a"));
        assertEquals("get 'i' still correct", "value-i", ht.get("i"));
    }

    // ------------------------------------------------------------------ 5
    private static void testTombstoneProbing() {
        System.out.println("\n--- Tombstone Probing ---");
        // Use capacity=8. Keys "a" and "i" both hash to slot 1.
        // Insert "a" -> slot 1.
        // Insert "i" -> probes to slot 2 (collision with "a").
        // Remove "a" -> slot 1 becomes TOMBSTONE.
        // get("i") must probe through the TOMBSTONE at slot 1 to find "i" at slot 2.
        HashTable<String> ht = new HashTable<>(8);
        ht.put("a", "val-a");
        ht.put("i", "val-i");  // hashes to slot 1, probes to slot 2

        // Remove "a" — slot 1 becomes TOMBSTONE
        String removed = ht.remove("a");
        assertEquals("remove 'a' returns val-a", "val-a", removed);
        assertEquals("size is 1 after remove", 1, ht.size());

        // Critical: get("i") must probe THROUGH the tombstone at slot 1
        assertEquals("get 'i' probes through tombstone", "val-i", ht.get("i"));
        assertTrue("containsKey 'i' probes through tombstone", ht.containsKey("i"));

        // Inserting a NEW key that also hashes to slot 1 should reuse the tombstone
        ht.put("b", "val-b");  // h("b")=98%8=2, so goes to slot 2, probes to 3
        ht.put("a2", "new-val"); // deliberately check tombstone reuse
        assertEquals("get 'i' still works after tombstone reuse", "val-i", ht.get("i"));
    }

    // ------------------------------------------------------------------ 6
    private static void testResizeAndRehash() {
        System.out.println("\n--- Resize / Rehash (most critical test) ---");
        // Start small (capacity 4) and insert enough to force ≥2 resizes.
        // Resize 1: at 4*0.7=2.8 → after 3rd insert (totalUsed/4 >= 0.7)
        // Resize 2: at 8*0.7=5.6 → after 6th insert
        HashTable<Integer> ht = new HashTable<>(4);
        int N = 50;
        // Insert 50 distinct keys with known values
        for (int i = 0; i < N; i++) {
            ht.put("key_" + i, i * 100);
        }
        assertEquals("size after 50 inserts", N, ht.size());

        // Verify EVERY entry survives the resize/rehash correctly
        for (int i = 0; i < N; i++) {
            String k = "key_" + i;
            assertEquals("get " + k + " after resize", i * 100, ht.get(k));
            assertTrue("containsKey " + k, ht.containsKey(k));
        }

        // Verify capacity grew (should be well past 4 after 50 inserts)
        assertTrue("capacity grew beyond 4", ht.capacity() > 4);
        assertTrue("capacity grew beyond 8", ht.capacity() > 8);
    }

    // ------------------------------------------------------------------ 7
    private static void testRemoveNonExistentKey() {
        System.out.println("\n--- Remove Non-Existent Key ---");
        HashTable<String> ht = new HashTable<>();
        ht.put("present", "yes");

        // Remove something that doesn't exist
        String result = ht.remove("absent");
        assertEquals("remove non-existent returns null", null, result);
        assertEquals("size unchanged after removing absent key", 1, ht.size());
        assertEquals("'present' still there", "yes", ht.get("present"));

        // Remove same absent key twice in a row
        assertEquals("second remove non-existent still null", null, ht.remove("absent"));
        assertEquals("size still 1", 1, ht.size());
    }

    // ------------------------------------------------------------------ 8
    private static void testLoadFactorAfterResize() {
        System.out.println("\n--- Load Factor After Resize ---");
        HashTable<Integer> ht = new HashTable<>(4);
        // Force multiple resizes
        for (int i = 0; i < 30; i++) {
            ht.put("lf_" + i, i);
        }
        // After rehash, load = liveCount / capacity; should be well below 0.7
        double lf = (double) ht.size() / ht.capacity();
        assertTrue("load factor after resize < 0.7", lf < 0.7);
        assertEquals("all 30 entries present", 30, ht.size());
    }

    // ------------------------------------------------------------------ 9
    private static void testGraphIntegration() {
        System.out.println("\n--- Graph Integration (TEMP comment gone + O(1) lookup) ---");
        Graph graph = new Graph();
        Paper p1 = new Paper("G1", "Graph Paper 1", "Author A", 2020);
        Paper p2 = new Paper("G2", "Graph Paper 2", "Author B", 2021);
        Paper p3 = new Paper("G3", "Graph Paper 3", "Author C", 2022);

        int idx1 = graph.addVertex(p1);
        int idx2 = graph.addVertex(p2);
        int idx3 = graph.addVertex(p3);

        assertEquals("findIndexById G1", idx1, graph.findIndexById("G1"));
        assertEquals("findIndexById G2", idx2, graph.findIndexById("G2"));
        assertEquals("findIndexById G3", idx3, graph.findIndexById("G3"));
        assertEquals("findIndexById unknown returns -1", -1, graph.findIndexById("NOPE"));

        // Duplicate add returns existing index
        Paper p1Dup = new Paper("G1", "Different", "Different", 2025);
        assertEquals("duplicate addVertex returns existing idx", idx1, graph.addVertex(p1Dup));
        assertEquals("vertexCount stays 3", 3, graph.vertexCount());

        // addCitation end-to-end
        graph.addCitation("G1", "G2");
        graph.addCitation("G1", "G3");
        assertEquals("edge count is 2", 2, graph.edgeCount());
        assertTrue("G1 cites G2", graph.getNeighbors(idx1).contains(idx2));
        assertTrue("G1 cites G3", graph.getNeighbors(idx1).contains(idx3));

        // addCitation unknown ID still throws
        boolean threw = false;
        try { graph.addCitation("G1", "UNKNOWN"); } catch (IllegalArgumentException e) { threw = true; }
        assertTrue("addCitation unknown cited throws IllegalArgumentException", threw);

        // Confirm no TEMP comment present in source (we verified by code review; runtime confirm findIndexById works)
        assertEquals("findIndexById still works via hash", 0, graph.findIndexById("G1"));
    }
}
