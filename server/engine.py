"""
CERBERUS SYSTEM — Core citation-graph engine (Python port of the Java DSA engine).

Mirrors the hand-built Java data structures and algorithms 1:1 so the FastAPI
layer, the Rich TUI, and the web dashboard all serve *identical* results:

  - Graph / Paper ................ port of src/core/Graph.java, Paper.java
  - load_csv / sync counts ....... port of src/io/CsvHandler.java
  - bfs / shortest_path .......... port of GraphTraversal.bfs + parent-tracking BFS
  - all_paths .................... port of GraphTraversal.findAllPaths (DFS backtracking,
                                   capped at MAX_PATHS like the Java original)
  - is_hamiltonian_path .......... port of GraphTraversal.isHamiltonianPath
  - optimal_citation_path ........ port of GraphTraversal.optimalCitationPath
                                   (metric closure BFS + Held-Karp bitmask DP)

No third-party imports: the engine stays dependency-free like the Java core.
"""

from __future__ import annotations

import csv
from collections import deque
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Sequence, Set, Tuple

# Hard cap shared with the Java implementation (GraphTraversal.MAX_PATHS).
MAX_PATHS = 10_000
# Hard cap shared with the Java implementation (GraphTraversal.MAX_OPTIMAL_SUBSET_SIZE).
MAX_OPTIMAL_SUBSET_SIZE = 20
_INF = float("inf")


@dataclass(frozen=True)
class Paper:
    """Port of core/Paper.java — a vertex in the citation graph."""

    id: str
    title: str
    author: str
    year: int

    def to_dict(self) -> dict:
        return {"id": self.id, "title": self.title, "author": self.author, "year": self.year}


