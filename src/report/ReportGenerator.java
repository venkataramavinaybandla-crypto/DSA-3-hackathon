package report;

import algo.MergeSort;
import core.DynamicArray;
import core.Graph;
import core.HashTable;
import core.Paper;

/**
 * Report generator for citation graph analysis.
 *
 * <p>STRICTLY READ-ONLY against the underlying {@link Graph} and {@link HashTable}
 * structures. Defensive copies of domain objects are created where necessary to
 * guarantee zero side effects on the core engine.
 *
 * <p>Uses Phase 6's {@link MergeSort} for stable ranking and custom data structures
 * with zero {@code java.util} imports.
 */
public final class ReportGenerator {

    private ReportGenerator() {
        // static utility class, prevent instantiation
    }

    /**
     * DTO representing aggregate citation statistics for an author.
     */
    public static class AuthorStats {
        private final String author;
        private int totalCitations;
        private int paperCount;

        public AuthorStats(String author) {
            this.author = (author == null || author.trim().isEmpty()) ? "Unknown" : author;
            this.totalCitations = 0;
            this.paperCount = 0;
        }

        public void addPaper(int citations) {
            this.totalCitations += citations;
            this.paperCount++;
        }

        public String getAuthor() {
            return author;
        }

        public int getTotalCitations() {
            return totalCitations;
        }

        public int getPaperCount() {
            return paperCount;
        }

        @Override
        public String toString() {
            return author + " (Total Citations: " + totalCitations + ", Papers: " + paperCount + ")";
        }
    }

    /**
     * DTO representing citation trends for a specific publication year.
     */
    public static class YearTrend {
        private final int year;
        private int paperCount;
        private int totalCitations;

        public YearTrend(int year) {
            this.year = year;
            this.paperCount = 0;
            this.totalCitations = 0;
        }

        public void addPaper(int citations) {
            this.paperCount++;
            this.totalCitations += citations;
        }

        public int getYear() {
            return year;
        }

        public int getPaperCount() {
            return paperCount;
        }

        public int getTotalCitations() {
            return totalCitations;
        }

        @Override
        public String toString() {
            return "Year " + year + ": " + paperCount + " paper(s), " + totalCitations + " citation(s)";
        }
    }

    /**
     * Returns the top N most-cited papers in the graph.
     * Uses Phase 6's stable {@link MergeSort}.
     *
     * @param graph the citation graph (read-only)
     * @param n     maximum number of papers to return (if n <= 0, returns empty)
     * @return dynamic array of top N papers in descending order of citation count
     */
    public static DynamicArray<Paper> getTopCitedPapers(Graph graph, int n) {
        DynamicArray<Paper> result = new DynamicArray<>();
        if (graph == null || n <= 0 || graph.vertexCount() == 0) {
            return result;
        }

        int vCount = graph.vertexCount();
        DynamicArray<Paper> copyList = new DynamicArray<>(vCount);

        // Pre-compute in-degrees to ensure accurate citation counts without mutating original papers
        int[] inDegrees = new int[vCount];
        for (int i = 0; i < vCount; i++) {
            DynamicArray<Integer> neighbors = graph.getNeighbors(i);
            for (int j = 0; j < neighbors.size(); j++) {
                int toIdx = neighbors.get(j);
                if (toIdx >= 0 && toIdx < vCount) {
                    inDegrees[toIdx]++;
                }
            }
        }

        for (int i = 0; i < vCount; i++) {
            Paper original = graph.getPaper(i);
            int count = Math.max(original.getCitationCount(), inDegrees[i]);
            // Defensive copy
            Paper clone = new Paper(original.getId(), original.getTitle(), original.getAuthor(), original.getYear(), count);
            copyList.add(clone);
        }

        // Sort via Phase 6 stable merge sort
        MergeSort.sort(copyList);

        int limit = Math.min(n, copyList.size());
        for (int i = 0; i < limit; i++) {
            result.add(copyList.get(i));
        }

        return result;
    }

