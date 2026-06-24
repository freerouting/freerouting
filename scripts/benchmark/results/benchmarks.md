# Freerouting Nightly Benchmarks Report
Generated on: 2026-06-24 13:02:23

This report lists the latest benchmark run results for each Freerouting version and fixture combination.

## Summary Table (Best Results per Fixture)

| Fixture Group                                | Fixture                                                         | Best Version | Unrouted | Violations | Score  | CPU Time (s) | Peak Heap (MB) |
| :------------------------------------------- | :-------------------------------------------------------------- | :----------- | -------: | ---------: | -----: | -----------: | -------------: |
| [DAC2020_boards](../fixtures/DAC2020_boards) | [DAC2020_bm07.dsn](../fixtures/DAC2020_boards/DAC2020_bm07.dsn) | **1.9.0**    |        0 |          0 | 999.97 |         3.78 |            453 |


## Group: [DAC2020_boards](../fixtures/DAC2020_boards)

### Fixture: [DAC2020_bm07.dsn](../fixtures/DAC2020_boards/DAC2020_bm07.dsn)

Size: 14.9 kB · Layers: 2 · Nets: 52 · Components: 13
Dimensions: 2.2 x 6 mm (0.1 cm²)
CAD: KiCad's Pcbnew (v9.0.6)

| Version     | Mode | Fanout        | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes  | Unrouted | Violations | Score  | Peak Heap (MB) | Total Alloc (GB) | Warn/Err |
| :---------- | :--- | ------------: | --------------: | --------------: | -----------------: | -------------: | ------: | -------: | ---------: | -----: | -------------: | ---------------: | -------: |
| 1.9.0       | GUI  |           N/A |             N/A |            4.13 |              36.91 |          41.04 |   0+8+0 |        0 |          0 | 999.97 |            453 |              1.7 |    2 / 0 |
| 2.0.1       | CLI  |           N/A |             N/A |             N/A |                N/A |            N/A |   0+7+0 |        1 |          0 | 988.34 |           1127 |              0.0 |   1 / 32 |
| 2.1.0       | CLI  |           N/A |             N/A |             N/A |                N/A |            N/A | 0+811+3 |        2 |          0 | 976.70 |           1428 |              0.0 |   1 / 20 |
| 2.2.4       | CLI  |           N/A |             N/A |            5.10 |              51.27 |          56.37 |   0+8+7 |        0 |          0 | 999.96 |           1366 |              2.2 |    3 / 0 |
| s2026.06.23 | CLI  | 84/87 (96.6%) |            8.81 |           16.11 |                N/A |          24.92 |  0+18+2 |        3 |          0 | 965.08 |           1380 |             20.1 |    0 / 0 |
| s2026.06.24 | CLI  | 84/87 (96.6%) |            9.04 |           15.91 |               1.68 |          26.63 | 20+18+2 |        3 |          0 | 965.08 |            776 |             21.4 |    0 / 0 |


