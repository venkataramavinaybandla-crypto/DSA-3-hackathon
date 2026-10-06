"""
CERBERUS SYSTEM — Static frame views.

Every function here returns a *fully rendered, fixed-width* Rich renderable
that goes INSIDE the window frame. Panels never contain prompts, so nothing
typed by the user can ever distort a border — views only change when a new
frame is rebuilt between input cycles.
"""

from __future__ import annotations

from typing import Any, Dict, List, Optional

from rich.box import DOUBLE, HEAVY, ROUNDED, SQUARE
from rich.console import Group, RenderableType
from rich.panel import Panel
from rich.rule import Rule
from rich.table import Table
from rich.text import Text

from . import theme
from .blockfont import render_banner, render_block_rule

TRUNCATE_TITLES_AT = 44


def clip(value: str, width: int) -> str:
    value = value or ""
    return value if len(value) <= width else value[: width - 1] + "…"


# ---------------------------------------------------------------- header --

def header_block(width: int, subtitle: str) -> RenderableType:
    """The pixel-perfect block wordmark + block rule, painted OUTSIDE the frame."""
    banner = render_banner("CERBERUS")
    rule = render_block_rule(width, color=theme.TECH_DIM)
    sub = Text.assemble(
        ("S Y S T E M", f"bold {theme.CYBER}"),
        ("   ·   ", theme.GHOST_FAINT),
        (subtitle, theme.GHOST_MUTED),
    )
    return Group(banner, rule, Text(""), sub, Text(""))


# ------------------------------------------------------------- dashboard --

def metrics_row(stats: Dict[str, Any], width: int) -> RenderableType:
    """Four metric tiles separated by violet rules — Laser Pink numbers."""
    most = stats.get("most_cited") or "—"
    tiles = [
        (str(stats.get("papers", 0)), "PAPERS"),
        (str(stats.get("edges", 0)), "CITATION EDGES"),
        (most, f"MOST CITED · {stats.get('most_cited_count', 0)}"),
        ("O(V+E)", "TRAVERSAL COST"),
    ]
    grid = Table.grid(padding=(0, 1), expand=True)
    grid.add_column(justify="center", ratio=1)
    for _ in range(3):
        grid.add_column(justify="center", width=1)
        grid.add_column(justify="center", ratio=1)
    row: List[RenderableType] = []
    for i, (value, label) in enumerate(tiles):
        cell = Text()
        cell.append(value, style=f"bold {theme.LASER}")
        cell.append("\n")
        cell.append(label, style=theme.GHOST_FAINT)
        if i:
            row.append(Text("▏", style=theme.TECH_DIM))
        row.append(cell)
    grid.add_row(*row)
    return grid


def top_cited_table(rows: List[Dict[str, Any]], width: int) -> RenderableType:
    table = Table(
        box=SQUARE,
        border_style=theme.TECH_DIM,
        header_style=f"bold {theme.CYBER}",
        expand=False,
        pad_edge=False,
    )
    table.add_column("#", justify="right", width=3, style=theme.GHOST_FAINT)
    table.add_column("ID", width=6, style=f"bold {theme.LASER}")
    table.add_column("Title", width=42, style=theme.GHOST, no_wrap=True, overflow="ellipsis")
    table.add_column("Year", justify="right", width=5, style=theme.GHOST_MUTED)
    table.add_column("Cites", justify="right", width=6, style=f"bold {theme.LASER}")
    table.add_column("Share", width=10 if width >= 100 else 6)

    top_count = max((r.get("citationCount", 0) for r in rows), default=0) or 1
    bar_w = 10 if width >= 100 else 6
    for i, row in enumerate(rows, start=1):
        count = row.get("citationCount", 0)
        filled = round((count / top_count) * bar_w)
        bar = Text("█" * filled + "░" * (bar_w - filled), style=f"{theme.LASER} on {theme.OBSIDIAN_SOFT}")
        table.add_row(
            str(i),
            row["id"],
            clip(row["title"], TRUNCATE_TITLES_AT),
            str(row.get("year", "")),
            str(count),
            bar,
        )
    return table


