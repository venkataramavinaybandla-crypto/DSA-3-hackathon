package core;

/**
 * Directed citation graph representation where vertices are papers and directed
 * edges represent citation relationships (fromIndex cites toIndex).
 * Index-based internally; uses HashTable for O(1)-average paper ID lookup.
 */
public class Graph {
    private final DynamicArray<Paper> vertices;
    private final DynamicArray<DynamicArray<Integer>> adjacency;
    private final HashTable<Integer> idIndex;   // paper id -> vertex index
    private int edgeCount;

    public Graph() {
        this.vertices = new DynamicArray<>();
        this.adjacency = new DynamicArray<>();
        this.idIndex = new HashTable<>();
        this.edgeCount = 0;
    }

    public Graph(int initialCapacity) {
        this.vertices = new DynamicArray<>(initialCapacity);
        this.adjacency = new DynamicArray<>(initialCapacity);
        this.idIndex = new HashTable<>(initialCapacity);
        this.edgeCount = 0;
    }

    /**
     * Adds a paper as a vertex in the graph.
     * If a paper with the same id already exists, returns its existing index.
     *
     * @param paper the paper to add
     * @return the assigned or existing vertex index
     */
    public int addVertex(Paper paper) {
        if (paper == null) {
            throw new IllegalArgumentException("Paper cannot be null");
        }

        // Check if paper already exists (deduplication by ID)
        int existingIndex = findIndexById(paper.getId());
        if (existingIndex != -1) {
            return existingIndex;
        }

        int newIndex = vertices.size();
        vertices.add(paper);
        adjacency.add(new DynamicArray<>());
        idIndex.put(paper.getId(), newIndex);
        return newIndex;
    }

    /**
     * Adds a directed edge representing: fromIndex cites toIndex.
     *
     * @param fromIndex the citing paper index
     * @param toIndex   the cited paper index
     */
    public void addEdge(int fromIndex, int toIndex) {
        validateVertexIndex(fromIndex);
        validateVertexIndex(toIndex);

        DynamicArray<Integer> neighbors = adjacency.get(fromIndex);
        // Prevent duplicate edges
        if (!neighbors.contains(toIndex)) {
            neighbors.add(toIndex);
            edgeCount++;
        }
    }

    /**
     * Finds the vertex index for a given paper ID in O(1) average time via HashTable lookup.
     *
     * @param paperId the unique paper ID to search for
     * @return the vertex index, or -1 if not found
     */
    public int findIndexById(String paperId) {
        if (paperId == null) {
            return -1;
        }
        Integer index = idIndex.get(paperId);
        return (index != null) ? index : -1;
    }

    /**
     * Convenience method to add a citation between two papers using their string IDs.
     *
     * @param citingPaperId the ID of the citing paper
     * @param citedPaperId  the ID of the cited paper
     */
    public void addCitation(String citingPaperId, String citedPaperId) {
        int fromIndex = findIndexById(citingPaperId);
        if (fromIndex == -1) {
            throw new IllegalArgumentException("Citing paper ID not found in graph: " + citingPaperId);
        }

        int toIndex = findIndexById(citedPaperId);
        if (toIndex == -1) {
            throw new IllegalArgumentException("Cited paper ID not found in graph: " + citedPaperId);
        }

        addEdge(fromIndex, toIndex);
    }

    /**
     * Returns the paper stored at the specified vertex index.
     *
     * @param index the vertex index
     * @return the Paper instance
     */
    public Paper getPaper(int index) {
        validateVertexIndex(index);
        return vertices.get(index);
    }

    /**
     * Returns the list of neighbor vertex indices that the given vertex cites.
     *
     * @param index the vertex index
     * @return dynamic array of cited vertex indices
     */
    public DynamicArray<Integer> getNeighbors(int index) {
        validateVertexIndex(index);
        return adjacency.get(index);
    }

    /**
     * Returns the total number of vertices (papers) in the graph.
     *
     * @return vertex count
     */
    public int vertexCount() {
        return vertices.size();
    }

    /**
     * Returns the total number of directed edges (citations) in the graph.
     *
     * @return edge count
     */
    public int edgeCount() {
        return edgeCount;
    }

    private void validateVertexIndex(int index) {
        if (index < 0 || index >= vertices.size()) {
            throw new IndexOutOfBoundsException("Invalid vertex index: " + index + ", Vertex count: " + vertices.size());
        }
    }
}
