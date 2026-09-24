package main;

import algo.FuzzyMatcher;
import algo.GraphTraversal;
import algo.KMPMatcher;
import core.DynamicArray;
import core.Graph;
import core.Paper;
import io.CsvHandler;
import report.GraphRenderer;
import report.ReportGenerator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Main interactive console application for the Citation Analysis System.
 *
 * <p>Delivered strictly as a terminal/CLI application using {@code System.in} and {@code System.out}.
 * Absolutely zero {@code java.util} imports, zero external dependencies, Java 17 standard library only.
 */
public class Main {

    private static final String DEFAULT_CSV_FILE = "citation_data.csv";

    private Graph graph;
    private final BufferedReader reader;
    private boolean unsavedChanges;

    public Main() {
        this.graph = new Graph();
        this.reader = new BufferedReader(new InputStreamReader(System.in));
        this.unsavedChanges = false;
        loadSampleDataIfEmpty();
    }

    public static void main(String[] args) {
        Main app = new Main();
        app.run();
    }

    /**
     * Seeds academic papers to make the system instantly interactive on launch.
     * Automatically loads the full dataset from citation_data.csv if available.
     */
    private void loadSampleDataIfEmpty() {
        File defaultFile = new File(DEFAULT_CSV_FILE);
        if (defaultFile.exists() && defaultFile.isFile()) {
            try {
                this.graph = CsvHandler.load(DEFAULT_CSV_FILE);
                return;
            } catch (IOException e) {
                // Fall back to built-in minimal dataset if file read fails
            }
        }

        Paper p1 = new Paper("P101", "Attention Is All You Need", "Vaswani et al.", 2017);
        Paper p2 = new Paper("P102", "BERT: Pre-training of Deep Bidirectional Transformers", "Devlin et al.", 2018);
        Paper p3 = new Paper("P103", "Language Models are Few-Shot Learners (GPT-3)", "Brown et al.", 2020);
        Paper p4 = new Paper("P104", "Deep Residual Learning for Image Recognition", "He et al.", 2016);
        Paper p5 = new Paper("P105", "Mastering the Game of Go with Deep Neural Networks", "Silver et al.", 2016);

        graph.addVertex(p1);
        graph.addVertex(p2);
        graph.addVertex(p3);
        graph.addVertex(p4);
        graph.addVertex(p5);

        graph.addCitation("P102", "P101"); // BERT cites Attention
        graph.addCitation("P103", "P101"); // GPT-3 cites Attention
        graph.addCitation("P103", "P102"); // GPT-3 cites BERT
        graph.addCitation("P101", "P104"); // Attention cites ResNet
        graph.addCitation("P102", "P104"); // BERT cites ResNet
        graph.addCitation("P105", "P104"); // AlphaGo cites ResNet

        CsvHandler.syncCitationCounts(graph);
    }

    public void run() {
        printBanner();

        boolean running = true;
        while (running) {
            printMainMenu();
            String choice = readLine("Enter your choice (1-12): ");
            if (choice == null) {
                // End of input stream (e.g. piped input or EOF)
                System.out.println("\nInput stream closed. Exiting.");
                break;
            }

            choice = choice.trim();
            if (choice.isEmpty()) {
                System.out.println("Please enter a selection from 1 to 12.");
                continue;
            }

            switch (choice) {
                case "1":
                    handleAddPaper();
                    break;
                case "2":
                    handleAddCitation();
                    break;
                case "3":
                    handleSearchPaper();
                    break;
                case "4":
                    handleTraverseGraph();
                    break;
                case "5":
                    handleReportsSubmenu();
                    break;
                case "6":
                    handleSaveCsv();
                    break;
                case "7":
                    handleLoadCsv();
                    break;
                case "8":
                    handleViewPaperContent();
                    break;
                case "9":
                    handleNarrateCitationChain();
                    break;
                case "10":
                    try {
                        handleDisplayAllPaths();
                    } catch (Exception e) {
                        System.out.println("[Error] Failed to display paths: " + e.getMessage());
                    }
                    break;
                case "11":
                    handleOptimalCitationPath();
                    break;
                case "12":
                    running = handleExit();
                    break;
                default:
                    System.out.println("[Error] Invalid choice: '" + choice + "'. Please enter a number between 1 and 12.");
            }
        }
    }