def rail_list(width: int) -> RenderableType:
    """Left rail: the hand-built data structures and their complexities."""
    grid = Table.grid(padding=(0, 0, 0, 0), expand=True)
    grid.add_column(ratio=1)
    grid.add_column(width=11, justify="right")

    def section(title: str, entries: List[tuple[str, str]]) -> None:
        head = Text(title, style=f"bold {theme.CYPER_BRIGHT}")
        grid.add_row(head, Text(""))
        for name, tag in entries:
            grid.add_row(Text(f"  {name}", style=theme.GHOST_MUTED), Text(tag, style=f"bold {theme.CYAN_INFO}"))
        grid.add_row(Text(""), Text(""))

    section(
        "DATA STRUCTURES",
        [
            ("Adjacency List Graph", "O(V+E)"),
            ("Custom Hash Table", "O(1)"),
            ("Array Stack & Queue", "O(1)"),
            ("Dynamic Array", "O(1)*"),
        ],
    )
    section(
        "ALGORITHMS",
        [
            ("BFS Shortest Path", "O(V+E)"),
            ("All Paths (DFS)", "exp."),
            ("Hamiltonian Check", "O(n)"),
            ("Bitmask DP Route", "O(2ⁿ·n²)"),
        ],
    )
    return grid


# ------------------------------------------------------------ results --

def path_flow_panel(resp: Dict[str, Any], title: str) -> RenderableType:
    """A shortest/optimal path as node chips joined by violet arrows."""
    if not resp.get("found") and not resp.get("walk"):
        return error_panel(resp.get("error") or "No path found.", title)

    walk = resp.get("path") or resp.get("walk") or []
    hops = resp.get("hops")
    flow = Text()
    for i, pid in enumerate(walk):
        if i:
            flow.append(" → ", style=f"bold {theme.TECH_BRIGHT}")
        flow.append(f" {pid} ", style=f"bold {theme.LASER} on {theme.OBSIDIAN_SOFT}")
    meta = Text()
    meta.append(f"HOPS ", style=theme.GHOST_FAINT)
    meta.append(str(hops if hops is not None else "—"), style=f"bold {theme.LASER}")
    if resp.get("nodes"):
        meta.append("   ·   ", style=theme.GHOST_FAINT)
        titles = " ▸ ".join(clip(n["title"], 30) for n in resp["nodes"][:4])
        meta.append(titles, style=theme.GHOST_MUTED)
    return Panel(Group(flow, Text(""), meta), title=title, border_style=theme.TECH, box=ROUNDED, expand=True)


def all_paths_table(resp: Dict[str, Any], width: int) -> RenderableType:
    if not resp.get("found"):
        return error_panel("No simple path exists between those papers.", "ALL PATHS")
    table = Table(
        box=SQUARE,
        border_style=theme.TECH_DIM,
        header_style=f"bold {theme.CYBER}",
        expand=False,
        pad_edge=False,
    )
    table.add_column("#", justify="right", width=3, style=theme.GHOST_FAINT)
    table.add_column("Route", width=60, style=theme.GHOST, no_wrap=True, overflow="ellipsis")
    table.add_column("Hops", justify="right", width=5, style=f"bold {theme.LASER}")
    table.add_column("Type", width=14)
    for i, p in enumerate(resp.get("paths", []), start=1):
        route = " → ".join(p["path"])
        kind = Text("[HAMILTONIAN]", style=f"bold {theme.GREEN_OK}") if p.get("hamiltonian") else Text("simple", style=theme.GHOST_FAINT)
        table.add_row(str(i), clip(route, 70), str(p["hops"]), kind)
    summary = Text()
    summary.append(f"total enumerated: ", theme.GHOST_FAINT)
    summary.append(str(resp.get("total", 0)), style=f"bold {theme.LASER}")
    summary.append("   ·   hamiltonian: ", theme.GHOST_FAINT)
    summary.append(str(resp.get("hamiltonianCount", 0)), style=f"bold {theme.GREEN_OK}")
    if resp.get("truncated"):
        summary.append("   ·   [engine cap 10,000 reached]", style=f"bold {theme.RED_ERR}")
    return Panel(Group(table, Text(""), summary), title="ALL SIMPLE PATHS · DFS ENUMERATION", border_style=theme.TECH, box=ROUNDED, expand=True)


def optimal_panel(resp: Dict[str, Any]) -> RenderableType:
    if not resp.get("walk"):
        return error_panel("No valid ordering visits every requested paper.", "BITMASK DP · HELD-KARP")
    flow = path_flow_panel(
        {"walk": resp["walk"], "hops": resp.get("hops"), "nodes": resp.get("nodes")},
        "OPTIMAL CITATION ROUTE · HELD-KARP BITMASK DP",
    )
    order = Text()
    order.append("visiting order:  ", theme.GHOST_FAINT)
    order.append(" → ".join(resp.get("order", [])), style=f"bold {theme.CYPER_BRIGHT}")
    return Group(flow, Text(""), Panel(Group(order), border_style=theme.TECH_DIM, box=SQUARE, expand=True))


