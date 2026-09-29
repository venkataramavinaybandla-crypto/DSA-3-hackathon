package main;

import algo.FuzzyMatcher;
import algo.GraphTraversal;
import algo.KMPMatcher;
import core.DynamicArray;
import core.Graph;
import core.Paper;
import io.CsvHandler;
import report.FlowRenderer;
import report.GraphRenderer;
import report.ReportGenerator;
import report.Style;

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
                System.out.println();
                System.out.println(Style.muted("Input stream closed. Exiting."));
                break;
            }

            choice = choice.trim();
            if (choice.isEmpty()) {
                System.out.println(Style.warn("Please enter a selection from 1 to 12."));
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
                        message("[Error] Failed to display paths: " + e.getMessage());
                    }
                    break;
                case "11":
                    handleOptimalCitationPath();
                    break;
                case "12":
                    running = handleExit();
                    break;
                default:
                    message("[Error] Invalid choice: '" + choice + "'. Please enter a number between 1 and 12.");
            }
        }
    }

    private void printBanner() {
        System.out.print(Style.banner("C E R B E R U S   S Y S T E M",
                Style.muted("Pure Java Data Structures & Algorithms  " + dot() + "  Citation Analysis"),
                infoPanel()));
        // Folded to the display width, so the summary never wraps on a narrow terminal.
        System.out.println(Style.fold("  " + Style.muted("Graph loaded with ")
                + Style.emphasis(String.valueOf(graph.vertexCount()))
                + Style.muted(" research papers and ")
                + Style.emphasis(String.valueOf(graph.edgeCount()))
                + Style.muted(" citation edges."), Style.width()));
    }

    private void printMainMenu() {
        System.out.println();
        System.out.print(Style.menuBox("MAIN MENU", new String[]{
                "  1. Add a paper",
                "  2. Add a citation",
                "  3. Search a paper (Exact / Fuzzy)",
                "  4. Explore citation network reachability (Level-wise / Deep Lineage)",
                "  5. View reports (Top papers, Top authors, Trends)",
                "  6. Save current data to CSV",
                "  7. Load data from CSV",
                "  8. View paper content",
                "  9. Narrate citation chain (Shortest Path)",
                " 10. Display All Paths (Hamiltonian Check)",
                " 11. Optimal Citation Path (Bitmask DP)",
                " 12. Exit"}));
    }

    // -------------------------------------------------------------------------
    // Option 1: Add a Paper
    // -------------------------------------------------------------------------
    private void handleAddPaper() {
        section("Add a New Paper");
        String id;
        while (true) {
            id = readLine("Enter paper ID (e.g. P106): ");
            if (id == null) return;
            id = id.trim();
            if (id.isEmpty()) {
                message("[Error] Paper ID cannot be empty. Please try again.");
                continue;
            }
            if (graph.findIndexById(id) != -1) {
                message("[Error] A paper with ID '" + id + "' already exists. Please choose a unique ID.");
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
                message("[Error] Paper title cannot be empty. Please try again.");
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
                message("[Error] Author name cannot be empty. Please try again.");
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
                    message("[Error] Please enter a valid publication year between 1500 and 2100.");
                    continue;
                }
                break;
            } catch (NumberFormatException e) {
                message("[Error] Malformed year '" + yearStr + "'. Please enter a numeric year.");
            }
        }

        Paper paper = new Paper(id, title, author, year);
        int idx = graph.addVertex(paper);
        unsavedChanges = true;
        message("[Success] Paper '" + title + "' added successfully at vertex index " + idx + ".");
        System.out.println(Style.muted("Total papers in graph: ")
                + Style.emphasis(String.valueOf(graph.vertexCount())));
    }

    // -------------------------------------------------------------------------
    // Option 2: Add a Citation
    // -------------------------------------------------------------------------
    private void handleAddCitation() {
        section("Add a Citation (Directed Edge: Paper A refers to Paper B)");
        if (graph.vertexCount() < 2) {
            message("[Notice] At least 2 papers must exist to create a citation edge.");
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
                message("[Error] Paper ID '" + citingId + "' not found. Available papers:");
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
                message("[Warning] Self-citations are permitted but generally unusual. Continuing...");
            }
            toIdx = graph.findIndexById(citedId);
            if (toIdx == -1) {
                message("[Error] Paper ID '" + citedId + "' not found. Available papers:");
                printAvailablePaperIds();
                continue;
            }
            break;
        }

        // Check if edge already exists
        if (graph.getNeighbors(fromIdx).contains(toIdx)) {
            message("[Notice] Citation where Paper [" + citingId + "] refers to Paper [" + citedId + "] already exists.");
            return;
        }

        graph.addCitation(citingId, citedId);
        CsvHandler.syncCitationCounts(graph);
        unsavedChanges = true;
        message("[Success] Citation recorded: Paper [" + citingId + "] refers to Paper [" + citedId + "].");
        System.out.println(Style.muted("Total citations in graph: ")
                + Style.emphasis(String.valueOf(graph.edgeCount())));
    }

    private void printAvailablePaperIds() {
        StringBuilder sb = new StringBuilder();
        int count = graph.vertexCount();
        for (int i = 0; i < count; i++) {
            sb.append(graph.getPaper(i).getId());
            if (i < count - 1) {
                sb.append(", ");
            }
        }
        System.out.println(Style.muted("  ") + Style.note(sb.toString()));
    }

    // -------------------------------------------------------------------------
    // Option 3: Search a Paper
    // -------------------------------------------------------------------------
    private void handleSearchPaper() {
        section("Search Papers");
        if (graph.vertexCount() == 0) {
            message("[Notice] Graph is empty. No papers to search.");
            return;
        }

        String query = readLine("Enter search query: ");
        if (query == null) return;
        query = query.trim();
        if (query.isEmpty()) {
            message("[Error] Search query cannot be empty.");
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
            message("[Error] Invalid choice. Please enter 'E' for exact or 'F' for fuzzy.");
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
            message("[Result] No papers matched your search query '" + query + "'.");
        } else {
            System.out.println();
            message("[Result] Found " + matchedIndices.size() + " matching paper(s):");
            System.out.println(Style.strong(String.format("%-10s | %-35s | %-22s | %-6s | %-10s",
                    "ID", "Title", "Author", "Year", "Citations")));
            System.out.println(Style.muted("-".repeat(95)));
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
        section("Explore Citation Network Reachability");
        if (graph.vertexCount() == 0) {
            message("[Notice] Graph is empty.");
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
                message("[Error] Paper ID '" + startId + "' not found. Available IDs:");
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
            message("[Error] Invalid choice. Please enter '1' for Level-wise or '2' for Deep Lineage.");
        }

        boolean isLevelWise = traversalType.equals("1") || traversalType.startsWith("L");
        DynamicArray<Integer> visitOrder = isLevelWise
                ? GraphTraversal.bfs(graph, startIdx)
                : GraphTraversal.dfs(graph, startIdx);

        System.out.println();
        System.out.println(Style.heading((isLevelWise ? "Level-wise Reachability Exploration" : "Deep Lineage Exploration")
                + " Order"));
        System.out.println(Style.muted("Starting from ") + Style.highlight("[" + startId + "]"));
        System.out.println(Style.muted("Total reachable papers in component: ")
                + Style.emphasis(String.valueOf(visitOrder.size())));
        System.out.println(Style.rule());

        for (int i = 0; i < visitOrder.size(); i++) {
            Paper p = graph.getPaper(visitOrder.get(i));
            System.out.println(Style.muted(String.format("%2d. ", (i + 1)))
                    + Style.highlight("[" + p.getId() + "]")
                    + " \"" + p.getTitle() + "\" by " + Style.note(p.getAuthor())
                    + " (" + p.getYear() + ")");
        }

        // Narrate the citation chain from the start paper to the last paper in the traversal
        if (visitOrder.size() > 1) {
            String lastPaperId = graph.getPaper(visitOrder.get(visitOrder.size() - 1)).getId();
            GraphTraversal traverser = new GraphTraversal();
            String chain = traverser.narrateChain(graph, startId, lastPaperId);
            System.out.println(Style.muted("Narrated chain to ")
                    + Style.highlight("[" + lastPaperId + "]") + Style.muted(": ") + chain);
        }
    }

    // -------------------------------------------------------------------------
    // Option 5: View Reports Submenu
    // -------------------------------------------------------------------------
    private void handleReportsSubmenu() {
        boolean inSubmenu = true;
        while (inSubmenu) {
            System.out.println();
            System.out.print(Style.menuBox("REPORTS SUBMENU", new String[]{
                    "  1. Top N Most-Cited Papers (Stable Ranked)",
                    "  2. Top Authors by Total Citations",
                    "  3. Yearly Citation Trends",
                    "  4. Return to Main Menu"}));

            String choice = readLine("Select report (1-4): ");
            if (choice == null) return;
            choice = choice.trim();

            switch (choice) {
                case "1":
                    int nPapers = readInt("Enter number of papers to rank (default 5): ", 5);
                    System.out.println();
                    System.out.println(Style.rule("TOP " + nPapers + " MOST-CITED PAPERS"));
                    System.out.println(ReportGenerator.formatTopPapers(graph, nPapers));
                    break;
                case "2":
                    int nAuthors = readInt("Enter number of top authors to rank (default 5): ", 5);
                    System.out.println();
                    System.out.println(Style.rule("TOP " + nAuthors + " AUTHORS BY CITATIONS"));
                    System.out.println(ReportGenerator.formatTopAuthors(graph, nAuthors));
                    break;
                case "3":
                    System.out.println();
                    System.out.println(Style.rule("YEARLY CITATION TRENDS"));
                    System.out.println(ReportGenerator.formatCitationTrends(graph));
                    break;
                case "4":
                    inSubmenu = false;
                    break;
                default:
                    message("[Error] Invalid choice. Please enter a number between 1 and 4.");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Option 6: Save Current Data to CSV
    // -------------------------------------------------------------------------
    private void handleSaveCsv() {
        section("Save Citation Data to CSV");
        String filename = readLine("Enter filename to save [default: " + DEFAULT_CSV_FILE + "]: ");
        if (filename == null) return;
        filename = filename.trim();
        if (filename.isEmpty()) {
            filename = DEFAULT_CSV_FILE;
        }

        try {
            CsvHandler.save(graph, filename);
            unsavedChanges = false;
            message("[Success] Graph state saved successfully to '" + filename + "'.");
            System.out.println(Style.muted("Saved ") + Style.emphasis(String.valueOf(graph.vertexCount()))
                    + Style.muted(" papers and ") + Style.emphasis(String.valueOf(graph.edgeCount()))
                    + Style.muted(" citations."));
        } catch (IOException e) {
            message("[Error] Failed to save CSV file: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Option 7: Load Data from CSV
    // -------------------------------------------------------------------------
    private void handleLoadCsv() {
        section("Load Citation Data from CSV");
        String filename = readLine("Enter filename to load [default: " + DEFAULT_CSV_FILE + "]: ");
        if (filename == null) return;
        filename = filename.trim();
        if (filename.isEmpty()) {
            filename = DEFAULT_CSV_FILE;
        }

        File file = new File(filename);
        if (!file.exists()) {
            message("[Error] File '" + filename + "' does not exist.");
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
                message("[Info] Replacing existing data with file contents.");
                // Reload by creating new instance or copying into current
                // Since Graph doesn't have clear(), we load into a fresh graph and point to it
                // To keep internal graph reference clean:
                this.graph = newGraph;
            } else {
                // Merge into current graph
                CsvHandler.loadInto(graph, filename);
            }
            unsavedChanges = false;
            message("[Success] Loaded data from '" + filename + "'.");
            System.out.println(Style.muted("Graph now contains ") + Style.emphasis(String.valueOf(graph.vertexCount()))
                    + Style.muted(" papers and ") + Style.emphasis(String.valueOf(graph.edgeCount()))
                    + Style.muted(" citations."));
        } catch (IOException e) {
            message("[Error] Failed to load CSV file: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Option 8: View Paper Content (Real PDF Viewer)
    // -------------------------------------------------------------------------
    private void handleViewPaperContent() {
        section("View Paper Content");
        String id = readLine("Enter paper ID to view (e.g. P101, P104, P501): ");
        if (id == null) return;
        id = id.trim();
        if (id.isEmpty()) {
            message("[Error] Paper ID cannot be empty.");
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
            message("[Error] PDF file not found: research_papers/" + id + ".pdf");
            message("[Notice] Available papers in the dataset have corresponding PDF files in research_papers/<id>.pdf");
            // No PDF for this paper: fall back to printing its text content in the terminal.
            printPaperAbstractIfAvailable(id);
            offerFullTextView(id);
            return;
        }

        message("[+] Found research paper PDF: " + file.getAbsolutePath());
        try {
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.OPEN)) {
                message("[+] Launching system PDF viewer for: " + file.getName() + "...");
                java.awt.Desktop.getDesktop().open(file);
                message("[Success] Paper PDF opened in default viewer.");
            } else {
                message("[Notice] Desktop integration is not supported in this environment (headless mode).");
                System.out.println(Style.muted("Please open the PDF manually at: ") + Style.note(file.getAbsolutePath()));
            }
        } catch (Exception e) {
            message("[Notice] Unable to launch default viewer: " + e.getMessage());
            System.out.println(Style.muted("Please open the PDF manually at: ") + Style.note(file.getAbsolutePath()));
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
            message("[Notice] Abstract file found but could not be read: " + e.getMessage());
            return;
        }

        if (content.trim().isEmpty()) {
            return;
        }

        System.out.println(Style.rule("PAPER CONTENT (TEXT FALLBACK)"));
        System.out.println(content);
        System.out.println(Style.rule());
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
            message("[Notice] Full text file found but could not be read: " + e.getMessage());
            return;
        }

        System.out.println(Style.note("Full text available (" + content.length() + " characters) \u2014 view? [Y/N]"));
        String answer = readLine("> ");
        if (answer == null) {
            return;
        }
        answer = answer.trim();
        if (!answer.equalsIgnoreCase("y") && !answer.equalsIgnoreCase("yes")) {
            return;
        }

        System.out.println(Style.rule("FULL TEXT: " + id));
        System.out.println(content);
        System.out.println(Style.rule());
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
        section("Narrate Citation Chain (Shortest Path)");
        try {
            if (graph.vertexCount() < 2) {
                message("[Notice] Need at least 2 papers in the graph to find a citation chain.");
                return;
            }

            String startId;
            while (true) {
                startId = readLine("Enter START paper ID (e.g. P101): ");
                if (startId == null) return;
                startId = startId.trim();
                if (startId.isEmpty()) {
                    message("[Error] Start paper ID cannot be empty.");
                    continue;
                }
                if (graph.findIndexById(startId) == -1) {
                    message("[Error] Paper ID '" + startId + "' not found. Available IDs:");
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
                    message("[Error] End paper ID cannot be empty.");
                    continue;
                }
                if (graph.findIndexById(endId) == -1) {
                    message("[Error] Paper ID '" + endId + "' not found. Available IDs:");
                    printAvailablePaperIds();
                    continue;
                }
                break;
            }

            GraphTraversal traverser = new GraphTraversal(graph);
            String chain = traverser.narrateChain(startId, endId);
            int hops = traverser.getChainLength();

            System.out.println();
            System.out.println(Style.rule("CITATION CHAIN NARRATION"));
            System.out.println(Style.field("From", "[" + startId + "]")
                    + "  " + Style.field("To", "[" + endId + "]"));
            System.out.println(Style.field("Hop Count", hops == -1 ? "Unreachable" : String.valueOf(hops)));
            System.out.println(Style.field("Narration", chain));
            System.out.println(Style.rule());
        } catch (Exception e) {
            message("[Error] An error occurred while narrating chain: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Option 10: Display All Paths (Hamiltonian Check)
    // -------------------------------------------------------------------------
    private void handleDisplayAllPaths() {
        section("Display All Paths (Hamiltonian Check)");
        try {
            if (graph.vertexCount() < 2) {
                message("[Notice] Need at least 2 papers in the graph to find paths.");
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
                    message("[Error] Paper ID '" + sourceId + "' not found. Available IDs:");
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
                    message("[Error] Paper ID '" + targetId + "' not found. Available IDs:");
                    printAvailablePaperIds();
                    continue;
                }
                break;
            }

            if (sourceIdx == targetIdx) {
                message("[Notice] Source and target are the same paper (" + sourceId + "). No paths to enumerate.");
                return;
            }

            GraphTraversal traverser = new GraphTraversal();
            DynamicArray<DynamicArray<String>> allPaths = traverser.findAllPaths(graph, sourceId, targetId);

            if (allPaths.isEmpty()) {
                message("[Result] No directed paths found from [" + sourceId + "] to [" + targetId + "].");
                return;
            }

            int totalNodes = graph.vertexCount();
            System.out.println();
            System.out.println(Style.rule("ALL PATHS: [" + sourceId + "] -> [" + targetId + "]"));
            System.out.println(Style.muted("Total paths found: ") + Style.emphasis(String.valueOf(allPaths.size())));
            System.out.println();
            System.out.println(Style.muted("Citation network diagram:"));
            System.out.print(Style.diagram(GraphRenderer.render(allPaths)));
            System.out.println();
            System.out.println(Style.muted("Path flows:"));
            int boxWidth = FlowRenderer.boxWidth(allPaths);

            for (int p = 0; p < allPaths.size(); p++) {
                DynamicArray<String> path = allPaths.get(p);
                boolean hamiltonian = GraphTraversal.isHamiltonianPath(path, totalNodes);
                System.out.print(FlowRenderer.renderPath(path, p + 1, hamiltonian, boxWidth));
            }
            System.out.println(Style.rule());
        } catch (Exception e) {
            message("[Error] An unexpected error occurred during path enumeration: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Option 11: Optimal Citation Path (Bitmask DP)
    // -------------------------------------------------------------------------
    private void handleOptimalCitationPath() {
        section("Optimal Citation Path (Bitmask DP)");
        try {
            if (graph.vertexCount() == 0) {
                message("[Notice] Graph is empty. No papers available.");
                return;
            }

            String input = readLine("Enter comma-separated paper IDs to visit (e.g. P101,P102,P104): ");
            if (input == null) return;
            input = input.trim();
            if (input.isEmpty()) {
                message("[Error] No paper IDs entered.");
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
                message("[Error] No valid paper IDs entered.");
                return;
            }

            GraphTraversal traverser = new GraphTraversal(graph);
            String result = traverser.optimalCitationPath(paperIds);
            DynamicArray<String> route = FlowRenderer.parseIds(result);

            System.out.println();
            System.out.println(Style.rule("OPTIMAL CITATION PATH (BITMASK DP)"));
            System.out.println(Style.field("Requested papers", String.valueOf(paperIds.size())));

            if (route.isEmpty()) {
                // No ordering visits every requested paper: report the engine's own explanation.
                System.out.println(Style.field("Result", result));
            } else {
                System.out.println(Style.field("Total hops", String.valueOf(route.size() - 1)));
                System.out.println(Style.field("Papers visited", String.valueOf(route.size())));

                DynamicArray<DynamicArray<String>> pathSet = new DynamicArray<>();
                pathSet.add(route);

                System.out.println();
                System.out.println(Style.muted("Citation graph of the optimal route:"));
                System.out.print(Style.diagram(GraphRenderer.render(pathSet)));
                System.out.println();
                System.out.println(Style.muted("Optimal route:"));
                System.out.print(FlowRenderer.renderSequence(route, FlowRenderer.boxWidth(pathSet)));
            }
            System.out.println(Style.rule());
        } catch (IllegalArgumentException e) {
            message("[Error] " + e.getMessage());
        } catch (Exception e) {
            message("[Error] An unexpected error occurred while computing the optimal path: " + e.getMessage());
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
        System.out.println();
        System.out.println(Style.highlight("Thank you for using the Citation Analysis System. Goodbye!"));
        return false; // Stop main loop
    }

    // -------------------------------------------------------------------------
    // Output Helpers (cosmetic only - no prompt or control text is altered)
    // -------------------------------------------------------------------------

    /** @return a middle dot on terminals that can encode one, else a plain ASCII stand-in */
    private static String dot() {
        return Style.unicode() ? "\u00B7" : "-";
    }

    /**
     * Builds the fetch-style information block shown beside the Cerberus logo. Every value is read
     * from the running application, so the panel never claims anything the system is not doing.
     */
    private String[] infoPanel() {
        return new String[]{
                infoRow("OS", System.getProperty("os.name", "?") + " (" + System.getProperty("os.arch", "?") + ")"),
                infoRow("Java", System.getProperty("java.version", "?")),
                infoRow("Dataset", DEFAULT_CSV_FILE),
                infoRow("Papers", String.valueOf(graph.vertexCount())),
                infoRow("Citations", String.valueOf(graph.edgeCount())),
                infoRow("Search", "KMP / Rabin-Karp / Fuzzy"),
                infoRow("Traversal", "BFS / DFS / Bitmask DP"),
                infoRow("Structures", "Custom HashTable"),
                infoRow("Reports", "MergeSort (stable)"),
                infoRow("Theme", themeName())
        };
    }

    private static String infoRow(String label, String value) {
        return Style.note(Style.padRight(label, 11)) + Style.strong(value);
    }

    private String themeName() {
        Style.Depth depth = Style.depth();
        if (depth == Style.Depth.TRUECOLOR) {
            return "truecolour";
        }
        if (depth == Style.Depth.XTERM256) {
            return "256-colour";
        }
        return "plain";
    }

    /** Prints a blank line, an open section heading, and the hairline rule beneath it. */
    private void section(String title) {
        System.out.println();
        System.out.println(Style.heading(title));
        System.out.println(Style.rule());
    }

    /** Prints a status message, colourising a leading {@code [Tag]} where the terminal supports it. */
    private void message(String text) {
        System.out.println(Style.status(text));
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
            message("[Notice] Invalid number. Using default value: " + defaultValue);
            return defaultValue;
        }
    }
}