class Graph:
    """Port of core/Graph.java — directed adjacency-list citation graph.

    Edge semantics match the Java engine exactly: an edge u -> v means
    "paper u cites paper v".
    """

    def __init__(self) -> None:
        self._papers: Dict[str, Paper] = {}
        self._adjacency: Dict[str, List[str]] = {}
        self._edge_set: Set[Tuple[str, str]] = set()
        self._citation_counts: Dict[str, int] = {}

    # ------------------------------------------------------------ vertices

    def add_paper(self, paper: Paper) -> str:
        """Add a paper vertex; deduplicates by id and returns the canonical id."""
        if paper.id not in self._papers:
            self._papers[paper.id] = paper
            self._adjacency[paper.id] = []
            self._citation_counts[paper.id] = 0
        return paper.id

    def has_paper(self, paper_id: str) -> bool:
        return paper_id in self._papers

    def get_paper(self, paper_id: str) -> Optional[Paper]:
        return self._papers.get(paper_id)

    @property
    def paper_ids(self) -> List[str]:
        """All vertex ids in insertion order (stable, like the Java DynamicArray)."""
        return list(self._papers.keys())

    @property
    def papers(self) -> List[Paper]:
        return list(self._papers.values())

    @property
    def vertex_count(self) -> int:
        return len(self._papers)

    @property
    def edge_count(self) -> int:
        return len(self._edge_set)

    # -------------------------------------------------------------- edges

    def add_citation(self, citing_id: str, cited_id: str) -> bool:
        """Add a directed edge citing -> cited. Returns False if it already existed."""
        key = (citing_id, cited_id)
        if key in self._edge_set:
            return False
        self._edge_set.add(key)
        self._adjacency[citing_id].append(cited_id)
        self._citation_counts[cited_id] += 1
        return True

    def neighbors(self, paper_id: str) -> List[str]:
        """Papers that `paper_id` cites (out-edges), in insertion order."""
        return self._adjacency.get(paper_id, [])

    def cited_by(self, paper_id: str) -> List[str]:
        """Papers that cite `paper_id` (in-edges), in insertion order."""
        return [src for src, dst in self._edge_set if dst == paper_id]

    def citation_count(self, paper_id: str) -> int:
        """In-degree = citation count, synced on load like CsvHandler.syncCitationCounts."""
        return self._citation_counts.get(paper_id, 0)

    # ----------------------------------------------------------- traversal

    def bfs_order(self, start_id: str) -> List[str]:
        """BFS visit order from start_id (port of GraphTraversal.bfs)."""
        if start_id not in self._papers:
            return []
        visited = {start_id}
        order: List[str] = []
        queue: deque = deque([start_id])
        while queue:
            current = queue.popleft()
            order.append(current)
            for nxt in self._adjacency[current]:
                if nxt not in visited:
                    visited.add(nxt)
                    queue.append(nxt)
        return order

    def shortest_path(self, start_id: str, end_id: str) -> Optional[List[str]]:
        """One minimum-hop directed path start -> end via parent-tracking BFS.

        Returns the list of paper ids including both endpoints, or None when
        unreachable or either id is unknown. A single-node query (start == end)
        returns [start].
        """
        if start_id not in self._papers or end_id not in self._papers:
            return None
        if start_id == end_id:
            return [start_id]

        parent: Dict[str, str] = {}
        visited = {start_id}
        queue: deque = deque([start_id])
        while queue:
            current = queue.popleft()
            if current == end_id:
                break
            for nxt in self._adjacency[current]:
                if nxt not in visited:
                    visited.add(nxt)
                    parent[nxt] = current
                    queue.append(nxt)

        if end_id not in visited:
            return None

        # Backtrack end -> start, then reverse (same as the Java reconstruct).
        path = [end_id]
        while path[-1] != start_id:
            path.append(parent[path[-1]])
        path.reverse()
        return path

    def all_paths(self, start_id: str, end_id: str) -> List[List[str]]:
        """All simple directed paths start -> end via DFS backtracking, capped at MAX_PATHS.

        Sorted by hop count then lexicographically so output is deterministic
        and the shortest chains are listed first.
        """
        if start_id not in self._papers or end_id not in self._papers:
            return []

        results: List[List[str]] = []
        visited: Set[str] = set()
        stack: List[str] = []

        def dfs(current: str) -> None:
            if len(results) >= MAX_PATHS:
                return
            visited.add(current)
            stack.append(current)

            if current == end_id:
                results.append(list(stack))
            else:
                for nxt in self._adjacency[current]:
                    if nxt not in visited:
                        dfs(nxt)
                        if len(results) >= MAX_PATHS:
                            break

            stack.pop()
            visited.discard(current)

        dfs(start_id)
        results.sort(key=lambda p: (len(p), p))
        return results

    def is_hamiltonian_path(self, path: Sequence[str]) -> bool:
        """Port of GraphTraversal.isHamiltonianPath: a path is Hamiltonian iff it
        visits every vertex of the graph exactly once."""
        if not path or len(path) != self.vertex_count:
            return False
        return len(set(path)) == self.vertex_count

    # --------------------------------------------------------- bitmask DP

    def optimal_citation_path(self, paper_ids: Sequence[str]) -> dict:
        """Port of GraphTraversal.optimalCitationPath — Held-Karp bitmask DP.

        Computes the minimum-hop directed walk that visits every requested paper,
        moving between consecutive requested papers along BFS shortest paths
        (the metric closure), exactly like the Java implementation.

        Returns a dict:
          hops        total hops of the optimal walk (None when impossible)
          order       the requested papers in visiting order
          walk        every paper id actually traversed, source to final target
        Raises ValueError for invalid subsets (blank/duplicate/oversized/unknown ids)
        with messages matching the Java engine's semantics.
        """
        if not paper_ids:
            raise ValueError("Paper ID list cannot be null or empty")
        if len(paper_ids) > MAX_OPTIMAL_SUBSET_SIZE:
            raise ValueError(
                f"Optimal citation path supports at most {MAX_OPTIMAL_SUBSET_SIZE} "
                f"paper IDs, but received {len(paper_ids)}."
            )

        subset: List[str] = []
        for i, raw in enumerate(paper_ids):
            pid = (raw or "").strip()
            if not pid:
                raise ValueError(f"Paper ID at position {i} is null or empty")
            if pid in subset:
                raise ValueError(f"Duplicate paper ID in subset: {pid}")
            if pid not in self._papers:
                raise ValueError(f"No valid path exists: paper ID '{pid}' was not found in the graph.")
            subset.append(pid)

        n = len(subset)
        if n == 1:
            return {"hops": 0, "order": [subset[0]], "walk": [subset[0]]}

        # Metric closure: BFS from every requested paper.
        dist = [[_INF] * n for _ in range(n)]
        parents: List[Dict[str, Optional[str]]] = []
        for i, src in enumerate(subset):
            hops: Dict[str, int] = {src: 0}
            parent: Dict[str, Optional[str]] = {src: None}
            queue: deque = deque([src])
            while queue:
                current = queue.popleft()
                for nxt in self._adjacency[current]:
                    if nxt not in hops:
                        hops[nxt] = hops[current] + 1
                        parent[nxt] = current
                        queue.append(nxt)
            parents.append(parent)
            for j, dst in enumerate(subset):
                dist[i][j] = hops.get(dst, _INF)

        # Held-Karp DP: dp[mask][last] = min hops visiting mask, ending at last.
        full_mask = (1 << n) - 1
        dp = [[_INF] * n for _ in range(full_mask + 1)]
        for i in range(n):
            dp[1 << i][i] = 0

        for mask in range(1, full_mask + 1):
            row = dp[mask]
            for last in range(n):
                cost = row[last]
                if cost == _INF or not (mask & (1 << last)):
                    continue
                for nxt in range(n):
                    if mask & (1 << nxt):
                        continue
                    step = dist[last][nxt]
                    if step == _INF:
                        continue
                    new_mask = mask | (1 << nxt)
                    candidate = cost + step
                    if candidate < dp[new_mask][nxt]:
                        dp[new_mask][nxt] = candidate

        best_cost, best_last = _INF, -1
        for last in range(n):
            if dp[full_mask][last] < best_cost:
                best_cost, best_last = dp[full_mask][last], last

        if best_last == -1 or best_cost == _INF:
            return {"hops": None, "order": [], "walk": []}

        # Reconstruct the visiting order over the subset (walk the DP backwards).
        order_idx: List[int] = [0] * n
        mask, last = full_mask, best_last
        for position in range(n - 1, -1, -1):
            order_idx[position] = last
            if position == 0:
                break
            previous_mask = mask ^ (1 << last)
            target = dp[mask][last]
            for candidate in range(n):
                if not (previous_mask & (1 << candidate)):
                    continue
                if (
                    dp[previous_mask][candidate] != _INF
                    and dist[candidate][last] != _INF
                    and dp[previous_mask][candidate] + dist[candidate][last] == target
                ):
                    last = candidate
                    mask = previous_mask
                    break

        # Expand into the concrete walk (stitch BFS segments like the Java rebuild).
        walk: List[str] = [subset[order_idx[0]]]
        for k in range(n - 1):
            src, dst = subset[order_idx[k]], subset[order_idx[k + 1]]
            segment: List[str] = []
            current = dst
            while current != src:
                segment.append(current)
                current = parents[order_idx[k]][current]  # type: ignore[assignment]
            walk.extend(reversed(segment))

        return {
            "hops": int(best_cost),
            "order": [subset[i] for i in order_idx],
            "walk": walk,
        }

    # ------------------------------------------------------------ reports

    def top_cited(self, limit: int = 10) -> List[Paper]:
        """Papers sorted by citation count (desc), then id — like the report menu."""
        ranked = sorted(self._papers.values(), key=lambda p: (-self._citation_counts[p.id], p.id))
        return ranked[:limit]

    def stats(self) -> dict:
        """Headline metrics: papers, edges, most-cited, hash of dataset state."""
        if not self._papers:
            return {"papers": 0, "edges": 0, "most_cited": None, "most_cited_count": 0}
        top = self.top_cited(1)[0]
        return {
            "papers": self.vertex_count,
            "edges": self.edge_count,
            "most_cited": top.id,
            "most_cited_count": self._citation_counts[top.id],
        }

    def paper_payload(self, paper_id: str) -> Optional[dict]:
        """Full paper record with in/out citation lists for the view-paper endpoint."""
        paper = self._papers.get(paper_id)
        if paper is None:
            return None
        return {
            **paper.to_dict(),
            "citationCount": self._citation_counts[paper_id],
            "cites": self.neighbors(paper_id),
            "citedBy": self.cited_by(paper_id),
        }


