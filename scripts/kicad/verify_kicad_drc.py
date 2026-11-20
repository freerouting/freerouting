#!/usr/bin/env python3
"""Multi-version KiCad DRC and Freerouting DSN/SES round-trip verification script.

Discovers installed KiCad versions (e.g., 9.0, 10.0, 10.99) and runs:
  - `drc`: Run `kicad-cli pcb drc --format json --severity-all` on a `.kicad_pcb`
    (with an optional `.kicad_dru` placed alongside it in a temp copy).
  - `roundtrip`: Export `.dsn` via `pcbnew`, run Freerouting headless to produce
    `.ses`, import `.ses` back into KiCad, refill zones, and run `kicad-cli pcb drc`.
"""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
import tempfile
from collections import Counter
from pathlib import Path


DEFAULT_KICAD_ROOTS = [
    Path(r"C:\Program Files\KiCad"),
]


def discover_kicad_installations(
    requested_versions: list[str] | None = None,
) -> list[dict[str, str | None]]:
    """Find installed KiCad versions with `kicad-cli` and optional bundled `python`."""
    found: list[dict[str, str | None]] = []
    for root in DEFAULT_KICAD_ROOTS:
        if not root.is_dir():
            continue
        for entry in sorted(root.iterdir()):
            if not entry.is_dir():
                continue
            ver = entry.name
            if requested_versions and ver not in requested_versions:
                continue
            cli = entry / "bin" / "kicad-cli.exe"
            py = entry / "bin" / "python.exe"
            if cli.is_file():
                found.append(
                    {
                        "version": ver,
                        "kicad_cli": str(cli),
                        "python": str(py) if py.is_file() else None,
                    }
                )
    return found


def run_kicad_drc(kicad_cli: str, pcb_path: Path, report_path: Path) -> dict:
    """Run `kicad-cli pcb drc` and return the parsed JSON report."""
    cmd = [
        kicad_cli,
        "pcb",
        "drc",
        "--format",
        "json",
        "--severity-all",
        "--output",
        str(report_path),
        str(pcb_path),
    ]
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if not report_path.is_file():
        raise RuntimeError(
            f"kicad-cli failed (rc={proc.returncode}): {proc.stderr or proc.stdout}"
        )
    return json.loads(report_path.read_text(encoding="utf-8"))


def summarize_drc(report: dict) -> dict:
    """Extract violation counts by type and unconnected item count."""
    violations = report.get("violations", [])
    unconnected = report.get("unconnected_items", [])
    by_type = Counter(v.get("type", "unknown") for v in violations)
    return {
        "total_violations": len(violations),
        "unconnected_items": len(unconnected),
        "by_type": dict(sorted(by_type.items())),
    }


def export_dsn_via_pcbnew(kicad_python: str, pcb_path: Path, dsn_path: Path) -> None:
    """Export a Specctra DSN from a `.kicad_pcb` using KiCad's bundled Python."""
    script = (
        "import pcbnew, sys; "
        "b = pcbnew.LoadBoard(sys.argv[1]); "
        "ok = pcbnew.ExportSpecctraDSN(b, sys.argv[2]); "
        "sys.exit(0 if ok else 1)"
    )
    proc = subprocess.run(
        [kicad_python, "-c", script, str(pcb_path), str(dsn_path)],
        capture_output=True,
        text=True,
    )
    if proc.returncode != 0 or not dsn_path.is_file():
        raise RuntimeError(
            f"ExportSpecctraDSN failed (rc={proc.returncode}): {proc.stderr or proc.stdout}"
        )


def import_ses_and_refill_via_pcbnew(
    kicad_python: str, pcb_path: Path, ses_path: Path, out_pcb_path: Path
) -> None:
    """Import a Specctra SES into a `.kicad_pcb`, refill zones, and save."""
    script = (
        "import pcbnew, sys; "
        "b = pcbnew.LoadBoard(sys.argv[1]); "
        "ok = pcbnew.ImportSpecctraSES(b, sys.argv[2]); "
        "assert ok, 'ImportSpecctraSES returned False'; "
        "zones = b.Zones(); "
        "pcbnew.ZONE_FILLER(b).Fill(zones); "
        "b.Save(sys.argv[3])"
    )
    proc = subprocess.run(
        [kicad_python, "-c", script, str(pcb_path), str(ses_path), str(out_pcb_path)],
        capture_output=True,
        text=True,
    )
    if proc.returncode != 0 or not out_pcb_path.is_file():
        raise RuntimeError(
            f"ImportSpecctraSES/refill failed (rc={proc.returncode}): {proc.stderr or proc.stdout}"
        )


