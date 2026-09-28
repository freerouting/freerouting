"""Compare candidate vs baseline Freerouting benchmark JSON (lexicographic autopilot gates)."""

from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


def _runs(store: dict[str, Any]) -> list[dict[str, Any]]:
    runs = store.get("runs") or []
    if isinstance(runs, dict):
        return list(runs.values())
    return list(runs)


def _latest(runs: list[dict[str, Any]], fixture_path: str) -> dict[str, Any] | None:
    leaf = fixture_path.split("/")[-1]
    matches = [
        r
        for r in runs
        if (r.get("fixture") or {}).get("relative_path") == fixture_path
        or (r.get("fixture") or {}).get("filename") == leaf
    ]
    if not matches:
        return None
    return sorted(matches, key=lambda r: r.get("run_at") or "", reverse=True)[0]


def _metric(run: dict[str, Any] | None, *keys: str, default: Any = 0) -> Any:
    if run is None:
        return default
    cur: Any = run
    for key in keys:
        if not isinstance(cur, dict) or key not in cur or cur[key] is None:
            return default
        cur = cur[key]
    return cur


def evaluate(candidate_path: Path, baseline_path: Path, noise_path: Path | None) -> dict[str, Any]:
    candidate = json.loads(candidate_path.read_text(encoding="utf-8"))
    baseline = json.loads(baseline_path.read_text(encoding="utf-8"))
    noise: dict[str, Any] = {}
    if noise_path and noise_path.exists():
        noise_obj = json.loads(noise_path.read_text(encoding="utf-8"))
        noise = noise_obj.get("fixtures") or {}

    cand_runs = _runs(candidate)
    base_runs = _runs(baseline)
    fixtures = sorted(
        {
            (r.get("fixture") or {}).get("relative_path")
            for r in cand_runs
            if (r.get("fixture") or {}).get("relative_path")
        }
    )

    reasons: list[str] = []
    deltas: dict[str, Any] = {}
    accept = True

    for fx in fixtures:
        c_run = _latest(cand_runs, fx)
        b_run = _latest(base_runs, fx)
        if not c_run:
            continue
        c_viol = int(_metric(c_run, "drc", "final_violations", default=_metric(c_run, "quality", "clearance_violations")))
        c_unr = int(_metric(c_run, "drc", "final_unrouted", default=_metric(c_run, "quality", "final_unrouted")))
        c_score = float(_metric(c_run, "quality", "quality_score"))
        b_viol = int(_metric(b_run, "drc", "final_violations", default=_metric(b_run, "quality", "clearance_violations"))) if b_run else 0
        b_unr = int(_metric(b_run, "drc", "final_unrouted", default=_metric(b_run, "quality", "final_unrouted", default=9999))) if b_run else 9999
        b_score = float(_metric(b_run, "quality", "quality_score")) if b_run else 0.0
        noise_band = 2.0
        if fx in noise and (noise[fx] or {}).get("unrouted_stddev", 0):
            noise_band = max(noise_band, 2.0 * float(noise[fx]["unrouted_stddev"]))
        deltas[fx] = {
            "violations_delta": c_viol - b_viol,
            "unrouted_delta": c_unr - b_unr,
            "score_delta": c_score - b_score,
        }
        if c_viol > b_viol:
            accept = False
            reasons.append(f"REJECT {fx} : violations increased ({b_viol} -> {c_viol})")
        if (c_unr - b_unr) > noise_band:
            accept = False
            reasons.append(f"REJECT {fx} : unrouted regression ({b_unr} -> {c_unr}, band={noise_band})")

    if accept and not reasons:
        reasons.append("ACCEPT: no hard gate violations detected")

    return {
        "accept": accept,
        "evaluated_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "reasons": reasons,
        "deltas": deltas,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--noise", type=Path)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--experiment-id", default="")
    args = parser.parse_args()
    verdict = evaluate(args.candidate, args.baseline, args.noise)
    verdict["experiment_id"] = args.experiment_id
    args.out.write_text(json.dumps(verdict, indent=2), encoding="utf-8")
    print(json.dumps(verdict, indent=2))
    return 0 if verdict["accept"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
