package algo;

import core.DynamicArray;

/**
 * Knuth-Morris-Pratt exact string matching, implemented from scratch.
 * <p>
 * The correctness of this entire class hinges on the LPS (Longest Proper
 * Prefix which is also a Suffix) array. Every index in {@code lps} answers:
 * "if a mismatch happens at this position in the pattern, how far back can
 * I safely resume matching from, without re-checking characters I already
 * know matched?"
 * <p>
 * Hand-traced example used to validate this implementation, pattern = "ABABC":
 * <pre>
 * index:    0  1  2  3  4
 * char:     A  B  A  B  C
 * lps:      0  0  1  2  0
 * </pre>
 * Why lps[4] = 0 and not something else: the prefix "ABAB" and suffix "BABC"
 * of "ABABC" share no common prefix/suffix, so on a mismatch after matching
 * "ABAB" followed by a non-C character, we must restart the pattern entirely
 * from index 0 — there's no partial overlap to exploit.
 * Why lps[2] = 1: "ABA" — prefix "A" equals suffix "A", length 1.
 * Why lps[3] = 2: "ABAB" — prefix "AB" equals suffix "AB", length 2.
 */
public final class KMPMatcher {

    private KMPMatcher() {
        // static utility class, no instances
    }

    /**
     * Builds the LPS (failure function) array for the given pattern.
     *
     * @param pattern the pattern to preprocess
     * @return an int array where lps[i] = length of the longest proper
     *         prefix of pattern[0..i] that is also a suffix of pattern[0..i]
     */
    static int[] buildLpsArray(String pattern) {
        int m = pattern.length();
        int[] lps = new int[m];
        if (m == 0) {
            return lps;
        }

        lps[0] = 0; // a single character has no proper prefix
        int prefixLen = 0; // length of the previous longest prefix-suffix
        int i = 1;

        while (i < m) {
            if (pattern.charAt(i) == pattern.charAt(prefixLen)) {
                prefixLen++;
                lps[i] = prefixLen;
                i++;
            } else if (prefixLen != 0) {
                // fall back to the previous known prefix-suffix length,
                // do NOT increment i here -- we haven't resolved this
                // position yet, we're just shrinking our candidate prefix
                prefixLen = lps[prefixLen - 1];
            } else {
                // no fallback possible, this character starts fresh
                lps[i] = 0;
                i++;
            }
        }
        return lps;
    }

    /**
     * Finds every occurrence of {@code pattern} within {@code text}, exact match.
     *
     * @param text    the string to search within
     * @param pattern the string to search for
     * @return a DynamicArray of starting indices (0-based) where pattern occurs
     *         in text; empty (not null) if there are no matches or if either
     *         input is null/empty
     */
    public static DynamicArray<Integer> search(String text, String pattern) {
        DynamicArray<Integer> matches = new DynamicArray<>();
        if (text == null || pattern == null || pattern.isEmpty() || text.isEmpty()) {
            return matches;
        }

        int n = text.length();
        int m = pattern.length();
        if (m > n) {
            return matches;
        }

        int[] lps = buildLpsArray(pattern);

        int i = 0; // index into text
        int j = 0; // index into pattern

        while (i < n) {
            if (text.charAt(i) == pattern.charAt(j)) {
                i++;
                j++;
                if (j == m) {
                    // full pattern matched, record start index
                    matches.add(i - j);
                    // fall back using lps to look for overlapping matches
                    j = lps[j - 1];
                }
            } else if (j != 0) {
                // mismatch after some partial match -- use lps to avoid
                // re-scanning text we already know matched
                j = lps[j - 1];
            } else {
                // mismatch at the very start of the pattern, just advance text
                i++;
            }
        }
        return matches;
    }

    /**
     * Convenience method: does {@code pattern} occur anywhere in {@code text}?
     */
    public static boolean contains(String text, String pattern) {
        return !search(text, pattern).isEmpty();
    }
}
