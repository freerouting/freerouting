#!/usr/bin/env python3
"""Extract normalized metadata, ground truth, and provenance manifest for PCBench boards."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


def sha256_file(path: Path) -> str:
    if not path.exists():
        return ""
    h = hashlib.sha256()
    with path.open("rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()


def normalize_iso_timestamp(ts_str: str) -> str:
    """Normalize timestamp string with possible unicode colon variants into standard ISO-8601 UTC."""
    if not ts_str:
        return ""
    cleaned = ts_str.replace("\u2236", ":").replace("∶", ":").strip()
    for fmt in (
        "%Y-%m-%d %H:%M:%S.%f",
        "%Y-%m-%d %H:%M:%S",
        "%Y-%m-%dT%H:%M:%S.%f",
        "%Y-%m-%dT%H:%M:%S",
        "%Y-%m-%d",
    ):
        try:
            dt = datetime.strptime(cleaned, fmt)
            return dt.replace(tzinfo=timezone.utc).isoformat()
        except ValueError:
            pass
    return cleaned


def parse_kicad_drc_report(drc_json_path: Path | None) -> dict[str, Any] | None:
    """Parse KiCad DRC report JSON into total count, unconnected count, and count per violation type."""
    if not drc_json_path or not drc_json_path.exists():
        return None
    try:
        data = json.loads(drc_json_path.read_text(encoding="utf-8"))
        violations = data.get("violations", [])
        if not violations and "errors" in data:
            violations = data.get("errors", [])
        unconnected = data.get("unconnected_items", [])
        by_type: dict[str, int] = {}
        by_severity: dict[str, int] = {}
        for v in violations:
            v_type = v.get("type", "unknown")
            v_sev = v.get("severity", "unknown")
            by_type[v_type] = by_type.get(v_type, 0) + 1
            by_severity[v_sev] = by_severity.get(v_sev, 0) + 1
        return {
            "total_violations": len(violations),
            "unconnected_count": len(unconnected),
            "by_type": dict(sorted(by_type.items())),
            "by_severity": dict(sorted(by_severity.items())),
        }
    except Exception as e:
        print(f"Warning: Failed to parse DRC report {drc_json_path}: {e}", file=sys.stderr)
        return None


def extract_pcb_metrics_with_pcbnew(pcb_path: Path) -> dict[str, Any] | None:
    """Extract metrics directly using KiCad's C++ pcbnew library when available."""
    try:
        import wx  # type: ignore
        app = wx.App(False)
        wx.Log.EnableLogging(False)
        import pcbnew  # type: ignore

        board = pcbnew.LoadBoard(str(pcb_path))
        if not board:
            return None

        via_count = 0
        seg_count = 0
        track_len_mm = 0.0
        for item in board.GetTracks():
            type_name = str(type(item).__name__).lower()
            if "via" in type_name or hasattr(item, "GetViaType"):
                via_count += 1
            else:
                seg_count += 1
                if hasattr(item, "GetLength"):
                    track_len_mm += item.GetLength() / 1e6

        footprints = len(list(board.GetFootprints())) if hasattr(board, "GetFootprints") else (len(list(board.GetModules())) if hasattr(board, "GetModules") else 0)
        nets = board.GetNetInfo().GetNetCount() if hasattr(board, "GetNetInfo") else 0
        zones = len(list(board.Zones())) if hasattr(board, "Zones") else (len(list(board.GetZones())) if hasattr(board, "GetZones") else 0)
        layers = board.GetCopperLayerCount() if hasattr(board, "GetCopperLayerCount") else 2

        bbox = board.ComputeBoundingBox(False) if hasattr(board, "ComputeBoundingBox") else board.GetBoardEdgesBoundingBox()
        w_mm = round(bbox.GetWidth() / 1e6, 2)
        h_mm = round(bbox.GetHeight() / 1e6, 2)
        area_cm2 = round((w_mm * h_mm) / 100.0, 2)

        return {
            "copper_layers": layers,
            "nets_count": nets,
            "components_count": footprints,
            "zones_count": zones,
            "via_count": via_count,
            "segment_count": seg_count,
            "track_length_mm": round(track_len_mm, 2),
            "width_mm": w_mm,
            "height_mm": h_mm,
            "area_cm2": area_cm2,
        }
    except Exception:
        return None


