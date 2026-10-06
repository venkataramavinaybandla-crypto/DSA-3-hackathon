"""
CERBERUS SYSTEM — Terminal theme.

The exact palette the web dashboard uses (tailwind.config.js / style.css):
  60% Obsidian Night · 30% Tech Violet · Cyber Purple secondary
  10% Laser Pink accent · Ghost White text

Also owns the single Rich Console: it forces UTF-8 on Windows so the block
matrix (█ ▀ ▄) never trips a cp1252 UnicodeEncodeError.
"""

from __future__ import annotations

import io
import os
import sys

from rich.console import Console
from rich.theme import Theme

# ---------------------------------------------------------------- palette --
OBSIDIAN = "#0B0813"        # canvas
OBSIDIAN_DEEP = "#07050D"
OBSIDIAN_SOFT = "#120D1F"
OBSIDIAN_LINE = "#1C1430"

TECH = "#5B0FFF"            # structural violet (borders, frames)
TECH_BRIGHT = "#7B3FFF"
TECH_DIM = "#2A0A66"

CYBER = "#A32EFF"           # secondary purple (authors, tags)
CYPER_BRIGHT = "#B85CFF"

LASER = "#FF007F"           # action pink (metrics, keys, highlights)
LASER_BRIGHT = "#FF4DA6"

GHOST = "#F5F3F7"           # body text
GHOST_MUTED = "#B9B1C9"
GHOST_FAINT = "#6E6684"

GREEN_OK = "#00E58C"        # semantic status — success
RED_ERR = "#FF3355"         # semantic status — errors (never theme-hidden)
CYAN_INFO = "#38E1FF"       # semantic status — notices

# ------------------------------------------------------------------ console --
_WIN32 = sys.platform.startswith("win")


def _force_utf8_streams() -> None:
    """Guarantee the block matrix survives Windows console codepages."""
    os.environ.setdefault("PYTHONIOENCODING", "utf-8")
    for stream_name in ("stdout", "stderr"):
        stream = getattr(sys, stream_name)
        if stream is not None and hasattr(stream, "reconfigure"):
            try:
                stream.reconfigure(encoding="utf-8", errors="replace")
            except (ValueError, OSError):
                pass
    if _WIN32:
        try:
            import colorama  # type: ignore

            colorama.just_fix_windows_console()
        except ImportError:
            pass  # Rich handles VT enabling on modern Windows terminals.


_force_utf8_streams()

CERBERUS_THEME = Theme(
    {
        "banner": f"bold {GHOST}",
        "title": f"bold {GHOST}",
        "violet": TECH,
        "violet.bright": TECH_BRIGHT,
        "violet.dim": TECH_DIM,
        "cyber": CYBER,
        "cyber.bright": CYPER_BRIGHT,
        "laser": f"bold {LASER}",
        "laser.bright": LASER_BRIGHT,
        "ghost": GHOST,
        "muted": GHOST_MUTED,
        "faint": GHOST_FAINT,
        "ok": f"bold {GREEN_OK}",
        "error": f"bold {RED_ERR}",
        "info": f"bold {CYAN_INFO}",
        "key": f"bold {LASER}",
        "metric": f"bold {LASER}",
        "tag": f"bold {CYAN_INFO}",
    }
)

console = Console(theme=CERBERUS_THEME, highlight=False, soft_wrap=False)


def force_utf8_stdio() -> None:
    """Public hook: re-assert UTF-8 stdio (idempotent, called at TUI start)."""
    _force_utf8_streams()


# ------------------------------------------------------------------ styles --
BORDER_VIOLET = f"rounded[{TECH}]"
BORDER_VIOLET_DOUBLE = f"double[{TECH}]"
BORDER_LASER = f"rounded[{LASER}]"
BORDER_CYBER = f"rounded[{CYBER}]"

TITLE_STYLE = f"bold {GHOST}"
HEAD_STYLE = f"bold {CYPER_BRIGHT}"
MUTED_STYLE = GHOST_MUTED
