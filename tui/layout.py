"""
CERBERUS SYSTEM — Static frame layout.

The window sheet holds ONLY static output: titlebar, metrics, rail, result
views, status line. It is rebuilt as one Group between input cycles, so an
interactive prompt can never shift a border or push content out of the frame.
The input console lives at the absolute bottom of the screen, OUTSIDE this
frame (see interface.py).
"""

from __future__ import annotations

from typing import Any, Dict, List, Optional

from rich.box import DOUBLE, SQUARE
from rich.console import Group, RenderableType
from rich.panel import Panel
from rich.table import Table
from rich.text import Text

from . import theme, views
from .client import CerberusClient, ApiError

WINDOW_PATH = "cerberus@dsa3 : ~/citation-graph  --  citation_data.csv"


def titlebar(width: int) -> RenderableType:
    """Three macOS traffic-light dots + shell path, in a heavy rule."""
    dots = Text(" ●", style=f"bold {theme.RED_ERR}")
    dots.append(" ●", style=f"bold {theme.CYAN_INFO}")
    dots.append(" ●", style=f"bold {theme.GREEN_OK}")
    bar = Table.grid(expand=True)
    bar.add_column(justify="left", ratio=1)
    bar.add_column(justify="right")
    text = WINDOW_PATH
    limit = max(width - 16, 20)
    if len(text) > limit:
        text = text[: limit - 1] + "…"
    bar.add_row(Text(text, style=theme.GHOST_FAINT), dots)
    return bar


def frame(
    client: CerberusClient,
    stats: Dict[str, Any],
    views_stack: List[RenderableType],
    status_message: Optional[tuple[str, bool]] = None,
) -> RenderableType:
    """Assemble the complete static frame: banner + window sheet + status line."""
    width = theme.console.width
    inner = max(width - 4, 60)

    head = views.header_block(width, f"local API · {client.base_url}")

    bar = titlebar(inner)

    metrics = Panel(
        views.metrics_row(stats, inner),
        title="LIVE METRICS",
        border_style=theme.TECH,
        box=SQUARE,
        expand=True,
        title_align="left",
    )

    rail = views.rail_list(inner)

    body = Table.grid(expand=True, padding=(0, 2))
    body.add_column(width=30, vertical="top")
    body.add_column(ratio=1, vertical="top")
    body.add_row(rail, Group(*views_stack) if views_stack else Text("  No result yet — use the console below.", style=theme.GHOST_FAINT))

    status_text, ok = status_message or ("engine ready · zero java.util in core logic", True)
    status = Text()
    status.append("✓ " if ok else "✕ ", style=f"bold {theme.GREEN_OK if ok else theme.RED_ERR}")
    status.append(status_text, style=theme.GHOST_MUTED)

    sheet = Panel(
        Group(bar, Text(""), metrics, Text(""), body),
        border_style=theme.TECH,
        box=DOUBLE,
        expand=True,
        subtitle=status,
        subtitle_align="left",
    )

    return Group(head, sheet)


def boot_banner_check(client: CerberusClient) -> tuple[bool, str]:
    """Verify the API is alive before the loop starts; returns (ok, message)."""
    try:
        health = client.health()
        return True, (
            f"engine online · {health.get('papers', '?')} papers · "
            f"{health.get('edges', '?')} edges · api v{health.get('version', '?')}"
        )
    except ApiError as exc:
        return False, str(exc)
