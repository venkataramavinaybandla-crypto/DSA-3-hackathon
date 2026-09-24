# Academic Literature Survey: Citation Analysis & Graph Mining

**Project:** Cerberus System — A Data Structures–Driven Approach to Tracking Academic Citations  
**Course:** Data Structures and Algorithms - 3 (25CS2103E)  
**Team:** Team 20 (Sec 07)  

---

## 1. Theoretical Foundations

Scholarly literature exhibits complex topological properties. When modeled as a graph $G = (V, E)$:
- **Vertices ($V$):** Represent individual scholarly publications containing attributes $\{ \text{ID}, \text{Title}, \text{Author}, \text{Year}, \text{In-Degree} \}$.
- **Directed Edges ($E$):** An edge $(u, v) \in E$ signifies that paper $u$ cites paper $v$. In-degree $\text{deg}^-(v)$ reflects total citations received, acting as a primary proxy for academic impact.

Cerberus System synthesizes four seminal bodies of academic research:

### 1.1 Information Diffusion & Influence Cascades
- **Kempe, Kleinberg, & Tardos (2003)** formulated information cascade dynamics over directed social and citation graphs under Independent Cascade and Linear Threshold models.
- **Watts & Strogatz (1998)** and **Barabási & Albert (1999)** demonstrated that real-world citation graphs exhibit "small-world" clustering and scale-free power-law degree distributions, meaning a small fraction of seminal papers capture a vast majority of citations.
- **In Cerberus System:** Used to justify reachability analysis (Level-wise spread and Deep lineage tracing) to discover which foundation papers seed entire research fields.

### 1.2 Network Flow & Influence Transmission Bottlenecks
- **Ford & Fulkerson (1956)** and **Edmonds & Karp (1972)** proved that the maximum flow through a network equals the minimum capacity cut.
- **In Cerberus System:** Citation networks are treated as pathways of scholarly influence. When evaluating the connection between two research clusters (e.g., from foundational graph algorithms to applied deep learning), the Edmonds-Karp maximum flow algorithm identifies the transmission capacity and critical citation bottlenecks connecting the domains.

### 1.3 Citation Link Analysis & Importance Ranking
- **Brin & Page (1998)** and **Kleinberg (1999)** demonstrated that link structure reveals latent document prestige. Kleinberg's HITS distinguished between *Authorities* (frequently cited papers) and *Hubs* (comprehensive survey papers).
- **In Cerberus System:** In-degree citation aggregation and custom sorting routines rank seminal papers and identify prolific authors.

### 1.4 String Matching & Fault-Tolerant Text Search
- **Knuth, Morris, & Pratt (1977)** and **Karp & Rabin (1987)** established linear-time exact substring searching.
- **Wagner & Fischer (1974)** established the dynamic programming matrix formulation for Levenshtein edit distance.
- **In Cerberus System:** Open-addressing hash tables achieve $O(1)$ average-case exact paper lookup, while KMP/Rabin-Karp and Wagner-Fischer enable fault-tolerant title search even when users input typographical errors.

---

## 2. Included Seminal Corpus Summary

