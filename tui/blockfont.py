"""
CERBERUS SYSTEM — Solid block-matrix wordmark renderer.

Renders text as a 5x5 pixel matrix drawn with Unicode half-block cells
(▀ ▄) so each terminal cell paints TWO pixel rows — the same technique
bitmap terminal fonts use to fake double vertical resolution. Combined
with 2px faux-bold dilation this reproduces the heavy, solid, anti-aliased
density of the Freebuff wordmark using only █ ▀ ▄ glyphs:

    ▀▀██▀▀   ← cell row 1 packs pixel rows 0-1 (top=fg, bottom=bg)
    ▀▄██▄▀   ← cell row 2 packs pixel rows 2-3
    ▄▄██▄▄   ← cell row 3 renders pixel row 4 as ▄

The wordmark is painted as a duotone gradient (Tech Violet → Cyber Purple →
Laser Pink) column by column, matching the web dashboard's Orbitron duotone.

Pure stdlib + Rich Text; no external font files, no pyfiglet.
"""

from __future__ import annotations

from typing import Iterable, List, Sequence, Tuple

from rich.text import Text

Pixel = bool
Matrix = List[List[Pixel]]

# ---------------------------------------------------------------------------
# 5x5 glyphs — hand-tuned heavy forms ("X" = ink, "." = void)
# ---------------------------------------------------------------------------

GLYPHS: dict[str, Matrix] = {
    "A": [".XXX.", "X...X", "XXXXX", "X...X", "X...X"],
    "B": ["XXXX.", "X...X", "XXXX.", "X...X", "XXXX."],
    "C": [".XXXX", "X....", "X....", "X....", ".XXXX"],
    "D": ["XXXX.", "X...X", "X...X", "X...X", "XXXX."],
    "E": ["XXXXX", "X....", "XXXX.", "X....", "XXXXX"],
    "F": ["XXXXX", "X....", "XXXX.", "X....", "X...."],
    "G": [".XXXX", "X....", "X..XX", "X...X", ".XXXX"],
    "H": ["X...X", "X...X", "XXXXX", "X...X", "X...X"],
    "I": ["XXXXX", "..X..", "..X..", "..X..", "XXXXX"],
    "J": ["....X", "....X", "....X", "X...X", ".XXX."],
    "K": ["X...X", "X..X.", "XXX..", "X..X.", "X...X"],
    "L": ["X....", "X....", "X....", "X....", "XXXXX"],
    "M": ["X...X", "XX.XX", "X.X.X", "X...X", "X...X"],
    "N": ["X...X", "XX..X", "X.X.X", "X..XX", "X...X"],
    "O": [".XXX.", "X...X", "X...X", "X...X", ".XXX."],
    "P": ["XXXX.", "X...X", "XXXX.", "X....", "X...."],
    "Q": [".XXX.", "X...X", "X...X", "X..X.", ".XX.X"],
    "R": ["XXXX.", "X...X", "XXXX.", "X..X.", "X...X"],
    "S": [".XXXX", "X....", ".XXX.", "....X", "XXXX."],
    "T": ["XXXXX", "..X..", "..X..", "..X..", "..X.."],
    "U": ["X...X", "X...X", "X...X", "X...X", ".XXX."],
    "V": ["X...X", "X...X", "X...X", ".X.X.", "..X.."],
    "W": ["X...X", "X...X", "X.X.X", "XX.XX", "X...X"],
    "X": ["X...X", ".X.X.", "..X..", ".X.X.", "X...X"],
    "Y": ["X...X", ".X.X.", "..X..", "..X..", "..X.."],
    "Z": ["XXXXX", "...X.", "..X..", ".X...", "XXXXX"],
    "0": [".XXX.", "X..XX", "X.X.X", "XX..X", ".XXX."],
    "1": ["..X..", ".XX..", "..X..", "..X..", "XXXXX"],
    "2": [".XXX.", "X...X", "..XX.", ".X...", "XXXXX"],
    "3": ["XXXX.", "....X", ".XXX.", "....X", "XXXX."],
    "4": ["X..X.", "X..X.", "XXXXX", "...X.", "...X."],
    "5": ["XXXXX", "X....", "XXXX.", "....X", "XXXX."],
    "6": [".XXX.", "X....", "XXXX.", "X...X", ".XXX."],
    "7": ["XXXXX", "....X", "...X.", "..X..", "..X.."],
    "8": [".XXX.", "X...X", ".XXX.", "X...X", ".XXX."],
    "9": [".XXX.", "X...X", ".XXXX", "....X", ".XXX."],
    "-": [".....", ".....", "XXXXX", ".....", "....."],
    ".": [".....", ".....", ".....", ".....", "..X.."],
    ":": [".....", "..X..", ".....", "..X..", "....."],
    " ": [".....", ".....", ".....", ".....", "....."],
}

GLYPH_W, GLYPH_H = 5, 5


# ---------------------------------------------------------------------------
# Matrix construction
# ---------------------------------------------------------------------------

