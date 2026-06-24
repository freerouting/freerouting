# Freerouting Nightly Benchmarks Report
Generated on: 2026-06-24 17:09:44
System: AMD Ryzen 5 3600 6-Core Processor (6 Cores, 31.9 GB RAM)

This report lists the latest benchmark run results for each Freerouting version and fixture combination.

## Summary Table (Best Results per Fixture)

| Fixture Group                                | Fixture                                                         | Best Version | Unrouted | Violations | Score | CPU Time (s) | Peak Heap (MB) |
| :------------------------------------------- | :-------------------------------------------------------------- | :----------- | -------: | ---------: | ----: | -----------: | -------------: |
| [DAC2020_boards](../fixtures/DAC2020_boards) | [DAC2020_bm01.dsn](../fixtures/DAC2020_boards/DAC2020_bm01.dsn) | **1.9.0**    |        0 |         87 |   911 |      1235.62 |            354 |
| [DAC2020_boards](../fixtures/DAC2020_boards) | [DAC2020_bm02.dsn](../fixtures/DAC2020_boards/DAC2020_bm02.dsn) | **1.9.0**    |        0 |          0 |  1000 |        35.25 |            124 |
| [DAC2020_boards](../fixtures/DAC2020_boards) | [DAC2020_bm04.dsn](../fixtures/DAC2020_boards/DAC2020_bm04.dsn) | **1.9.0**    |        2 |          4 |   980 |      1771.71 |            402 |
| [DAC2020_boards](../fixtures/DAC2020_boards) | [DAC2020_bm07.dsn](../fixtures/DAC2020_boards/DAC2020_bm07.dsn) | **1.9.0**    |        0 |          0 |  1000 |        39.52 |            154 |


## Group: [DAC2020_boards](../fixtures/DAC2020_boards)

### Fixture: [DAC2020_bm01.dsn](../fixtures/DAC2020_boards/DAC2020_bm01.dsn)

Size: 30.5 kB · Layers: 2 · Nets: 99 · Components: 20 · Dimensions: 101.6 x 53.3 mm (54.2 cm²) · CAD: KiCad's Pcbnew (v9.0.6)

| Version | Mode | Fanout          | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes   | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err |
| :------ | :--- | --------------: | --------------: | --------------: | -----------------: | -------------: | -------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: |
| 1.9.0   | GUI  | 186/212 (87.7%) |            4.92 |           44.91 |            1208.77 |        1258.60 | 20+10+18 |        0 |         87 |   911 |            354 |           1351.2 |   42 / 0 |


### Fixture: [DAC2020_bm02.dsn](../fixtures/DAC2020_boards/DAC2020_bm02.dsn)

Size: 79.7 kB · Layers: 2 · Nets: 34 · Components: 13 · Dimensions: 50.8 x 22.9 mm (11.6 cm²) · CAD: KiCad's Pcbnew (v9.0.6)

| Version | Mode | Fanout        | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes  | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err |
| :------ | :--- | ------------: | --------------: | --------------: | -----------------: | -------------: | ------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: |
| 1.9.0   | GUI  | 37/45 (82.2%) |            1.93 |            0.56 |              33.62 |          36.11 | 20+4+13 |        0 |          0 |  1000 |            124 |             34.9 |    0 / 0 |


### Fixture: [DAC2020_bm04.dsn](../fixtures/DAC2020_boards/DAC2020_bm04.dsn)

Size: 27 kB · Layers: 16 · Nets: 80 · Components: 16 · Dimensions: 43.9 x 35.1 mm (15.4 cm²) · CAD: KiCad's Pcbnew (v9.0.6)

| Version | Mode | Fanout          | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes  | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err |
| :------ | :--- | --------------: | --------------: | --------------: | -----------------: | -------------: | ------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: |
| 1.9.0   | GUI  | 159/198 (80.3%) |            6.46 |          100.12 |            1708.37 |        1814.95 | 20+24+3 |        2 |          4 |   980 |            402 |           1309.7 |    2 / 0 |


### Fixture: [DAC2020_bm07.dsn](../fixtures/DAC2020_boards/DAC2020_bm07.dsn)

Size: 14.9 kB · Layers: 2 · Nets: 52 · Components: 13 · Dimensions: 22 x 60 mm (13.2 cm²) · CAD: KiCad's Pcbnew (v9.0.6)

| Version     | Mode | Fanout        | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes  | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err |
| :---------- | :--- | ------------: | --------------: | --------------: | -----------------: | -------------: | ------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: |
| 1.9.0       | GUI  | 85/87 (97.7%) |            1.17 |            3.30 |              37.00 |          41.47 |  2+5+10 |        0 |          0 |  1000 |            154 |             35.8 |    0 / 0 |
| 2.0.1       | CLI  |           N/A |             N/A |             N/A |                N/A |            N/A |   0+7+0 |        1 |          0 |   988 |           1127 |              0.0 |   1 / 32 |
| 2.1.0       | CLI  |           N/A |             N/A |             N/A |                N/A |            N/A | 0+811+3 |        2 |          0 |   977 |           1428 |              0.0 |   1 / 20 |
| 2.2.4       | CLI  |           N/A |             N/A |            4.26 |              44.84 |          49.10 |   0+8+7 |        0 |          0 |  1000 |           1471 |              2.1 |    3 / 0 |
| s2026.06.23 | CLI  | 84/87 (96.6%) |            8.81 |           16.11 |                N/A |          24.92 |  0+18+2 |        3 |          0 |   965 |           1380 |             20.1 |    0 / 0 |
| s2026.06.24 | CLI  | 84/87 (96.6%) |            8.61 |           15.51 |               1.57 |          25.69 | 20+18+2 |        3 |          0 |   965 |            788 |             25.4 |    0 / 0 |