    /**
     * Returns the top authors ranked by total citations across their papers.
     *
     * @param graph the citation graph (read-only)
     * @param n     maximum number of authors to return
     * @return dynamic array of top author statistics
     */
    public static DynamicArray<AuthorStats> getTopAuthors(Graph graph, int n) {
        DynamicArray<AuthorStats> result = new DynamicArray<>();
        if (graph == null || n <= 0 || graph.vertexCount() == 0) {
            return result;
        }

        int vCount = graph.vertexCount();
        int[] inDegrees = new int[vCount];
        for (int i = 0; i < vCount; i++) {
            DynamicArray<Integer> neighbors = graph.getNeighbors(i);
            for (int j = 0; j < neighbors.size(); j++) {
                int toIdx = neighbors.get(j);
                if (toIdx >= 0 && toIdx < vCount) {
                    inDegrees[toIdx]++;
                }
            }
        }

        HashTable<Integer> authorIndex = new HashTable<>();
        DynamicArray<AuthorStats> authorList = new DynamicArray<>();

        for (int i = 0; i < vCount; i++) {
            Paper p = graph.getPaper(i);
            String author = (p.getAuthor() != null && !p.getAuthor().trim().isEmpty()) ? p.getAuthor().trim() : "Unknown";
            int citations = Math.max(p.getCitationCount(), inDegrees[i]);

            Integer existingIdx = authorIndex.get(author);
            if (existingIdx != null) {
                authorList.get(existingIdx).addPaper(citations);
            } else {
                AuthorStats stats = new AuthorStats(author);
                stats.addPaper(citations);
                int idx = authorList.size();
                authorList.add(stats);
                authorIndex.put(author, idx);
            }
        }

        // Stable sort authors by totalCitations descending
        sortAuthorsDescending(authorList);

        int limit = Math.min(n, authorList.size());
        for (int i = 0; i < limit; i++) {
            result.add(authorList.get(i));
        }

        return result;
    }

    /**
     * Returns citation trends grouped by publication year, sorted chronologically.
     *
     * @param graph the citation graph (read-only)
     * @return dynamic array of YearTrend objects sorted ascending by year
     */
    public static DynamicArray<YearTrend> getCitationTrends(Graph graph) {
        DynamicArray<YearTrend> trends = new DynamicArray<>();
        if (graph == null || graph.vertexCount() == 0) {
            return trends;
        }

        int vCount = graph.vertexCount();
        int[] inDegrees = new int[vCount];
        for (int i = 0; i < vCount; i++) {
            DynamicArray<Integer> neighbors = graph.getNeighbors(i);
            for (int j = 0; j < neighbors.size(); j++) {
                int toIdx = neighbors.get(j);
                if (toIdx >= 0 && toIdx < vCount) {
                    inDegrees[toIdx]++;
                }
            }
        }

        HashTable<Integer> yearIndex = new HashTable<>();

        for (int i = 0; i < vCount; i++) {
            Paper p = graph.getPaper(i);
            int year = p.getYear();
            int citations = Math.max(p.getCitationCount(), inDegrees[i]);
            String yearKey = String.valueOf(year);

            Integer existingIdx = yearIndex.get(yearKey);
            if (existingIdx != null) {
                trends.get(existingIdx).addPaper(citations);
            } else {
                YearTrend trend = new YearTrend(year);
                trend.addPaper(citations);
                int idx = trends.size();
                trends.add(trend);
                yearIndex.put(yearKey, idx);
            }
        }

        // Sort trends ascending by year
        sortTrendsAscending(trends);

        return trends;
    }

    /**
     * Formats top cited papers as a clean textual report table.
     */
    public static String formatTopPapers(Graph graph, int n) {
        DynamicArray<Paper> papers = getTopCitedPapers(graph, n);
        if (papers.isEmpty()) {
            return "No papers available to display.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-5s | %-10s | %-32s | %-20s | %-6s | %-10s%n",
                "Rank", "ID", "Title", "Author", "Year", "Citations"));
        sb.append("-------------------------------------------------------------------------------------------------\n");

