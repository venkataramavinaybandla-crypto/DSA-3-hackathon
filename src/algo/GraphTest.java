package algo;

import core.DynamicArray;
import core.Graph;
import core.Paper;

/**
 * Test suite for Graph core and GraphTraversal algorithms (Phase 2).
 */
public class GraphTest {

    private static int totalTests = 0;
    private static int passedTests = 0;

    public static void main(String[] args) {
        System.out.println("Running Phase 2 Graph & GraphTraversal Test Suite...\n");

        testAddVertex();
        testAddEdge();
        testAddCitation();
        testBFS();
        testDFS();
        testEdgeCases();
        testNarrateChain2Hop();
        testNarrateChain3Hop();
        testFindAllPathsAndHamiltonian();
        testFixtureAB_AC_BD();
        testOptimalCitationPathKnownOptimum();
        testOptimalCitationPathNoValidPath();

        System.out.println("\n==========================================");
        System.out.println("GRAPH TEST RESULTS: " + passedTests + " / " + totalTests + " PASSED");
        System.out.println("==========================================");

        if (passedTests != totalTests) {
            System.exit(1);
        }
    }

    private static void assertTrue(String name, boolean cond) {
        totalTests++;
        if (cond) {
            passedTests++;
            System.out.println("[PASS] " + name);
        } else {
            System.err.println("[FAIL] " + name);
            throw new AssertionError("FAILED: " + name);
        }
    }

    private static void assertEquals(String name, Object expected, Object actual) {
        totalTests++;
        boolean eq = (expected == null) ? (actual == null) : expected.equals(actual);
        if (eq) {
            passedTests++;
            System.out.println("[PASS] " + name);
        } else {
            System.err.println("[FAIL] " + name + " | Expected=" + expected + " Actual=" + actual);
            throw new AssertionError("FAILED: " + name);
        }
    }

    // ------------------------------------------------------------------ addVertex
    private static void testAddVertex() {
        System.out.println("--- Graph.addVertex Tests ---");
        Graph graph = new Graph();

        Paper p0 = new Paper("P0", "Title 0", "Author 0", 2020);
        Paper p1 = new Paper("P1", "Title 1", "Author 1", 2021);
        Paper p2 = new Paper("P2", "Title 2", "Author 2", 2022);

        int idx0 = graph.addVertex(p0);
        int idx1 = graph.addVertex(p1);
        int idx2 = graph.addVertex(p2);

        assertEquals("idx0 is 0", 0, idx0);
        assertEquals("idx1 is 1", 1, idx1);
        assertEquals("idx2 is 2", 2, idx2);
        assertEquals("vertexCount is 3", 3, graph.vertexCount());

        // Duplicate id returns EXISTING index and vertexCount does not increase
        Paper p0Dup = new Paper("P0", "Duplicate Title", "Author X", 2025);
        int dupIdx = graph.addVertex(p0Dup);
        assertEquals("Duplicate addVertex returns existing idx 0", idx0, dupIdx);
        assertEquals("vertexCount stays 3 after duplicate insert", 3, graph.vertexCount());
    }

    // ------------------------------------------------------------------ addEdge
    private static void testAddEdge() {
        System.out.println("\n--- Graph.addEdge Tests ---");
        Graph graph = new Graph();
        graph.addVertex(new Paper("P0", "T0", "A0", 2020));
        graph.addVertex(new Paper("P1", "T1", "A1", 2021));

        graph.addEdge(0, 1);
        assertEquals("edgeCount is 1", 1, graph.edgeCount());

        // Duplicate edge is ignored
        graph.addEdge(0, 1);
        assertEquals("edgeCount stays 1 after adding duplicate edge", 1, graph.edgeCount());
        assertEquals("neighbors size of 0 is 1", 1, graph.getNeighbors(0).size());

        // Out-of-bounds index rejects with IndexOutOfBoundsException
        boolean threwFrom = false;
        try {
            graph.addEdge(-1, 1);
        } catch (IndexOutOfBoundsException e) {
            threwFrom = true;
        }
        assertTrue("addEdge negative fromIndex throws IndexOutOfBoundsException", threwFrom);

        boolean threwTo = false;
        try {
            graph.addEdge(0, 99);
        } catch (IndexOutOfBoundsException e) {
            threwTo = true;
        }
        assertTrue("addEdge out of bounds toIndex throws IndexOutOfBoundsException", threwTo);
    }

