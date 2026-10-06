"""
CERBERUS SYSTEM — API client for the Rich TUI.

The terminal is a *thin client*: every graph computation (shortest path,
all paths + Hamiltonian, bitmask-DP optimal route, lineage, search, stats)
is fetched from the local FastAPI server. No graph logic lives here —
mirroring the web dashboard's fetch layer so both interfaces display
identical data.
"""

from __future__ import annotations

import os
from typing import Any, Dict, Optional

import requests

DEFAULT_BASE_URL = "http://127.0.0.1:8005"


class ApiError(RuntimeError):
    """Raised for any transport or API-level failure, carrying a readable message."""

    def __init__(self, message: str, status: Optional[int] = None) -> None:
        super().__init__(message)
        self.status = status


class CerberusClient:
    """Minimal typed wrapper over the local CERBERUS FastAPI endpoints."""

    def __init__(self, base_url: Optional[str] = None, timeout: float = 10.0) -> None:
        env_url = os.environ.get("CERBERUS_API_URL", "")
        self.base_url = (base_url or env_url or DEFAULT_BASE_URL).rstrip("/")
        self.timeout = timeout
        self._session = requests.Session()

    # ------------------------------------------------------------- helpers --

    def _get(self, path: str, params: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        try:
            response = self._session.get(f"{self.base_url}{path}", params=params, timeout=self.timeout)
        except requests.ConnectionError as exc:
            raise ApiError(f"Cannot reach the CERBERUS API at {self.base_url} — is the server running?\n"
                           f"  start it with:  python -m uvicorn server.main:app --port 8005") from exc
        except requests.Timeout as exc:
            raise ApiError(f"Request to {path} timed out after {self.timeout}s.") from exc
        return self._unwrap(response)

    def _post(self, path: str, json_body: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        try:
            response = self._session.post(f"{self.base_url}{path}", json=json_body or {}, timeout=self.timeout)
        except requests.ConnectionError as exc:
            raise ApiError(f"Cannot reach the CERBERUS API at {self.base_url}.") from exc
        except requests.Timeout as exc:
            raise ApiError(f"Request to {path} timed out after {self.timeout}s.") from exc
        return self._unwrap(response)

    @staticmethod
    def _unwrap(response: requests.Response) -> Dict[str, Any]:
        if response.status_code >= 400:
            try:
                payload = response.json()
                message = payload.get("error") or payload.get("detail") or response.text
            except ValueError:
                message = response.text or response.reason
            raise ApiError(str(message), status=response.status_code)
        try:
            return response.json()
        except ValueError as exc:
            raise ApiError(f"Malformed JSON from {response.url}") from exc

    # ------------------------------------------------------------- endpoints --

    def health(self) -> Dict[str, Any]:
        return self._get("/api/health")

    def stats(self) -> Dict[str, Any]:
        return self._get("/api/stats")

    def papers(self, limit: int = 20, query: str = "", fuzzy: bool = False) -> Dict[str, Any]:
        params: Dict[str, Any] = {"limit": limit}
        if query:
            params["q"] = query
        if fuzzy:
            params["fuzzy"] = True
        return self._get("/api/papers", params=params)

    def paper(self, paper_id: str) -> Dict[str, Any]:
        return self._get(f"/api/papers/{paper_id}")

    def shortest_path(self, source: str, target: str) -> Dict[str, Any]:
        return self._get("/api/path", {"source": source, "target": target})

    def all_paths(self, source: str, target: str, limit: int = 50) -> Dict[str, Any]:
        return self._get("/api/paths", {"source": source, "target": target, "limit": limit})

    def optimal_route(self, paper_ids: list[str]) -> Dict[str, Any]:
        return self._get("/api/optimal-route", {"papers": ",".join(paper_ids)})

    def lineage(self, source: str, depth: int = 2) -> Dict[str, Any]:
        return self._get("/api/lineage", {"source": source, "depth": depth})

    def traverse(self, source: str, mode: str = "bfs") -> Dict[str, Any]:
        """Full visit order from a paper — BFS or DFS, computed by the engine."""
        return self._get("/api/traverse", {"source": source, "mode": mode})

    def top_authors(self, limit: int = 10) -> Dict[str, Any]:
        return self._get("/api/reports/authors", {"limit": limit})

    def trends(self) -> Dict[str, Any]:
        return self._get("/api/reports/trends")

    def add_paper(self, paper_id: str, title: str, author: str, year: int) -> Dict[str, Any]:
        return self._post("/api/papers", {
            "id": paper_id, "title": title, "author": author, "year": year,
        })

    def add_citation(self, citing: str, cited: str) -> Dict[str, Any]:
        return self._post("/api/citations", {"citing": citing, "cited": cited})

    def reload(self) -> Dict[str, Any]:
        return self._post("/api/reload")