        for (int i = 0; i < papers.size(); i++) {
            Paper p = papers.get(i);
            String title = p.getTitle();
            if (title.length() > 30) {
                title = title.substring(0, 27) + "...";
            }
            String author = p.getAuthor();
            if (author.length() > 18) {
                author = author.substring(0, 15) + "...";
            }

            sb.append(String.format("%-5d | %-10s | %-32s | %-20s | %-6d | %-10d%n",
                    (i + 1), p.getId(), title, author, p.getYear(), p.getCitationCount()));
        }
        return sb.toString();
    }

    /**
     * Formats top authors as a clean textual report table.
     */
    public static String formatTopAuthors(Graph graph, int n) {
        DynamicArray<AuthorStats> authors = getTopAuthors(graph, n);
        if (authors.isEmpty()) {
            return "No author data available to display.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-5s | %-25s | %-15s | %-12s%n",
                "Rank", "Author", "Total Citations", "Paper Count"));
        sb.append("--------------------------------------------------------------------\n");

        for (int i = 0; i < authors.size(); i++) {
            AuthorStats a = authors.get(i);
            String name = a.getAuthor();
            if (name.length() > 23) {
                name = name.substring(0, 20) + "...";
            }
            sb.append(String.format("%-5d | %-25s | %-15d | %-12d%n",
                    (i + 1), name, a.getTotalCitations(), a.getPaperCount()));
        }
        return sb.toString();
    }

    /**
     * Formats yearly trends as a clean textual report table.
     */
    public static String formatCitationTrends(Graph graph) {
        DynamicArray<YearTrend> trends = getCitationTrends(graph);
        if (trends.isEmpty()) {
            return "No trend data available to display.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-8s | %-15s | %-15s%n", "Year", "Papers Published", "Total Citations"));
        sb.append("----------------------------------------------------\n");

        for (int i = 0; i < trends.size(); i++) {
            YearTrend t = trends.get(i);
            String yearStr = (t.getYear() <= 0) ? "N/A" : String.valueOf(t.getYear());
            sb.append(String.format("%-8s | %-15d | %-15d%n", yearStr, t.getPaperCount(), t.getTotalCitations()));
        }
        return sb.toString();
    }

    /**
     * Stable merge sort for AuthorStats in descending order of totalCitations.
     */
    private static void sortAuthorsDescending(DynamicArray<AuthorStats> list) {
        if (list.size() <= 1) return;
        int n = list.size();
        AuthorStats[] arr = new AuthorStats[n];
        for (int i = 0; i < n; i++) arr[i] = list.get(i);
        AuthorStats[] aux = new AuthorStats[n];

        mergeSortAuthors(arr, aux, 0, n - 1);
        for (int i = 0; i < n; i++) list.set(i, arr[i]);
    }

    private static void mergeSortAuthors(AuthorStats[] arr, AuthorStats[] aux, int low, int high) {
        if (low >= high) return;
        int mid = low + (high - low) / 2;
        mergeSortAuthors(arr, aux, low, mid);
        mergeSortAuthors(arr, aux, mid + 1, high);

        for (int k = low; k <= high; k++) aux[k] = arr[k];

        int i = low, j = mid + 1;
        for (int k = low; k <= high; k++) {
            if (i > mid) arr[k] = aux[j++];
            else if (j > high) arr[k] = aux[i++];
            else if (aux[i].getTotalCitations() >= aux[j].getTotalCitations()) arr[k] = aux[i++];
            else arr[k] = aux[j++];
        }
    }

    /**
     * Stable merge sort for YearTrend in ascending order of year.
     */
    private static void sortTrendsAscending(DynamicArray<YearTrend> list) {
        if (list.size() <= 1) return;
        int n = list.size();
        YearTrend[] arr = new YearTrend[n];
        for (int i = 0; i < n; i++) arr[i] = list.get(i);
        YearTrend[] aux = new YearTrend[n];

        mergeSortTrends(arr, aux, 0, n - 1);
        for (int i = 0; i < n; i++) list.set(i, arr[i]);
    }

    private static void mergeSortTrends(YearTrend[] arr, YearTrend[] aux, int low, int high) {
        if (low >= high) return;
        int mid = low + (high - low) / 2;
        mergeSortTrends(arr, aux, low, mid);
        mergeSortTrends(arr, aux, mid + 1, high);

        for (int k = low; k <= high; k++) aux[k] = arr[k];

        int i = low, j = mid + 1;
        for (int k = low; k <= high; k++) {
            if (i > mid) arr[k] = aux[j++];
            else if (j > high) arr[k] = aux[i++];
            else if (aux[i].getYear() <= aux[j].getYear()) arr[k] = aux[i++];
            else arr[k] = aux[j++];
        }
    }
}
