#!/usr/bin/env python3
"""Headless wall-clock benchmark for the Freerouting CLI.

Runs one or more Freerouting builds over a fixed board set exactly the way a
command-line caller (for example the KiCad MCP server) does, and records for
every board:

* process wall time (JVM start to exit) and per-phase durations,
* routing quality (incomplete connections, clearance violations, score),
* the SHA-256 of the produced ``.ses`` file, so determinism and
  "byte-identical to the reference build" can be checked.

Usage::

    python scripts/perf/bench.py run --jar build/libs/freerouting-current-executable.jar \
        --label wip --out logs/perf/wip.json [--workers 4] [--repeat 1] [-- extra cli args]
    python scripts/perf/bench.py compare logs/perf/base.json logs/perf/wip.json

Keep ``--workers`` identical between the runs you compare.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import statistics
import subprocess
import sys
import tempfile
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
DEFAULT_BOARD_LIST = Path(__file__).with_name("boards.txt")


def load_boards(path: Path) -> list[Path]:
    boards = []
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.split("#", 1)[0].strip()
        if line:
            p = (REPO / line).resolve()
            if not p.exists():
                sys.exit(f"board not found: {p}")
            boards.append(p)
    return boards


def board_id(p: Path) -> str:
    rel = p.relative_to(REPO).as_posix()
    if p.name == "unrouted.dsn":
        return p.parent.name
    return rel.rsplit("/", 1)[-1]


def run_one(java: str, jvm_args: list[str], jar: Path, board: Path, extra: list[str],
            workdir: Path, timeout: int) -> dict:
    bid = board_id(board)
    ses = workdir / f"{bid}.ses"
    res = workdir / f"{bid}.json"
    log = workdir / f"{bid}.log"
    for f in (ses, res):
        f.unlink(missing_ok=True)
    cmd = [java, *jvm_args, "-jar", str(jar), "-de", str(board), "-do", str(ses),
           "--gui.enabled=false", "--api_server.enabled=false",
           f"--router.result_json={res}", *extra]
    # Freerouting rewrites <user-data>/freerouting.json on every run; give every run its
    # own directory so parallel runs cannot race on it or leak settings into each other.
    env = dict(os.environ, FREEROUTING__USER_DATA_PATH=str(workdir / f"{bid}.userdata"))
    t0 = time.perf_counter()
    try:
        with open(log, "wb") as lf:
            proc = subprocess.run(cmd, stdout=lf, stderr=subprocess.STDOUT,
                                  stdin=subprocess.DEVNULL, timeout=timeout, env=env)
        code = proc.returncode
    except subprocess.TimeoutExpired:
        code = "timeout"
    wall = time.perf_counter() - t0
    row: dict = {"board": bid, "wall_s": round(wall, 3), "exit": code}
    if ses.exists():
        row["ses_sha256"] = hashlib.sha256(ses.read_bytes()).hexdigest()
    if res.exists():
        d = json.loads(res.read_text(encoding="utf-8"))
        bs = d.get("board_statistics", {})
        row["incomplete"] = bs.get("connections", {}).get("incomplete_count")
        row["violations"] = bs.get("clearance_violations", {}).get("total_count")
        row["score"] = d.get("normalized_score")
        row["opt_score"] = d.get("optimizer_score")
        row["trace_mm"] = bs.get("traces", {}).get("total_length_mm")
        row["vias"] = bs.get("vias", {}).get("total_count")
        for ph, v in (d.get("phases") or {}).items():
            row[f"{ph}_s"] = v.get("duration_seconds")
        row["cpu_s"] = (d.get("resource_usage") or {}).get("cpu_time")
    return row


def cmd_run(a: argparse.Namespace) -> None:
    jar = Path(a.jar).resolve()
    boards = load_boards(Path(a.boards))
    if a.only:
        boards = [b for b in boards if any(o in board_id(b) for o in a.only.split(","))]
    workdir = Path(tempfile.mkdtemp(prefix=f"frbench-{a.label}-"))
    jvm_args = a.jvm.split() if a.jvm else []
    extra = a.extra[1:] if a.extra and a.extra[0] == "--" else (a.extra or [])
    rows = []
    t0 = time.perf_counter()
    for rep in range(a.repeat):
        jobs = [(b, workdir / f"r{rep}") for b in boards]
        for _, wd in jobs:
            wd.mkdir(parents=True, exist_ok=True)
        with ThreadPoolExecutor(max_workers=a.workers) as ex:
            futs = [ex.submit(run_one, a.java, jvm_args, jar, b, extra, wd, a.timeout)
                    for b, wd in jobs]
            for f in futs:
                r = f.result()
                r["repeat"] = rep
                rows.append(r)
                print(f"  [{a.label}] {r['board']:<48} {r['wall_s']:>8.2f}s  "
                      f"inc={r.get('incomplete')} drc={r.get('violations')} "
                      f"score={r.get('score')}", flush=True)
    total = time.perf_counter() - t0
    out = {"label": a.label, "jar": str(jar), "workers": a.workers, "jvm": jvm_args,
           "extra": extra, "elapsed_s": round(total, 2), "rows": rows, "workdir": str(workdir)}
    Path(a.out).parent.mkdir(parents=True, exist_ok=True)
    Path(a.out).write_text(json.dumps(out, indent=1), encoding="utf-8")
    print(f"total elapsed {total:.1f}s -> {a.out}")


def _by_board(rows: list[dict]) -> dict[str, list[dict]]:
    m: dict[str, list[dict]] = {}
    for r in rows:
        m.setdefault(r["board"], []).append(r)
    return m


def cmd_compare(a: argparse.Namespace) -> None:
    base = json.loads(Path(a.base).read_text())
    cand = json.loads(Path(a.cand).read_text())
    if base["workers"] != cand["workers"]:
        print(f"WARNING: worker counts differ ({base['workers']} vs {cand['workers']})")
    bb, cb = _by_board(base["rows"]), _by_board(cand["rows"])
    hdr = (f"{'board':<44}{'base s':>9}{'cand s':>9}{'speedup':>9}  "
           f"{'inc b/c':>9}  {'drc b/c':>9}  {'score b/c':>15}  ses")
    print(hdr)
    print("-" * len(hdr))
    tb = tc = 0.0
    speedups = []
    regressions = []
    for bid in bb:
        if bid not in cb:
            continue
        wb = statistics.median(r["wall_s"] for r in bb[bid])
        wc = statistics.median(r["wall_s"] for r in cb[bid])
        rb, rc = bb[bid][0], cb[bid][0]
        tb += wb
        tc += wc
        sp = wb / wc if wc else float("nan")
        speedups.append(sp)
        same = "same" if rb.get("ses_sha256") == rc.get("ses_sha256") else "DIFF"
        flag = ""
        if (rc.get("incomplete") or 0) > (rb.get("incomplete") or 0) or \
           (rc.get("violations") or 0) > (rb.get("violations") or 0):
            flag = "  <-- QUALITY REGRESSION"
            regressions.append(bid)
        print(f"{bid[:43]:<44}{wb:>9.2f}{wc:>9.2f}{sp:>8.2f}x  "
              f"{str(rb.get('incomplete'))+'/'+str(rc.get('incomplete')):>9}  "
              f"{str(rb.get('violations'))+'/'+str(rc.get('violations')):>9}  "
              f"{str(rb.get('score'))+'/'+str(rc.get('score')):>15}  {same}{flag}")
    geo = statistics.geometric_mean([s for s in speedups if s > 0]) if speedups else 0
    print("-" * len(hdr))
    print(f"sum of board wall times: base {tb:.1f}s, cand {tc:.1f}s -> {tb / tc:.2f}x; "
          f"geometric-mean speedup {geo:.2f}x; quality regressions: {len(regressions)}")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    r = sub.add_parser("run")
    r.add_argument("--jar", required=True)
    r.add_argument("--label", required=True)
    r.add_argument("--out", required=True)
    r.add_argument("--boards", default=str(DEFAULT_BOARD_LIST))
    r.add_argument("--only", help="comma-separated substrings of board ids")
    r.add_argument("--workers", type=int, default=4)
    r.add_argument("--repeat", type=int, default=1)
    r.add_argument("--timeout", type=int, default=1800)
    r.add_argument("--java", default=os.environ.get("FR_JAVA", "java"))
    r.add_argument("--jvm", default="", help="extra JVM options, space separated")
    r.add_argument("extra", nargs=argparse.REMAINDER)
    r.set_defaults(func=cmd_run)
    c = sub.add_parser("compare")
    c.add_argument("base")
    c.add_argument("cand")
    c.set_defaults(func=cmd_compare)
    a = ap.parse_args()
    a.func(a)


if __name__ == "__main__":
    main()
