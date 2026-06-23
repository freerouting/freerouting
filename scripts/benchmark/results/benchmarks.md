# Freerouting Nightly Benchmarks Report
Generated on: 2026-06-23 17:52:36

This report lists the latest benchmark run results for each Freerouting version and fixture combination.

## Summary Table (Best Results per Fixture)

| Fixture Group                                | Fixture                                                         | Best Version | Unrouted (DRC) | Violations (DRC) | Score (DRC) | CPU Time | Peak Heap |
| :--------------------------------------------- | :---------------------------------------------------------------- | :------------- | ---------------: | -----------------: | ------------: | ---------: | ----------: |
| [DAC2020_boards](../fixtures/DAC2020_boards) | [DAC2020_bm07.dsn](../fixtures/DAC2020_boards/DAC2020_bm07.dsn) | **1.9.0**    |              0 |                0 |      999,97 |    3.41s |  439.2 MB |


## Group: [DAC2020_boards](../fixtures/DAC2020_boards)

### Fixture: [DAC2020_bm07.dsn](../fixtures/DAC2020_boards/DAC2020_bm07.dsn)

Size: 14.9 kB Â· Layers: 2 Â· Nets: 52 Â· Components: 13
Dimensions: 2.2 x 6 mm (0.1 cmÂ²)
CAD: KiCad's Pcbnew (v9.0.6)

| Version     | Mode | Fanout        | Fanout Time | Router Time | Optimizer Time | Passes | Unrouted (DRC) | Violations (DRC) | Score (DRC) | Peak Heap | Total Alloc | Warn/Err |
| :------------ | :----- | --------------: | ------------: | ------------: | ---------------: | -------: | ---------------: | -----------------: | ------------: | ----------: | ------------: | ---------: |
| 1.9.0       | GUI  |           N/A |         N/A |       4.29s |         34.76s |      8 |              0 |                0 |      999,97 |  439.2 MB |     1.71 GB |    2 / 0 |
| 2.0.1       | GUI  |           N/A |         N/A |       3.93s |             0s |      6 |              0 |                0 |         N/A | 1096.5 MB |        0 GB |   1 / 16 |
| 2.1.0       | GUI  |           N/A |         N/A |       7.06s |             0s |     12 |              0 |                0 |      991,30 | 1126.1 MB |        0 GB |   2 / 10 |
| 2.2.4       | GUI  |           N/A |         N/A |         N/A |           2.3s |      8 |              0 |                0 |         N/A | 1516.2 MB |        0 GB |    2 / 0 |
| s2026.06.23 | GUI  | 84/87 (96.6%) |       8.38s |      15.77s |          1.02s |     18 |        3 (↓🔻) |                0 |      965,08 | 1113.2 MB |    21.25 GB |    1 / 0 |


