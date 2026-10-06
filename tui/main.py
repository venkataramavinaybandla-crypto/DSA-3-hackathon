"""
CERBERUS SYSTEM — Rich Terminal User Interface.

Architecture (fixes frame breaks & input drift):

  ┌──────────────────────────────────────────────────┐
  │  STATIC FRAME (rebuilt between input cycles)     │
  │   · block-matrix wordmark (outside the sheet)    │
  │   · titlebar dots + double-line window sheet     │
  │   · metric tiles, data-structure rail, results   │
  │   · status line                                  │
  └──────────────────────────────────────────────────┘
        ▸ ISOLATED INPUT ZONE — plain console lines at the
          absolute bottom, OUTSIDE every panel. Prompt
          strings can therefore never touch a border.

Run:  python -m tui.main            (server: python -m uvicorn server.main:app --port 8005)
"""

from __future__ import annotations

from typing import Any, Callable, Dict, List, Optional

from rich.console import Group, RenderableType
from rich.panel import Panel
from rich.prompt import Prompt
from rich.rule import Rule
from rich.table import Table
from rich.text import Text

from . import layout, theme, views
from .client import ApiError, CerberusClient

MENU: tuple[tuple[str, str], ...] = (
    ("1", "Overview"),
    ("2", "Shortest path"),
    ("3", "All paths"),
    ("4", "Optimal route"),
    ("5", "Lineage"),
    ("6", "Search"),
    ("7", "Paper record"),
    ("8", "Reload CSV"),
    ("Q", "Quit"),
)


