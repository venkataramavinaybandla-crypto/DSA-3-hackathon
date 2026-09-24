package algo;

import core.DynamicArray;
import core.HashTable;

/**
 * Fuzzy (typo-tolerant) string matching via Wagner-Fischer edit distance.
 * <p>
 * Naive edit distance is O(m*n) time AND space per comparison. Run
 * unfiltered against an entire corpus of paper titles, that becomes the
 * system's bottleneck. Two mitigations are applied here:
 * <ol>
 *   <li><b>Space optimization:</b> the DP only ever needs the previous row
 *       to compute the current row, so this implementation uses two
 *       length-(m+1) arrays instead of a full (n+1) x (m+1) matrix.</li>
 *   <li><b>Pre-filtering:</b> {@link #fuzzySearch} never runs the DP against
 *       the whole corpus. It first buckets candidates by first-letter (via
 *       {@link core.HashTable}) and discards any candidate whose length
 *       differs from the query by more than {@code maxDistance} -- a valid
 *       filter because edit distance is always &gt;= the length difference
 *       of the two strings, so a candidate failing this check cannot
 *       possibly be within the requested distance.</li>
 * </ol>
 */
public final class FuzzyMatcher {

    private FuzzyMatcher() {
        // static utility class, no instances
    }

    /**
     * Computes the Levenshtein (edit) distance between two strings: the
     * minimum number of single-character insertions, deletions, or
     * substitutions needed to turn {@code a} into {@code b}.
     * <p>
     * Space-optimized: O(min(n,m)) extra space via two rolling rows,
     * O(n*m) time (irreducible for the classic DP formulation).
     */
    public static int editDistance(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";

        int n = a.length();
        int m = b.length();

        if (n == 0) return m;
        if (m == 0) return n;

        // Ensure 'b' (the column dimension) is the shorter string, to
        // minimize the row width and thus memory usage.
        if (m > n) {
            String tmp = a;
            a = b;
            b = tmp;
            int t = n;
            n = m;
            m = t;
        }

        int[] previousRow = new int[m + 1];
        int[] currentRow = new int[m + 1];

        for (int j = 0; j <= m; j++) {
            previousRow[j] = j; // distance from empty prefix of a to prefix of b of length j
        }

        for (int i = 1; i <= n; i++) {
            currentRow[0] = i; // distance from prefix of a of length i to empty prefix of b
            char aChar = a.charAt(i - 1);

            for (int j = 1; j <= m; j++) {
                char bChar = b.charAt(j - 1);
                if (aChar == bChar) {
                    currentRow[j] = previousRow[j - 1];
                } else {
                    int substitution = previousRow[j - 1] + 1;
                    int insertion = currentRow[j - 1] + 1;
                    int deletion = previousRow[j] + 1;
                    currentRow[j] = Math.min(substitution, Math.min(insertion, deletion));
                }
            }

            // swap rows: current becomes previous for the next iteration
            int[] swap = previousRow;
            previousRow = currentRow;
            currentRow = swap;
        }

        // after the final swap, the answer sits in previousRow[m]
        return previousRow[m];
    }

    /**
     * Builds a first-letter bucket index over a set of titles, for use as a
     * repeated pre-filter across many {@link #fuzzySearch} calls without
     * rebuilding the index each time.
     *
     * @param titles the corpus of titles (e.g. all paper titles), by index
     * @return a HashTable mapping lowercase first-letter (as a String key)
     *         to a DynamicArray of indices into {@code titles}
     */
    public static HashTable<DynamicArray<Integer>> buildFirstLetterIndex(DynamicArray<String> titles) {
        HashTable<DynamicArray<Integer>> index = new HashTable<>();
        if (titles == null) {
            return index;
        }
        for (int i = 0; i < titles.size(); i++) {
            String title = titles.get(i);
            if (title == null || title.isEmpty()) {
                continue;
            }
            String key = String.valueOf(Character.toLowerCase(title.charAt(0)));
            DynamicArray<Integer> bucket = index.get(key);
            if (bucket == null) {
                bucket = new DynamicArray<>();
                index.put(key, bucket);
            }
            bucket.add(i);
        }
        return index;
    }

    /**
     * Finds every title within {@code maxDistance} edits of {@code query},
     * using the first-letter pre-filter to avoid running the DP against the
     * entire corpus.
     * <p>
     * Falls back to also checking titles in the (query's first letter)
     * bucket ONLY -- this means a typo in the very first character of the
     * query (e.g. "pdijkstra" vs "dijkstra") will miss a same-bucket
     * candidate. That tradeoff is intentional: gating strictly by first
     * letter keeps the pre-filter O(1) instead of degrading toward a full
     * scan, and first-letter typos are comparatively rare in practice for
     * this use case (title/author search). Documented here so it is a
     * deliberate, known limitation, not a silent one.
     *
     * @param titles      the corpus of titles, by index
     * @param query       the search query
     * @param maxDistance maximum edit distance to accept as a fuzzy match
     * @return indices into {@code titles} of every match within maxDistance,
     *         sorted by ascending edit distance is NOT guaranteed here --
     *         ranking is Phase 6's job (sorting), this method only filters
     */
    public static DynamicArray<Integer> fuzzySearch(DynamicArray<String> titles, String query, int maxDistance) {
        DynamicArray<Integer> results = new DynamicArray<>();
        if (titles == null || query == null || query.isEmpty() || maxDistance < 0) {
            return results;
        }

        HashTable<DynamicArray<Integer>> index = buildFirstLetterIndex(titles);
        String key = String.valueOf(Character.toLowerCase(query.charAt(0)));
        DynamicArray<Integer> candidates = index.get(key);
        if (candidates == null) {
            return results;
        }

        int queryLen = query.length();
        for (int c = 0; c < candidates.size(); c++) {
            int idx = candidates.get(c);
            String title = titles.get(idx);

            // Length-difference pre-filter: edit distance >= |len(a) - len(b)|,
            // so skip any candidate that couldn't possibly qualify -- this
            // avoids running the O(n*m) DP on obviously-too-different titles.
            if (Math.abs(title.length() - queryLen) > maxDistance) {
                continue;
            }

            int distance = editDistance(query, title);
            if (distance <= maxDistance) {
                results.add(idx);
            }
        }
        return results;
    }
}
