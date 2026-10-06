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

    /** Length, in cells, of the proportional bar drawn in the report tables. */
    private static final int BAR_CELLS = 10;

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

        // Rank, ID, Year and Citations keep their width, as do the six " | " separators and the
        // share bar. Title and Author share whatever is left, so the row always fits the window the
        // console drew around it rather than spilling past the frame on a normal-width terminal.
        int fixed = 5 + 10 + 6 + 9 + 6 * 3 + BAR_CELLS;
        int slack = Math.max(14, Style.width() - fixed);
        int authorWidth = Math.min(18, Math.max(6, slack / 3));
        int titleWidth = Math.max(8, slack - authorWidth);

        StringBuilder sb = new StringBuilder();
        String header = String.format("%-5s | %-10s | %-" + titleWidth + "s | %-" + authorWidth
                        + "s | %-6s | %-9s | %-10s",
                "Rank", "ID", "Title", "Author", "Year", "Citations", "Share");
        sb.append(Style.tableHeader(header)).append('\n');
        sb.append(Style.muted("-".repeat(header.length()))).append('\n');

        int maxCitations = papers.get(0).getCitationCount();

        for (int i = 0; i < papers.size(); i++) {
            Paper p = papers.get(i);
            String title = shorten(p.getTitle(), titleWidth);
            String author = shorten(p.getAuthor(), authorWidth);

            sb.append(String.format("%-5d | %-10s | %-" + titleWidth + "s | %-" + authorWidth
                            + "s | %-6d | %-9d | %s%n",
                    (i + 1), p.getId(), title, author, p.getYear(), p.getCitationCount(),
                    Style.bar(p.getCitationCount(), maxCitations, BAR_CELLS)));
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

        // Only the author name flexes; every other column, the separators and the bar are fixed.
        int fixed = 5 + 15 + 12 + 4 * 3 + BAR_CELLS;
        int authorWidth = Math.min(25, Math.max(8, Style.width() - fixed));

        StringBuilder sb = new StringBuilder();
        String header = String.format("%-5s | %-" + authorWidth + "s | %-15s | %-12s | %-10s",
                "Rank", "Author", "Total Citations", "Paper Count", "Share");
        sb.append(Style.tableHeader(header)).append('\n');
        sb.append(Style.muted("-".repeat(header.length()))).append('\n');

        int maxCitations = authors.get(0).getTotalCitations();

        for (int i = 0; i < authors.size(); i++) {
            AuthorStats a = authors.get(i);
            String name = shorten(a.getAuthor(), authorWidth);
            sb.append(String.format("%-5d | %-" + authorWidth + "s | %-15d | %-12d | %s%n",
                    (i + 1), name, a.getTotalCitations(), a.getPaperCount(),
                    Style.bar(a.getTotalCitations(), maxCitations, BAR_CELLS)));
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

        // Year and Share are fixed; the two counting columns split what is left between them.
        int fixed = 8 + 10 + 3 * 3;
        int slack = Math.max(20, Style.width() - fixed);
        int papersWidth = Math.max(10, slack / 2);
        int citationsWidth = Math.max(10, slack - papersWidth);

        StringBuilder sb = new StringBuilder();
        String header = String.format("%-8s | %-" + papersWidth + "s | %-" + citationsWidth
                        + "s | %-10s",
                "Year", "Papers Published", "Total Citations", "Share");
        sb.append(Style.tableHeader(header)).append('\n');
        sb.append(Style.muted("-".repeat(header.length()))).append('\n');

        int maxCitations = 0;
        for (int i = 0; i < trends.size(); i++) {
            maxCitations = Math.max(maxCitations, trends.get(i).getTotalCitations());
        }

        for (int i = 0; i < trends.size(); i++) {
            YearTrend t = trends.get(i);
            String yearStr = (t.getYear() <= 0) ? "N/A" : String.valueOf(t.getYear());
            sb.append(String.format("%-8s | %-" + papersWidth + "d | %-" + citationsWidth + "d | %s%n",
                    yearStr, t.getPaperCount(), t.getTotalCitations(),
                    Style.bar(t.getTotalCitations(), maxCitations, BAR_CELLS)));
        }
        return sb.toString();
    }

    /**
     * Shortens a table cell to at most {@code max} characters, marking the cut with an ellipsis.
     *
     * @param text the cell text
     * @param max  the column width it has to fit
     * @return the text unchanged when it fits, else a shortened copy
     */
    private static String shorten(String text, int max) {
        String value = (text == null) ? "" : text;
        if (value.length() <= max) {
            return value;
        }
        return max <= 3 ? value.substring(0, Math.max(0, max)) : value.substring(0, max - 3) + "...";
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