class CerberusTUI:
    """REPL driving a static, API-fed dashboard frame with a bottom input zone."""

    def __init__(self, client: Optional[CerberusClient] = None) -> None:
        self.client = client or CerberusClient()
        self.stats: Dict[str, Any] = {"papers": 0, "edges": 0, "most_cited": None, "most_cited_count": 0}
        self.views_stack: List[RenderableType] = []
        self.status_message: tuple[str, bool] = ("booting…", True)
        self.running = True

    # ------------------------------------------------------------ status --

    def set_status(self, message: str, ok: bool = True) -> None:
        self.status_message = (message, ok)

    # ------------------------------------------------------------ prompts --

    def _prompt(self, label: str, default: Optional[str] = None) -> Optional[str]:
        """Bottom-zone prompt: plain line under the frame, borders untouched."""
        mark = Text("  ▸ ", style=f"bold {theme.LASER}")
        try:
            value = Prompt.ask(mark + Text(label, style=f"bold {theme.GHOST}"),
                               default=default or "", show_default=bool(default),
                               console=theme.console)
        except (EOFError, KeyboardInterrupt):
            return None
        value = value.strip()
        return value or (default or None)

    # ------------------------------------------------------------- frames --

    def _input_zone(self) -> RenderableType:
        grid = Table.grid(padding=(0, 2))
        for _ in range(3):
            grid.add_column(ratio=1)
        items = list(MENU)
        for start in range(0, len(items), 3):
            row: List[RenderableType] = []
            for key, label in items[start:start + 3]:
                cell = Text("  ")
                cell.append(key, style=f"bold {theme.LASER}")
                cell.append(f"  {label}", style=theme.GHOST_MUTED)
                row.append(cell)
            row += [Text("")] * (3 - len(row))
            grid.add_row(*row)
        hint = Text()
        hint.append("  ▸ key or inline: ", style=theme.GHOST_FAINT)
        hint.append("path A B", theme.CYPER_BRIGHT)
        hint.append(" · ", theme.GHOST_FAINT)
        hint.append("route P101,P104,P107", theme.CYPER_BRIGHT)
        hint.append(" · ", theme.GHOST_FAINT)
        hint.append("search attention", theme.CYPER_BRIGHT)
        return Group(Rule(style=theme.TECH_DIM), Text(""), grid, Text(""), hint)

    def render(self) -> RenderableType:
        return Group(
            layout.frame(self.client, self.stats, self.views_stack, self.status_message),
            Text(""),
            self._input_zone(),
        )

    # ------------------------------------------------------------- helpers --

    def refresh_home(self) -> None:
        try:
            self.stats = self.client.stats()
            papers = self.client.papers(limit=8).get("results", [])
            self.views_stack = [views.top_cited_table(papers, theme.console.width)]
            self.set_status(f"engine online · {self.stats.get('papers')} papers · "
                            f"{self.stats.get('edges')} edges · most cited {self.stats.get('most_cited')}")
        except ApiError as exc:
            self.set_status(str(exc).splitlines()[0], ok=False)

    # ------------------------------------------------------------- actions --

    def _fail(self, exc: ApiError) -> None:
        self.views_stack = [views.error_panel(str(exc), "API ERROR")]
        self.set_status(str(exc).splitlines()[0], ok=False)

    def action_overview(self) -> None:
        self.refresh_home()

    def action_shortest_path(self, source: str, target: str) -> None:
        try:
            resp = self.client.shortest_path(source, target)
        except ApiError as exc:
            self._fail(exc)
            return
        if resp.get("found"):
            label = f"BFS SHORTEST CITATION PATH · {source.upper()} → {target.upper()}"
            self.views_stack = [views.path_flow_panel(resp, label)]
            self.set_status(f"shortest path · {resp.get('hops')} hops · {source.upper()} → {target.upper()}")
        else:
            self.views_stack = [views.error_panel(f"No directed citation path {source.upper()} → {target.upper()}.", "UNREACHABLE")]
            self.set_status("unreachable", ok=False)

    def action_all_paths(self, source: str, target: str) -> None:
        try:
            resp = self.client.all_paths(source, target, limit=12)
        except ApiError as exc:
            self._fail(exc)
            return
        self.views_stack = [views.all_paths_table(resp, theme.console.width)]
        self.set_status(f"all paths · {resp.get('total')} enumerated · "
                        f"{resp.get('hamiltonianCount')} hamiltonian")

    def action_optimal_route(self, ids_text: str) -> None:
        ids = [p.strip().upper() for p in ids_text.replace(" ", ",").split(",") if p.strip()]
        if len(ids) < 2:
            self.views_stack = [views.error_panel("Provide at least two paper IDs, comma separated.", "BITMASK DP")]
            return
        try:
            resp = self.client.optimal_route(ids)
        except ApiError as exc:
            self._fail(exc)
            return
        self.views_stack = [views.optimal_panel(resp)]
        if resp.get("walk"):
            self.set_status(f"optimal route · {resp.get('hops')} hops · visits {len(resp.get('order', []))} papers")
        else:
            self.set_status("no valid ordering", ok=False)

    def action_lineage(self, source: str, depth_text: str) -> None:
        try:
            depth = max(1, min(6, int(depth_text or 2)))
        except ValueError:
            depth = 2
        try:
            resp = self.client.lineage(source, depth)
        except ApiError as exc:
            self._fail(exc)
            return
        self.views_stack = [views.lineage_view(resp)]
        self.set_status(f"lineage · BFS horizon from {source.upper()} · depth {depth}")

    def action_search(self, query: str) -> None:
        try:
            resp = self.client.papers(limit=15, query=query)
        except ApiError as exc:
            self._fail(exc)
            return
        results = resp.get("results", [])
        self.views_stack = [views.papers_table(results, f"SEARCH · '{query}' · {len(results)} hits")]
        self.set_status(f"search '{query}' · {len(results)} results" if results else f"no hits for '{query}'",
                        ok=bool(results))

    def action_paper(self, paper_id: str) -> None:
        try:
            record = self.client.paper(paper_id)
        except ApiError as exc:
            self._fail(exc)
            return
        self.views_stack = [views.paper_detail_panel(record)]
        self.set_status(f"paper record · {paper_id.upper()} · {record.get('citationCount', 0)} citations")

    def action_reload(self) -> None:
        try:
            self.client.reload()
            self.refresh_home()
            self.set_status("dataset reloaded from citation_data.csv")
        except ApiError as exc:
            self._fail(exc)

    # -------------------------------------------------------------- parse --

    def parse_inline(self, raw: str) -> Optional[tuple[str, List[str]]]:
        tokens = raw.strip().split()
        if not tokens:
            return None
        word = tokens[0].lower()
        alias = {
            "path": "2", "paths": "3", "all": "3", "route": "4", "optimal": "4",
            "lineage": "5", "search": "6", "find": "6", "paper": "7", "view": "7",
            "overview": "1", "stats": "1", "home": "1", "dashboard": "1",
            "reload": "8", "quit": "q", "exit": "q",
        }
        key = alias.get(word, tokens[0])
        return key, tokens[1:]

    def handle(self, raw: str) -> None:
        parsed = self.parse_inline(raw)
        if not parsed:
            self.set_status("empty input — choose a module key", ok=False)
            return
        key, args = parsed
        key = key.upper() if key.upper() in {"Q"} else key
        handlers: Dict[str, Callable[..., None]] = {
            "1": self.action_overview,
            "2": self.action_shortest_path,
            "3": self.action_all_paths,
            "4": self.action_optimal_route,
            "5": self.action_lineage,
            "6": self.action_search,
            "7": self.action_paper,
            "8": self.action_reload,
        }
        if key == "Q":
            self.running = False
            return
        if key not in handlers:
            self.set_status(f"unknown module '{key}' — pick 1-8 or Q", ok=False)
            return

        # Interactive fill-in for missing arguments, all inside the input zone.
        handler = handlers[key]
        if key == "2":
            source = args[0] if args else self._prompt("Enter SOURCE paper ID", "P103")
            target = args[1] if len(args) > 1 else self._prompt("Enter TARGET paper ID", "P101")
            if source and target:
                handler(source.upper(), target.upper())
        elif key == "3":
            source = args[0] if args else self._prompt("Enter SOURCE paper ID", "P103")
            target = args[1] if len(args) > 1 else self._prompt("Enter TARGET paper ID", "P101")
            if source and target:
                handler(source.upper(), target.upper())
        elif key == "4":
            ids_text = ",".join(args) if args else self._prompt("Enter paper IDs (comma separated)", "P101,P102,P104")
            if ids_text:
                handler(ids_text)
        elif key == "5":
            source = args[0] if args else self._prompt("Enter SOURCE paper ID", "P101")
            depth = args[1] if len(args) > 1 else self._prompt("Depth 1-6", "2")
            if source:
                handler(source.upper(), depth)
        elif key == "6":
            query = " ".join(args) if args else self._prompt("Search query (title/author/id)", "attention")
            if query:
                handler(query)
        elif key == "7":
            paper_id = args[0] if args else self._prompt("Enter paper ID", "P101")
            if paper_id:
                handler(paper_id.upper())
        else:
            handler()

    # ---------------------------------------------------------------- run --

    def run(self) -> None:
        ok, message = layout.boot_banner_check(self.client)
        if not ok:
            theme.console.print(views.error_panel(message, "API OFFLINE"))
            theme.console.print(Text("Start the engine, then rerun the TUI:\n"
                                     "  python -m uvicorn server.main:app --port 8005",
                                     style=theme.GHOST_MUTED))
            return
        self.set_status(message)
        self.refresh_home()

        while self.running:
            theme.console.clear()
            theme.console.print(self.render())
            try:
                raw = Prompt.ask(Text("  ❯", style=f"bold {theme.LASER}"), console=theme.console, show_default=False)
            except (EOFError, KeyboardInterrupt):
                self.running = False
                break
            self.handle(raw)

        theme.console.print(Text("\n  ✓ session closed — edges stay directed.\n", style=f"bold {theme.GREEN_OK}"))


def main() -> None:
    theme.force_utf8_stdio()
    CerberusTUI().run()


if __name__ == "__main__":
    main()