    // ------------------------------------------------------------------ addCitation
    private static void testAddCitation() {
        System.out.println("\n--- Graph.addCitation Tests ---");
        Graph graph = new Graph();
        graph.addVertex(new Paper("P0", "T0", "A0", 2020));
        graph.addVertex(new Paper("P1", "T1", "A1", 2021));

        graph.addCitation("P0", "P1");
        assertEquals("edgeCount is 1", 1, graph.edgeCount());
        assertTrue("neighbors of 0 contains 1", graph.getNeighbors(0).contains(1));

        // Unknown id throws IllegalArgumentException
        boolean threwCiting = false;
        try {
            graph.addCitation("UNKNOWN", "P1");
        } catch (IllegalArgumentException e) {
            threwCiting = true;
        }
        assertTrue("addCitation unknown citing paper throws IllegalArgumentException", threwCiting);

        boolean threwCited = false;
        try {
            graph.addCitation("P0", "UNKNOWN");
        } catch (IllegalArgumentException e) {
            threwCited = true;
        }
        assertTrue("addCitation unknown cited paper throws IllegalArgumentException", threwCited);
    }

    // ------------------------------------------------------------------ BFS
    private static void testBFS() {
        System.out.println("\n--- BFS Traversal Tests ---");
        // Graph structure:
        // 0 -> 1, 0 -> 2
        // 1 -> 3, 1 -> 4
        // 2 -> 5
        // 6 is disconnected
        Graph graph = new Graph();
        for (int i = 0; i <= 6; i++) {
            graph.addVertex(new Paper("P" + i, "T" + i, "A" + i, 2020 + i));
        }

        graph.addEdge(0, 1);
        graph.addEdge(0, 2);
        graph.addEdge(1, 3);
        graph.addEdge(1, 4);
        graph.addEdge(2, 5);

        DynamicArray<Integer> bfsResult = GraphTraversal.bfs(graph, 0);
        assertEquals("BFS visit count (component of 6 reachable vertices)", 6, bfsResult.size());
        assertEquals("BFS order[0] == 0", 0, (int) bfsResult.get(0));
        assertEquals("BFS order[1] == 1", 1, (int) bfsResult.get(1));
        assertEquals("BFS order[2] == 2", 2, (int) bfsResult.get(2));
        assertEquals("BFS order[3] == 3", 3, (int) bfsResult.get(3));
        assertEquals("BFS order[4] == 4", 4, (int) bfsResult.get(4));
        assertEquals("BFS order[5] == 5", 5, (int) bfsResult.get(5));
        assertTrue("BFS excludes disconnected vertex 6", !bfsResult.contains(6));
    }

    // ------------------------------------------------------------------ DFS
    private static void testDFS() {
        System.out.println("\n--- DFS Traversal Tests ---");
        // Graph structure:
        // 0 -> 1, 0 -> 2
        // 1 -> 3, 1 -> 4
        // 2 -> 5
        // 6 is disconnected
        Graph graph = new Graph();
        for (int i = 0; i <= 6; i++) {
            graph.addVertex(new Paper("P" + i, "T" + i, "A" + i, 2020 + i));
        }

        graph.addEdge(0, 1);
        graph.addEdge(0, 2);
        graph.addEdge(1, 3);
        graph.addEdge(1, 4);
        graph.addEdge(2, 5);

        DynamicArray<Integer> dfsResult = GraphTraversal.dfs(graph, 0);
        assertEquals("DFS visit count (component of 6 reachable vertices)", 6, dfsResult.size());
        assertEquals("DFS order[0] == 0", 0, (int) dfsResult.get(0));
        assertEquals("DFS order[1] == 1 (left path first)", 1, (int) dfsResult.get(1));
        assertEquals("DFS order[2] == 3 (deepest left neighbor of 1)", 3, (int) dfsResult.get(2));
        assertEquals("DFS order[3] == 4 (next neighbor of 1)", 4, (int) dfsResult.get(3));
        assertEquals("DFS order[4] == 2 (backtrack to 0, visit right branch)", 2, (int) dfsResult.get(4));
        assertEquals("DFS order[5] == 5 (deepest neighbor of 2)", 5, (int) dfsResult.get(5));
        assertTrue("DFS excludes disconnected vertex 6", !dfsResult.contains(6));
    }

