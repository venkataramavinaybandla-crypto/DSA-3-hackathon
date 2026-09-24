# Citation Analysis System — Build Plan & Research Dossier
**Target execution tool:** Google Antigravity (multi-agent, credit-metered)
**Objective:** Ship a correct, from-scratch (no `java.util`) DSA engine, exposed through a
**pure-Java, zero-dependency website**, while burning the minimum possible credits.

**v2 update:** Added a full web delivery layer (Phases 8–11). Core DSA engine constraint
(no `java.util`, no third-party libraries) now explicitly extends to the web layer too —
the backend is hand-rolled on the JDK's built-in `HttpServer`, with a **hand-rolled JSON
serializer**, no Maven/Gradle, no frameworks. Frontend is plain HTML/CSS/vanilla JS.

---

## Part 1 — Research: The 8 Hard Problems & Their Known Solutions

Every module in this project is a *solved* CS problem. The risk isn't "can it be done" — it's implementing it correctly **without the standard library safety net**, and without an AI agent looping/retrying its way through bugs (which is where credits actually get burned). Below: the problem, the canonical solution, the from-scratch gotchas, and the complexity target.

### 1. Graph Representation & Reachability Traversal (Multi-Tier & Lineage)
- **Problem:** Represent a potentially large, sparse citation graph and traverse it to find direct/indirect relationships.
- **Existing solution:** Adjacency list over adjacency matrix — citation graphs are sparse (most papers don't cite most other papers), so adjacency list keeps it at `O(V+E)` space instead of `O(V²)`.
- **From-scratch gotcha:** No `ArrayList<Integer>` per node — needs a **custom dynamic array** or **custom linked list** as the edge-list backing structure. Build this ONE reusable dynamic array class first; everything else (hash buckets, adjacency lists, level-wise exploration queues) reuses it.
- **Level-wise reachability needs a queue; deep lineage tracing needs a stack (or recursion).** Build a custom array-backed queue/stack once, reuse everywhere.
- **Complexity target:** `O(V+E)` traversal, `O(V+E)` space.

### 2. Custom Hashing (Paper Lookup by Title/Author)
- **Problem:** O(1) average lookup without `HashMap`.
- **Existing solution:** Open addressing (linear/quadratic probing) or separate chaining with a custom bucket array. For a fixed academic dataset size, **open addressing with a good hash + resize-on-load-factor** is simpler to get correct than chaining and avoids needing linked lists inside buckets.
- **From-scratch gotcha:** String hashing needs a solid hash function (polynomial rolling hash, e.g. base 31 or 131, mod a large prime) — a weak hash function is the #1 source of silent bugs (clustering → degrades to O(n)). Also must handle **tombstones** for deletion under open addressing, and **resize + rehash** at ~0.7 load factor.
- **Complexity target:** `O(1)` average insert/search, `O(n)` worst case (acceptable, document it).

### 3. Exact String Matching (Title/Author Search) — KMP & Rabin-Karp
- **Problem:** Find exact substring matches for search queries.
- **Existing solution:** KMP for single-pattern deterministic `O(n+m)` matching; Rabin-Karp when checking a query against **many** paper titles at once (rolling hash amortizes well across multiple comparisons).
- **From-scratch gotcha:** KMP's failure function (LPS array) construction is the classic bug source — off-by-one errors are extremely common. This is the single highest-risk-of-wasted-iteration module; get the LPS array right with a hand-traced example *before* writing the matcher.
- **Complexity target:** KMP `O(n+m)`, Rabin-Karp `O(n+m)` average.

### 4. Fuzzy Matching — Edit Distance (Wagner-Fischer)
- **Problem:** Tolerate typos in search queries (e.g., "Dijkstar" → "Dijkstra").
- **Existing solution:** Classic Wagner-Fischer DP — `dp[i][j]` = edit distance between first `i` and `j` chars of two strings.
- **From-scratch gotcha:** Naive implementation is `O(m·n)` **time and space** — fine for title-length strings, but if applied paper-by-paper across a large corpus it becomes the system's bottleneck. Mitigate by **pre-filtering candidates via the hash table** (e.g., same first letter / similar length) before running edit distance, rather than running DP against every paper title.
- **Complexity target:** `O(m·n)` per comparison; use **space-optimized rolling 2-row DP** (not full matrix) to cut memory.

### 5. Ranking / Sorting by Citation Count
- **Problem:** Rank papers by citation count for "top cited" reports.
- **Existing solution:** Merge sort or quicksort, hand-built, `O(n log n)`. Merge sort is safer to implement correctly from scratch (no pivot-selection edge cases) and is stable — useful if you ever want secondary sort keys (e.g., tie-break by year).
- **Complexity target:** `O(n log n)`.

### 6. Reporting & Persistence
- **Problem:** Turn graph/hash-table state into human-readable trend reports; persist between runs.
- **Existing solution:** CSV read/write via plain `java.io` (allowed — only *collections* are banned, not I/O). Reports are just formatted traversals of your own structures.
- **Gotcha:** Keep report generation **decoupled** from the algorithms — reports should only *read* graph/hash-table state, never mutate it. Cleaner testing, fewer regression bugs = fewer wasted agent iterations fixing report bugs that break the core engine.

### 7. Web Delivery Layer (Zero-Dependency HTTP + Hand-Rolled JSON) — NEW
- **Problem:** Expose the engine through a browser-based website, without pulling in Maven/Gradle or any third-party framework (Spring, Javalin, Gson, Jackson) — keeping the entire codebase pure, dependency-free Java.
- **Existing solution:** `com.sun.net.httpserver.HttpServer` — bundled in the JDK since Java 6, requires zero external dependencies, and can register `HttpHandler`s per route (`/api/papers`, `/api/search`, `/api/report`) plus serve static files (HTML/CSS/JS) straight off disk.
- **From-scratch gotcha #1 — JSON:** No Gson/Jackson means responses need a **hand-rolled JSON serializer**. Keep it deliberately narrow: it only ever needs to serialize `Paper`, `Citation`, and report result objects — not arbitrary Java objects. Write it as a small set of `toJson()` methods on each model (or a tiny `JsonWriter` helper with `object()`, `array()`, `field()` builder methods), NOT a general-purpose reflection-based serializer — that would be massive overkill and a huge credit sink for zero benefit.
- **From-scratch gotcha #2 — routing:** `HttpServer` has no built-in router — each `context.createContext(path, handler)` call is a manual route registration, and each handler must check `exchange.getRequestMethod()` itself (GET vs POST) and parse query params / request bodies by hand (no `request.getParameter()` convenience like servlets). Keep a single small `Router` helper class that all handlers share, rather than reinventing parsing logic per-endpoint.
- **From-scratch gotcha #3 — static files:** Serving `index.html`/`style.css`/`app.js` is just reading bytes off disk and setting the right `Content-Type` header — no templating engine needed since the JS fetches data from the JSON API at runtime.
- **Complexity target:** This is I/O-bound, not algorithmically interesting — keep it thin. All the real complexity budget should go to Modules 1–7, not the web plumbing.

---

## Part 2 — Antigravity Credit-Cost Strategy

Antigravity meters usage via a **5-hour-refresh compute quota + paid credit top-ups**, with cost varying heavily by **model tier** and **task complexity** (not just token count). Multi-agent parallelism and long back-and-forth debugging loops are the two biggest credit sinks. Strategy to minimize spend:

| Lever | Why it saves credits |
|---|---|
| **Model-tier matching** | Use the cheapest model (Flash-tier) for boilerplate: custom dynamic array, queue/stack, CSV I/O, JSON field-writer, static file serving, getters/setters. Reserve the higher-reasoning model tier ONLY for the genuinely tricky logic: KMP's LPS array, hash table resize/rehash, HTTP router edge cases (malformed requests, missing params). |
| **One mission per module, fully specified upfront** | Every retry/clarification round is a new billed turn. Write complete mission briefs (inputs, outputs, method signatures, edge cases) *before* dispatching. |
| **Sequential, not parallel, for dependent modules** | This project is a dependency chain (dynamic array → graph → BFS → API → frontend). Running dependent modules in parallel causes rework when interfaces don't match. Only parallelize truly independent pieces. |
| **Reuse the plan as grounding context, not re-explaining each time** | Feed this document (or a condensed version) into each mission so the agent doesn't re-derive architecture decisions per session. |
| **Test-as-you-go, not one giant integration test at the end** | Catching a bug in a 50-line module costs one cheap-model fix. Catching the same bug after everything's wired together costs an expensive full-context debugging session. |
| **API contract locked BEFORE frontend work starts** | Define the exact JSON shape of every endpoint in Phase 9 before Phase 10 (frontend) begins — prevents the classic "backend returns X, frontend expects Y" rework loop, which is one of the most expensive credit sinks in full-stack builds. |
| **Use the Manager view's plan artifacts** | Antigravity generates a task plan before executing — review and correct that plan BEFORE it starts coding, since fixing a plan is cheap and fixing already-generated code is not. |

---

## Part 3 — Phased Build Plan (Mission Briefs for Antigravity)

Each phase = one Antigravity mission. Dependency order matters — do not skip ahead.

### Phase 0 — Project Scaffold *(Flash tier)* ✅ *dispatched*
- Java project structure: `core`, `algo`, `io`, `report`, `main` packages
- `Paper` and `Citation` model classes (POJOs, no logic)
- Git init + `.gitignore`
- *(Note for next Phase 0 revisit if needed: add empty `web` package placeholder for Phases 7–10 — `web.http`, `web.json`, and a `web/static/` resources folder for HTML/CSS/JS.)*

### Phase 1 — Foundational Data Structures *(Flash tier)*
- Custom dynamic array (generic, resizable)
- Custom array-backed stack + queue
- Custom singly/doubly linked list (used inside hash buckets if chaining is chosen)
- Unit tests for each before moving on — these are load-bearing for every later module.

### Phase 2 — Citation Graph Core *(Flash tier)*
- Adjacency-list graph class built on Phase 1's dynamic array
- Add paper (vertex) / add citation (directed edge)
- BFS + DFS traversal methods

### Phase 3 — Custom Hash Table *(High reasoning tier)*
- Polynomial rolling hash function for strings
- Open addressing with linear probing, tombstone deletion, resize-on-load-factor
- Insert/search/delete by title and by author

### Phase 4 — String Matching *(High reasoning tier — LPS array is the highest-risk module)*
- KMP exact matcher (hand-trace the LPS array against a test string before accepting the implementation)
- Rabin-Karp for multi-title scanning
- Wagner-Fischer edit distance, space-optimized (2-row DP), gated behind a cheap pre-filter from the hash table

### Phase 5 — Sorting & Ranking *(Flash tier)*
- Merge sort by citation count (stable, so ties can secondary-sort by year later)

### Phase 6 — Persistence & Reporting *(Flash tier)*
- CSV read/write via `java.io`
- Report generator: top authors, most-cited papers, citation trend summary
- Strictly read-only against core structures

### Phase 7 — Hand-Rolled JSON Serializer *(Flash/mid tier)* — NEW
- Small `JsonWriter` builder helper (`object()`, `array()`, `field()`, proper string-escaping for quotes/backslashes/unicode)
- `toJson()` methods on `Paper`, `Citation`, and report result types
- **No reflection, no generic object graph walking** — scope it tight to just this project's model types
- Unit tests: escaping edge cases (quotes in titles, unicode author names, empty arrays)

### Phase 8 — HTTP Backend Layer *(Mid/high tier — routing + request parsing edge cases)* — NEW
- `com.sun.net.httpserver.HttpServer` setup on a configurable port
- Shared `Router` helper: registers routes, dispatches by method (GET/POST), parses query params and JSON request bodies
- Endpoints (lock this contract before Phase 9 starts):
  - `GET /api/papers` — list all papers
  - `POST /api/papers` — add a paper
  - `POST /api/citations` — add a citation edge
  - `GET /api/search?q=...&fuzzy=true|false` — hash lookup or fuzzy match
  - `GET /api/traverse?from=...&mode=bfs|dfs` — traversal from a paper
  - `GET /api/report?type=top-authors|top-papers|trends` — reports
- Static file handler serving `web/static/` (index.html, style.css, app.js)
- Error handling: consistent JSON error shape for 400/404/500 responses

### Phase 9 — Frontend (HTML / CSS / Vanilla JS) *(Flash tier)* — NEW
- `index.html` — single-page layout: add paper/citation forms, search bar, traversal view, reports panel
- `style.css` — clean, minimal styling (no framework/build step)
- `app.js` — `fetch()` calls against the Phase 8 API contract, renders results into the DOM
- No npm, no bundler, no build step — plain `<script src="app.js">`, served directly by the Phase 8 static handler

### Phase 10 — Integration Pass *(Mid tier)* — NEW
- Wire everything behind `Main.java`: start the HTTP server, load any persisted CSV data on boot
- End-to-end smoke test: add paper → add citation → search → traverse → view report, all through the browser
- Confirm graceful shutdown (flush CSV on exit)

### Phase 11 — Test Pass & Polish *(Flash tier)*
- JUnit coverage on graph, hash table, KMP, edit distance, JSON serializer, router
- Edge cases: empty graph, self-citation, duplicate titles, disconnected clusters, malformed API requests
- Swap in the polished README (already drafted separately) as the final `README.md`

---

## Execution Order Summary

```
Phase 0 → Phase 1 → Phase 2 ─┬─→ Phase 5 (Sorting & Ranking)
                              └─→ Phase 6 (Persistence & Reporting)
       Phase 3 → Phase 4 (needs Phase 3's hash table for pre-filtering)
                              ↓
                         Phase 7 (JSON serializer — independent, can run
                                   parallel to Phases 3–6)
                              ↓
                         Phase 8 (HTTP backend — needs Phases 2,3,4,5,6,7)
                              ↓
                         Phase 9 (frontend — needs Phase 8's locked API contract)
                              ↓
                         Phase 10 (integration)
                              ↓
                         Phase 11 (test pass + polish)
```

**Parallelization opportunities:**
- Phase 1→2 and Phase 3 can run as two parallel agents (no shared dependency)
- Phase 7 (JSON serializer) is fully independent of the algorithm modules — can run anytime after Phase 0, in parallel with Phases 1–6
- Phase 4 must wait for Phase 3
- Phase 8 is the big convergence point — don't dispatch it until 2, 3, 4, 5, 6, and 7 are all done and tested
- Phase 9 must NOT start until Phase 8's API contract (the endpoint list above) is finalized — this is the single most important sequencing rule for the whole web layer, since a frontend built against a shifting API is the most expensive kind of rework in this entire plan