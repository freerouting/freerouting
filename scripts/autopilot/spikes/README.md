# Phase 0 validation spikes

## 0.1 CLI vs API equivalence

```powershell
./scripts/autopilot/spikes/Invoke-CliApiEquivalence.ps1
```

Routes `DAC2020_bm01.dsn` and `ecc83-pp.dsn` via CLI (`--router.result_json`) and REST
(`POST /v1/jobs/...` on a local API server with auth disabled). Compares
`connections.incomplete_count` and clearance violations, and records wall-clock overhead.

## 0.2 PCBench conversion PoC

```powershell
./scripts/autopilot/spikes/Invoke-PCBenchConversionPoC.ps1
```

Uses the local clone at `C:\Work\PCBench` and KiCad 10 at
`C:\Program Files\KiCad\10.0\bin\kicad-cli.exe`. Strips and converts three stratified boards;
records KiCad version and conversion success.

## Prerequisites

- `gradlew.bat executableJar` (spike 0.1)
- KiCad 10 `kicad-cli` and `C:\Work\PCBench\PCBs\` (spike 0.2)
