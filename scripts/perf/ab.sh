#!/usr/bin/env bash
# A/B one board on two Freerouting JARs, started at the same time so both see the same machine
# load. Prints each run's wall time, phase timings and the SES hash (identical hash = identical
# routing result).
#
#   scripts/perf/ab.sh BASE.jar CAND.jar BOARD.dsn OUTDIR [extra freerouting args...]
set -euo pipefail
base=$1 cand=$2 board=$3 out=$4
shift 4
mkdir -p "$out"
run() {
  local tag=$1 jar=$2
  rm -rf "$out/$tag.ud" "$out/$tag.ses"
  local s e
  s=$(date +%s%N)
  FREEROUTING__USER_DATA_PATH="$out/$tag.ud" java -jar "$jar" -de "$board" -do "$out/$tag.ses" \
    --gui.enabled=false --api_server.enabled=false \
    --usage_and_diagnostic_data.disable_analytics=true "$@" >"$out/$tag.log" 2>&1 || true
  e=$(date +%s%N)
  echo "$tag wall=$(((e - s) / 1000000))ms ses=$(sha256sum "$out/$tag.ses" 2>/dev/null | cut -c1-16)" \
    >"$out/$tag.result"
}
run base "$base" "$@" &
run cand "$cand" "$@" &
wait
cat "$out/base.result" "$out/cand.result"
for t in base cand; do
  grep -E "stage completed|Auto-routing pass #" "$out/$t.log" | sed -E 's/^.{24}//' | cut -c1-150 |
    sed "s/^/  [$t] /"
done