    private void printBanner() {
        System.out.println("========================================================================");
        System.out.println("                          CERBERUS SYSTEM (CS)                       ");
        System.out.println("                 Pure Java Data Structures & Algorithms                 ");
        System.out.println("========================================================================");
        System.out.println("Graph loaded with " + graph.vertexCount() + " research papers and " + graph.edgeCount() + " citation edges.");
    }

    private void printMainMenu() {
        System.out.println("\n----------------------------- MAIN MENU --------------------------------");
        System.out.println("  1. Add a paper");
        System.out.println("  2. Add a citation");
        System.out.println("  3. Search a paper (Exact / Fuzzy)");
        System.out.println("  4. Explore citation network reachability (Level-wise / Deep Lineage)");
        System.out.println("  5. View reports (Top papers, Top authors, Trends)");
        System.out.println("  6. Save current data to CSV");
        System.out.println("  7. Load data from CSV");
        System.out.println("  8. View paper content");
        System.out.println("  9. Narrate citation chain (Shortest Path)");
        System.out.println(" 10. Display All Paths (Hamiltonian Check)");
        System.out.println(" 11. Optimal Citation Path (Bitmask DP)");
        System.out.println(" 12. Exit");
        System.out.println("------------------------------------------------------------------------");
    }

    // -------------------------------------------------------------------------
    // Option 1: Add a Paper
    // -------------------------------------------------------------------------
    private void handleAddPaper() {
        System.out.println("\n--- Add a New Paper ---");
        String id;
        while (true) {
            id = readLine("Enter paper ID (e.g. P106): ");
            if (id == null) return;
            id = id.trim();
            if (id.isEmpty()) {
                System.out.println("[Error] Paper ID cannot be empty. Please try again.");
                continue;
            }
            if (graph.findIndexById(id) != -1) {
                System.out.println("[Error] A paper with ID '" + id + "' already exists. Please choose a unique ID.");
                continue;
            }
            break;
        }

        String title;
        while (true) {
            title = readLine("Enter paper title: ");
            if (title == null) return;
            title = title.trim();
            if (title.isEmpty()) {
                System.out.println("[Error] Paper title cannot be empty. Please try again.");
                continue;
            }
            break;
        }

        String author;
        while (true) {
            author = readLine("Enter author name: ");
            if (author == null) return;
            author = author.trim();
            if (author.isEmpty()) {
                System.out.println("[Error] Author name cannot be empty. Please try again.");
                continue;
            }
            break;
        }

        int year;
        while (true) {
            String yearStr = readLine("Enter publication year (e.g. 2023): ");
            if (yearStr == null) return;
            yearStr = yearStr.trim();
            try {
                year = Integer.parseInt(yearStr);
                if (year < 1500 || year > 2100) {
                    System.out.println("[Error] Please enter a valid publication year between 1500 and 2100.");
                    continue;
                }
                break;
            } catch (NumberFormatException e) {
                System.out.println("[Error] Malformed year '" + yearStr + "'. Please enter a numeric year.");
            }
        }

        Paper paper = new Paper(id, title, author, year);
        int idx = graph.addVertex(paper);
        unsavedChanges = true;
        System.out.println("[Success] Paper '" + title + "' added successfully at vertex index " + idx + ".");
        System.out.println("Total papers in graph: " + graph.vertexCount());
    }

