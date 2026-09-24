package algo;

import core.ArrayQueue;
import core.ArrayStack;
import core.DynamicArray;
import core.Graph;

import java.util.ArrayList;
import java.util.List;

/**
 * Graph traversal algorithms including Breadth-First Search (BFS) and Depth-First Search (DFS).
 * Uses ArrayQueue and ArrayStack without standard collections.
 */
public class GraphTraversal {

    /** Citation graph instance (optional, for instance-based traversal calls). */
    private Graph graph;

    /** Default constructor. */
    public GraphTraversal() {}

    /**
     * Constructs a GraphTraversal bound to a specific citation graph.
     *
     * @param graph the citation graph
     */
    public GraphTraversal(Graph graph) {
        this.graph = graph;
    }

    /**
     * Sets the citation graph for this traverser.
     *
     * @param graph the citation graph
     */
    public void setGraph(Graph graph) {
        this.graph = graph;
    }

    /**
     * Gets the citation graph associated with this traverser.
     *
     * @return the citation graph, or null if unset
     */
    public Graph getGraph() {
        return this.graph;
    }

    /** Last computed chain length (hop count), -1 if unreachable. */
    private int lastChainLength = -1;

    /**
     * Traverses the graph in Breadth-First Search (BFS) order starting from startIndex.
     * Only visits the reachable component from startIndex.
     *
     * @param graph      the graph to traverse
     * @param startIndex the starting vertex index
     * @return dynamic array of visited vertex indices in BFS order
     */
    public static DynamicArray<Integer> bfs(Graph graph, int startIndex) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        if (startIndex < 0 || startIndex >= graph.vertexCount()) {
            throw new IndexOutOfBoundsException("Start index out of bounds: " + startIndex + ", Vertex count: " + graph.vertexCount());
        }

        DynamicArray<Integer> visitOrder = new DynamicArray<>();
        boolean[] visited = new boolean[graph.vertexCount()];
        ArrayQueue<Integer> queue = new ArrayQueue<>();

        visited[startIndex] = true;
        queue.enqueue(startIndex);

        while (!queue.isEmpty()) {
            int current = queue.dequeue();
            visitOrder.add(current);

            DynamicArray<Integer> neighbors = graph.getNeighbors(current);
            for (int i = 0; i < neighbors.size(); i++) {
                int neighbor = neighbors.get(i);
                if (!visited[neighbor]) {
                    visited[neighbor] = true;
                    queue.enqueue(neighbor);
                }
            }
        }

