<p align="center">
  <img src="assets/banner.svg" alt="Cerberus Citation Analysis System" width="100%">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-17%2B-%235B0FFF?style=flat-square&labelColor=%230B0813" alt="Java 17+">
  <img src="https://img.shields.io/badge/java.util-banned-%23FF007F?style=flat-square&labelColor=%230B0813" alt="java.util banned">
  <img src="https://img.shields.io/badge/algorithms-from%20scratch-%23A32EFF?style=flat-square&labelColor=%230B0813" alt="Algorithms built from scratch">
  <img src="https://img.shields.io/badge/tests-782%20green-%235B0FFF?style=flat-square&labelColor=%230B0813" alt="782 checks green">
  <img src="https://img.shields.io/badge/license-MIT-%23F5F3F7?style=flat-square&labelColor=%230B0813" alt="MIT license">
</p>

<p align="center">
  <a href="#overview">Overview</a> &nbsp;|&nbsp;
  <a href="#capabilities">Capabilities</a> &nbsp;|&nbsp;
  <a href="#interfaces">Interfaces</a> &nbsp;|&nbsp;
  <a href="#architecture">Architecture</a> &nbsp;|&nbsp;
  <a href="#complexity">Complexity</a> &nbsp;|&nbsp;
  <a href="#getting-started">Getting Started</a> &nbsp;|&nbsp;
  <a href="#testing">Testing</a> &nbsp;|&nbsp;
  <a href="#roadmap">Roadmap</a> &nbsp;|&nbsp;
  <a href="#team">Team</a>
</p>

<br>

<h3 align="center">Every paper is a vertex. Every citation is a directed edge.</h3>

<p align="center">
Cerberus models scholarly literature as a directed citation graph, then answers the questions researchers actually ask:<br>
who cites whom, which work carries influence, and how an idea travels from one paper to the next.<br>
One engine, four surfaces: a Python REST backend, a Rich terminal UI, a web dashboard and the original Java console.<br>
Every graph, hash table and search routine is written by hand. No <code>java.util</code> collections. No frameworks in the core.
</p>

<img src="assets/divider.svg" alt="" width="100%">

## Overview

Research literature grows as a web of citations, yet tracking that web is still mostly manual. Relationships live in siloed databases and spreadsheets, influential papers hide behind scattered counts, and teams end up repeating work that already exists.

Cerberus replaces that with one structure you can query. Papers become vertices, citations become directed edges, and classic graph, hashing and string algorithms turn the result into a navigable network.

| In the literature | In the graph |
| :--- | :--- |
| A research paper | A vertex |
| Paper A cites Paper B | A directed edge from A to B |
| A highly cited paper | A vertex with high in-degree |

> [!IMPORTANT]
> Built from first principles. The graph, the hash tables and every search and sort routine are implemented from scratch, with no `java.util` collection classes in the core logic. That constraint is the point of the project.

<img src="assets/divider.svg" alt="" width="100%">

## Capabilities

| Capability | Implementation |
| :--- | :--- |
| Register papers and record citations | Custom **adjacency-list graph**, plus in-memory mutations over the API |
| Trace citation relationships | **Level-wise horizon** and **deep lineage** traversal, BFS and DFS visit order |
| Look up papers by title or author | Custom **open-addressing hash tables**, O(1) average |
| Search through typos | **Wagner-Fischer** edit distance |
| Match exact patterns | **KMP** and **Rabin-Karp** |
| Route between papers | BFS shortest path, all simple paths, **Held-Karp bitmask** optimal route |
| Rank and report | Custom sorting: top authors, most-cited papers, yearly citation trends |

<img src="assets/divider.svg" alt="" width="100%">

## Interfaces

One engine, four surfaces. Two terminals and a browser cover the full workflow; each surface speaks the same REST contract or reads the same CSV directly.

### 1. API backend — terminal one

```bash
pip install -r requirements.txt
python -m uvicorn server.main:app --port 8005
```

Serves the REST API and hosts the web dashboard at `http://127.0.0.1:8005/`. The dataset loads from `citation_data.csv` on boot; `POST /api/reload` re-reads it.

### 2. Rich terminal UI — terminal two

```bash
PYTHONIOENCODING=utf-8 python -m tui.main
```

With the backend running: exact and fuzzy search, shortest path / optimal route / lineage, BFS and DFS traversal, top-author and trend reports, and live add-paper / add-citation commands. Set `CERBERUS_API_URL` to point it at a different backend.

### 3. Web dashboard — browser

Start the backend, then open `http://127.0.0.1:8005/`.

