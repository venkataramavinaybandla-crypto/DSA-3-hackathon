# Paper Record: P301

- **Paper ID:** P301
- **Title:** Theoretical Improvements in Algorithmic Efficiency for Network Flow Problems
- **Authors:** Jack Edmonds, Richard M. Karp
- **Year:** 1972
- **Journal:** Journal of the ACM (JACM), 19(2), 248-264
- **Domain:** Network Flow, Augmenting Paths, Graph Optimization

## Abstract
This paper presents polynomial-time algorithms for finding the maximum flow in a network and for related problems. Edmonds and Karp showed that by selecting the shortest augmenting path in the residual network at each step (measured by number of edges rather than capacities), the Ford-Fulkerson method is guaranteed to terminate in O(V * E^2) operations, independent of edge capacities.

## Role in Cerberus System
- This paper forms the foundational theoretical underpinning for Cerberus System's `EdmondsKarp.java` engine!
- Directly used in Phase 5 / Option 5 to measure scholarly citation flow bottlenecks between research clusters.
