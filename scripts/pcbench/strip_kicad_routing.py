#!/usr/bin/env python3
"""Strip routing from KiCad .kicad_pcb files for PCBench ground-truth re-routing.

Removes top-level ``segment``, ``via``, and ``track`` blocks. Unfills zones by
dropping ``filled_polygon`` children while keeping zone outlines so pours can
regenerate. Validates balanced parentheses on input and output.

With ``--zones-from raw.kicad_pcb``, the top-level zones of that file are first
grafted onto the input. PCBench's ``processed.kicad_pcb`` has no zones at all
(issue #1122), so stripping it alone gives an unrouted board without its planes.
Each grafted zone takes its net code from the input, looked up by ``net_name``,
because the two files do not always number their nets the same way.
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


def _sexpr_end(text: str, start: int) -> int:
    """Index of the ``)`` closing the s-expression at ``start``, skipping quoted strings."""
    depth = 0
    i = start
    n = len(text)
    in_str = False
    while i < n:
        ch = text[i]
        if in_str:
            if ch == "\\":
                i += 1
            elif ch == '"':
                in_str = False
        elif ch == '"':
            in_str = True
        elif ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    raise ValueError(f"Unbalanced parentheses at offset {start}")


def _top_level_children(text: str) -> list[tuple[str, int, int]]:
    """``(head, start, end)`` of each direct child of the ``kicad_pcb`` block."""
    root = text.find("(")
    if root < 0:
        return []
    root_end = _sexpr_end(text, root)
    children: list[tuple[str, int, int]] = []
    i = root + 1
    in_str = False
    while i < root_end:
        ch = text[i]
        if in_str:
            if ch == "\\":
                i += 1
            elif ch == '"':
                in_str = False
        elif ch == '"':
            in_str = True
        elif ch == "(":
            end = _sexpr_end(text, i)
            m = re.match(r"\(\s*([A-Za-z_][\w-]*)", text[i : end + 1])
            children.append((m.group(1) if m else "", i, end))
            i = end
        i += 1
    return children


_ATOM = r'(?:"((?:[^"\\]|\\.)*)"|([^\s()"]+))'


def _atom(m: re.Match, first: int) -> str:
    return m.group(first) if m.group(first) is not None else m.group(first + 1)


def graft_zones(target: str, source: str) -> tuple[str, int, int]:
    """Copy the top-level zones of ``source`` into ``target``.

    Returns the new text, the number of zones grafted, and the number dropped
    because their net does not exist in ``target``. A ``target`` that already
    has zones is returned unchanged: there is nothing to restore.
    """
    target_children = _top_level_children(target)
    if any(head == "zone" for head, _s, _e in target_children):
        return target, 0, 0
    codes: dict[str, str] = {}
    for head, start, end in target_children:
        if head == "net":
            m = re.match(r"\(net\s+(\d+)\s+" + _ATOM, target[start : end + 1])
            if m:
                codes[_atom(m, 2)] = m.group(1)
    grafted: list[str] = []
    dropped = 0
    for head, start, end in _top_level_children(source):
        if head != "zone":
            continue
        zone = source[start : end + 1]
        m = re.search(r"\(net_name\s+" + _ATOM + r"\)", zone)
        name = _atom(m, 1) if m else ""
        if name and name not in codes:
            dropped += 1
            continue
        grafted.append(re.sub(r"\(net\s+\d+\)", f"(net {codes.get(name, '0')})", zone, count=1))
    if not grafted:
        return target, 0, dropped
    close = _sexpr_end(target, target.find("("))
    head_text = target[:close].rstrip()
    body = "".join(f"\n  {zone}" for zone in grafted)
    return head_text + body + "\n" + target[close:], len(grafted), dropped


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
    parser.add_argument(
        "--zones-from",
        type=Path,
        default=None,
        help="Graft the top-level zones of this PCB (PCBench raw.kicad_pcb) before stripping",
    )
    args = parser.parse_args()
    src = args.input.read_text(encoding="utf-8", errors="replace")
    if args.zones_from is not None:
        zones_src = args.zones_from.read_text(encoding="utf-8", errors="replace")
        src, grafted, dropped = graft_zones(src, zones_src)
        if grafted or dropped:
            print(
                f"Grafted {grafted} zone(s) from {args.zones_from}"
                + (f", dropped {dropped} whose net is not in {args.input}" if dropped else ""),
                file=sys.stderr,
            )
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