def text_matrix(text: str, spacing: int = 1, word_gap: int = 3) -> Matrix:
    """Concatenate glyph matrices into one pixel matrix (rows x width)."""
    rows: Matrix = [[] for _ in range(GLYPH_H)]
    gap = [False] * word_gap
    first = True
    for ch in text.upper():
        glyph = GLYPHS.get(ch)
        if glyph is None:
            glyph = GLYPHS["?"] if "?" in GLYPHS else GLYPHS[" "]
        if not first:
            pad = gap if ch == " " else [False] * spacing
            for r in range(GLYPH_H):
                rows[r].extend(pad)
        for r in range(GLYPH_H):
            rows[r].extend(c == "X" for c in glyph[r])
        first = False
    return rows


def bolden(matrix: Matrix) -> Matrix:
    """Faux-bold: dilate every ink pixel one cell to the right (2px strokes).

    Each row grows by exactly one column so ink is never lost; the 1px
    inter-glyph spacing survives because void spacing stays void.
    """
    out: Matrix = []
    for row in matrix:
        new_row: List[Pixel] = []
        previous = False
        for pixel in row:
            new_row.append(pixel or previous)
            previous = pixel
        new_row.append(previous)  # shifted-in copy widens the row by one
        out.append(new_row)
    return out


def matrix_width(matrix: Matrix) -> int:
    return len(matrix[0]) if matrix else 0


# ---------------------------------------------------------------------------
# Gradient colouring
# ---------------------------------------------------------------------------

def _hex_to_rgb(value: str) -> Tuple[int, int, int]:
    value = value.lstrip("#")
    return int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16)


def _rgb_to_hex(rgb: Tuple[int, int, int]) -> str:
    return "#{:02X}{:02X}{:02X}".format(*rgb)


def _lerp(a: Tuple[int, int, int], b: Tuple[int, int, int], t: float) -> Tuple[int, int, int]:
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))  # type: ignore[return-value]


def gradient_column(x: int, width: int, stops: Sequence[str]) -> str:
    """Multi-stop horizontal gradient sampled at column x of width columns."""
    rgbs = [_hex_to_rgb(s) for s in stops]
    if width <= 1 or len(rgbs) == 1:
        return _rgb_to_hex(rgbs[0])
    t = x / (width - 1)
    segments = len(rgbs) - 1
    scaled = t * segments
    idx = min(int(scaled), segments - 1)
    return _rgb_to_hex(_lerp(rgbs[idx], rgbs[idx + 1], scaled - idx))


# ---------------------------------------------------------------------------
# Half-block rendering
# ---------------------------------------------------------------------------

def render_halfblocks(
    matrix: Matrix,
    *,
    stops: Sequence[str] = ("#5B0FFF", "#A32EFF", "#FF007F"),
    bg: str = "#0B0813",
) -> Text:
    """Pack pixel rows pairwise into half-block cells and paint the gradient.

    Cell encoding (top pixel, bottom pixel) -> (char, fg, bg):
      (1, 1) -> ▀ fg=top   bg=bottom      (solid cell, both halves inked)
      (1, 0) -> ▀ fg=top   bg=void
      (0, 1) -> ▄ fg=bottom bg=void
      (0, 0) -> space      bg=void
    """
    out = Text()
    for r in range(0, len(matrix), 2):
        top_row = matrix[r]
        bottom_row = matrix[r + 1] if r + 1 < len(matrix) else [False] * len(top_row)
        width = len(top_row)
        for c in range(width):
            top, bottom = top_row[c], bottom_row[c]
            color = gradient_column(c, width, stops)
            if top and bottom:
                out.append("▀", style=f"{color} on {bg}")
            elif top:
                out.append("▀", style=f"{color} on {bg}")
            elif bottom:
                out.append("▄", style=f"{color} on {bg}")
            else:
                out.append(" ", style=f"on {bg}")
        out.append("\n", style=f"on {bg}")
    # Trim the trailing newline so the Text prints flush.
    if out.plain.endswith("\n"):
        out.right_crop(1)
    return out


def render_banner(
    text: str,
    *,
    bold: bool = True,
    spacing: int = 1,
    word_gap: int = 3,
    stops: Sequence[str] = ("#5B0FFF", "#A32EFF", "#FF007F"),
    bg: str = "#0B0813",
) -> Text:
    """Render `text` as the solid duotone block wordmark (3 terminal rows tall).

    Bold dilation widens every stroke by 1px, so the inter-glyph gap grows to
    2px when bold — otherwise a 1px gap is swallowed and letters fuse.
    """
    if bold:
        spacing = max(spacing, 2)
        word_gap = max(word_gap, spacing + 1)
    matrix = text_matrix(text, spacing=spacing, word_gap=word_gap)
    if bold:
        matrix = bolden(matrix)
    return render_halfblocks(matrix, stops=stops, bg=bg)


def render_block_rule(width: int, *, color: str = "#2A0A66", bg: str = "#0B0813") -> Text:
    """A one-row heavy block divider: ▄ repeated across the full width."""
    rule = Text()
    for _ in range(max(0, width)):
        rule.append("▄", style=f"{color} on {bg}")
    return rule