    // ------------------------------------------------------------------ Edge Cases
    private static void testEdgeCases() {
        System.out.println("\n--- Edge Cases (Single Vertex & Cyclic Graph) ---");

        // Single vertex graph
        Graph single = new Graph();
        single.addVertex(new Paper("P0", "T0", "A0", 2020));

        DynamicArray<Integer> singleBfs = GraphTraversal.bfs(single, 0);
        assertEquals("Single vertex BFS size is 1", 1, singleBfs.size());
        assertEquals("Single vertex BFS element is 0", 0, (int) singleBfs.get(0));

        DynamicArray<Integer> singleDfs = GraphTraversal.dfs(single, 0);
        assertEquals("Single vertex DFS size is 1", 1, singleDfs.size());
        assertEquals("Single vertex DFS element is 0", 0, (int) singleDfs.get(0));

        // Cyclic graph: 0 -> 1 -> 2 -> 0
        Graph cyclic = new Graph();
        cyclic.addVertex(new Paper("C0", "T0", "A0", 2020));
        cyclic.addVertex(new Paper("C1", "T1", "A1", 2021));
        cyclic.addVertex(new Paper("C2", "T2", "A2", 2022));

        cyclic.addEdge(0, 1);
        cyclic.addEdge(1, 2);
        cyclic.addEdge(2, 0);

        DynamicArray<Integer> cyclicBfs = GraphTraversal.bfs(cyclic, 0);
        assertEquals("Cyclic BFS terminates with size 3", 3, cyclicBfs.size());
        assertEquals("Cyclic BFS [0]", 0, (int) cyclicBfs.get(0));
        assertEquals("Cyclic BFS [1]", 1, (int) cyclicBfs.get(1));
        assertEquals("Cyclic BFS [2]", 2, (int) cyclicBfs.get(2));

        DynamicArray<Integer> cyclicDfs = GraphTraversal.dfs(cyclic, 0);
        assertEquals("Cyclic DFS terminates with size 3", 3, cyclicDfs.size());
        assertEquals("Cyclic DFS [0]", 0, (int) cyclicDfs.get(0));
        assertEquals("Cyclic DFS [1]", 1, (int) cyclicDfs.get(1));
        assertEquals("Cyclic DFS [2]", 2, (int) cyclicDfs.get(2));
    }

    // --------------------------------------------------------- narrateChain 2-hop
    private static void testNarrateChain2Hop() {
        System.out.println("\n--- narrateChain 2-Hop Test ---");
        // Chain: A -> B -> C  (2 hops)
        Graph graph = new Graph();
        graph.addVertex(new Paper("A", "Paper A", "AuthA", 2020));
        graph.addVertex(new Paper("B", "Paper B", "AuthB", 2021));
        graph.addVertex(new Paper("C", "Paper C", "AuthC", 2022));

        graph.addCitation("A", "B");
        graph.addCitation("B", "C");

        GraphTraversal traverser = new GraphTraversal();
        String result = traverser.narrateChain(graph, "A", "C");

        String expected = "Paper A refers to Paper B & Paper B refers to Paper C, so Paper A refers to Paper C.";
        assertEquals("narrateChain 2-hop output", expected, result);
        assertEquals("getChainLength 2-hop returns 2", 2, traverser.getChainLength());
    }

    // --------------------------------------------------------- narrateChain 3-hop
    private static void testNarrateChain3Hop() {
        System.out.println("\n--- narrateChain 3-Hop Test ---");
        // Chain: X -> Y -> Z -> W  (3 hops)
        Graph graph = new Graph();
        graph.addVertex(new Paper("X", "Paper X", "AuthX", 2019));
        graph.addVertex(new Paper("Y", "Paper Y", "AuthY", 2020));
        graph.addVertex(new Paper("Z", "Paper Z", "AuthZ", 2021));
        graph.addVertex(new Paper("W", "Paper W", "AuthW", 2022));

        graph.addCitation("X", "Y");
        graph.addCitation("Y", "Z");
        graph.addCitation("Z", "W");

        GraphTraversal traverser = new GraphTraversal();
        String result = traverser.narrateChain(graph, "X", "W");

        String expected = "Paper X refers to Paper Y & Paper Y refers to Paper Z & Paper Z refers to Paper W, so Paper X refers to Paper W.";
        assertEquals("narrateChain 3-hop output", expected, result);
        assertEquals("getChainLength 3-hop returns 3", 3, traverser.getChainLength());
    }

