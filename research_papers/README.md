# 📚 Research Papers Corpus & Citation Dataset for Cerberus System

This directory contains the academic research paper dataset and theoretical literature corpus that directly powers the **Cerberus Citation Analysis System**.

---

## ❓ Frequently Asked Questions (FAQ)

### 1. How can we get all those research papers at once?
You do not need to manually download hundreds of PDFs one-by-one! In computer science and academic graph analysis, research papers are modeled by their **metadata (Paper ID, Title, Primary Authors, Publication Year, Abstract)** and their **directed citation references**.

We have set up two immediate ways to get research papers at once:
1. **Pre-compiled Seminal Corpus (`papers_database.csv`)**:
   - Contains **35 seminal research papers** across Graph Theory, Information Cascades, Network Flow, and Deep Learning, with 40+ verified directed citation edges connecting them.
   - Ready to use out of the box.
2. **Automated Live Fetcher Script (`fetch_arxiv_papers.py`)**:
   - Run `python research_papers/fetch_arxiv_papers.py 50`
   - It queries the official arXiv API and downloads the latest papers in Computer Science, Data Structures, and Information Networks directly into a structured CSV formatted for Cerberus System.

---

### 2. How do we use these Research Papers in this Project?

In Cerberus System, papers are not just static reading material — they are the **data engine** of the application:

1. **Graph Vertices:** Every research paper entry is instantiated as a `Paper` object (vertex in `Graph.java`).
2. **Directed Edges:** When Paper A cites Paper B, a directed edge `A -> B` is added to the adjacency list.
3. **Exact & Typo Search:** Papers are indexed in open-addressing Hash Tables and searched using **KMP**, **Rabin-Karp**, and **Wagner-Fischer** edit distance algorithms.
4. **Reachability Traversal:** Starting from seminal works like *Kempe et al. (P201)* or *Attention Is All You Need (P101)*, the system runs Level-wise Spread and Deep Lineage Tracing to uncover multi-generational citation cascades.
5. **Edmonds-Karp Max-Flow:** Evaluates the maximum influence capacity flowing between research subfields (e.g. from foundational Graph Theory to modern Graph Neural Networks).
6. **Analytics Reports:** Automatically tallies citation counts to rank top papers and top authors (e.g. Kleinberg, Vaswani, He, Tarjan).

---

## 🚀 How to Load the Research Papers in Cerberus System

1. Launch Cerberus System:
   ```bash
   java -cp out main.Main
   ```
2. In the Main Menu, choose:
   ```
   8. Load data from CSV
   ```
3. Press **Enter** to accept the default (`citation_data.csv`) or specify:
   ```
   research_papers/papers_database.csv
   ```
4. All 35 papers and citation links will be loaded into the graph!
5. Test the capabilities:
   - Press **3** to search for "Attention" or typo "Attenshun" (Wagner-Fischer fuzzy search).
   - Press **4** to explore reachability starting from `P103` (GPT-3) or `P201` (Kempe).
   - Press **5** to analyze flow from `P304` to `P302` or `P103` to `P104`.
   - Press **6** to view top papers and author influence rankings.

---

## 📂 Directory Structure

```
research_papers/
├── README.md                     <- This guide
├── LITERATURE_SURVEY.md          <- Academic review of the papers and theoretical models
├── papers_database.csv           <- Ready-to-load 35-paper dataset with citation edges
├── fetch_arxiv_papers.py         <- Automated API script to fetch papers in bulk
└── corpus/                       <- Individual paper abstracts and metadata
    ├── P101_Vaswani_Attention_Is_All_You_Need.md
    ├── P201_Kempe_Maximizing_Spread_Influence.md
    ├── P205_Brin_Page_PageRank_Search.md
    └── P301_Edmonds_Karp_Network_Flow.md
```