| ID | Title | Authors | Year | Domain |
|---|---|---|:---:|---|
| **P101** | Attention Is All You Need | Vaswani et al. | 2017 | Deep Learning / Transformers |
| **P102** | BERT: Pre-training of Deep Bidirectional Transformers | Devlin et al. | 2018 | Language Modeling |
| **P103** | Language Models are Few-Shot Learners (GPT-3) | Brown et al. | 2020 | Foundation Models |
| **P104** | Deep Residual Learning for Image Recognition | He et al. | 2016 | Computer Vision |
| **P105** | Mastering the Game of Go with Deep Neural Networks | Silver et al. | 2016 | Reinforcement Learning |
| **P106** | Gradient-Based Learning Applied to Document Recognition | LeCun et al. | 1998 | Convolutional Networks |
| **P107** | ImageNet Classification with Deep CNNs (AlexNet) | Krizhevsky et al. | 2012 | Deep Learning |
| **P108** | Generative Adversarial Nets | Goodfellow et al. | 2014 | Generative Models |
| **P109** | Adam: A Method for Stochastic Optimization | Kingma & Ba | 2014 | Optimization |
| **P110** | Dropout: A Simple Way to Prevent Overfitting | Srivastava et al. | 2014 | Regularization |
| **P111** | Semi-Supervised Classification with GCNs | Kipf & Welling | 2017 | Graph Neural Networks |
| **P112** | Graph Attention Networks (GAT) | Veličković et al. | 2018 | Graph Neural Networks |
| **P113** | Inductive Representation Learning on Large Graphs | Hamilton et al. | 2017 | Graph Representation |
| **P114** | DeepWalk: Online Learning of Social Representations | Perozzi et al. | 2014 | Graph Embeddings |
| **P115** | node2vec: Scalable Feature Learning for Networks | Grover & Leskovec | 2016 | Graph Embeddings |
| **P201** | Maximizing the Spread of Influence through a Social Network | Kempe et al. | 2003 | Cascade Dynamics |
| **P202** | Collective Dynamics of 'Small-World' Networks | Watts & Strogatz | 1998 | Network Topology |
| **P203** | Emergence of Scaling in Random Networks | Barabási & Albert | 1999 | Scale-Free Networks |
| **P204** | Authoritative Sources in a Hyperlinked Environment | Kleinberg | 1999 | Link Analysis |
| **P205** | The Anatomy of a Large-Scale Hypertextual Web Search Engine | Brin & Page | 1998 | Web Graph Mining |
| **P206** | Threshold Models of Collective Behavior | Granovetter | 1978 | Sociological Diffusion |
| **P207** | Cost-Effective Outbreak Detection in Networks | Leskovec et al. | 2007 | Information Cascades |
| **P208** | Epidemic Spreading in Scale-Free Networks | Pastor-Satorras et al. | 2001 | Epidemic Dynamics |
| **P209** | Information Cascades in the Blogosphere | Leskovec et al. | 2007 | Cascade Mining |
| **P210** | Cascading Behavior in Networks | Easley & Kleinberg | 2010 | Network Economics |
| **P301** | Theoretical Improvements in Algorithmic Efficiency for Flow | Edmonds & Karp | 1972 | Max-Flow Algorithms |
| **P302** | Maximal Flow Through a Network | Ford & Fulkerson | 1956 | Network Flow Theory |
| **P303** | Solution of a Problem of Maximum Flow with Power Estimation | Dinic | 1970 | Blocking Flow Theory |
| **P304** | A New Approach to the Maximum-Flow Problem | Goldberg & Tarjan | 1988 | Push-Relabel Flow |
| **P305** | Depth-First Search and Linear Graph Algorithms | Tarjan | 1972 | Graph Optimization |
| **P306** | A Note on Two Problems in Connexion with Graphs | Dijkstra | 1959 | Shortest Paths |
| **P401** | Fast Pattern Matching in Strings | Knuth et al. | 1977 | String Algorithms |
| **P402** | Efficient Randomized Pattern-Matching Algorithms | Karp & Rabin | 1987 | Hash Matching |
| **P403** | The String-to-String Correction Problem | Wagner & Fischer | 1974 | Edit Distance Dynamic Prog. |
| **P404** | A Mathematical Theory of Communication | Shannon | 1948 | Information Theory |

---

## 3. How the Corpus is Integrated with Cerberus System

1. **Persistent Format:** Conforms to RFC-4180 CSV specifications in `research_papers/papers_database.csv`.
2. **IO Engine:** Streamed and parsed using Cerberus's custom `CsvHandler.java` using zero external libraries or `java.util` data structures.
3. **Graph Construction:** Each entry populates `Paper` vertices and `Graph` adjacency lists with instantaneous synchronization of citation counts.
