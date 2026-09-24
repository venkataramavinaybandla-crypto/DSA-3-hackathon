package io;

import core.DynamicArray;
import core.Graph;
import core.Paper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * CSV Persistence handler for loading and saving the citation graph using only {@code java.io}.
 * Strictly conforms to the zero {@code java.util} constraint.
 *
 * <p>Supports combined CSV files containing both papers and citation edges, as well as
 * separate paper and citation CSV files.
 *
 * <p>Handles RFC-4180 standard CSV quoting (commas and escaped quotes within fields).
 */
public final class CsvHandler {

    private CsvHandler() {
        // utility class, prevent instantiation
    }

    /**
     * Saves the entire state of the given Graph (all papers and directed citation edges)
     * to a single unified CSV file.
     *
     * @param graph    the citation graph to persist
     * @param filePath destination file path
     * @throws IOException if a file write error occurs
     */
    public static void save(Graph graph, String filePath) throws IOException {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("File path cannot be null or empty");
        }

        File file = new File(filePath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            // Section 1: Papers
            writer.write("# PAPERS");
            writer.newLine();
            writer.write("id,title,author,year,citationCount");
            writer.newLine();

            int vCount = graph.vertexCount();
            for (int i = 0; i < vCount; i++) {
                Paper p = graph.getPaper(i);
                writer.write(escapeCsv(p.getId()));
                writer.write(",");
                writer.write(escapeCsv(p.getTitle()));
                writer.write(",");
                writer.write(escapeCsv(p.getAuthor()));
                writer.write(",");
                writer.write(String.valueOf(p.getYear()));
                writer.write(",");
                writer.write(String.valueOf(p.getCitationCount()));
                writer.newLine();
            }

            // Section 2: Citations
            writer.write("# CITATIONS");
            writer.newLine();
            writer.write("citingPaperId,citedPaperId");
            writer.newLine();

            for (int i = 0; i < vCount; i++) {
                Paper citingPaper = graph.getPaper(i);
                DynamicArray<Integer> neighbors = graph.getNeighbors(i);
                for (int j = 0; j < neighbors.size(); j++) {
                    Paper citedPaper = graph.getPaper(neighbors.get(j));
                    writer.write(escapeCsv(citingPaper.getId()));
                    writer.write(",");
                    writer.write(escapeCsv(citedPaper.getId()));
                    writer.newLine();
                }
            }
        }
    }

    /**
     * Loads papers and citations from a CSV file into a new {@link Graph}.
     *
     * @param filePath the path of the CSV file to load
     * @return a populated Graph instance
     * @throws IOException if the file cannot be read or is malformed
     */
    public static Graph load(String filePath) throws IOException {
        Graph graph = new Graph();
        loadInto(graph, filePath);
        return graph;
    }

    /**
     * Loads papers and citations from a CSV file into an existing {@link Graph}.
     *
     * @param graph    the graph to populate
     * @param filePath the path of the CSV file to load
     * @throws IOException if the file cannot be read
     */
    public static void loadInto(Graph graph, String filePath) throws IOException {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("File path cannot be null or empty");
        }

        File file = new File(filePath);
        if (!file.exists()) {
            throw new IOException("File does not exist: " + filePath);
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            boolean inPapersSection = true;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                // Check section headers or comment markers
                if (line.equalsIgnoreCase("# PAPERS")) {
                    inPapersSection = true;
                    continue;
                }
                if (line.equalsIgnoreCase("# CITATIONS")) {
                    inPapersSection = false;
                    continue;
                }
                if (line.startsWith("#")) {
                    continue; // Skip comments
                }

                DynamicArray<String> fields = parseCsvLine(line);
                if (fields.isEmpty()) {
                    continue;
                }

                // Check header lines
                String firstField = fields.get(0).toLowerCase();
                if (firstField.equals("id") || firstField.equals("paperid")) {
                    inPapersSection = true;
                    continue;
                }
                if (firstField.equals("citingpaperid") || firstField.equals("citing") || firstField.equals("from")) {
                    inPapersSection = false;
                    continue;
                }

                if (inPapersSection) {
                    // id, title, author, year, [citationCount]
                    if (fields.size() >= 4) {
                        String id = fields.get(0);
                        String title = fields.get(1);
                        String author = fields.get(2);
                        int year = 0;
                        try {
                            year = Integer.parseInt(fields.get(3).trim());
                        } catch (NumberFormatException ignored) {}

                        int citationCount = 0;
                        if (fields.size() >= 5) {
                            try {
                                citationCount = Integer.parseInt(fields.get(4).trim());
                            } catch (NumberFormatException ignored) {}
                        }

                        Paper paper = new Paper(id, title, author, year, citationCount);
                        graph.addVertex(paper);
                    }
                } else {
                    // citingPaperId, citedPaperId
                    if (fields.size() >= 2) {
                        String citingId = fields.get(0);
                        String citedId = fields.get(1);
                        try {
                            graph.addCitation(citingId, citedId);
                        } catch (IllegalArgumentException e) {
                            // Skip invalid or missing citation vertices gracefully
                            System.err.println("Warning loading citation: " + e.getMessage());
                        }
                    }
                }
            }
        }

        // Synchronize in-degree citation counts
        syncCitationCounts(graph);
    }

    /**
     * Synchronizes each paper's citationCount field to match its in-degree
     * in the citation graph.
     *
     * @param graph the graph whose paper citation counts to synchronize
     */
    public static void syncCitationCounts(Graph graph) {
        if (graph == null) return;
        int vCount = graph.vertexCount();
        int[] inDegrees = new int[vCount];

        for (int i = 0; i < vCount; i++) {
            DynamicArray<Integer> neighbors = graph.getNeighbors(i);
            for (int j = 0; j < neighbors.size(); j++) {
                int citedIdx = neighbors.get(j);
                if (citedIdx >= 0 && citedIdx < vCount) {
                    inDegrees[citedIdx]++;
                }
            }
        }

        for (int i = 0; i < vCount; i++) {
            Paper p = graph.getPaper(i);
            // In case external citationCount was higher (e.g. from historical data), keep the max
            int count = Math.max(p.getCitationCount(), inDegrees[i]);
            p.setCitationCount(count);
        }
    }

    /**
     * Parses a single CSV line into tokens, properly handling quotation marks and escaped commas.
     *
     * @param line the line of text to parse
     * @return DynamicArray of field values
     */
    public static DynamicArray<String> parseCsvLine(String line) {
        DynamicArray<String> tokens = new DynamicArray<>();
        if (line == null) {
            return tokens;
        }

        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        int len = line.length();

        for (int i = 0; i < len; i++) {
            char c = line.charAt(i);

            if (c == '"') {
                if (inQuotes && i + 1 < len && line.charAt(i + 1) == '"') {
                    // Escaped quote: "" becomes single "
                    sb.append('"');
                    i++;
                } else {
                    // Toggle quote state
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                // Delimiter reached
                tokens.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString().trim());
        return tokens;
    }

    /**
     * Escapes a string value according to RFC-4180 CSV standard.
     * Wraps in quotes if it contains commas, quotes, or newlines; internal quotes are doubled.
     *
     * @param value string to escape
     * @return CSV-safe string
     */
    public static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        boolean mustQuote = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        if (!mustQuote) {
            return value;
        }
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"') {
                sb.append("\"\"");
            } else {
                sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
