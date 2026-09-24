package algo;

import core.DynamicArray;
import core.Paper;

/**
 * Test suite for stable MergeSort algorithm (Phase 6).
 */
public class MergeSortTest {

    private static int totalTests = 0;
    private static int passedTests = 0;

    public static void main(String[] args) {
        System.out.println("Running Phase 6 MergeSort Test Suite...\n");

        testEmptyAndSingle();
        testAlreadySortedDescending();
        testAscendingToDescending();
        testStabilityWithTies();
        testSortedCopyNonDestructive();
        testLargerDataset();

        System.out.println("\n==========================================");
        System.out.println("MERGESORT TEST RESULTS: " + passedTests + " / " + totalTests + " PASSED");
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

    private static void testEmptyAndSingle() {
        System.out.println("--- Empty and Single Element Tests ---");
        DynamicArray<Paper> empty = new DynamicArray<>();
        MergeSort.sort(empty);
        assertEquals("Empty array size remains 0", 0, empty.size());

        DynamicArray<Paper> single = new DynamicArray<>();
        Paper p1 = new Paper("P1", "Title", "Author", 2020, 10);
        single.add(p1);
        MergeSort.sort(single);
        assertEquals("Single element array size is 1", 1, single.size());
        assertEquals("Single element remains p1", p1, single.get(0));
    }

    private static void testAlreadySortedDescending() {
        System.out.println("\n--- Already Sorted Array ---");
        DynamicArray<Paper> list = new DynamicArray<>();
        list.add(new Paper("P1", "T1", "A1", 2020, 50));
        list.add(new Paper("P2", "T2", "A2", 2020, 30));
        list.add(new Paper("P3", "T3", "A3", 2020, 10));

        MergeSort.sort(list);
        assertEquals("First paper citation is 50", 50, list.get(0).getCitationCount());
        assertEquals("Second paper citation is 30", 30, list.get(1).getCitationCount());
        assertEquals("Third paper citation is 10", 10, list.get(2).getCitationCount());
    }

    private static void testAscendingToDescending() {
        System.out.println("\n--- Ascending to Descending Sort ---");
        DynamicArray<Paper> list = new DynamicArray<>();
        list.add(new Paper("P1", "T1", "A1", 2020, 5));
        list.add(new Paper("P2", "T2", "A2", 2020, 15));
        list.add(new Paper("P3", "T3", "A3", 2020, 25));
        list.add(new Paper("P4", "T4", "A4", 2020, 35));

        MergeSort.sort(list);
        assertEquals("Top paper citation is 35", 35, list.get(0).getCitationCount());
        assertEquals("Top paper ID is P4", "P4", list.get(0).getId());
        assertEquals("Second paper citation is 25", 25, list.get(1).getCitationCount());
        assertEquals("Third paper citation is 15", 15, list.get(2).getCitationCount());
        assertEquals("Fourth paper citation is 5", 5, list.get(3).getCitationCount());
    }

    private static void testStabilityWithTies() {
        System.out.println("\n--- Stability Verification (Ties preserve relative order) ---");
        DynamicArray<Paper> list = new DynamicArray<>();
        // All papers with citation count 20, but distinct IDs and inserted in sequence
        list.add(new Paper("Tie-1", "First", "Author", 2018, 20));
        list.add(new Paper("High", "Top", "Author", 2019, 100));
        list.add(new Paper("Tie-2", "Second", "Author", 2020, 20));
        list.add(new Paper("Low", "Bottom", "Author", 2021, 5));
        list.add(new Paper("Tie-3", "Third", "Author", 2022, 20));
        list.add(new Paper("Tie-4", "Fourth", "Author", 2023, 20));

        MergeSort.sort(list);

        assertEquals("First element is High (100)", "High", list.get(0).getId());
        // Check exact stable sequence for the tied papers: Tie-1, Tie-2, Tie-3, Tie-4
        assertEquals("Tied index 1 is Tie-1", "Tie-1", list.get(1).getId());
        assertEquals("Tied index 2 is Tie-2", "Tie-2", list.get(2).getId());
        assertEquals("Tied index 3 is Tie-3", "Tie-3", list.get(3).getId());
        assertEquals("Tied index 4 is Tie-4", "Tie-4", list.get(4).getId());
        assertEquals("Last element is Low (5)", "Low", list.get(5).getId());
    }

    private static void testSortedCopyNonDestructive() {
        System.out.println("\n--- Non-Destructive sortedCopy Test ---");
        DynamicArray<Paper> original = new DynamicArray<>();
        original.add(new Paper("A", "Title A", "Auth", 2020, 10));
        original.add(new Paper("B", "Title B", "Auth", 2020, 40));
        original.add(new Paper("C", "Title C", "Auth", 2020, 20));

        DynamicArray<Paper> sorted = MergeSort.sortedCopy(original);

        // Verify sorted copy is descending
        assertEquals("Sorted[0] is B (40)", "B", sorted.get(0).getId());
        assertEquals("Sorted[1] is C (20)", "C", sorted.get(1).getId());
        assertEquals("Sorted[2] is A (10)", "A", sorted.get(2).getId());

        // Verify original is untouched
        assertEquals("Original[0] is still A", "A", original.get(0).getId());
        assertEquals("Original[1] is still B", "B", original.get(1).getId());
        assertEquals("Original[2] is still C", "C", original.get(2).getId());
    }

    private static void testLargerDataset() {
        System.out.println("\n--- Larger Dataset Sort Correctness ---");
        DynamicArray<Paper> list = new DynamicArray<>();
        int[] counts = { 42, 17, 88, 3, 55, 99, 12, 67, 34, 88, 5, 23, 76, 55, 1 };

        for (int i = 0; i < counts.length; i++) {
            list.add(new Paper("P" + i, "Title " + i, "Author", 2020, counts[i]));
        }

        MergeSort.sort(list);

        assertEquals("Sorted size matches original", counts.length, list.size());
        for (int i = 0; i < list.size() - 1; i++) {
            assertTrue("Descending invariant at index " + i,
                    list.get(i).getCitationCount() >= list.get(i + 1).getCitationCount());
        }

        assertEquals("Highest citation is 99", 99, list.get(0).getCitationCount());
        assertEquals("Lowest citation is 1", 1, list.get(list.size() - 1).getCitationCount());
    }
}
