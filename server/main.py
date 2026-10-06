"""
CERBERUS SYSTEM — FastAPI backend for the hybrid TUI + web platform.

One local server, one dataset, one set of endpoints. The Rich TUI and the web
dashboard are both *clients* of this API — neither holds graph logic.

Run from the project root:

    python -m uvicorn server.main:app --port 8005

    GET /                      -> the web dashboard (web/static served directly)
    GET /docs                  -> OpenAPI Swagger UI
    GET /api/health            -> engine status
    GET /api/stats             -> headline metrics (papers, edges, most cited)
    GET /api/papers?q=&limit=  -> ranked papers / substring search
    GET /api/papers/{id}       -> paper detail with in/out citation edges
    GET /api/path?source=&target=           -> BFS shortest citation path
    GET /api/paths?source=&target=&limit=   -> all simple paths + Hamiltonian flags
    GET /api/optimal-route?papers=a,b,c     -> Held-Karp bitmask DP optimal route
    GET /api/lineage?source=&depth=         -> BFS reachability horizon by level
    POST /api/reload                        -> re-read citation_data.csv

Environment:
    CERBERUS_CSV     path to the dataset (default: citation_data.csv at repo root)
"""

from __future__ import annotations

import os
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Optional

from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles

from . import engine as eng
from .schemas import (
    AllPathsResponse,
    AuthorsResponse,
    CitationCreate,
    CitationResponse,
    LineageResponse,
    OptimalPathResponse,
    PaperCreate,
    PaperRecord,
    PathNodeStep,
    ReloadResponse,
    SearchResponse,
    ShortestPathResponse,
    StatsResponse,
    TraverseResponse,
    TrendsResponse,
)

API_VERSION = "1.0.0"
REPO_ROOT = Path(__file__).resolve().parent.parent
WEB_DIR = REPO_ROOT / "web" / "static"

# The engine is created in lifespan startup (see below).
state: dict = {"engine": None}


def _engine() -> eng.CitationEngine:
    if state["engine"] is None:
        raise HTTPException(status_code=503, detail="Engine not initialised")
    return state["engine"]


def _resolve_paper(engine: eng.CitationEngine, paper_id: str) -> None:
    """404 with a machine-readable message when a paper id is unknown."""
    if not engine.graph.has_paper(paper_id):
        raise HTTPException(status_code=404, detail=f"Paper ID '{paper_id}' not found in the graph.")


@asynccontextmanager
async def lifespan(app: FastAPI):
    csv_path = os.environ.get("CERBERUS_CSV", str(REPO_ROOT / "citation_data.csv"))
    state["engine"] = eng.CitationEngine(csv_path)
    yield
    state["engine"] = None


app = FastAPI(
    title="CERBERUS SYSTEM — Citation Graph API",
    description="DSA citation-network engine: shortest paths, Hamiltonian checks and "
    "bitmask-DP optimal routes, served to both the terminal UI and the web dashboard.",
    version=API_VERSION,
    lifespan=lifespan,
)

# The dashboard may be served by this app (same origin) or opened from any
# static server / file:// — allow everything in local development.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/api/health")
def health() -> dict:
    e = _engine()
    s = e.stats()
    return {
        "ok": True,
        "engine": "cerberus-dsa",
        "version": API_VERSION,
        "csv": str(e.csv_path),
        **s,
    }


@app.get("/api/stats", response_model=StatsResponse)
def stats() -> StatsResponse:
    return _engine().stats()


@app.post("/api/reload", response_model=ReloadResponse)
def reload_dataset() -> ReloadResponse:
    e = _engine()
    s = e.reload()
    return ReloadResponse(reloaded=True, **s)


@app.get("/api/papers", response_model=SearchResponse)
def papers(
    q: str = Query("", description="Substring match on id/title/author; empty = all"),
    limit: int = Query(20, ge=1, le=200),
    fuzzy: bool = Query(False, description="Typo-tolerant Wagner-Fischer matching"),
) -> SearchResponse:
    e = _engine()
    if q.strip():
        results = e.search(q, limit=limit, fuzzy=fuzzy)
    else:
        results = e.papers(limit=limit)
    return SearchResponse(query=q, results=results, fuzzy=fuzzy)