    // ------------------------------------------------- findAllPaths & Hamiltonian
    private static void testFindAllPathsAndHamiltonian() {
        System.out.println("\n--- findAllPaths & Hamiltonian Path Tests ---");

        // Diamond graph:
        //      N1
        //    /    \
        //  N0      N3
        //    \    /
        //      N2
        Graph diamond = new Graph();
        diamond.addVertex(new Paper("N0", "Paper 0", "A0", 2020));
        diamond.addVertex(new Paper("N1", "Paper 1", "A1", 2021));
        diamond.addVertex(new Paper("N2", "Paper 2", "A2", 2022));
        diamond.addVertex(new Paper("N3", "Paper 3", "A3", 2023));

        diamond.addCitation("N0", "N1");
        diamond.addCitation("N0", "N2");
        diamond.addCitation("N1", "N3");
        diamond.addCitation("N2", "N3");

        GraphTraversal traverser = new GraphTraversal(diamond);

        // Test DynamicArray version
        DynamicArray<DynamicArray<String>> daPaths = traverser.findAllPaths(diamond, "N0", "N3");
        assertEquals("Diamond graph has 2 paths (DynamicArray)", 2, daPaths.size());

        // Test List<List<String>> version
        java.util.List<java.util.List<String>> listPaths = traverser.findAllPaths("N0", "N3");
        assertEquals("Diamond graph has 2 paths (List)", 2, listPaths.size());

        // Neither path in diamond visits all 4 nodes (each visits 3 nodes: N0->N1->N3 or N0->N2->N3)
        for (int i = 0; i < daPaths.size(); i++) {
            assertTrue("Diamond path " + i + " is not Hamiltonian (3 of 4 nodes)",
                    !GraphTraversal.isHamiltonianPath(daPaths.get(i), 4));
            assertTrue("Diamond path " + i + " is not Hamiltonian (List overload)",
                    !GraphTraversal.isHamiltonianPath(listPaths.get(i), 4));
        }

        // Linear graph visiting all 4 nodes: N0 -> N1 -> N2 -> N3
        Graph linear = new Graph();
        linear.addVertex(new Paper("L0", "P0", "A0", 2020));
        linear.addVertex(new Paper("L1", "P1", "A1", 2021));
        linear.addVertex(new Paper("L2", "P2", "A2", 2022));
        linear.addVertex(new Paper("L3", "P3", "A3", 2023));

        linear.addCitation("L0", "L1");
        linear.addCitation("L1", "L2");
        linear.addCitation("L2", "L3");

        GraphTraversal linearTraverser = new GraphTraversal(linear);
        DynamicArray<DynamicArray<String>> linPaths = linearTraverser.findAllPaths(linear, "L0", "L3");
        assertEquals("Linear graph has 1 path", 1, linPaths.size());
        assertTrue("Linear path visiting all nodes is Hamiltonian (DynamicArray)",
                GraphTraversal.isHamiltonianPath(linPaths.get(0), 4));

        java.util.List<java.util.List<String>> linListPaths = linearTraverser.findAllPaths("L0", "L3");
        assertTrue("Linear path visiting all nodes is Hamiltonian (List)",
                GraphTraversal.isHamiltonianPath(linListPaths.get(0), 4));

        // Edge case: path with duplicate nodes is not Hamiltonian
        DynamicArray<String> dupPath = new DynamicArray<>();
        dupPath.add("L0");
        dupPath.add("L1");
        dupPath.add("L0");
        dupPath.add("L3");
        assertTrue("Path with duplicate node is not Hamiltonian",
                !GraphTraversal.isHamiltonianPath(dupPath, 4));

        // Edge case: null or empty path
        assertTrue("Null path is not Hamiltonian", !GraphTraversal.isHamiltonianPath((DynamicArray<String>) null, 4));
        assertTrue("Zero nodes count is not Hamiltonian", !GraphTraversal.isHamiltonianPath(dupPath, 0));
    }