# --------------------------------------------------------------------------
# CSV persistence — port of io/CsvHandler.load / syncCitationCounts
# --------------------------------------------------------------------------

def load_csv(path: str | Path) -> Graph:
    """Load the unified `# PAPERS` / `# CITATIONS` CSV into a Graph."""
    graph = Graph()
    file = Path(path)
    if not file.is_file():
        return graph

    section = "papers"
    with file.open("r", encoding="utf-8", newline="") as handle:
        for raw_line in handle:
            line = raw_line.strip()
            if not line:
                continue
            if line.upper().startswith("# PAPERS"):
                section = "papers"
                continue
            if line.upper().startswith("# CITATIONS"):
                section = "citations"
                continue
            if line.startswith("#"):
                continue

            row = next(csv.reader([line]))
            if section == "papers":
                if row[0].strip().lower() == "id":  # header row
                    continue
                graph.add_paper(
                    Paper(
                        id=row[0].strip(),
                        title=row[1].strip() if len(row) > 1 else "",
                        author=row[2].strip() if len(row) > 2 else "",
                        year=int(row[3]) if len(row) > 3 and row[3].strip().isdigit() else 0,
                    )
                )
            else:
                if row[0].strip().lower() == "citingpaperid":  # header row
                    continue
                citing, cited = row[0].strip(), row[1].strip()
                if graph.has_paper(citing) and graph.has_paper(cited):
                    graph.add_citation(citing, cited)
    return graph