def lineage_view(resp: Dict[str, Any]) -> RenderableType:
    if not resp.get("found"):
        return error_panel("Unknown source paper.", "CITATION LINEAGE")
    table = Table(box=SQUARE, border_style=theme.TECH_DIM, header_style=f"bold {theme.CYBER}", expand=False, pad_edge=False)
    table.add_column("Horizon", justify="right", width=8, style=f"bold {theme.CYPER_BRIGHT}")
    table.add_column("Papers reached", width=64, style=theme.GHOST, no_wrap=True, overflow="ellipsis")
    for level, nodes in enumerate(resp.get("levels", []), start=1):
        ids = "  ".join(n["id"] for n in nodes) if nodes else "—"
        table.add_row(f"hop {level}", clip(ids, 80))
    return Panel(table, title="CITATION LINEAGE · BFS HORIZON", border_style=theme.TECH, box=ROUNDED, expand=True)


def traversal_view(resp: Dict[str, Any], title: str) -> RenderableType:
    """BFS/DFS visit order from /api/traverse as a hop-numbered table."""
    order = resp.get("order") or []
    if not order:
        return error_panel("Traversal reached no papers.", title)
    table = Table(box=SQUARE, border_style=theme.TECH_DIM, header_style=f"bold {theme.CYBER}", expand=False, pad_edge=False)
    table.add_column("#", justify="right", width=4, style=theme.GHOST_FAINT)
    table.add_column("ID", width=6, style=f"bold {theme.LASER}")
    table.add_column("Title", width=44, style=theme.GHOST, no_wrap=True, overflow="ellipsis")
    table.add_column("Year", justify="right", width=5, style=theme.GHOST_MUTED)
    table.add_column("Cites", justify="right", width=6, style=f"bold {theme.LASER}")
    for i, node in enumerate(order, start=1):
        table.add_row(
            str(i),
            node["id"],
            clip(node["title"], TRUNCATE_TITLES_AT),
            str(node.get("year", "")),
            str(node.get("citationCount", 0)),
        )
    summary = Text()
    summary.append("visit order · ", theme.GHOST_FAINT)
    summary.append(str(resp.get("reached", len(order))), style=f"bold {theme.LASER}")
    summary.append(" papers reached · hop 1 is the source · ", theme.GHOST_FAINT)
    summary.append(str(resp.get("mode", "bfs")).upper(), style=f"bold {theme.CYPER_BRIGHT}")
    return Panel(Group(table, Text(""), summary), title=title, border_style=theme.TECH, box=ROUNDED, expand=True)


def authors_table(rows: List[Dict[str, Any]], width: int) -> RenderableType:
    """Top authors by summed citations — share bars like the top-cited table."""
    if not rows:
        return error_panel("No authors found in the graph.", "TOP AUTHORS")
    table = Table(box=SQUARE, border_style=theme.TECH_DIM, header_style=f"bold {theme.CYBER}", expand=False, pad_edge=False)
    table.add_column("#", justify="right", width=3, style=theme.GHOST_FAINT)
    table.add_column("Author", width=34, style=theme.CYPER_BRIGHT, no_wrap=True, overflow="ellipsis")
    table.add_column("Papers", justify="right", width=6, style=theme.GHOST)
    table.add_column("Citations", justify="right", width=9, style=f"bold {theme.LASER}")
    table.add_column("Share", width=10 if width >= 100 else 6)

    top_count = max((r.get("totalCitations", 0) for r in rows), default=0) or 1
    bar_w = 10 if width >= 100 else 6
    for i, row in enumerate(rows, start=1):
        count = row.get("totalCitations", 0)
        filled = round((count / top_count) * bar_w)
        bar = Text("█" * filled + "░" * (bar_w - filled), style=f"{theme.LASER} on {theme.OBSIDIAN_SOFT}")
        table.add_row(
            str(i),
            clip(row.get("author", "Unknown"), 34),
            str(row.get("papers", 0)),
            str(count),
            bar,
        )
    summary = Text()
    summary.append("sorted by ", theme.GHOST_FAINT)
    summary.append("total citations", style=f"bold {theme.CYPER_BRIGHT}")
    summary.append(" · papers counted per author", theme.GHOST_FAINT)
    return Panel(Group(table, Text(""), summary), title="TOP AUTHORS · YEARLY INFLUENCE",
                 border_style=theme.TECH, box=ROUNDED, expand=True)


