# Freerouting Nightly Benchmarks Report
Generated on: 2026-06-24 14:11:24

This report lists the latest benchmark run results for each Freerouting version and fixture combination.

## Summary Table (Best Results per Fixture)

| Fixture Group                                | Fixture                                                         | Best Version | Unrouted | Violations | Score  | CPU Time (s) | Peak Heap (MB) |
| :------------------------------------------- | :-------------------------------------------------------------- | :----------- | -------: | ---------: | -----: | -----------: | -------------: |
| [DAC2020_boards](../fixtures/DAC2020_boards) | [DAC2020_bm07.dsn](../fixtures/DAC2020_boards/DAC2020_bm07.dsn) | **1.9.0**    |        0 |          0 | 999.99 |        43.92 |            146 |


## Group: [DAC2020_boards](../fixtures/DAC2020_boards)

### Fixture: [DAC2020_bm07.dsn](../fixtures/DAC2020_boards/DAC2020_bm07.dsn)

Size: 14.9 kB · Layers: 2 · Nets: 52 · Components: 13
Dimensions: 22 x 60 mm (13.2 cm²)
CAD: KiCad's Pcbnew (v9.0.6)

| Version     | Mode | Fanout        | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes  | Unrouted | Violations | Score  | Peak Heap (MB) | Total Alloc (GB) | Warn/Err |
| :---------- | :--- | ------------: | --------------: | --------------: | -----------------: | -------------: | ------: | -------: | ---------: | -----: | -------------: | ---------------: | -------: |
| 1.9.0       | GUI  | 85/87 (97.7%) |            1.19 |            3.56 |              40.96 |          45.71 |  2+6+10 |        0 |          0 | 999.99 |            146 |             36.2 |    0 / 0 |
| 2.0.1       | CLI  |           N/A |             N/A |             N/A |                N/A |            N/A |   0+7+0 |        1 |          0 | 988.34 |           1127 |              0.0 |   1 / 32 |
| 2.1.0       | CLI  |           N/A |             N/A |             N/A |                N/A |            N/A | 0+811+3 |        2 |          0 | 976.70 |           1428 |              0.0 |   1 / 20 |
| 2.2.4       | CLI  |           N/A |             N/A |            3.62 |              45.38 |          49.00 |   0+8+7 |        0 |          0 | 999.96 |           1584 |              1.7 |    3 / 0 |
| s2026.06.23 | CLI  | 84/87 (96.6%) |            8.81 |           16.11 |                N/A |          24.92 |  0+18+2 |        3 |          0 | 965.08 |           1380 |             20.1 |    0 / 0 |
| s2026.06.24 | CLI  | 84/87 (96.6%) |            8.82 |           16.72 |               1.85 |          27.39 | 20+18+2 |        3 |          0 | 965.08 |            780 |             22.7 |    0 / 0 |