| Tab | What it shows |
| :--- | :--- |
| Graph | Live metrics, top-cited table, citation lineage |
| Search | Exact or fuzzy results from the query console |
| Traversal | BFS/DFS visit order, shortest path, bitmask-optimal route |
| Reports | Top authors and yearly citation trends |
| Mutate | Add papers and citation edges; errors surface in the status line |

### Alternate surfaces

```bash
java -cp out main.Main           # original menu-driven console, reads citation_data.csv directly
java -cp out main.Main --serve   # pure-Java REST server + dashboard on port 8006 (override: --serve 9000)
```

The Java server needs no Python at all: JDK `com.sun.net.httpserver` only. Mutations are in-memory and the graph is flushed back to `citation_data.csv` as UTF-8 on Ctrl+C.

### API contract

| Route | Purpose |
| :--- | :--- |
| `GET /api/papers?q=&limit=&fuzzy=` | List or search papers |
| `GET /api/traverse?source=&mode=bfs\|dfs` | Visit order from a paper |
| `GET /api/reports/authors` and `/api/reports/trends` | Ranked reports |
| `GET /api/path`, `/api/paths`, `/api/optimal-route`, `/api/lineage` | Routing and reachability |
| `POST /api/papers`, `POST /api/citations` | In-memory mutations |
| `GET /api/health`, `GET /api/stats`, `POST /api/reload` | Status and CSV reload |

The pure-Java server mirrors this contract; reports live at `GET /api/report?type=top-authors|top-papers|trends`.

<img src="assets/divider.svg" alt="" width="100%">

## Architecture

<p align="center">
  <img src="assets/architecture.svg" alt="Cerberus architecture: presentation layer, core engine and persistence" width="100%">
</p>

The presentation layer — console, terminal UI and dashboard — sends queries into the core engine through the REST layer. The engine keeps the citation graph and the hash tables in step, runs string matching and sorting on demand, and loads from and saves to plain CSV files.

<img src="assets/divider.svg" alt="" width="100%">

## Complexity

| Operation | Algorithm | Time | Space |
| :--- | :--- | :--- | :--- |
| Add a paper or citation edge | Adjacency-list insert | `O(1)` | `O(V + E)` |
| Traverse the graph | Reachability traversal | `O(V + E)` | `O(V)` |
| Exact title or author search | Custom hash table | `O(1)` average | `O(n)` |
| Typo-tolerant search | Wagner-Fischer | `O(m * n)` | `O(m * n)` |
| Exact pattern match | KMP | `O(n + m)` | `O(m)` |
| Multi-pattern search | Rabin-Karp | `O(n + m)` average | `O(1)` |
| Citation-count ranking | Custom sort | `O(n log n)` | `O(n)` |

`V` is the number of papers, `E` the number of citations, and `n` and `m` are string lengths.

<img src="assets/divider.svg" alt="" width="100%">

## Getting Started

**Prerequisites**

- JDK 17 or newer (Oracle, Temurin or Corretto builds all work)
- Python 3.11 or newer, plus `pip install -r requirements.txt`
- Node 18 or newer — optional, only for the browser end-to-end test
- Git

**Build and run**

```bash
git clone https://github.com/venkataramavinaybandla-crypto/DSA-3-hackathon.git
cd DSA-3-hackathon

javac -encoding UTF-8 -d out $(find src -name "*.java")
java -cp out main.Main
```

**Full stack in three terminals**

```bash
# terminal 1 — API backend
python -m uvicorn server.main:app --port 8005

# terminal 2 — Rich terminal UI
PYTHONIOENCODING=utf-8 python -m tui.main

# browser — open http://127.0.0.1:8005/
```

**A typical session**

1. Add a paper with its title, authors and year.
2. Add a citation to link Paper A to Paper B.
3. Search by exact title or author, or fall back to fuzzy matching.
4. Traverse the network outward from any paper.
5. Generate a report of top authors, popular papers and trends.

<img src="assets/divider.svg" alt="" width="100%">

## Design System

The console and the dashboard share one cyberpunk palette. Sixty percent canvas, thirty percent structure, ten percent accent, and Ghost White for text — never more, never less.

| Role | Hex | Share | Applied to |
| :--- | :--- | :--- | :--- |
| Obsidian Night | `#0B0813` | 60% | Canvas: window sheets, panel backgrounds |
| <img src="https://img.shields.io/badge/Tech_Violet-%235B0FFF?style=flat-square&labelColor=%230B0813" alt="Tech Violet #5B0FFF"> | | 30% | Structure: borders, frames, divider grids |
| <img src="https://img.shields.io/badge/Cyber_Purple-%23A32EFF?style=flat-square&labelColor=%230B0813" alt="Cyber Purple #A32EFF"> | | secondary | Categories: authors, tags, section labels |
| <img src="https://img.shields.io/badge/Laser_Pink-%23FF007F?style=flat-square&labelColor=%230B0813" alt="Laser Pink #FF007F"> | | 10% | Action: metrics, citation keys, active states |
| <img src="https://img.shields.io/badge/Ghost_White-%23F5F3F7?style=flat-square&labelColor=%230B0813" alt="Ghost White #F5F3F7"> | | text | Body copy and table cells |