    // ------------------------------------------------- Fixture A->B, A->C, B->D
    private static void testFixtureAB_AC_BD() {
        System.out.println("\n--- Fixture A->B, A->C, B->D ---");
        Graph graph = new Graph();
        graph.addVertex(new Paper("A", "Paper A", "Author A", 2020));
        graph.addVertex(new Paper("B", "Paper B", "Author B", 2021));
        graph.addVertex(new Paper("C", "Paper C", "Author C", 2022));
        graph.addVertex(new Paper("D", "Paper D", "Author D", 2023));

        graph.addCitation("A", "B");
        graph.addCitation("A", "C");
        graph.addCitation("B", "D");

        GraphTraversal traverser = new GraphTraversal(graph);

        String narration = traverser.narrateChain(graph, "A", "D");
        System.out.println("narrateChain(A, D): " + narration);

        int chainLength = traverser.getChainLength();
        System.out.println("getChainLength: " + chainLength);

        DynamicArray<DynamicArray<String>> paths = traverser.findAllPaths(graph, "A", "D");
        System.out.println("findAllPaths(A, D): " + paths);

        assertEquals("narrateChain matches expected",
                "Paper A refers to Paper B & Paper B refers to Paper D, so Paper A refers to Paper D.", narration);
        assertEquals("getChainLength matches 2", 2, chainLength);
        assertEquals("findAllPaths count is 1", 1, paths.size());
    }

    // ------------------------------------ optimalCitationPath (known 4-node optimum)
    private static void testOptimalCitationPathKnownOptimum() {
        System.out.println("\n--- Optimal Citation Path (Bitmask DP): known 4-node optimum ---");
        // Chain A -> B -> C -> D (3 hops) is the only ordering that visits all four
        // papers; the decoy shortcut A -> D cannot reach B or C.
        Graph graph = new Graph();
        graph.addVertex(new Paper("A", "Paper A", "Author A", 2020));
        graph.addVertex(new Paper("B", "Paper B", "Author B", 2021));
        graph.addVertex(new Paper("C", "Paper C", "Author C", 2022));
        graph.addVertex(new Paper("D", "Paper D", "Author D", 2023));

        graph.addCitation("A", "B");
        graph.addCitation("B", "C");
        graph.addCitation("C", "D");
        graph.addCitation("A", "D"); // decoy shortcut

        GraphTraversal traverser = new GraphTraversal(graph);
        java.util.List<String> subset = new java.util.ArrayList<>();
        subset.add("A");
        subset.add("B");
        subset.add("C");
        subset.add("D");

        String result = traverser.optimalCitationPath(subset);
        assertEquals("optimalCitationPath returns the known optimal 4-node chain",
                "Minimum-cost citation path (hops=3): A -> B -> C -> D", result);
    }

    // ------------------------------------ optimalCitationPath (no valid path)
    private static void testOptimalCitationPathNoValidPath() {
        System.out.println("\n--- Optimal Citation Path (Bitmask DP): no valid path ---");
        // Two disjoint directed components A -> B and D -> C, so no single ordering
        // can visit all four requested papers. Must report it clearly, not crash.
        Graph graph = new Graph();
        graph.addVertex(new Paper("A", "Paper A", "Author A", 2020));
        graph.addVertex(new Paper("B", "Paper B", "Author B", 2021));
        graph.addVertex(new Paper("C", "Paper C", "Author C", 2022));
        graph.addVertex(new Paper("D", "Paper D", "Author D", 2023));

        graph.addCitation("A", "B");
        graph.addCitation("D", "C");

        GraphTraversal traverser = new GraphTraversal(graph);
        java.util.List<String> subset = new java.util.ArrayList<>();
        subset.add("A");
        subset.add("B");
        subset.add("C");
        subset.add("D");

        String result = null;
        try {
            result = traverser.optimalCitationPath(subset);
        } catch (RuntimeException e) {
            assertTrue("optimalCitationPath must not throw for an unreachable subset", false);
        }

        assertTrue("no-valid-path result is non-null", result != null);
        assertTrue("no-valid-path result clearly says 'no valid path'",
                result != null && result.toLowerCase().contains("no valid path"));
    }
}

