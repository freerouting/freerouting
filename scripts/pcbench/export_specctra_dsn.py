"""Export a KiCad PCB to Specctra DSN using pcbnew.ExportSpecctraDSN.

Must be run with KiCad's bundled Python, e.g.
``C:\\Program Files\\KiCad\\10.0\\bin\\python.exe``.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--fallback", type=Path, default=None, help="Fallback PCB if primary fails to export")
    args = parser.parse_args()

    try:
        import wx  # type: ignore
        app = wx.App(False)
        wx.Log.EnableLogging(False)
    except Exception:
        pass

    try:
        import pcbnew  # type: ignore
    except ImportError:
        print("pcbnew is not available; run this script with KiCad's python.exe", file=sys.stderr)
        return 2

    args.output.parent.mkdir(parents=True, exist_ok=True)
    board = pcbnew.LoadBoard(str(args.input))
    ok = pcbnew.ExportSpecctraDSN(board, str(args.output))
    if (not ok or not args.output.exists()) and args.fallback and args.fallback.exists():
        board = pcbnew.LoadBoard(str(args.fallback))
        ok = pcbnew.ExportSpecctraDSN(board, str(args.output))

    if not ok and not args.output.exists():
        print(f"ExportSpecctraDSN failed for {args.input}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