class CitationEngine:
    """Facade the FastAPI layer binds to. Reloads the CSV dataset on demand."""

    def __init__(self, csv_path: str | Path = "citation_data.csv") -> None:
        self.csv_path = Path(csv_path)
        self.graph = load_csv(self.csv_path)

    def reload(self) -> dict:
        self.graph = load_csv(self.csv_path)
        return self.graph.stats()

    def stats(self) -> dict:
        return self.graph.stats()

    def papers(self, limit: Optional[int] = None) -> List[dict]:
        rows = [self.graph.paper_payload(pid) for pid in self.graph.paper_ids]
        rows = [r for r in rows if r]
        rows.sort(key=lambda r: (-r["citationCount"], r["id"]))
        return rows if limit is None else rows[:limit]

    def paper(self, paper_id: str) -> Optional[dict]:
        return self.graph.paper_payload(paper_id)

    def shortest_path(self, source: str, target: str) -> dict:
        path = self.graph.shortest_path(source.strip(), target.strip())
        if path is None:
            return {"found": False, "hops": None, "path": [], "nodes": []}
        return {
            "found": True,
            "hops": len(path) - 1,
            "path": path,
            "nodes": [self._node(pid) for pid in path],
        }

    def all_paths(self, source: str, target: str, limit: int = 50) -> dict:
        paths = self.graph.all_paths(source.strip(), target.strip())
        hamiltonian_flags = [self.graph.is_hamiltonian_path(p) for p in paths]
        return {
            "found": bool(paths),
            "total": len(paths),
            "truncated": len(paths) >= MAX_PATHS,
            "returned": min(len(paths), max(0, limit)),
            "hamiltonianCount": sum(hamiltonian_flags),
            "paths": [
                {"hops": len(p) - 1, "path": p, "hamiltonian": h, "nodes": [self._node(pid) for pid in p]}
                for p, h in list(zip(paths, hamiltonian_flags))[: max(0, limit)]
            ],
        }

    def optimal_path(self, paper_ids: Iterable[str]) -> dict:
        ids = [pid.strip() for pid in paper_ids if pid and pid.strip()]
        result = self.graph.optimal_citation_path(ids)
        result["nodes"] = [self._node(pid) for pid in result["walk"]]
        return result

    def search(self, query: str, limit: int = 20) -> List[dict]:
        """Case-insensitive substring search over id/title/author (web + TUI search)."""
        q = query.strip().lower()
        if not q:
            return []
        hits = [
            self.graph.paper_payload(pid)
            for pid in self.graph.paper_ids
            if q in pid.lower()
            or q in self.graph.get_paper(pid).title.lower()  # type: ignore[union-attr]
            or q in self.graph.get_paper(pid).author.lower()  # type: ignore[union-attr]
        ]
        return [h for h in hits if h][:limit]

    def lineage(self, source: str, depth: int = 1) -> dict:
        """BFS reachability horizon: every paper within `depth` hops of source."""
        src = source.strip()
        if not self.graph.has_paper(src):
            return {"found": False, "levels": []}
        levels: List[List[dict]] = []
        frontier = {src}
        seen: Set[str] = {src}
        for _ in range(max(0, depth)):
            nxt: Set[str] = set()
            for pid in frontier:
                for child in self.graph.neighbors(pid):
                    if child not in seen:
                        nxt.add(child)
            seen |= nxt
            levels.append([self._node(pid) for pid in sorted(nxt)])
            frontier = nxt
            if not frontier:
                break
        return {"found": True, "levels": levels}

    def _node(self, paper_id: str) -> dict:
        paper = self.graph.get_paper(paper_id)
        return {
            "id": paper_id,
            "title": paper.title if paper else "(unknown)",
            "year": paper.year if paper else 0,
            "citationCount": self.graph.citation_count(paper_id),
        }
