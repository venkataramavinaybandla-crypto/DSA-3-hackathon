# Paper Record: P201

- **Paper ID:** P201
- **Title:** Maximizing the Spread of Influence through a Social Network
- **Authors:** David Kempe, Jon Kleinberg, Éva Tardos
- **Year:** 2003
- **Conference:** ACM SIGKDD International Conference on Knowledge Discovery and Data Mining (KDD)
- **Domain:** Influence Cascades, Contagion Dynamics, Submodular Maximization

## Abstract
Models for the spread of influence and information through social networks have been studied in sociology and viral marketing. We consider the fundamental algorithmic problem: if we can convince a subset of individuals to adopt a new product or idea, which nodes should we target to trigger the largest cascade of adoptions? We formulate this as a discrete optimization problem under the Independent Cascade and Linear Threshold models. We prove that the influence maximization objective function is submodular and show that a greedy approximation algorithm guarantees a solution within (1 - 1/e) of the optimal.

## Role in Cerberus System
- Represents a high-in-degree vertex in the influence contagion cluster.
- Cited by modern cascade analysis and epidemic spreading papers (P207, P209, P210).
- Demonstrates multi-tier reachable cascade propagation starting from seed papers.
