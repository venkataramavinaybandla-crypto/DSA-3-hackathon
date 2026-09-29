<div align="center">

```
 ██████╗███████╗██████╗ ██████╗ ███████╗██████╗ ██╗   ██╗███████╗
██╔════╝██╔════╝██╔══██╗██╔══██╗██╔════╝██╔══██╗██║   ██║██╔════╝
██║     █████╗  ██████╔╝██████╔╝█████╗  ██████╔╝██║   ██║███████╗
██║     ██╔══╝  ██╔══██╗██╔══██╗██╔══╝  ██╔══██╗██║   ██║╚════██║
╚██████╗███████╗██║  ██║██████╔╝███████╗██║  ██║╚██████╔╝███████║
 ╚═════╝╚══════╝╚═╝  ╚═╝╚═════╝ ╚══════╝╚═╝  ╚═╝ ╚═════╝ ╚══════╝
     S Y S T E M
```

### 🕸️ A Data Structures–Driven Approach to Tracking Academic Citations

*Every paper is a vertex. Every citation is a directed edge. Every algorithm here was hand-built, not imported.*

<br>

[![Java](https://img.shields.io/badge/Java-JDK%2026-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Zero java.util](https://img.shields.io/badge/java.util-BANNED-red?style=for-the-badge&logo=coffeescript&logoColor=white)](.)
[![Algorithms](https://img.shields.io/badge/Built-From%20Scratch-purple?style=for-the-badge)](.)
[![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)](LICENSE)
[![Status](https://img.shields.io/badge/Status-Active%20Development-brightgreen?style=for-the-badge)](.)

<br>

**[Overview](#-overview) · [The Problem](#-the-problem) · [How It Works](#️-how-it-works) · [Architecture](#-architecture) · [Features](#-features) · [Tech Stack](#️-tech-stack) · [Complexity](#-complexity-cheat-sheet) · [Getting Started](#-getting-started) · [Roadmap](#️-roadmap) · [Team](#-team)**

</div>

<br>

---

## 🧠 Overview

Research literature grows through an ever-expanding web of citations — yet tracking **who cites whom**, spotting **influential work**, and navigating this network is still largely manual and unsystematic. Researchers deserve better than scattered spreadsheets and gut-feeling rankings.

**Citation Analysis System** fixes that by modeling scholarly literature as a **directed citation graph**:

<div align="center">

| Real World | → | Graph World |
|:---:|:---:|:---:|
| 📄 Research Paper | → | 🔵 Vertex |
| 🔗 "Paper A cites Paper B" | → | ➡️ Directed Edge (A → B) |
| 🔥 Highly cited paper | → | 🎯 High in-degree vertex |

</div>

Once the graph exists, the system unleashes core graph algorithms to explore it — traversal, ranking, fast search — turning a tangled mess of academic cross-referencing into a **structured, queryable network**.

> [!IMPORTANT]
> No frameworks doing the heavy lifting. No `java.util` shortcuts. Every graph, hash table, and search algorithm here is **hand-built from scratch**, from the ground up — because that's the entire point of the exercise.

<br>

## ❗ The Problem

Researchers currently lack a systematic, data-structure-driven way to:

- 🔍 Track citation relationships across papers
- ⭐ Identify influential / high-impact work

Most of this happens **manually or across siloed databases** — which slows down research discovery, causes redundant duplicate work, and lets genuinely seminal papers quietly go unnoticed amid scattered citation data.

<br>

## ⚙️ How It Works

```mermaid
flowchart LR
    A[📥 Add Paper] --> G[(Citation Graph<br/>Adjacency List)]
    C[🔗 Add Citation] --> G
    G --> B{{Citation Reachability<br/>& Lineage Analysis}}
    G --> S{{Sort by<br/>Citation Count}}
    H[(Hash Table<br/>Title / Author)] --> Q[🔎 Query Paper]
    Q --> KMP[KMP / Rabin-Karp<br/>Exact Match]
    Q --> WF[Wagner-Fischer<br/>Fuzzy Match]
    B --> R[📊 Reports:<br/>Trends · Top Authors · Popular Papers]
    S --> R

    style G fill:#4c1d95,color:#fff
    style H fill:#7c3aed,color:#fff
    style R fill:#059669,color:#fff
```

| Capability | Algorithm(s) Used |
|---|---|
| 🔗 Add papers & record citation edges | Custom **adjacency-list graph** |
| 🔍 Traverse citation relationships | **Level-wise Horizon & Deep Lineage Traversal** |
| ⚡ Search papers by title/author | Custom **hash tables** (open addressing) — O(1) lookup |
| ✏️ Fuzzy / typo-tolerant search | **Wagner–Fischer edit distance** |
| 🧵 Exact string/pattern search | **KMP** & **Rabin–Karp** |
| 📊 Rank papers by citation count | Custom **sorting routines** |
| 📈 Generate trend & influence reports | Graph + hash-table aggregation |

<br>

## 🏗️ Architecture

```mermaid
graph TD
    subgraph Presentation["🖥️ Presentation Layer"]
        UI[Console / JavaFX Reports]
    end
    subgraph Core["⚙️ Core Engine"]
        Graph[Citation Graph<br/>Adjacency List]
        Hash[Custom Hash Tables]
        Search[String Matching<br/>KMP · Rabin-Karp · Wagner-Fischer]
        Sort[Custom Sort Routines]
    end
    subgraph Storage["💾 Persistence"]
        CSV[(CSV / Local Files)]
    end

    UI --> Graph
    UI --> Search
    Graph --> Hash
    Graph --> Sort
    Hash --> CSV
    Graph --> CSV

    style Core fill:#1e1b4b,color:#fff
    style Storage fill:#312e81,color:#fff
    style Presentation fill:#4338ca,color:#fff
```

<br>

## ✨ Features

- ➕ **Add papers** and record directed citation relationships
- 🔎 **Search** via hashing, with edit-distance–based fuzzy matching for typos
- 📉 **Sort & rank** papers by citation count
- 🕸️ **Traverse** the citation graph (Level-wise Reach / Deep Lineage) to reveal direct & indirect relationships
- 📑 **Generate reports** — citation trends, top authors, most-cited papers

<br>

## 🛠️ Tech Stack

<div align="center">

| Category | Tools |
|---|---|
| **Language** | ![Java](https://img.shields.io/badge/-Java%2026-ED8B00?style=flat-square&logo=openjdk&logoColor=white) hand-built graph, hashing & string algorithms |
| **Data Structures** | Custom adjacency-list graph · Custom hash tables |
| **Algorithms** | Reachability Traversal · KMP · Rabin–Karp · Wagner–Fischer |
| **Data Storage** | CSV / local file-based persistence |
| **Dev Environment** | ![IntelliJ](https://img.shields.io/badge/-IntelliJ%20IDEA-000000?style=flat-square&logo=intellijidea&logoColor=white) ![VSCode](https://img.shields.io/badge/-VS%20Code-007ACC?style=flat-square&logo=visualstudiocode&logoColor=white) |
| **Version Control** | ![Git](https://img.shields.io/badge/-Git%20%26%20GitHub-181717?style=flat-square&logo=github&logoColor=white) |
| **Testing** | ![JUnit](https://img.shields.io/badge/-JUnit-25A162?style=flat-square&logo=junit5&logoColor=white) |
| **UI (optional)** | Console / JavaFX report views |

</div>

> [!WARNING]
> **No `java.util` collection classes** are used for core logic — every graph, hash table, and algorithm is implemented from scratch, consistent with the course's built-from-first-principles constraint.

<br>

## 📐 Complexity Cheat Sheet

<details>
<summary><strong>Click to expand the Big-O breakdown</strong></summary>

<br>

| Operation | Algorithm | Time Complexity | Space |
|---|---|:---:|:---:|
| Add paper / citation edge | Adjacency list insert | `O(1)` | `O(V + E)` |
| Traverse graph | Reachability Traversal | `O(V + E)` | `O(V)` |
| Exact title/author search | Custom Hash Table | `O(1)` avg | `O(n)` |
| Fuzzy search (typo-tolerant) | Wagner–Fischer | `O(m·n)` | `O(m·n)` |
| Exact string pattern match | KMP | `O(n + m)` | `O(m)` |
| Multi-pattern search | Rabin–Karp | `O(n + m)` avg | `O(1)` |
| Citation-count ranking | Custom Sort | `O(n log n)` | `O(n)` |

*`V` = papers, `E` = citations, `n`/`m` = string lengths.*

</details>

<br>

## 🚀 Getting Started

### Prerequisites
- JDK 26 (Oracle build)
- IntelliJ IDEA or VS Code (with Java extensions)
- Git

### Installation

```bash
# Clone the repository
git clone https://github.com/<your-username>/DSA-3_Projects.git
cd DSA-3_Projects

# Build the project
javac -encoding UTF-8 -d out $(find src -name "*.java")

# Run
java -cp out main.Main
```

### Quick Usage

```
1. Add a paper        →  Register title, author(s), year
2. Add a citation      →  Link Paper A → Paper B (directed edge)
3. Search a paper       →  By exact title/author, or fuzzy match
4. Traverse            →  Explore reachable citation network from any paper
5. Generate report       →  View top authors, popular papers, trends
```

### 🎨 Console Styling

The terminal UI carries a **rainbow theme**: system titles, section headings, menu numbering, rule
captions and report table headers are all painted with a spectrum sweep, and every node in a graph
diagram gets its own hue. The layout is deliberately **open** — sections are separated by a coloured
hairline rule and a line of air instead of drawn boxes — so nothing is spent on borders and the
console reads cleanly at any width. None of it changes a prompt, a menu number or a line of data, and
it is detected at startup then degrades cleanly:

| Situation | What you see |
|:---|:---|
| Modern terminal (UTF-8, ANSI) | Rainbow banner and headings, gradient hairline rules, colour-coded status tags, report bars, graph diagrams whose nodes are hue-tinted chips |
| 24-bit colour (`COLORTERM`) | The spectrum in full 24-bit; otherwise it is quantised to the 256-colour cube |
| Legacy console / piped output | Plain ASCII (`▌` becomes `|`, `─` becomes `-`, `-->` arrows, `[P102]` nodes), no escape codes at all |
| Narrow window | Every open section fits the terminal width: long rows fold or truncate, the splash switches to a single column |
| Status tags | Always semantic - red for errors, green for success, cyan for notices - so the rainbow never hides a problem |

```bash
NO_COLOR=1 java -cp out main.Main              # force plain output
CERBERUS_COLOR=always java -cp out main.Main   # force colour (accepts always | never | auto)
```

<br>

## 🎯 Expected Outcome

- ✅ A functioning citation graph supporting full **reachability & lineage traversal**
- ✅ **Ranked citation counts** and automatic identification of top authors
- ✅ **Citation-trend reports** generated straight from graph + hash-table data
- ✅ A demonstrable **end-to-end search-and-analysis flow**, start to finish

This project proves that core DSA concepts — **graphs, hashing, and string algorithms** — aren't just theory. They solve a real, practical academic research problem.

<br>

## 🗺️ Roadmap

- [x] Core graph engine (adjacency list, reachability traversal)
- [x] Custom hash table for O(1) lookup
- [x] KMP / Rabin-Karp exact search
- [x] Wagner-Fischer fuzzy matching
- [ ] JavaFX visual graph explorer
- [ ] Export reports to PDF
- [ ] Import bulk citation datasets (BibTeX)

<br>

## 👥 Team

<div align="center">

| Name | Roll Number |
|---|---|
| **Bandla Vinay** | 2520030437 |
| **Sai Sashank** | 2520030454 |
| **Ganesh** | 2520030252 |

**Section 07 · Team 20 · DSA-3 (25CS2103E)**
**Guide:** Dr. S. Madhavi

</div>

<br>

## 📄 License

This project is built for academic purposes under **DSA-3 (25CS2103E)**. Licensed under [MIT](LICENSE) unless your course says otherwise — check with your guide before going full open-source rebel.

---

<div align="center">

**Built with directed edges, hand-rolled hash tables, and zero `java.util` shortcuts.**

⭐ *If this repo saved your grade, star it. That's the whole ask.*

</div>