def trends_view(rows: List[Dict[str, Any]], width: int) -> RenderableType:
    """Yearly citation trend with a proportional citation bar per bucket."""
    if not rows:
        return error_panel("No year buckets in the graph.", "CITATION TRENDS")
    table = Table(box=SQUARE, border_style=theme.TECH_DIM, header_style=f"bold {theme.CYBER}", expand=False, pad_edge=False)
    table.add_column("Year", justify="right", width=6, style=f"bold {theme.CYPER_BRIGHT}")
    table.add_column("Papers", justify="right", width=6, style=theme.GHOST)
    table.add_column("Citations", justify="right", width=9, style=f"bold {theme.LASER}")
    table.add_column("Citation load", width=24 if width >= 100 else 14)

    top_count = max((r.get("totalCitations", 0) for r in rows), default=0) or 1
    bar_w = 24 if width >= 100 else 14
    for row in rows:
        count = row.get("totalCitations", 0)
        filled = round((count / top_count) * bar_w)
        bar = Text("█" * filled + "░" * (bar_w - filled), style=f"{theme.CYPER_BRIGHT} on {theme.OBSIDIAN_SOFT}")
        table.add_row(
            str(row.get("year", "—")),
            str(row.get("papers", 0)),
            str(count),
            bar,
        )
    summary = Text()
    summary.append(f"{len(rows)} year buckets · ", theme.GHOST_FAINT)
    summary.append("citations summed by publication year of the cited paper", theme.GHOST_FAINT)
    return Panel(Group(table, Text(""), summary), title="YEARLY CITATION TRENDS",
                 border_style=theme.TECH, box=ROUNDED, expand=True)


def paper_detail_panel(record: Dict[str, Any]) -> RenderableType:
    body = Table.grid(padding=(0, 2), expand=True)
    body.add_column(justify="right", width=10)
    body.add_column(ratio=1)
    body.add_row(Text("ID", style=theme.GHOST_FAINT), Text(record["id"], style=f"bold {theme.LASER}"))
    body.add_row(Text("Title", style=theme.GHOST_FAINT), Text(record["title"], style=theme.GHOST))
    body.add_row(Text("Author", style=theme.GHOST_FAINT), Text(record.get("author", "—"), style=theme.CYPER_BRIGHT))
    body.add_row(Text("Year", style=theme.GHOST_FAINT), Text(str(record.get("year", "—")), style=theme.GHOST))
    body.add_row(Text("Citations", style=theme.GHOST_FAINT), Text(str(record.get("citationCount", 0)), style=f"bold {theme.LASER}"))
    cites = Text(", ".join(record.get("cites", []) or ["—"]), style=theme.GHOST_MUTED)
    cited_by = Text(", ".join(record.get("citedBy", []) or ["—"]), style=theme.GHOST_MUTED)
    body.add_row(Text("Cites", style=theme.GHOST_FAINT), cites)
    body.add_row(Text("Cited by", style=theme.GHOST_FAINT), cited_by)
    return Panel(body, title=f"PAPER RECORD · {record['id']}", border_style=theme.TECH, box=ROUNDED, expand=True)


def papers_table(rows: List[Dict[str, Any]], title: str) -> RenderableType:
    table = Table(box=SQUARE, border_style=theme.TECH_DIM, header_style=f"bold {theme.CYBER}", expand=False, pad_edge=False)
    table.add_column("ID", width=6, style=f"bold {theme.LASER}")
    table.add_column("Title", width=42, style=theme.GHOST, no_wrap=True, overflow="ellipsis")
    table.add_column("Author", width=24, style=theme.CYPER_BRIGHT, no_wrap=True, overflow="ellipsis")
    table.add_column("Year", justify="right", width=5, style=theme.GHOST_MUTED)
    table.add_column("Cites", justify="right", width=6, style=f"bold {theme.LASER}")
    for row in rows:
        table.add_row(
            row["id"],
            clip(row["title"], TRUNCATE_TITLES_AT),
            clip(row.get("author", "—"), 24),
            str(row.get("year", "")),
            str(row.get("citationCount", 0)),
        )
    return Panel(table, title=title, border_style=theme.TECH, box=ROUNDED, expand=True)


def error_panel(message: str, title: str = "ERROR") -> RenderableType:
    body = Text(f"  {message}  ", style=f"bold {theme.RED_ERR}")
    return Panel(body, title=f"✕ {title}", border_style=theme.RED_ERR, box=ROUNDED, expand=True)


def status_panel(message: str, ok: bool = True) -> RenderableType:
    mark = "✓" if ok else "✕"
    style = theme.GREEN_OK if ok else theme.RED_ERR
    body = Text(f"  {message}  ", style=f"bold {style}")
    return Panel(body, title=mark, border_style=style, box=SQUARE, expand=True)