@app.get("/api/search", response_model=SearchResponse)
def search(
    q: str = Query("...", min_length=1, description="Search query"),
    fuzzy: bool = Query(False, description="true = Wagner-Fischer fuzzy match"),
    limit: int = Query(20, ge=1, le=200),
) -> SearchResponse:
    """Plan Phase 8 contract endpoint: explicit fuzzy toggle."""
    e = _engine()
    return SearchResponse(query=q, results=e.search(q, limit=limit, fuzzy=fuzzy), fuzzy=fuzzy)


@app.get("/api/traverse", response_model=TraverseResponse)
def traverse(
    source: str = Query(..., min_length=1, description="Start paper id"),
    mode: str = Query("bfs", pattern="^(bfs|dfs)$", description="Traversal strategy"),
) -> TraverseResponse:
    e = _engine()
    _resolve_paper(e, source)
    return e.traverse(source, mode=mode)


@app.get("/api/reports/authors", response_model=AuthorsResponse)
def reports_authors(limit: int = Query(10, ge=1, le=100)) -> AuthorsResponse:
    return AuthorsResponse(authors=_engine().graph.top_authors(limit))


@app.get("/api/reports/trends", response_model=TrendsResponse)
def reports_trends() -> TrendsResponse:
    return TrendsResponse(trends=_engine().graph.yearly_trends())


@app.post("/api/papers", response_model=PaperRecord)
def create_paper(body: PaperCreate) -> PaperRecord:
    e = _engine()
    try:
        return e.add_paper(body.id, body.title, body.author, body.year)
    except ValueError as exc:
        raise HTTPException(status_code=409, detail=str(exc)) from exc


@app.post("/api/citations", response_model=CitationResponse)
def create_citation(body: CitationCreate) -> CitationResponse:
    e = _engine()
    try:
        return e.add_citation(body.citing, body.cited)
    except ValueError as exc:
        message = str(exc)
        code = 404 if "not found" in message else 409
        raise HTTPException(status_code=code, detail=message) from exc


@app.get("/api/papers/{paper_id}", response_model=PaperRecord)
def paper_detail(paper_id: str) -> PaperRecord:
    e = _engine()
    _resolve_paper(e, paper_id)
    return e.paper(paper_id)


@app.get("/api/path", response_model=ShortestPathResponse)
def shortest_path(
    source: str = Query(..., min_length=1, description="Citing paper id, e.g. P103"),
    target: str = Query(..., min_length=1, description="Cited paper id, e.g. P101"),
) -> ShortestPathResponse:
    e = _engine()
    _resolve_paper(e, source)
    _resolve_paper(e, target)
    return e.shortest_path(source, target)


@app.get("/api/paths", response_model=AllPathsResponse)
def all_paths(
    source: str = Query(..., min_length=1),
    target: str = Query(..., min_length=1),
    limit: int = Query(50, ge=1, le=500, description="Max paths returned (engine enumerates up to 10k)"),
) -> AllPathsResponse:
    e = _engine()
    _resolve_paper(e, source)
    _resolve_paper(e, target)
    return e.all_paths(source, target, limit=limit)


@app.get("/api/optimal-route", response_model=OptimalPathResponse)
def optimal_route(
    papers: str = Query(..., description="Comma-separated paper ids, e.g. P101,P104,P107 (max 20)"),
) -> OptimalPathResponse:
    e = _engine()
    ids = [p.strip() for p in papers.split(",") if p.strip()]
    try:
        return e.optimal_path(ids)
    except ValueError as exc:
        # Unknown-id and over-cap cases mirror the Java engine's error messages.
        message = str(exc)
        code = 404 if "was not found" in message else 400
        raise HTTPException(status_code=code, detail=message)


@app.get("/api/lineage", response_model=LineageResponse)
def lineage(
    source: str = Query(..., min_length=1),
    depth: int = Query(1, ge=1, le=6, description="How many BFS hops to expand"),
) -> LineageResponse:
    e = _engine()
    _resolve_paper(e, source)
    return e.lineage(source, depth=depth)


@app.exception_handler(HTTPException)
async def http_error(_: object, exc: HTTPException) -> JSONResponse:
    """Uniform error envelope for both clients: {ok: false, error: ...}."""
    return JSONResponse(status_code=exc.status_code, content={"ok": False, "error": exc.detail})


# Serve the web dashboard last so /api/* keeps priority.
app.mount("/", StaticFiles(directory=str(WEB_DIR), html=True), name="web")
