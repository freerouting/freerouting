#!/usr/bin/env python3
"""Inclusive/self CPU breakdown of a JFR recording (jdk.ExecutionSample).

    python scripts/perf/jfr_inclusive.py recording.jfr [--jfr PATH_TO_jfr] [--top 40]
                                         [--focus app.freerouting.foo.Bar.method]

Prints, for every method, the share of samples in which it appears on the stack
(inclusive) and on top of the stack (self). ``--focus`` restricts the analysis to
samples whose stack contains the given frame prefix.
"""

from __future__ import annotations

import argparse
import collections
import re
import shutil
import subprocess

FRAME_RE = re.compile(r"^\s+([\w.$<>]+)\(", re.M)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("recording")
    ap.add_argument("--jfr", default=shutil.which("jfr") or "jfr")
    ap.add_argument("--top", type=int, default=40)
    ap.add_argument("--focus")
    ap.add_argument("--thread", help="substring of the sampled thread name")
    a = ap.parse_args()
    out = subprocess.run([a.jfr, "print", "--events", "jdk.ExecutionSample", "--stack-depth",
                          "256", a.recording], capture_output=True, text=True,
                         encoding="utf-8", errors="replace").stdout
    events = out.split("jdk.ExecutionSample {")[1:]
    incl: collections.Counter[str] = collections.Counter()
    self_: collections.Counter[str] = collections.Counter()
    n = 0
    for e in events:
        if a.thread:
            m = re.search(r'sampledThread = "([^"]*)"', e)
            if not m or a.thread not in m.group(1):
                continue
        frames = FRAME_RE.findall(e)
        if not frames:
            continue
        if a.focus and not any(f.startswith(a.focus) for f in frames):
            continue
        n += 1
        self_[frames[0]] += 1
        for f in set(frames):
            incl[f] += 1
    print(f"{n} samples")
    print(f"{'incl%':>7} {'self%':>7}  method")
    for f, c in incl.most_common(a.top):
        print(f"{100 * c / n:7.2f} {100 * self_[f] / n:7.2f}  {f}")


if __name__ == "__main__":
    main()
