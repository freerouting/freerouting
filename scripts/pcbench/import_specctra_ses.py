"""Import a Specctra SES into a KiCad PCB using pcbnew.ImportSpecctraSES.

Must be run with KiCad's bundled Python.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pcb", type=Path, help="Base .kicad_pcb to import into")
    parser.add_argument("ses", type=Path, help="Specctra session file")
    parser.add_argument("output", type=Path, help="Written .kicad_pcb after SES import")
    args = parser.parse_args()

    try:
        import pcbnew  # type: ignore
    except ImportError:
        print("pcbnew is not available; run this script with KiCad's python.exe", file=sys.stderr)
        return 2

    args.output.parent.mkdir(parents=True, exist_ok=True)
    board = pcbnew.LoadBoard(str(args.pcb))
    tracks_before = len(list(board.GetTracks()))
    ok = False
    try:
        ok = bool(pcbnew.ImportSpecctraSES(board, str(args.ses)))
    except TypeError:
        ok = False
    if not ok:
        try:
            ok = bool(pcbnew.ImportSpecctraSES(str(args.ses)))
        except Exception:
            ok = False
    board.Save(str(args.output))
    tracks_after = len(list(board.GetTracks()))
    if tracks_after > tracks_before:
        return 0
    if not ok:
        print(
            f"ImportSpecctraSES returned false for {args.ses} "
            f"(tracks {tracks_before} -> {tracks_after})",
            file=sys.stderr,
        )
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
