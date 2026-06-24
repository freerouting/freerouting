# Freerouting Nightly Benchmarks Report
Generated on: 2026-06-24 11:45:23

This report lists the latest benchmark run results for each Freerouting version and fixture combination.

## Summary Table (Best Results per Fixture)

| Fixture Group                                | Fixture                                                         | Best Version | Unrouted (DRC) | Violations (DRC) | Score (DRC) | CPU Time | Peak Heap |
| :------------------------------------------- | :-------------------------------------------------------------- | :----------- | -------------: | ---------------: | ----------: | -------: | --------: |
| [DAC2020_boards](../fixtures/DAC2020_boards) | [DAC2020_bm07.dsn](../fixtures/DAC2020_boards/DAC2020_bm07.dsn) | **1.9.0**    |              0 |                0 |      999,97 |    3.83s |  518.3 MB |


## Group: [DAC2020_boards](../fixtures/DAC2020_boards)

### Fixture: [DAC2020_bm07.dsn](../fixtures/DAC2020_boards/DAC2020_bm07.dsn)

Size: 14.9 kB · Layers: 2 · Nets: 52 · Components: 13
Dimensions: 2.2 x 6 mm (0.1 cm²)
CAD: KiCad's Pcbnew (v9.0.6)

| Version     | Mode | Fanout        | Fanout Time | Router Time | Optimizer Time | Passes | Unrouted (DRC) | Violations (DRC) | Score (DRC)  | Peak Heap | Total Alloc | Warn/Err |
| :---------- | :--- | ------------: | ----------: | ----------: | -------------: | -----: | -------------: | ---------------: | -----------: | --------: | ----------: | -------: |
| 1.9.0       | GUI  |           N/A |         N/A |       4.25s |         38.26s |      8 |              0 |                0 |       999,97 |  518.3 MB |     1.76 GB |    2 / 0 |
| 2.0.1       | CLI  |           N/A |         N/A |         N/A |            N/A |      7 |        1 (↓🔻) |                0 | 988,34 (↓🔻) |  985.6 MB |        0 GB |   1 / 32 |
| 2.1.0       | CLI  |           N/A |         N/A |         N/A |            N/A |    807 |        2 (↓🔻) |                0 | 976,70 (↓🔻) | 1419.4 MB |        0 GB |   1 / 20 |
| 2.2.4       | CLI  |           N/A |         N/A |       3.89s |         49.27s |      8 |        0 (↑🟢) |                0 | 999,96 (↑🟢) | 1603.7 MB |     1.53 GB |    3 / 0 |
| s2026.06.23 | CLI  | 84/87 (96.6%) |       8.81s |      16.11s |            N/A |     18 |        3 (↓🔻) |                0 | 965,08 (↓🔻) | 1379.6 MB |     20.1 GB |    0 / 0 |
| s2026.06.24 | CLI  | 84/87 (96.6%) |       8.75s |      15.95s |          1.81s |     18 |              3 |                0 | 965,08 (↓🔻) |  821.8 MB |    23.66 GB |    0 / 0 |