    // -------------------------------------------------------------------------
    // Option 2: Add a Citation
    // -------------------------------------------------------------------------
    private void handleAddCitation() {
        System.out.println("\n--- Add a Citation (Directed Edge: Paper A refers to Paper B) ---");
        if (graph.vertexCount() < 2) {
            System.out.println("[Notice] At least 2 papers must exist to create a citation edge.");
            return;
        }

        String citingId;
        int fromIdx;
        while (true) {
            citingId = readLine("Enter CITING paper ID (Paper A - refers to): ");
            if (citingId == null) return;
            citingId = citingId.trim();
            fromIdx = graph.findIndexById(citingId);
            if (fromIdx == -1) {
                System.out.println("[Error] Paper ID '" + citingId + "' not found. Available papers:");
                printAvailablePaperIds();
                continue;
            }
            break;
        }

        String citedId;
        int toIdx;
        while (true) {
            citedId = readLine("Enter CITED paper ID (Paper B - referred to): ");
            if (citedId == null) return;
            citedId = citedId.trim();
            if (citedId.equals(citingId)) {
                System.out.println("[Warning] Self-citations are permitted but generally unusual. Continuing...");
            }
            toIdx = graph.findIndexById(citedId);
            if (toIdx == -1) {
                System.out.println("[Error] Paper ID '" + citedId + "' not found. Available papers:");
                printAvailablePaperIds();
                continue;
            }
            break;
        }

        // Check if edge already exists
        if (graph.getNeighbors(fromIdx).contains(toIdx)) {
            System.out.println("[Notice] Citation where Paper [" + citingId + "] refers to Paper [" + citedId + "] already exists.");
            return;
        }

        graph.addCitation(citingId, citedId);
        CsvHandler.syncCitationCounts(graph);
        unsavedChanges = true;
        System.out.println("[Success] Citation recorded: Paper [" + citingId + "] refers to Paper [" + citedId + "].");
        System.out.println("Total citations in graph: " + graph.edgeCount());
    }

    private void printAvailablePaperIds() {
        StringBuilder sb = new StringBuilder("  ");
        int count = graph.vertexCount();
        for (int i = 0; i < count; i++) {
            sb.append(graph.getPaper(i).getId());
            if (i < count - 1) {
                sb.append(", ");
            }
        }
        System.out.println(sb.toString());
    }

