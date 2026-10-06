"""CERBERUS SYSTEM — API schemas (the contract both the TUI and web UI consume)."""

from __future__ import annotations

from typing import List, Optional

from pydantic import BaseModel, Field


class PaperNode(BaseModel):
    """A paper as it appears inside a path/lineage payload."""

    id: str
    title: str
    year: int
    citationCount: int


class PaperRecord(BaseModel):
    """Full paper record."""

    id: str
    title: str
    author: str
    year: int
    citationCount: int
    cites: List[str] = Field(default_factory=list)
    citedBy: List[str] = Field(default_factory=list)


class StatsResponse(BaseModel):
    papers: int
    edges: int
    most_cited: Optional[str] = None
    most_cited_count: int = 0


class ReloadResponse(StatsResponse):
    reloaded: bool = True


class PathNodeStep(BaseModel):
    id: str
    title: str
    year: int
    citationCount: int


class ShortestPathResponse(BaseModel):
    found: bool
    hops: Optional[int] = None
    path: List[str] = Field(default_factory=list)
    nodes: List[PathNodeStep] = Field(default_factory=list)


class PathEntry(BaseModel):
    hops: int
    path: List[str]
    hamiltonian: bool
    nodes: List[PathNodeStep]


class AllPathsResponse(BaseModel):
    found: bool
    total: int
    truncated: bool
    returned: int
    hamiltonianCount: int
    paths: List[PathEntry]


class OptimalPathResponse(BaseModel):
    hops: Optional[int]
    order: List[str] = Field(default_factory=list)
    walk: List[str] = Field(default_factory=list)
    nodes: List[PathNodeStep] = Field(default_factory=list)


class LineageResponse(BaseModel):
    found: bool
    levels: List[List[PaperNode]] = Field(default_factory=list)


class SearchResponse(BaseModel):
    query: str
    results: List[PaperRecord]
    fuzzy: bool = False


class TraverseResponse(BaseModel):
    found: bool
    mode: str
    source: Optional[str] = None
    reached: int = 0
    order: List[PathNodeStep] = Field(default_factory=list)


class AuthorStatsRow(BaseModel):
    author: str
    papers: int
    totalCitations: int


class AuthorsResponse(BaseModel):
    authors: List[AuthorStatsRow]


class YearTrendRow(BaseModel):
    year: int
    papers: int
    totalCitations: int


class TrendsResponse(BaseModel):
    trends: List[YearTrendRow]


class PaperCreate(BaseModel):
    id: str = Field(..., min_length=1)
    title: str = Field(..., min_length=1)
    author: str = Field(..., min_length=1)
    year: int = Field(..., ge=1500, le=2100)


class CitationCreate(BaseModel):
    citing: str = Field(..., min_length=1)
    cited: str = Field(..., min_length=1)


class CitationResponse(BaseModel):
    added: bool
    citing: str
    cited: str
    papers: int
    edges: int
    most_cited: Optional[str] = None
    most_cited_count: int = 0