        return visitOrder;
    }

    /**
     * Traverses the graph in Depth-First Search (DFS) order starting from startIndex.
     * Iterative implementation using ArrayStack.
     * Only visits the reachable component from startIndex.
     *
     * @param graph      the graph to traverse
     * @param startIndex the starting vertex index
     * @return dynamic array of visited vertex indices in DFS order
     */
    public static DynamicArray<Integer> dfs(Graph graph, int startIndex) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        if (startIndex < 0 || startIndex >= graph.vertexCount()) {
            throw new IndexOutOfBoundsException("Start index out of bounds: " + startIndex + ", Vertex count: " + graph.vertexCount());
        }

        DynamicArray<Integer> visitOrder = new DynamicArray<>();
        boolean[] visited = new boolean[graph.vertexCount()];
        ArrayStack<Integer> stack = new ArrayStack<>();

        stack.push(startIndex);

        while (!stack.isEmpty()) {
            int current = stack.pop();

            if (!visited[current]) {
                visited[current] = true;
                visitOrder.add(current);

                DynamicArray<Integer> neighbors = graph.getNeighbors(current);
                // Push neighbors in reverse order so that neighbor at index 0 is popped and visited first
                for (int i = neighbors.size() - 1; i >= 0; i--) {
                    int neighbor = neighbors.get(i);
                    if (!visited[neighbor]) {
                        stack.push(neighbor);
                    }
                }
            }
        }

        return visitOrder;
    }

    /**
     * Narrates a citation chain between two papers. Reuses existing bfs() to
     * first verify reachability, then reconstructs one shortest path using a
     * parent-tracking BFS and formats the chain as:
     * "Paper &lt;A&gt; refers to Paper &lt;B&gt; &amp; Paper &lt;B&gt; refers to Paper &lt;C&gt;,
     * so Paper &lt;A&gt; refers to Paper &lt;C&gt;."
     *
     * @param graph   the citation graph
     * @param startId the ID of the starting paper
     * @param endId   the ID of the ending paper
     * @return the narrated chain string, or "No path found." if unreachable
     */
    public String narrateChain(Graph graph, String startId, String endId) {
        int startIdx = graph.findIndexById(startId);
        int endIdx = graph.findIndexById(endId);

        if (startIdx == -1 || endIdx == -1) {
            lastChainLength = -1;
            return "No path found.";
        }

        if (startIdx == endIdx) {
            lastChainLength = 0;
            return "Paper " + startId + " is the same as Paper " + endId + ".";
        }

        // Use existing bfs() to verify that endIdx is reachable from startIdx
        DynamicArray<Integer> bfsOrder = bfs(graph, startIdx);
        boolean reachable = false;
        for (int i = 0; i < bfsOrder.size(); i++) {
            if (bfsOrder.get(i) == endIdx) {
                reachable = true;
                break;
            }
        }

        if (!reachable) {
            lastChainLength = -1;
            return "No path found.";
        }

        // Reconstruct one shortest path via parent-tracking BFS
        int n = graph.vertexCount();
        int[] parent = new int[n];
        boolean[] visited = new boolean[n];
        for (int i = 0; i < n; i++) {
            parent[i] = -1;
        }

        ArrayQueue<Integer> queue = new ArrayQueue<>();
        visited[startIdx] = true;
        queue.enqueue(startIdx);

        while (!queue.isEmpty()) {
            int current = queue.dequeue();
            if (current == endIdx) {
                break;
            }
            DynamicArray<Integer> neighbors = graph.getNeighbors(current);
            for (int i = 0; i < neighbors.size(); i++) {
                int neighbor = neighbors.get(i);
                if (!visited[neighbor]) {
                    visited[neighbor] = true;
                    parent[neighbor] = current;
                    queue.enqueue(neighbor);
                }
            }
        }

        // Backtrack to build the path from start to end
        DynamicArray<String> pathIds = new DynamicArray<>();
        int cur = endIdx;
        while (cur != -1) {
            pathIds.add(graph.getPaper(cur).getId());
            cur = parent[cur];
        }

        // Reverse the path (it's currently end -> start)
        DynamicArray<String> reversedPath = new DynamicArray<>();
        for (int i = pathIds.size() - 1; i >= 0; i--) {
            reversedPath.add(pathIds.get(i));
        }

        lastChainLength = reversedPath.size() - 1;

        // Build the narrated chain
        // For each consecutive pair: "Paper <A> refers to Paper <B>"
        // Join pairs with " & "
        // Append ", so Paper <first> refers to Paper <last>."
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < reversedPath.size() - 1; i++) {
            if (i > 0) {
                sb.append(" & ");
            }
            sb.append("Paper ").append(reversedPath.get(i))
              .append(" refers to Paper ").append(reversedPath.get(i + 1));
        }

        if (reversedPath.size() > 2) {
            sb.append(", so Paper ").append(reversedPath.get(0))
              .append(" refers to Paper ").append(reversedPath.get(reversedPath.size() - 1));
        }
        sb.append(".");

        return sb.toString();
    }

    /**
     * Narrates a citation chain using the traverser's bound graph.
     *
     * @param startId the ID of the starting paper
     * @param endId   the ID of the ending paper
     * @return the narrated chain string
     */
    public String narrateChain(String startId, String endId) {
        if (this.graph == null) {
            throw new IllegalStateException("Graph has not been set for GraphTraversal");
        }
        return narrateChain(this.graph, startId, endId);
    }

    /**
     * Returns the hop count of the last chain computed by {@link #narrateChain},
     * or -1 if no path was found or narrateChain has not been called.
     *
     * @return the hop count, or -1 if unreachable
     */
    public int getChainLength() {
        return lastChainLength;
    }

    /** Hard cap on the number of paths enumerated by {@link #findAllPaths}. */
    private static final int MAX_PATHS = 10_000;

    /**
     * Finds all simple (loop-free) directed paths from {@code startId} to
     * {@code endId} using recursive DFS with backtracking.
     * <p>
     * Enumeration is hard-capped at {@value #MAX_PATHS} paths. If the cap is
     * reached a warning is printed to {@code System.err}.
     *
     * @param graph   the citation graph
     * @param startId the source paper ID
     * @param endId   the target paper ID
     * @return a DynamicArray of paths, where each path is a DynamicArray of paper IDs
     */
    public DynamicArray<DynamicArray<String>> findAllPaths(Graph graph, String startId, String endId) {
        DynamicArray<DynamicArray<String>> allPaths = new DynamicArray<>();

        int startIdx = graph.findIndexById(startId);
        int endIdx = graph.findIndexById(endId);

        if (startIdx == -1 || endIdx == -1) {
            return allPaths;
        }

        boolean[] visited = new boolean[graph.vertexCount()];
        DynamicArray<Integer> currentPath = new DynamicArray<>();

        dfsEnumerate(graph, startIdx, endIdx, visited, currentPath, allPaths);
        return allPaths;
    }

    /**
     * Finds all simple (loop-free) directed paths from {@code startId} to
     * {@code endId} using the bound citation graph, returning a standard {@link List} of paths.
     *
     * @param startId the source paper ID
     * @param endId   the target paper ID
     * @return a List of paths, where each path is a List of paper IDs
     */
    public List<List<String>> findAllPaths(String startId, String endId) {
        if (this.graph == null) {
            throw new IllegalStateException("Graph has not been set for GraphTraversal");
        }
        return findAllPathsList(this.graph, startId, endId);
    }

    /**
     * Finds all simple (loop-free) directed paths from {@code startId} to
     * {@code endId} returning a standard {@link List} of paths.
     *
     * @param graph   the citation graph
     * @param startId the source paper ID
     * @param endId   the target paper ID
     * @return a List of paths, where each path is a List of paper IDs
     */
    public List<List<String>> findAllPathsList(Graph graph, String startId, String endId) {
        DynamicArray<DynamicArray<String>> dArrayPaths = findAllPaths(graph, startId, endId);
        List<List<String>> listPaths = new ArrayList<>();
        for (int i = 0; i < dArrayPaths.size(); i++) {
            DynamicArray<String> dPath = dArrayPaths.get(i);
            List<String> lPath = new ArrayList<>();
            for (int j = 0; j < dPath.size(); j++) {
                lPath.add(dPath.get(j));
            }
            listPaths.add(lPath);
        }
        return listPaths;
    }

    /**
     * Recursive DFS helper that enumerates all simple paths.
     * Stops adding new paths once {@link #MAX_PATHS} is reached.
     */
    private void dfsEnumerate(Graph graph, int current, int endIdx,
                              boolean[] visited, DynamicArray<Integer> currentPath,
                              DynamicArray<DynamicArray<String>> allPaths) {

        if (allPaths.size() >= MAX_PATHS) {
            return;
        }

        visited[current] = true;
        currentPath.add(current);

        if (current == endIdx) {
            // Snapshot current path as paper IDs
            DynamicArray<String> pathCopy = new DynamicArray<>();
            for (int i = 0; i < currentPath.size(); i++) {
                pathCopy.add(graph.getPaper(currentPath.get(i)).getId());
            }
            allPaths.add(pathCopy);

            if (allPaths.size() >= MAX_PATHS) {
                System.out.println("[Warning] Path enumeration hard-cap of " + MAX_PATHS
                        + " reached. Results are truncated.");
                System.err.println("[Warning] Path enumeration hard-cap of " + MAX_PATHS
                        + " reached. Results are truncated.");
            }
        } else {
            DynamicArray<Integer> neighbors = graph.getNeighbors(current);
            for (int i = 0; i < neighbors.size(); i++) {
                int neighbor = neighbors.get(i);
                if (!visited[neighbor]) {
                    dfsEnumerate(graph, neighbor, endIdx, visited, currentPath, allPaths);
                    if (allPaths.size() >= MAX_PATHS) {
                        break;
                    }
                }
            }
        }

        // Backtrack
        visited[current] = false;
        currentPath.remove(currentPath.size() - 1);
    }

    /**
     * Checks whether a given path is a Hamiltonian path, i.e. it visits every
     * node in the graph exactly once.
     *
     * @param path           a single path represented as a DynamicArray of paper IDs
     * @param totalNodeCount the total number of nodes in the graph
     * @return {@code true} if the path visits exactly {@code totalNodeCount}
     *         distinct nodes (i.e. every node once)
     */
    public static boolean isHamiltonianPath(DynamicArray<String> path, int totalNodeCount) {
        if (path == null || totalNodeCount <= 0 || path.size() != totalNodeCount) {
            return false;
        }
        // Verify all elements are distinct
        for (int i = 0; i < path.size(); i++) {
            for (int j = i + 1; j < path.size(); j++) {
                if (path.get(i).equals(path.get(j))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Checks whether a given path is a Hamiltonian path, i.e. it visits every
     * node in the graph exactly once.
     *
     * @param path           a single path represented as a List of paper IDs
     * @param totalNodeCount the total number of nodes in the graph
     * @return {@code true} if the path visits exactly {@code totalNodeCount}
     *         distinct nodes (i.e. every node once)
     */
    public static boolean isHamiltonianPath(List<String> path, int totalNodeCount) {
        if (path == null || totalNodeCount <= 0 || path.size() != totalNodeCount) {
            return false;
        }
        // Verify all elements are distinct
        for (int i = 0; i < path.size(); i++) {
            for (int j = i + 1; j < path.size(); j++) {
                if (path.get(i).equals(path.get(j))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Maximum number of paper IDs accepted by {@link #optimalCitationPath(List)}. */
    public static final int MAX_OPTIMAL_SUBSET_SIZE = 20;

    /**
     * Computes the minimum-cost directed citation path that visits every paper in
     * {@code paperIds}, using a Held-Karp style bitmask dynamic programming table
     * {@code dp[mask][lastNode]}.
     *
     * <p>The cost of moving between two requested papers is the number of hops along the
     * shortest directed citation path between them (passing through any other papers as
     * intermediates), or effectively infinity when no such path exists. The computation is
     * therefore a shortest Hamiltonian path over the requested subset, evaluated on the
     * metric closure of the citation graph. Only existing directed edges are ever used.
     *
     * <p>This method is purely additive: {@link #findAllPaths} and
     * {@link #isHamiltonianPath} are left untouched.
     *
     * @param graph    the citation graph
     * @param paperIds the paper IDs that must all be visited (at most
     *                 {@value #MAX_OPTIMAL_SUBSET_SIZE})
     * @return a human-readable description of the optimal path, or a clear
     *         "No valid path" message when no ordering visits every requested paper
     * @throws IllegalArgumentException if the list is null/empty, contains a blank or
     *         duplicate ID, or exceeds {@value #MAX_OPTIMAL_SUBSET_SIZE}
     */
    public static String optimalCitationPath(Graph graph, List<String> paperIds) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        if (paperIds == null || paperIds.isEmpty()) {
            throw new IllegalArgumentException("Paper ID list cannot be null or empty");
        }
        if (paperIds.size() > MAX_OPTIMAL_SUBSET_SIZE) {
            throw new IllegalArgumentException("Optimal citation path supports at most "
                    + MAX_OPTIMAL_SUBSET_SIZE + " paper IDs, but received " + paperIds.size() + ".");
        }

        int n = paperIds.size();
        String[] ids = new String[n];
        int[] vertex = new int[n];

        // Resolve every requested paper ID, rejecting blanks, duplicates and unknown IDs.
        for (int i = 0; i < n; i++) {
            String raw = paperIds.get(i);
            String id = (raw == null) ? "" : raw.trim();
            if (id.isEmpty()) {
                throw new IllegalArgumentException("Paper ID at position " + i + " is null or empty");
            }
            for (int j = 0; j < i; j++) {
                if (ids[j].equals(id)) {
                    throw new IllegalArgumentException("Duplicate paper ID in subset: " + id);
                }
            }
            int idx = graph.findIndexById(id);
            if (idx == -1) {
                return "No valid path exists: paper ID '" + id + "' was not found in the graph.";
            }
            ids[i] = id;
            vertex[i] = idx;
        }

        if (n == 1) {
            return "Minimum-cost citation path (hops=0): " + ids[0];
        }

        final int INF = Integer.MAX_VALUE / 4;
        int vertexCount = graph.vertexCount();

        // Metric closure: dist[i][j] = shortest directed hop count from requested paper i to j.
        // parentFrom[i] is the BFS tree rooted at requested paper i, used to rebuild the walk.
        int[][] dist = new int[n][n];
        int[][] parentFrom = new int[n][];
        for (int i = 0; i < n; i++) {
            int[] parent = new int[vertexCount];
            java.util.Arrays.fill(parent, -1);
            int[] hops = new int[vertexCount];
            java.util.Arrays.fill(hops, -1);

            ArrayQueue<Integer> queue = new ArrayQueue<>();
            hops[vertex[i]] = 0;
            queue.enqueue(vertex[i]);
            while (!queue.isEmpty()) {
                int current = queue.dequeue();
                DynamicArray<Integer> neighbors = graph.getNeighbors(current);
                for (int k = 0; k < neighbors.size(); k++) {
                    int neighbor = neighbors.get(k);
                    if (hops[neighbor] == -1) {
                        hops[neighbor] = hops[current] + 1;
                        parent[neighbor] = current;
                        queue.enqueue(neighbor);
                    }
                }
            }

            parentFrom[i] = parent;
            for (int j = 0; j < n; j++) {
                dist[i][j] = (hops[vertex[j]] == -1) ? INF : hops[vertex[j]];
            }
        }

        // Held-Karp bitmask DP over the requested papers.
        int fullMask = (1 << n) - 1;
        int[] dp = new int[(fullMask + 1) * n];
        java.util.Arrays.fill(dp, INF);
        for (int i = 0; i < n; i++) {
            dp[(1 << i) * n + i] = 0;
        }

        for (int mask = 1; mask <= fullMask; mask++) {
            int rowBase = mask * n;
            for (int last = 0; last < n; last++) {
                if ((mask & (1 << last)) == 0) {
                    continue;
                }
                int currentCost = dp[rowBase + last];
                if (currentCost >= INF) {
                    continue;
                }
                for (int next = 0; next < n; next++) {
                    if ((mask & (1 << next)) != 0) {
                        continue;
                    }
                    int step = dist[last][next];
                    if (step >= INF) {
                        continue;
                    }
                    int nextMask = mask | (1 << next);
                    int candidate = currentCost + step;
                    if (candidate < dp[nextMask * n + next]) {
                        dp[nextMask * n + next] = candidate;
                    }
                }
            }
        }

        int bestCost = INF;
        int bestLast = -1;
        for (int last = 0; last < n; last++) {
            int value = dp[fullMask * n + last];
            if (value < bestCost) {
                bestCost = value;
                bestLast = last;
            }
        }

        if (bestLast == -1 || bestCost >= INF) {
            return noOptimalPathMessage(n);
        }

        // Rebuild the visiting order over the subset by walking the DP table backwards.
        int[] order = new int[n];
        int mask = fullMask;
        int last = bestLast;
        boolean complete = true;
        for (int position = n - 1; position >= 0; position--) {
            order[position] = last;
            if (position == 0) {
                break;
            }
            int previousMask = mask ^ (1 << last);
            int target = dp[mask * n + last];
            int previous = -1;
            for (int candidate = 0; candidate < n; candidate++) {
                if ((previousMask & (1 << candidate)) == 0) {
                    continue;
                }
                int candidateCost = dp[previousMask * n + candidate];
                if (candidateCost >= INF || dist[candidate][last] >= INF) {
                    continue;
                }
                if (candidateCost + dist[candidate][last] == target) {
                    previous = candidate;
                    break;
                }
            }
            if (previous == -1) {
                complete = false;
                break;
            }
            last = previous;
            mask = previousMask;
        }

        if (!complete) {
            return noOptimalPathMessage(n);
        }

        // Expand the subset order into the concrete sequence of papers actually traversed.
        DynamicArray<Integer> walk = new DynamicArray<>();
        walk.add(vertex[order[0]]);
        for (int k = 0; k < n - 1; k++) {
            int source = vertex[order[k]];
            int target = vertex[order[k + 1]];
            DynamicArray<Integer> segment = new DynamicArray<>();
            int current = target;
            while (current != source) {
                segment.add(current);
                current = parentFrom[order[k]][current];
            }
            for (int s = segment.size() - 1; s >= 0; s--) {
                walk.add(segment.get(s));
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Minimum-cost citation path (hops=").append(bestCost).append("): ");
        for (int i = 0; i < walk.size(); i++) {
            if (i > 0) {
                sb.append(" -> ");
            }
            sb.append(graph.getPaper(walk.get(i)).getId());
        }
        return sb.toString();
    }

    /**
     * Computes the minimum-cost citation path visiting every paper in {@code paperIds}
     * using the traverser's bound citation graph.
     *
     * @param paperIds the paper IDs that must all be visited
     * @return a human-readable description of the optimal path, or a "No valid path" message
     * @throws IllegalStateException    if no graph has been bound to this traverser
     * @throws IllegalArgumentException if the list is null/empty, contains a blank or
     *         duplicate ID, or exceeds {@value #MAX_OPTIMAL_SUBSET_SIZE}
     */
    public String optimalCitationPath(List<String> paperIds) {
        if (this.graph == null) {
            throw new IllegalStateException("Graph has not been set for GraphTraversal");
        }
        return optimalCitationPath(this.graph, paperIds);
    }

    /** Builds the standard "no valid path" result for a subset of {@code n} papers. */
    private static String noOptimalPathMessage(int n) {
        return "No valid path exists that visits all " + n
                + " specified papers using directed citation edges.";
    }
}