    // -------------------------------------------------------------------------
    // Option 3: Search a Paper
    // -------------------------------------------------------------------------
    private void handleSearchPaper() {
        System.out.println("\n--- Search Papers ---");
        if (graph.vertexCount() == 0) {
            System.out.println("[Notice] Graph is empty. No papers to search.");
            return;
        }

        String query = readLine("Enter search query: ");
        if (query == null) return;
        query = query.trim();
        if (query.isEmpty()) {
            System.out.println("[Error] Search query cannot be empty.");
            return;
        }

        String mode;
        while (true) {
            mode = readLine("Search mode - [E]xact substring (KMP) or [F]uzzy typo-tolerant (Wagner-Fischer)? [E/F]: ");
            if (mode == null) return;
            mode = mode.trim().toUpperCase();
            if (mode.equals("E") || mode.equals("EXACT") || mode.equals("F") || mode.equals("FUZZY")) {
                break;
            }
            System.out.println("[Error] Invalid choice. Please enter 'E' for exact or 'F' for fuzzy.");
        }

        boolean isFuzzy = mode.startsWith("F");
        DynamicArray<Integer> matchedIndices = new DynamicArray<>();

        if (!isFuzzy) {
            // Exact substring search via KMPMatcher across title, author, and ID
            String lowerQuery = query.toLowerCase();
            for (int i = 0; i < graph.vertexCount(); i++) {
                Paper p = graph.getPaper(i);
                boolean matchTitle = KMPMatcher.contains(p.getTitle().toLowerCase(), lowerQuery);
                boolean matchAuthor = KMPMatcher.contains(p.getAuthor().toLowerCase(), lowerQuery);
                boolean matchId = KMPMatcher.contains(p.getId().toLowerCase(), lowerQuery);

                if (matchTitle || matchAuthor || matchId) {
                    matchedIndices.add(i);
                }
            }
        } else {
            // Fuzzy search via Wagner-Fischer edit distance against paper titles and title words
            int maxDist = 2;
            if (query.length() >= 8) {
                maxDist = 3;
            }

            String lowerQuery = query.toLowerCase();
            for (int i = 0; i < graph.vertexCount(); i++) {
                Paper p = graph.getPaper(i);
                String title = p.getTitle();
                boolean matched = false;

                if (FuzzyMatcher.editDistance(lowerQuery, title.toLowerCase()) <= maxDist) {
                    matched = true;
                } else {
                    // Check against words within title
                    String[] words = title.split("[\\s,.:;!?()\\[\\]\\-]+");
                    for (String w : words) {
                        if (!w.isEmpty() && Math.abs(w.length() - lowerQuery.length()) <= maxDist) {
                            if (FuzzyMatcher.editDistance(lowerQuery, w.toLowerCase()) <= maxDist) {
                                matched = true;
                                break;
                            }
                        }
                    }
                }

                if (matched) {
                    matchedIndices.add(i);
                }
            }
        }

        if (matchedIndices.isEmpty()) {
            System.out.println("[Result] No papers matched your search query '" + query + "'.");
        } else {
            System.out.println("\n[Result] Found " + matchedIndices.size() + " matching paper(s):");
            System.out.printf("%-10s | %-35s | %-22s | %-6s | %-10s%n", "ID", "Title", "Author", "Year", "Citations");
            System.out.println("---------------------------------------------------------------------------------------------");
            for (int k = 0; k < matchedIndices.size(); k++) {
                Paper p = graph.getPaper(matchedIndices.get(k));
                String title = p.getTitle();
                if (title.length() > 33) title = title.substring(0, 30) + "...";
                String author = p.getAuthor();
                if (author.length() > 20) author = author.substring(0, 17) + "...";

                System.out.printf("%-10s | %-35s | %-22s | %-6d | %-10d%n",
                        p.getId(), title, author, p.getYear(), p.getCitationCount());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Option 4: Explore Citation Network Reachability
    // -------------------------------------------------------------------------
    private void handleTraverseGraph() {
        System.out.println("\n--- Explore Citation Network Reachability ---");
        if (graph.vertexCount() == 0) {
            System.out.println("[Notice] Graph is empty.");
            return;
        }

        String startId;
        int startIdx;
        while (true) {
            startId = readLine("Enter start paper ID for exploration: ");
            if (startId == null) return;
            startId = startId.trim();
            startIdx = graph.findIndexById(startId);
            if (startIdx == -1) {
                System.out.println("[Error] Paper ID '" + startId + "' not found. Available IDs:");
                printAvailablePaperIds();
                continue;
            }
            break;
        }

        String traversalType;
        while (true) {
            traversalType = readLine("Choose exploration mode - [1] Level-wise or [2] Deep Lineage [1/2]: ");
            if (traversalType == null) return;
            traversalType = traversalType.trim().toUpperCase();
            if (traversalType.equals("1") || traversalType.equals("L") || traversalType.equals("LEVEL") || traversalType.equals("LEVEL-WISE")
                    || traversalType.equals("2") || traversalType.equals("D") || traversalType.equals("DEEP") || traversalType.equals("DEEP LINEAGE")) {
                break;
            }
            System.out.println("[Error] Invalid choice. Please enter '1' for Level-wise or '2' for Deep Lineage.");
        }

        boolean isLevelWise = traversalType.equals("1") || traversalType.startsWith("L");
        DynamicArray<Integer> visitOrder = isLevelWise
                ? GraphTraversal.bfs(graph, startIdx)
                : GraphTraversal.dfs(graph, startIdx);

        System.out.println("\n" + (isLevelWise ? "Level-wise Reachability Exploration" : "Deep Lineage Exploration")
                + " Order starting from [" + startId + "]:");
        System.out.println("Total reachable papers in component: " + visitOrder.size());
        System.out.println("------------------------------------------------------------------------");

        for (int i = 0; i < visitOrder.size(); i++) {
            Paper p = graph.getPaper(visitOrder.get(i));
            System.out.printf("%2d. [%s] \"%s\" by %s (%d)%n",
                    (i + 1), p.getId(), p.getTitle(), p.getAuthor(), p.getYear());
        }

        // Narrate the citation chain from the start paper to the last paper in the traversal
        if (visitOrder.size() > 1) {
            String lastPaperId = graph.getPaper(visitOrder.get(visitOrder.size() - 1)).getId();
            GraphTraversal traverser = new GraphTraversal();
            String chain = traverser.narrateChain(graph, startId, lastPaperId);
            System.out.println("Narrated chain to [" + lastPaperId + "]: " + chain);
        }
    }

    // -------------------------------------------------------------------------
    // Option 5: View Reports Submenu
    // -------------------------------------------------------------------------
    private void handleReportsSubmenu() {
        boolean inSubmenu = true;
        while (inSubmenu) {
            System.out.println("\n------------------------- REPORTS SUBMENU ------------------------------");
            System.out.println("  1. Top N Most-Cited Papers (Stable Ranked)");
            System.out.println("  2. Top Authors by Total Citations");
            System.out.println("  3. Yearly Citation Trends");
            System.out.println("  4. Return to Main Menu");
            System.out.println("------------------------------------------------------------------------");

            String choice = readLine("Select report (1-4): ");
            if (choice == null) return;
            choice = choice.trim();

            switch (choice) {
                case "1":
                    int nPapers = readInt("Enter number of papers to rank (default 5): ", 5);
                    System.out.println("\n=== TOP " + nPapers + " MOST-CITED PAPERS ===");
                    System.out.println(ReportGenerator.formatTopPapers(graph, nPapers));
                    break;
                case "2":
                    int nAuthors = readInt("Enter number of top authors to rank (default 5): ", 5);
                    System.out.println("\n=== TOP " + nAuthors + " AUTHORS BY CITATIONS ===");
                    System.out.println(ReportGenerator.formatTopAuthors(graph, nAuthors));
                    break;
                case "3":
                    System.out.println("\n=== YEARLY CITATION TRENDS ===");
                    System.out.println(ReportGenerator.formatCitationTrends(graph));
                    break;
                case "4":
                    inSubmenu = false;
                    break;
                default:
                    System.out.println("[Error] Invalid choice. Please enter a number between 1 and 4.");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Option 6: Save Current Data to CSV
    // -------------------------------------------------------------------------
    private void handleSaveCsv() {
        System.out.println("\n--- Save Citation Data to CSV ---");
        String filename = readLine("Enter filename to save [default: " + DEFAULT_CSV_FILE + "]: ");
        if (filename == null) return;
        filename = filename.trim();
        if (filename.isEmpty()) {
            filename = DEFAULT_CSV_FILE;
        }

        try {
            CsvHandler.save(graph, filename);
            unsavedChanges = false;
            System.out.println("[Success] Graph state saved successfully to '" + filename + "'.");
            System.out.println("Saved " + graph.vertexCount() + " papers and " + graph.edgeCount() + " citations.");
        } catch (IOException e) {
            System.out.println("[Error] Failed to save CSV file: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Option 7: Load Data from CSV
    // -------------------------------------------------------------------------
    private void handleLoadCsv() {
        System.out.println("\n--- Load Citation Data from CSV ---");
        String filename = readLine("Enter filename to load [default: " + DEFAULT_CSV_FILE + "]: ");
        if (filename == null) return;
        filename = filename.trim();
        if (filename.isEmpty()) {
            filename = DEFAULT_CSV_FILE;
        }

        File file = new File(filename);
        if (!file.exists()) {
            System.out.println("[Error] File '" + filename + "' does not exist.");
            return;
        }

        String mode = readLine("Do you want to [M]erge with current data or [R]eplace it? [M/R, default M]: ");
        if (mode == null) return;
        mode = mode.trim().toUpperCase();

        try {
            if (mode.startsWith("R")) {
                // To replace: create a new graph and load into it
                Graph newGraph = CsvHandler.load(filename);
                // Clear and rebuild
                System.out.println("[Info] Replacing existing data with file contents.");
                // Reload by creating new instance or copying into current
                // Since Graph doesn't have clear(), we load into a fresh graph and point to it
                // To keep internal graph reference clean:
                this.graph = newGraph;
            } else {
                // Merge into current graph
                CsvHandler.loadInto(graph, filename);
            }
            unsavedChanges = false;
            System.out.println("[Success] Loaded data from '" + filename + "'.");
            System.out.println("Graph now contains " + graph.vertexCount() + " papers and " + graph.edgeCount() + " citations.");
        } catch (IOException e) {
            System.out.println("[Error] Failed to load CSV file: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Option 8: View Paper Content (Real PDF Viewer)
    // -------------------------------------------------------------------------
    private void handleViewPaperContent() {
        System.out.println("\n--- View Paper Content ---");
        String id = readLine("Enter paper ID to view (e.g. P101, P104, P501): ");
        if (id == null) return;
        id = id.trim();
        if (id.isEmpty()) {
            System.out.println("[Error] Paper ID cannot be empty.");
            return;
        }

        File file = new File("research_papers", id + ".pdf");
        if (!file.exists() || !file.isFile()) {
            file = new File("research_papers", id.toUpperCase() + ".pdf");
        }
        if (!file.exists() || !file.isFile()) {
            file = new File("research_papers", id.toLowerCase() + ".pdf");
        }

        if (!file.exists() || !file.isFile()) {
            System.out.println("[Error] PDF file not found: research_papers/" + id + ".pdf");
            System.out.println("[Notice] Available papers in the dataset have corresponding PDF files in research_papers/<id>.pdf");
            // No PDF for this paper: fall back to printing its text content in the terminal.
            printPaperAbstractIfAvailable(id);
            offerFullTextView(id);
            return;
        }

        System.out.println("[+] Found research paper PDF: " + file.getAbsolutePath());
        try {
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.OPEN)) {
                System.out.println("[+] Launching system PDF viewer for: " + file.getName() + "...");
                java.awt.Desktop.getDesktop().open(file);
                System.out.println("[Success] Paper PDF opened in default viewer.");
            } else {
                System.out.println("[Notice] Desktop integration is not supported in this environment (headless mode).");
                System.out.println("Please open the PDF manually at: " + file.getAbsolutePath());
            }
        } catch (Exception e) {
            System.out.println("[Notice] Unable to launch default viewer: " + e.getMessage());
            System.out.println("Please open the PDF manually at: " + file.getAbsolutePath());
        }
        // A PDF exists and was handled above: the paper is read in the PDF viewer, so no
        // paper content (abstract or full text) is printed to the terminal for this paper.
    }

    /**
     * Option 8 text fallback: prints the paper's title, authors, year and abstract from the
     * companion text file {@code research_papers/<id>.txt}.
     *
     * <p>Only called when no PDF exists for the paper, so a paper whose PDF was never
     * downloaded still shows its content in the terminal. The Layer A files already store
     * {@code Title: / Authors: / Year: / Abstract:} in the project's standard order, so the
     * file is echoed verbatim. When the file is missing (or empty) nothing at all is printed.
     *
     * @param id the paper ID entered by the user
     */
    private void printPaperAbstractIfAvailable(String id) {
        File abstractFile = findResearchPaperFile(id, ".txt");
        if (abstractFile == null) {
            return;
        }

        String content;
        try {
            content = readFileUtf8(abstractFile);
        } catch (IOException e) {
            System.out.println("[Notice] Abstract file found but could not be read: " + e.getMessage());
            return;
        }

        if (content.trim().isEmpty()) {
            return;
        }

        System.out.println("----------------------- PAPER CONTENT (TEXT FALLBACK) -----------------------");
        System.out.println(content);
        System.out.println("------------------------------------------------------------------------");
    }

    /**
     * Resolves {@code research_papers/<id><suffix>}, trying the ID exactly as entered, then
     * uppercased, then lowercased. Returns {@code null} when no such file exists.
     */
    private File findResearchPaperFile(String id, String suffix) {
        String[] candidates = { id, id.toUpperCase(), id.toLowerCase() };
        for (String candidate : candidates) {
            File candidateFile = new File("research_papers", candidate + suffix);
            if (candidateFile.exists() && candidateFile.isFile()) {
                return candidateFile;
            }
        }
        return null;
    }

    /**
     * Option 8 helper: offers the plain-text companion file for a paper, if one exists.
     *
     * <p>Looks for {@code research_papers/<id>_fulltext.txt} (trying the exact ID, then its
     * uppercase and lowercase forms). When the file is missing, nothing is printed at all.
     * When it is present, the character count is announced and the contents are printed only
     * if the user answers Y. Only reached on the no-PDF fallback path, so a paper that has a
     * PDF never prints its full text to the terminal.
     *
     * @param id the paper ID entered by the user
     */
    private void offerFullTextView(String id) {
        File fullTextFile = findResearchPaperFile(id, "_fulltext.txt");

        if (fullTextFile == null) {
            // No full text companion file for this paper - keep Option 9 output unchanged.
            return;
        }

        String content;
        try {
            content = readFileUtf8(fullTextFile);
        } catch (IOException e) {
            System.out.println("[Notice] Full text file found but could not be read: " + e.getMessage());
            return;
        }

        System.out.println("Full text available (" + content.length() + " characters) \u2014 view? [Y/N]");
        String answer = readLine("> ");
        if (answer == null) {
            return;
        }
        answer = answer.trim();
        if (!answer.equalsIgnoreCase("y") && !answer.equalsIgnoreCase("yes")) {
            return;
        }

        System.out.println("============================= FULL TEXT: " + id + " =============================");
        System.out.println(content);
        System.out.println("========================================================================");
    }

    /**
     * Reads a text file fully as UTF-8, preserving the exact character sequence of the file
     * (line terminators are normalised to the platform separator).
     */
    private String readFileUtf8(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader fileReader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
        try {
            String line;
            boolean firstLine = true;
            while ((line = fileReader.readLine()) != null) {
                if (!firstLine) {
                    sb.append(System.lineSeparator());
                }
                sb.append(line);
                firstLine = false;
            }
        } finally {
            fileReader.close();
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Option 9: Narrate Citation Chain (Shortest Path)
    // -------------------------------------------------------------------------
    private void handleNarrateCitationChain() {
        System.out.println("\n--- Narrate Citation Chain (Shortest Path) ---");
        try {
            if (graph.vertexCount() < 2) {
                System.out.println("[Notice] Need at least 2 papers in the graph to find a citation chain.");
                return;
            }

            String startId;
            while (true) {
                startId = readLine("Enter START paper ID (e.g. P101): ");
                if (startId == null) return;
                startId = startId.trim();
                if (startId.isEmpty()) {
                    System.out.println("[Error] Start paper ID cannot be empty.");
                    continue;
                }
                if (graph.findIndexById(startId) == -1) {
                    System.out.println("[Error] Paper ID '" + startId + "' not found. Available IDs:");
                    printAvailablePaperIds();
                    continue;
                }
                break;
            }

            String endId;
            while (true) {
                endId = readLine("Enter END paper ID (e.g. P106): ");
                if (endId == null) return;
                endId = endId.trim();
                if (endId.isEmpty()) {
                    System.out.println("[Error] End paper ID cannot be empty.");
                    continue;
                }
                if (graph.findIndexById(endId) == -1) {
                    System.out.println("[Error] Paper ID '" + endId + "' not found. Available IDs:");
                    printAvailablePaperIds();
                    continue;
                }
                break;
            }

            GraphTraversal traverser = new GraphTraversal(graph);
            String chain = traverser.narrateChain(startId, endId);
            int hops = traverser.getChainLength();

            System.out.println("\n======================= CITATION CHAIN NARRATION =======================");
            System.out.println("From: [" + startId + "] -> To: [" + endId + "]");
            System.out.println("Hop Count: " + (hops == -1 ? "Unreachable" : hops));
            System.out.println("Narration: " + chain);
            System.out.println("========================================================================");
        } catch (Exception e) {
            System.out.println("[Error] An error occurred while narrating chain: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Option 10: Display All Paths (Hamiltonian Check)
    // -------------------------------------------------------------------------
    private void handleDisplayAllPaths() {
        System.out.println("\n--- Display All Paths (Hamiltonian Check) ---");
        try {
            if (graph.vertexCount() < 2) {
                System.out.println("[Notice] Need at least 2 papers in the graph to find paths.");
                return;
            }

            String sourceId;
            int sourceIdx;
            while (true) {
                sourceId = readLine("Enter SOURCE paper ID: ");
                if (sourceId == null) return;
                sourceId = sourceId.trim();
                sourceIdx = graph.findIndexById(sourceId);
                if (sourceIdx == -1) {
                    System.out.println("[Error] Paper ID '" + sourceId + "' not found. Available IDs:");
                    printAvailablePaperIds();
                    continue;
                }
                break;
            }

            String targetId;
            int targetIdx;
            while (true) {
                targetId = readLine("Enter TARGET paper ID: ");
                if (targetId == null) return;
                targetId = targetId.trim();
                targetIdx = graph.findIndexById(targetId);
                if (targetIdx == -1) {
                    System.out.println("[Error] Paper ID '" + targetId + "' not found. Available IDs:");
                    printAvailablePaperIds();
                    continue;
                }
                break;
            }

            if (sourceIdx == targetIdx) {
                System.out.println("[Notice] Source and target are the same paper (" + sourceId + "). No paths to enumerate.");
                return;
            }

            GraphTraversal traverser = new GraphTraversal();
            DynamicArray<DynamicArray<String>> allPaths = traverser.findAllPaths(graph, sourceId, targetId);

            if (allPaths.isEmpty()) {
                System.out.println("[Result] No directed paths found from [" + sourceId + "] to [" + targetId + "].");
                return;
            }

            int totalNodes = graph.vertexCount();
            System.out.println("\n===================== ALL PATHS: [" + sourceId + "] -> [" + targetId + "] =====================");
            System.out.println("Total paths found: " + allPaths.size());
            System.out.println("------------------------------------------------------------------------");
            System.out.println("Citation path diagram:");
            System.out.print(GraphRenderer.render(allPaths));
            System.out.println("------------------------------------------------------------------------");

            for (int p = 0; p < allPaths.size(); p++) {
                DynamicArray<String> path = allPaths.get(p);
                StringBuilder sb = new StringBuilder();
                for (int k = 0; k < path.size(); k++) {
                    if (k > 0) sb.append(" -> ");
                    sb.append(path.get(k));
                }

                boolean hamiltonian = GraphTraversal.isHamiltonianPath(path, totalNodes);
                System.out.printf("  Path %d (hops=%d): %s %s%n",
                        (p + 1), path.size() - 1, sb.toString(),
                        hamiltonian ? "[HAMILTONIAN]" : "");
            }
            System.out.println("========================================================================");
        } catch (Exception e) {
            System.out.println("[Error] An unexpected error occurred during path enumeration: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Option 11: Optimal Citation Path (Bitmask DP)
    // -------------------------------------------------------------------------
    private void handleOptimalCitationPath() {
        System.out.println("\n--- Optimal Citation Path (Bitmask DP) ---");
        try {
            if (graph.vertexCount() == 0) {
                System.out.println("[Notice] Graph is empty. No papers available.");
                return;
            }

            String input = readLine("Enter comma-separated paper IDs to visit (e.g. P101,P102,P104): ");
            if (input == null) return;
            input = input.trim();
            if (input.isEmpty()) {
                System.out.println("[Error] No paper IDs entered.");
                return;
            }

            java.util.List<String> paperIds = new java.util.ArrayList<>();
            String[] parts = input.split(",");
            for (String part : parts) {
                String id = part.trim();
                if (!id.isEmpty()) {
                    paperIds.add(id);
                }
            }

            if (paperIds.isEmpty()) {
                System.out.println("[Error] No valid paper IDs entered.");
                return;
            }

            GraphTraversal traverser = new GraphTraversal(graph);
            String result = traverser.optimalCitationPath(paperIds);

            System.out.println("\n================ OPTIMAL CITATION PATH (BITMASK DP) ================");
            System.out.println("Requested papers : " + paperIds.size());
            System.out.println("Result           : " + result);
            System.out.println("==================================================================");
        } catch (IllegalArgumentException e) {
            System.out.println("[Error] " + e.getMessage());
        } catch (Exception e) {
            System.out.println("[Error] An unexpected error occurred while computing the optimal path: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Option 12: Exit
    // -------------------------------------------------------------------------
    private boolean handleExit() {
        if (unsavedChanges) {
            String ans = readLine("You have unsaved changes. Do you want to save before exiting? [y/N]: ");
            if (ans != null && (ans.trim().equalsIgnoreCase("y") || ans.trim().equalsIgnoreCase("yes"))) {
                handleSaveCsv();
            }
        }
        System.out.println("\nThank you for using the Citation Analysis System. Goodbye!");
        return false; // Stop main loop
    }

    // -------------------------------------------------------------------------
    // Input Helpers
    // -------------------------------------------------------------------------
    private String readLine(String prompt) {
        System.out.print(prompt);
        try {
            return reader.readLine();
        } catch (IOException e) {
            System.out.println("[Error reading input: " + e.getMessage() + "]");
            return null;
        }
    }

    private int readInt(String prompt, int defaultValue) {
        String s = readLine(prompt);
        if (s == null) return defaultValue;
        s = s.trim();
        if (s.isEmpty()) return defaultValue;
        try {
            int val = Integer.parseInt(s);
            return val > 0 ? val : defaultValue;
        } catch (NumberFormatException e) {
            System.out.println("[Notice] Invalid number. Using default value: " + defaultValue);
            return defaultValue;
        }
    }
}
