package algo;

import core.DynamicArray;

/**
 * Rabin-Karp string matching using a polynomial rolling hash.
 * <p>
 * Unlike KMP (best for one pattern against one text), Rabin-Karp shines when
 * checking the SAME pattern against MANY different texts (e.g. a search query
 * scanned against every paper title in the corpus) -- the rolling hash lets
 * each text be scanned in amortized O(n) without re-deriving pattern structure
 * per text, and a single {@link #search(String, String)} call is still a
 * correct, standalone O(n+m) average-case exact matcher.
 */
public final class RabinKarpMatcher {

    // Same base/prime family as core.HashTable, chosen for the same reason:
    // base 31 is well-studied for ASCII text (mirrors java.lang.String's own
    // hashCode base), and a large prime modulus minimizes hash collisions.
    private static final long BASE = 31;
    private static final long MODULUS = 1_000_000_007L;

    private RabinKarpMatcher() {
        // static utility class, no instances
    }

    /**
     * Finds every occurrence of {@code pattern} within {@code text}, exact match.
     *
     * @return starting indices (0-based) of every match; empty (not null) if
     *         no matches or invalid input
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

        // highestPower = BASE^(m-1) mod MODULUS, needed to "remove" the
        // leading character's contribution when the window slides forward
        long highestPower = 1;
        for (int i = 0; i < m - 1; i++) {
            highestPower = (highestPower * BASE) % MODULUS;
        }

        long patternHash = 0;
        long windowHash = 0;
        for (int i = 0; i < m; i++) {
            patternHash = (patternHash * BASE + pattern.charAt(i)) % MODULUS;
            windowHash = (windowHash * BASE + text.charAt(i)) % MODULUS;
        }

        for (int i = 0; i <= n - m; i++) {
            // Hash equality is a CANDIDATE, not proof -- two different
            // substrings can collide under modular arithmetic. Always
            // verify with a direct character comparison before accepting.
            if (windowHash == patternHash && text.regionMatches(i, pattern, 0, m)) {
                matches.add(i);
            }

            // Slide the window: remove the outgoing character's contribution,
            // shift everything up by one power of BASE, add the incoming char.
            if (i < n - m) {
                windowHash = (windowHash - text.charAt(i) * highestPower % MODULUS + MODULUS) % MODULUS;
                windowHash = (windowHash * BASE + text.charAt(i + m)) % MODULUS;
            }
        }
        return matches;
    }

    /**
     * Scans {@code pattern} against MULTIPLE texts at once (e.g. many paper
     * titles), returning the indices (into the {@code texts} array) of every
     * text that contains at least one match. This is the intended use case
     * for Rabin-Karp in this project: one query, many candidate titles.
     *
     * @param texts   the candidate strings to scan (e.g. paper titles)
     * @param pattern the search pattern
     * @return indices into {@code texts} of entries that contain {@code pattern}
     */
    public static DynamicArray<Integer> searchMany(DynamicArray<String> texts, String pattern) {
        DynamicArray<Integer> hits = new DynamicArray<>();
        if (texts == null || pattern == null || pattern.isEmpty()) {
            return hits;
        }
        for (int i = 0; i < texts.size(); i++) {
            if (!search(texts.get(i), pattern).isEmpty()) {
                hits.add(i);
            }
        }
        return hits;
    }

    public static boolean contains(String text, String pattern) {
        return !search(text, pattern).isEmpty();
    }
}
