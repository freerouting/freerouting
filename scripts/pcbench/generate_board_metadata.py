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


def parse_kicad_pcb_metrics(pcb_text: str) -> dict[str, Any]:
    # 1. Nets
    nets = re.findall(r'\(net\s+(\d+)\s+"([^"]*)"\)', pcb_text)
    named_nets = [name for num, name in nets if name.strip()]

    # 2. Footprints / modules
    footprints = len(re.findall(r'\((?:footprint|module)\s+', pcb_text))

    # 3. Layers
    layers_match = re.search(r'\(layers[\s\S]*?\n\s*\)', pcb_text)
    copper_layers = 0
    if layers_match:
        copper_layers = len(re.findall(r'\(\d+\s+"?[FIB]\.?[\w.]+"?\s+signal', layers_match.group(0), re.IGNORECASE))
        if copper_layers == 0:
            copper_layers = len(re.findall(r'\(\d+\s+"?[FIB]\.?[\w.]+"?\s+(?:signal|power|user)', layers_match.group(0), re.IGNORECASE))
    if copper_layers == 0:
        if "F.Cu" in pcb_text and "B.Cu" in pcb_text:
            copper_layers = 2

    # 4. Zones
    zones = len(re.findall(r'\(zone\s+', pcb_text))

    # 5. Vias & Segments
    via_count = len(re.findall(r'\(via\s+', pcb_text))
    segments = re.findall(r'\(segment\b[\s\S]*?\(start\s+([\d.-]+)\s+([\d.-]+)\)[\s\S]*?\(end\s+([\d.-]+)\s+([\d.-]+)\)', pcb_text)
    seg_count = len(segments)
    track_len_mm = 0.0
    for x1_s, y1_s, x2_s, y2_s in segments:
        try:
            x1, y1, x2, y2 = float(x1_s), float(y1_s), float(x2_s), float(y2_s)
            track_len_mm += math.hypot(x2 - x1, y2 - y1)
        except ValueError:
            pass

    # 6. Dimensions from Edge.Cuts
    edge_pts: list[tuple[float, float]] = []
    for m in re.finditer(r'\(gr_line[\s\S]*?\(start\s+([\d.-]+)\s+([\d.-]+)\)[\s\S]*?\(end\s+([\d.-]+)\s+([\d.-]+)\)[\s\S]*?\(layer\s+"?Edge\.Cuts"?\)', pcb_text, re.IGNORECASE):
        edge_pts.append((float(m.group(1)), float(m.group(2))))
        edge_pts.append((float(m.group(3)), float(m.group(4))))
    for m in re.finditer(r'\(gr_rect[\s\S]*?\(start\s+([\d.-]+)\s+([\d.-]+)\)[\s\S]*?\(end\s+([\d.-]+)\s+([\d.-]+)\)[\s\S]*?\(layer\s+"?Edge\.Cuts"?\)', pcb_text, re.IGNORECASE):
        edge_pts.append((float(m.group(1)), float(m.group(2))))
        edge_pts.append((float(m.group(3)), float(m.group(4))))
    for m in re.finditer(r'\(gr_circle[\s\S]*?\(center\s+([\d.-]+)\s+([\d.-]+)\)[\s\S]*?\(end\s+([\d.-]+)\s+([\d.-]+)\)[\s\S]*?\(layer\s+"?Edge\.Cuts"?\)', pcb_text, re.IGNORECASE):
        cx, cy, ex, ey = float(m.group(1)), float(m.group(2)), float(m.group(3)), float(m.group(4))
        r = math.hypot(ex - cx, ey - cy)
        edge_pts.extend([(cx - r, cy - r), (cx + r, cy + r)])

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
        "copper_layers": copper_layers,
        "nets_count": len(named_nets) if named_nets else len(nets),
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

    if not raw_pcb.exists():
        print(f"Error: {raw_pcb} not found", file=sys.stderr)
        return 1

    pcb_text = raw_pcb.read_text(encoding="utf-8", errors="replace")
    metrics = parse_kicad_pcb_metrics(pcb_text)

    pcbench_meta: dict[str, Any] = {}
    if meta_path.exists():
        try:
            pcbench_meta = json.loads(meta_path.read_text(encoding="utf-8"))
        except Exception:
            pass

    layers = pcbench_meta.get("layers") or metrics["copper_layers"] or 2
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
        "source_pcb": str(raw_pcb),
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