The terminal wordmark and rules are painted with a three-stop gradient of the same roles — Tech Violet through Cyber Purple to Laser Pink — so the title reads as one deliberate system instead of a spectrum sweep. Styling never changes a prompt, a menu number or a line of data, and it adapts to the terminal at startup.

| Environment | Behavior |
| :--- | :--- |
| UTF-8 terminal with ANSI support | Gradient banner and headings, color-coded status tags, hue-tinted graph diagrams |
| 24-bit color (`COLORTERM`) | Full 24-bit palette, otherwise quantized to the 256-color cube |
| Legacy console or piped output | Plain ASCII |
| Narrow window | Rows fold or truncate to fit, and the splash switches to a single column |
| Status tags | Always semantic: red for errors, green for success, cyan for notices |

```bash
NO_COLOR=1 java -cp out main.Main              # force plain output
CERBERUS_COLOR=always java -cp out main.Main   # force color (always | never | auto)
```

<img src="assets/divider.svg" alt="" width="100%">

## Repository Layout

```text
.
├── src/                              Java engine: graph, hash tables, algorithms, reports, API server
├── server/                           FastAPI backend: REST API and dashboard host
├── tui/                              Rich terminal UI
├── web/static/                       Web dashboard: HTML, CSS and vanilla JS
├── tools/                            Dataset fetch and PDF text extraction
├── research_papers/                  Reference papers (PDF and extracted text)
├── assets/                           README artwork: banner, logo, dividers, architecture
├── citation_data.csv                 Papers and citation edges (dataset)
├── papers.csv                        Paper records
├── BUILD_PLAN.md                     Phase-by-phase build plan
├── Agents.md                         Agent working notes
├── e2e_dashboard.mjs                 Headless-browser dashboard end-to-end test
├── requirements.txt                  Python dependencies
├── CERBERUS_SYSTEM.pptx              Project presentation
└── Cerberus_System_Abstract.docx     Project abstract
```

<img src="assets/divider.svg" alt="" width="100%">

## Testing

**Java — 13 suites, 782 checks.** Plain `main()` runners with pass/fail counters; any failure exits 1.

```bash
javac -encoding UTF-8 -d out $(find src -name "*.java")
for t in $(find src -name '*Test.java'); do
  java -cp out "$(echo "${t#src/}" | sed 's|\.java$||; s|/|.|g')"
done
```

Covers the graph, hash tables, sorting, string matching, CSV I/O, JSON, reports, renderers, integration edge cases and the REST server (every endpoint, validation, 404/405/409 paths, static file serving).

**Browser end-to-end — 23 checks.** Drives the dashboard in headless Chrome over the DevTools protocol: boot, fuzzy search, BFS/DFS traversal, reports, mutations, error surfacing, zero uncaught page exceptions.

```bash
node e2e_dashboard.mjs                                # against the Python backend :8005
API_BASE=http://127.0.0.1:8006 node e2e_dashboard.mjs # against the pure-Java server :8006
```

<img src="assets/divider.svg" alt="" width="100%">

## Roadmap

- [x] Core graph engine with adjacency list and reachability traversal
- [x] Custom hash table with O(1) lookup
- [x] KMP and Rabin-Karp exact search
- [x] Wagner-Fischer fuzzy matching
- [x] REST API, Rich terminal UI and web dashboard
- [x] Dataset tooling: arXiv fetch and PDF text extraction
- [ ] JavaFX visual graph explorer
- [ ] PDF report export
- [ ] Bulk citation import from BibTeX

## Team

| Name | Roll number |
| :--- | :--- |
| **Bandla Vinay** | 2520030437 |
| **Sai Sashank** | 2520030454 |
| **Ganesh** | 2520030252 |

Section 07, Team 20, DSA-3 (25CS2103E). Guide: Dr. S. Madhavi.

## License

Released under the [MIT License](https://opensource.org/license/mit). Built as coursework for DSA-3 (25CS2103E).

<img src="assets/divider.svg" alt="" width="100%">

<p align="center">
  <img src="assets/logo.svg" alt="Cerberus System" width="320">
</p>

<p align="center">
  <sub>Built with directed edges and hand-rolled hash tables.</sub>
</p>
