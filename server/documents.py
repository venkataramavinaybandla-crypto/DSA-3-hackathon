"""
CERBERUS SYSTEM — Paper document index.

Maps the paper ids used by the citation graph (P101, P203, ...) to the files
that actually live in ``research_papers/``. The corpus is deliberately mixed:

  * 52 papers ship with a real PDF (``P101.pdf``) plus extracted plain text
    (``P101_fulltext.txt``) and a short record (``P101.txt``).
  * the remaining papers ship as a plain-text record only (``P106.txt``).

This module only indexes what is on disk — it never invents documents. The
FastAPI layer turns the resolved paths into URLs; the frontend decides whether
to embed a PDF or render text.
"""

from __future__ import annotations

from pathlib import Path
from typing import Dict, Optional

# Files in research_papers/ are named `<PAPER_ID>.pdf`, `<PAPER_ID>.txt` and
# `<PAPER_ID>_fulltext.txt`. We match on the leading id token so unrelated
# files in the folder are ignored.
PDF_SUFFIX = ".pdf"
TEXT_SUFFIX = ".txt"
FULLTEXT_MARKER = "_fulltext"


class DocumentIndex:
    """Read-only index of the local paper corpus, keyed by paper id."""

    def __init__(self, directory: str | Path) -> None:
        self.directory = Path(directory)
        self.pdf_files: Dict[str, Path] = {}
        self.text_files: Dict[str, Path] = {}
        self.fulltext_files: Dict[str, Path] = {}
        self.refresh()

    def refresh(self) -> None:
        """Re-scan the corpus directory (cheap; called on construction only)."""
        self.pdf_files = {}
        self.text_files = {}
        self.fulltext_files = {}
        if not self.directory.is_dir():
            return

        for entry in sorted(self.directory.iterdir()):
            if not entry.is_file():
                continue
            stem = entry.stem.upper()
            if entry.suffix.lower() == PDF_SUFFIX:
                self.pdf_files[stem] = entry
            elif entry.suffix.lower() == TEXT_SUFFIX:
                if stem.endswith(FULLTEXT_MARKER.upper()):
                    self.fulltext_files[stem[: -len(FULLTEXT_MARKER)]] = entry
                else:
                    self.text_files[stem] = entry

    def resolve(self, paper_id: str) -> dict:
        """Return the files available for a paper id.

        The result is a plain dict so it can be dropped straight into a JSON
        payload:
            kind    "pdf" | "text" | None  (what the viewer should focus on)
            pdf     Path | None
            text    Path | None            (short record: title/authors/abstract)
            fulltext Path | None           (extracted text, only when a PDF exists)
        """
        key = (paper_id or "").strip().upper()
        pdf = self.pdf_files.get(key)
        text = self.text_files.get(key)
        fulltext = self.fulltext_files.get(key)
        if pdf is not None:
            kind: Optional[str] = "pdf"
        elif text is not None:
            kind = "text"
        else:
            kind = None
        return {"kind": kind, "pdf": pdf, "text": text, "fulltext": fulltext}

    def has_document(self, paper_id: str) -> bool:
        return self.resolve(paper_id)["kind"] is not None
