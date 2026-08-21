#!/usr/bin/env python3
"""Strip routing from KiCad .kicad_pcb files for PCBench ground-truth re-routing.

Removes top-level ``segment``, ``via``, and ``track`` blocks. Unfills zones by
dropping ``filled_polygon`` children while keeping zone outlines so pours can
regenerate. Validates balanced parentheses on input and output.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


def _balanced_parens(text: str, start: int) -> int:
    depth = 0
    i = start
    n = len(text)
    while i < n:
        ch = text[i]
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    raise ValueError(f"Unbalanced parentheses at offset {start}")


def _paren_delta(text: str) -> int:
    return text.count("(") - text.count(")")


def _remove_named_children(block: str, names: set[str]) -> str:
    """Remove nested s-expr children whose head symbol is in ``names``."""
    open_paren = block.find("(")
    if open_paren < 0:
        return block
    # Skip the outer wrapper: keep "(" + head, rewrite interior, keep closing ")".
    inner_start = open_paren + 1
    inner_end = len(block) - 1
    if inner_end <= inner_start:
        return block
    inner = block[inner_start:inner_end]
    out: list[str] = []
    i = 0
    n = len(inner)
    while i < n:
        if inner[i] == "(":
            end = _balanced_parens(inner, i)
            child = inner[i : end + 1]
            m = re.match(r"\(\s*([A-Za-z_][\w-]*)", child)
            name = m.group(1) if m else ""
            if name not in names:
                out.append(child)
            i = end + 1
        else:
            out.append(inner[i])
            i += 1
    return block[:inner_start] + "".join(out) + block[inner_end:]


def _unfill_zone(zone_text: str) -> str:
    return _remove_named_children(zone_text, {"filled_polygon"})


def _strip_kicad_pcb_children(block: str) -> str:
    """Drop copper children of a ``kicad_pcb`` wrapper and unfill zones."""
    drop = {"segment", "via", "track"}
    open_paren = block.find("(")
    if open_paren < 0:
        return block
    inner_start = open_paren + 1
    inner_end = len(block) - 1
    if inner_end <= inner_start:
        return block
    inner = block[inner_start:inner_end]
    out: list[str] = []
    i = 0
    n = len(inner)
    while i < n:
        if inner[i] == "(":
            end = _balanced_parens(inner, i)
            child = inner[i : end + 1]
            m = re.match(r"\(\s*([A-Za-z_][\w-]*)", child)
            name = m.group(1) if m else ""
            if name in drop:
                i = end + 1
                continue
            if name == "zone":
                child = _unfill_zone(child)
            out.append(child)
            i = end + 1
        else:
            out.append(inner[i])
            i += 1
    return block[:inner_start] + "".join(out) + block[inner_end:]


def strip_routing(text: str) -> str:
    if _paren_delta(text) != 0:
        raise ValueError("Input PCB has unbalanced parentheses")
    out: list[str] = []
    i = 0
    n = len(text)
    drop = {"segment", "via", "track"}
    while i < n:
        if text[i] == "(":
            end = _balanced_parens(text, i)
            block = text[i : end + 1]
            m = re.match(r"\(\s*([A-Za-z_][\w-]*)", block)
            name = m.group(1) if m else ""
            if name == "kicad_pcb":
                block = _strip_kicad_pcb_children(block)
                out.append(block)
            elif name not in drop:
                if name == "zone":
                    block = _unfill_zone(block)
                out.append(block)
            i = end + 1
        else:
            out.append(text[i])
            i += 1
    result = "".join(out)
    if _paren_delta(result) != 0:
        raise ValueError("Stripped PCB has unbalanced parentheses")
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    src = args.input.read_text(encoding="utf-8", errors="replace")
    dst = strip_routing(src)
    remaining_segments = len(re.findall(r"\(segment\s", dst))
    remaining_vias = len(re.findall(r"\(via\s", dst))
    if remaining_segments or remaining_vias:
        print(
            f"Strip left {remaining_segments} segment(s) and {remaining_vias} via(s) in {args.input}",
            file=sys.stderr,
        )
        return 1
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(dst, encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