def parse_kicad_pcb_metrics_fallback(pcb_text: str) -> dict[str, Any]:
    """Safe, single-pass line scanner without unbounded regex backtracking."""
    via_count = 0
    seg_count = 0
    track_len_mm = 0.0
    footprints = 0
    zones = 0
    nets: set[str] = set()
    edge_pts: list[tuple[float, float]] = []

    for line in pcb_text.splitlines():
        line = line.strip()
        if not line:
            continue
        if line.startswith("(via ") or " (via " in line:
            via_count += 1
        elif line.startswith("(segment ") or " (segment " in line:
            seg_count += 1
            m_start = re.search(r'\(start\s+([\d.-]+)\s+([\d.-]+)\)', line)
            m_end = re.search(r'\(end\s+([\d.-]+)\s+([\d.-]+)\)', line)
            if m_start and m_end:
                try:
                    x1, y1 = float(m_start.group(1)), float(m_start.group(2))
                    x2, y2 = float(m_end.group(1)), float(m_end.group(2))
                    track_len_mm += math.hypot(x2 - x1, y2 - y1)
                except ValueError:
                    pass
        elif line.startswith("(footprint ") or line.startswith("(module ") or " (footprint " in line or " (module " in line:
            footprints += 1
        elif line.startswith("(zone ") or " (zone " in line:
            zones += 1
        elif "(net " in line:
            m_net = re.search(r'\(net\s+(\d+)\s+"([^"]*)"\)', line)
            if m_net and m_net.group(2).strip():
                nets.add(m_net.group(2))
        elif "Edge.Cuts" in line:
            m_xy = re.findall(r'\((?:start|end|center|xy)\s+([\d.-]+)\s+([\d.-]+)\)', line)
            for x_s, y_s in m_xy:
                try:
                    edge_pts.append((float(x_s), float(y_s)))
                except ValueError:
                    pass

    width_mm = 0.0
    height_mm = 0.0
    area_cm2 = 0.0
    if edge_pts:
        xs = [p[0] for p in edge_pts]
        ys = [p[1] for p in edge_pts]
        width_mm = round(max(xs) - min(xs), 2)
        height_mm = round(max(ys) - min(ys), 2)
        area_cm2 = round((width_mm * height_mm) / 100.0, 2)

    return {
        "copper_layers": 2,
        "nets_count": len(nets),
        "components_count": footprints,
        "zones_count": zones,
        "via_count": via_count,
        "segment_count": seg_count,
        "track_length_mm": round(track_len_mm, 2),
        "width_mm": width_mm,
        "height_mm": height_mm,
        "area_cm2": area_cm2,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--board-dir", required=True, type=Path, help="Path to PCBench board source folder (PCBs/<board>)")
    parser.add_argument("--output-dir", required=True, type=Path, help="Target fixture output directory (scripts/benchmark/fixtures/PCBench/<board>)")
    parser.add_argument("--kicad-drc-json", type=Path, default=None, help="Path to KiCad DRC report JSON")
    parser.add_argument("--kicad-drc-violations", type=int, default=None, help="KiCad DRC total violations count")
    parser.add_argument("--kicad-version", default="10.0.2", help="KiCad conversion version")
    args = parser.parse_args()

    board_dir = args.board_dir
    output_dir = args.output_dir
    board_name = board_dir.name

    raw_pcb = board_dir / "raw.kicad_pcb"
    processed_pcb = board_dir / "processed.kicad_pcb"
    meta_path = board_dir / "metadata.json"
    final_json = board_dir / "final.json"

    if not raw_pcb.exists() and not processed_pcb.exists():
        print(f"Error: Neither {raw_pcb} nor {processed_pcb} found", file=sys.stderr)
        return 1

    inspect_pcb = raw_pcb if raw_pcb.exists() else processed_pcb

    # 1. Try extracting with pcbnew first, fallback to line scanner
    metrics = extract_pcb_metrics_with_pcbnew(inspect_pcb)
    if not metrics:
        pcb_text = inspect_pcb.read_text(encoding="utf-8", errors="replace")
        metrics = parse_kicad_pcb_metrics_fallback(pcb_text)

    pcbench_meta: dict[str, Any] = {}
    if meta_path.exists():
        try:
            pcbench_meta = json.loads(meta_path.read_text(encoding="utf-8"))
        except Exception:
            pass

    layers = pcbench_meta.get("layers") or metrics.get("copper_layers") or 2
    raw_retrieved_at = pcbench_meta.get("retrieved at", "")
    iso_retrieved_at = normalize_iso_timestamp(raw_retrieved_at)

    drc_report = parse_kicad_drc_report(args.kicad_drc_json)
    total_drc_violations = args.kicad_drc_violations
    if drc_report and total_drc_violations is None:
        total_drc_violations = drc_report["total_violations"]

    # 1. metadata.normalized.json
    normalized_meta: dict[str, Any] = {
        "board_id": board_name,
        "display_name": board_name,
        "pcbench_directory": f"PCBs/{board_name}",
        "source": pcbench_meta.get("source", ""),
        "author": pcbench_meta.get("author", ""),
        "license": pcbench_meta.get("licenses", {}).get("name", "Unknown") if isinstance(pcbench_meta.get("licenses"), dict) else str(pcbench_meta.get("licenses", "")),
        "license_spdx": pcbench_meta.get("licenses", {}).get("spdx_id", "") if isinstance(pcbench_meta.get("licenses"), dict) else "",
        "retrieved_at": iso_retrieved_at,
        "cad": {
            "source_version": pcbench_meta.get("CAD version", "KiCad"),
            "conversion_version": args.kicad_version,
        },
        "board": {
            "layers": layers,
            "nets": metrics["nets_count"],
            "components": metrics["components_count"],
            "dimensions_mm": {
                "width": metrics["width_mm"],
                "height": metrics["height_mm"],
            },
            "area_cm2": metrics["area_cm2"],
            "zones": metrics["zones_count"],
        },
        "reference": {
            "segments": metrics["segment_count"],
            "vias": metrics["via_count"],
            "track_length_mm": metrics["track_length_mm"],
            "kicad_drc_violations": total_drc_violations,
        },
        "tags": [f"{layers}-layer"],
    }

    if drc_report:
        normalized_meta["reference"]["kicad_drc_breakdown"] = drc_report

    # 2. ground_truth.json
    ground_truth: dict[str, Any] = {
        "board": board_name,
        "source_pcb": str(inspect_pcb),
        "layers": layers,
        "via_count": metrics["via_count"],
        "segment_count": metrics["segment_count"],
        "approximate_track_length_mm": metrics["track_length_mm"],
        "kicad_drc_violations": total_drc_violations,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "note": "KiCad DSN export omits copper-to-edge clearance (Issue 558); KiCad DRC on raw.kicad_pcb is the external reference.",
    }

    if drc_report:
        ground_truth["kicad_drc_breakdown"] = drc_report

    # 3. board-manifest.json
    files_info = {
        "raw_kicad_pcb": {"bytes": raw_pcb.stat().st_size if raw_pcb.exists() else 0, "sha256": sha256_file(raw_pcb)},
        "processed_kicad_pcb": {"bytes": processed_pcb.stat().st_size if processed_pcb.exists() else 0, "sha256": sha256_file(processed_pcb)},
        "metadata_json": {"bytes": meta_path.stat().st_size if meta_path.exists() else 0, "sha256": sha256_file(meta_path)},
    }
    if final_json.exists():
        files_info["final_json"] = {"bytes": final_json.stat().st_size, "sha256": sha256_file(final_json)}

    board_manifest = {
        "schema_version": 1,
        "board_id": board_name,
        "source_files": files_info,
        "generated_files": {
            "raw_kicad_pcb": "raw.kicad_pcb",
            "processed_kicad_pcb": "processed.kicad_pcb",
            "unrouted_kicad_pcb": "unrouted.kicad_pcb",
            "unrouted_dsn": "unrouted.dsn",
            "reference_routed_dsn": "reference-routed.dsn",
            "ground_truth_json": "ground_truth.json",
            "metadata_normalized_json": "metadata.normalized.json",
        },
        "conversion_tools": {
            "kicad_version": args.kicad_version,
            "generator": "scripts/pcbench/generate_board_metadata.py",
            "generated_at": datetime.now(timezone.utc).isoformat(),
        },
    }

    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / "metadata.normalized.json").write_text(json.dumps(normalized_meta, indent=2), encoding="utf-8")
    (output_dir / "ground_truth.json").write_text(json.dumps(ground_truth, indent=2), encoding="utf-8")
    (output_dir / "board-manifest.json").write_text(json.dumps(board_manifest, indent=2), encoding="utf-8")

    print(f"Generated normalized metadata, ground truth, and manifest for {board_name} in {output_dir}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
