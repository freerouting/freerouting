# Freerouting Nightly Benchmarks Report
Generated on: 2026-06-24 12:11:47

This report lists the latest benchmark run results for each Freerouting version and fixture combination.

## Summary Table (Best Results per Fixture)

| Fixture Group                                | Fixture                                                         | Best Version | Unrouted (DRC) | Violations (DRC) | Score (DRC) | CPU Time | Peak Heap |
| :------------------------------------------- | :-------------------------------------------------------------- | :----------- | -------------: | ---------------: | ----------: | -------: | --------: |
| [DAC2020_boards](../fixtures/DAC2020_boards) | [DAC2020_bm07.dsn](../fixtures/DAC2020_boards/DAC2020_bm07.dsn) | **1.9.0**    |              0 |                0 |      999,97 |    3.59s |  465.6 MB |


## Group: [DAC2020_boards](../fixtures/DAC2020_boards)

### Fixture: [DAC2020_bm07.dsn](../fixtures/DAC2020_boards/DAC2020_bm07.dsn)

Size: 14.9 kB · Layers: 2 · Nets: 52 · Components: 13
Dimensions: 2.2 x 6 mm (0.1 cm²)
CAD: KiCad's Pcbnew (v9.0.6)

| Version     | Mode | Fanout        | Fanout Time | Router Time | Optimizer Time | Passes | Unrouted (DRC) | Violations (DRC) | Score (DRC) | Peak Heap | Total Alloc | Warn/Err |
| :---------- | :--- | ------------: | ----------: | ----------: | -------------: | -----: | -------------: | ---------------: | ----------: | --------: | ----------: | -------: |
| 1.9.0       | GUI  |           N/A |         N/A |        3.9s |         35.49s |      8 |              0 |                0 |      999,97 |  465.6 MB |     1.74 GB |    2 / 0 |
| 2.0.1       | CLI  |           N/A |         N/A |         N/A |            N/A |      7 |              1 |                0 |      988,34 | 1070.8 MB |        0 GB |   1 / 35 |
| 2.1.0       | CLI  |           N/A |         N/A |         N/A |            N/A |    807 |              2 |                0 |      976,70 | 1530.7 MB |        0 GB |   1 / 23 |
| 2.2.4       | CLI  |           N/A |         N/A |       3.94s |         43.26s |      8 |              0 |                0 |      999,96 | 1528.5 MB |     1.55 GB |    4 / 0 |
| s2026.06.23 | CLI  | 84/87 (96.6%) |       8.81s |      16.11s |            N/A |     18 |              3 |                0 |      965,08 | 1379.6 MB |     20.1 GB |    0 / 0 |
| s2026.06.24 | CLI  | 84/87 (96.6%) |       8.61s |      15.73s |          1.93s |     18 |              3 |                0 |      965,08 |  845.1 MB |    25.34 GB |    1 / 0 |