def run_freerouting(
    jar_path: Path,
    dsn_path: Path,
    ses_path: Path,
    dru_path: Path | None = None,
    extra_args: list[str] | None = None,
) -> subprocess.CompletedProcess:
    """Run Freerouting headless on a DSN (and optional `.kicad_dru`)."""
    cmd = ["java", "-jar", str(jar_path), "-de", str(dsn_path)]
    if dru_path is not None:
        cmd.append(str(dru_path))
    cmd.extend(["-do", str(ses_path), "-mt", "1"])
    if extra_args:
        cmd.extend(extra_args)
    return subprocess.run(cmd, capture_output=True, text=True)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Run multi-version KiCad DRC and Freerouting round-trip checks."
    )
    parser.add_argument(
        "mode", choices=["drc", "roundtrip"], help="Verification mode to run."
    )
    parser.add_argument("--pcb", required=True, type=Path, help="Path to .kicad_pcb")
    parser.add_argument(
        "--dru", type=Path, default=None, help="Optional .kicad_dru file to apply"
    )
    parser.add_argument(
        "--dsn", type=Path, default=None, help="Optional pre-exported .dsn file"
    )
    parser.add_argument(
        "--jar",
        type=Path,
        default=Path("build/libs/freerouting-current-executable.jar"),
        help="Path to Freerouting executable JAR (for roundtrip mode)",
    )
    parser.add_argument(
        "--kicad-versions",
        default=None,
        help="Comma-separated KiCad versions to test (default: all installed)",
    )
    parser.add_argument(
        "--freerouting-args",
        nargs="*",
        default=[],
        help="Extra CLI arguments passed to Freerouting",
    )
    parser.add_argument(
        "--json-out", type=Path, default=None, help="Optional path to write JSON summary"
    )
    args = parser.parse_args()

    versions = (
        [v.strip() for v in args.kicad_versions.split(",") if v.strip()]
        if args.kicad_versions
        else None
    )
    installs = discover_kicad_installations(versions)
    if not installs:
        print("ERROR: No matching KiCad installations found.", file=sys.stderr)
        return 2

    results: list[dict] = []
    with tempfile.TemporaryDirectory(prefix="kicad_verify_") as tmp:
        tmp_dir = Path(tmp)
        for inst in installs:
            ver = inst["version"]
            ver_dir = tmp_dir / f"kicad_{ver}"
            ver_dir.mkdir(parents=True, exist_ok=True)
            work_pcb = ver_dir / args.pcb.name
            shutil.copy2(args.pcb, work_pcb)
            work_dru = None
            if args.dru is not None:
                work_dru = ver_dir / f"{work_pcb.stem}.kicad_dru"
                shutil.copy2(args.dru, work_dru)

            entry_result: dict = {"kicad_version": ver, "mode": args.mode}
            try:
                if args.mode == "drc":
                    report = run_kicad_drc(
                        inst["kicad_cli"], work_pcb, ver_dir / "drc.json"
                    )
                    entry_result["status"] = "ok"
                    entry_result["drc"] = summarize_drc(report)
                else:
                    if not inst["python"]:
                        entry_result["status"] = "skipped"
                        entry_result["reason"] = "No bundled python.exe (pcbnew) found"
                    else:
                        dsn_path = args.dsn or (ver_dir / f"{work_pcb.stem}.dsn")
                        if args.dsn is None:
                            export_dsn_via_pcbnew(inst["python"], work_pcb, dsn_path)
                        ses_path = ver_dir / f"{work_pcb.stem}.ses"
                        fr_proc = run_freerouting(
                            args.jar,
                            dsn_path,
                            ses_path,
                            dru_path=work_dru,
                            extra_args=args.freerouting_args,
                        )
                        entry_result["freerouting_rc"] = fr_proc.returncode
                        if not ses_path.is_file():
                            raise RuntimeError(
                                f"Freerouting did not produce SES (rc={fr_proc.returncode})"
                            )
                        routed_pcb = ver_dir / f"{work_pcb.stem}_routed.kicad_pcb"
                        import_ses_and_refill_via_pcbnew(
                            inst["python"], work_pcb, ses_path, routed_pcb
                        )
                        report = run_kicad_drc(
                            inst["kicad_cli"], routed_pcb, ver_dir / "drc.json"
                        )
                        entry_result["status"] = "ok"
                        entry_result["drc"] = summarize_drc(report)
            except Exception as exc:  # noqa: BLE001
                entry_result["status"] = "error"
                entry_result["error"] = str(exc)

            results.append(entry_result)

    if args.json_out:
        args.json_out.parent.mkdir(parents=True, exist_ok=True)
        args.json_out.write_text(json.dumps(results, indent=2), encoding="utf-8")

    print(json.dumps(results, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
