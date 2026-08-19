# Freerouting Nightly Benchmarks Report
Generated on: 2026-08-19 23:37:27
System: AMD Ryzen 5 3600 6-Core Processor (6 Cores, 31.9 GB RAM)

This report lists the latest benchmark run results for each Freerouting version and fixture combination.

## Summary

### Summary Table (All Tiers Combined)
Comprehensive performance across all benchmark fixtures.

| Version         | Fixtures | Perfects         | All-routed       | Timeouts     | Failures       | Avg. Score |
| :-------------- | -------: | ---------------: | ---------------: | -----------: | -------------: | ---------: |
| 1.9.0           |       20 |       8/20 (40%) |       9/20 (45%) |    1/20 (5%) |      1/20 (5%) |      902.9 |
| 2.2.4           |       20 |       7/20 (35%) |       7/20 (35%) |    0/20 (0%) |     2/20 (10%) |      891.5 |
| 2.3.0           |       20 |       3/20 (15%) |       8/20 (40%) |    1/20 (5%) |     2/20 (10%) |      915.1 |
| v2.3.1-SNAPSHOT |     1157 | 363/1157 (31.4%) | 607/1157 (52.5%) | 11/1157 (1%) | 18/1157 (1.6%) |  **933.7** |


### Tier A: Canary Gate
Fast-solving 2-layer boards (0 unrouted, 0 clearance violations expected).

| Version         | Fixtures | Perfects       | All-routed     | Timeouts   | Failures   | Avg. Score |
| :-------------- | -------: | -------------: | -------------: | ---------: | ---------: | ---------: |
| v2.3.1-SNAPSHOT |      169 | 169/169 (100%) | 169/169 (100%) | 0/169 (0%) | 0/169 (0%) | **1000.0** |


### Tier B: Routine Benchmarks
Standard 2-4 layer boards evaluated for routine optimization progress.

| Version         | Fixtures | Perfects        | All-routed      | Timeouts     | Failures      | Avg. Score |
| :-------------- | -------: | --------------: | --------------: | -----------: | ------------: | ---------: |
| v2.3.1-SNAPSHOT |      844 | 175/844 (20.7%) | 401/844 (47.5%) | 7/844 (0.8%) | 11/844 (1.3%) |  **923.4** |


### Tier C: Complex / Multi-Layer
Dense and 6+ layer boards requiring deeper pathfinding.

| Version         | Fixtures | Perfects       | All-routed   | Timeouts     | Failures     | Avg. Score |
| :-------------- | -------: | -------------: | -----------: | -----------: | -----------: | ---------: |
| v2.3.1-SNAPSHOT |      122 | 18/122 (14.8%) | 33/122 (27%) | 1/122 (0.8%) | 3/122 (2.5%) |  **911.9** |


### Tier D: Extreme Stress / Diagnostic
High net-count and large surface-area stress boards.

| Version         | Fixtures | Perfects    | All-routed   | Timeouts     | Failures     | Avg. Score |
| :-------------- | -------: | ----------: | -----------: | -----------: | -----------: | ---------: |
| v2.3.1-SNAPSHOT |       22 | 1/22 (4.5%) | 4/22 (18.2%) | 3/22 (13.6%) | 4/22 (18.2%) |  **931.0** |


### General / Legacy Golden Fixtures
In-repo regression and golden fixture benchmark suite.

| Version | Fixtures | Perfects   | All-routed | Timeouts  | Failures   | Avg. Score |
| :------ | -------: | ---------: | ---------: | --------: | ---------: | ---------: |
| 1.9.0   |       20 | 8/20 (40%) | 9/20 (45%) | 1/20 (5%) |  1/20 (5%) |      902.9 |
| 2.2.4   |       20 | 7/20 (35%) | 7/20 (35%) | 0/20 (0%) | 2/20 (10%) |      891.5 |
| 2.3.0   |       20 | 3/20 (15%) | 8/20 (40%) | 1/20 (5%) | 2/20 (10%) |  **915.1** |


## Group: [DAC2020_boards](../fixtures/DAC2020_boards)

### Fixture: [DAC2020_bm01.dsn](../fixtures/DAC2020_boards/DAC2020_bm01.dsn)

Size: 30.5 kB · Layers: 2 · Nets: 99 · Components: 20 · Dimensions: 101.6 x 53.3 mm (54.2 cm²) · CAD: KiCad's Pcbnew (v9.0.6)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | GUI  |  186/ 212 ( 87.7%) |       6.40 |      53.46 |  1169.60 |   1229.46 |  20+ 10+ 18 |        0 |        N/A |  1000 |       229 |     1336.5 |   42 / 0 |       |
| 2.2.4   | CLI  |                N/A |        N/A |      89.24 |      N/A |     89.24 |   0+ 18+  4 |        0 |        N/A |  1000 |      1709 |       76.4 |   16 / 0 |       |
| 2.3.0   | CLI  |  186/ 187 ( 99.5%) |       3.78 |     250.87 |    10.19 |    264.84 |   4+ 21+  1 |        4 |        N/A |   979 |       920 |     1168.6 |    2 / 0 |       |


### Fixture: [DAC2020_bm02.dsn](../fixtures/DAC2020_boards/DAC2020_bm02.dsn)

Size: 79.7 kB · Layers: 2 · Nets: 34 · Components: 13 · Dimensions: 50.8 x 22.9 mm (11.6 cm²) · CAD: KiCad's Pcbnew (v9.0.6)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | GUI  |   37/  45 ( 82.2%) |       1.80 |       0.51 |    34.75 |     37.06 |  20+  4+ 13 |        0 |        N/A |  1000 |       157 |       34.2 |    0 / 0 |       |
| 2.2.4   | CLI  |                N/A |        N/A |       2.17 |     5.82 |      7.99 |   0+  6+  1 |        0 |        N/A |  1000 |      1573 |        0.9 |   16 / 0 |       |
| 2.3.0   | CLI  |   38/  38 (100.0%) |       1.32 |       2.27 |     0.04 |      3.63 |   7+  2+  0 |        0 |        N/A |  1000 |       456 |        7.2 |    2 / 0 |       |


### Fixture: [DAC2020_bm04.dsn](../fixtures/DAC2020_boards/DAC2020_bm04.dsn)

Size: 27 kB · Layers: 16 · Nets: 80 · Components: 16 · Dimensions: 43.9 x 35.1 mm (15.4 cm²) · CAD: KiCad's Pcbnew (v9.0.6)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | GUI  |  159/ 198 ( 80.3%) |       6.75 |      96.40 |  1711.05 |   1814.20 |  20+ 24+  3 |        2 |        N/A |   986 |       293 |     1366.5 |    2 / 0 |       |
| 2.2.4   | CLI  |                N/A |        N/A |      92.00 |    25.60 |    117.60 |   0+ 18+  2 |        2 |        N/A |   986 |      1951 |       67.6 |   16 / 0 |       |
| 2.3.0   | CLI  |  157/ 192 ( 81.8%) |       6.05 |     533.42 |    20.19 |    559.66 |   4+ 22+  1 |        3 |        N/A |   979 |      1754 |     2433.6 |    2 / 0 |       |


### Fixture: [DAC2020_bm05.dsn](../fixtures/DAC2020_boards/DAC2020_bm05.dsn)

Size: 16.8 kB · Layers: 2 · Nets: 54 · Components: 9 · Dimensions: 40 x 41 mm (16.4 cm²) · CAD: KiCad's Pcbnew (v9.0.6)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | GUI  |   88/ 138 ( 63.8%) |       5.30 |      82.58 |  1713.05 |   1800.93 |  20+ 33+  4 |       37 |        N/A |   589 |       164 |     2184.8 |   42 / 0 |       |
| 2.2.4   | CLI  |                N/A |        N/A |      69.51 |     3.74 |     73.25 |   0+ 22+  2 |       37 |        N/A |   570 |      1666 |       62.3 |   16 / 0 |       |
| 2.3.0   | CLI  |  119/ 138 ( 86.2%) |       3.44 |     134.01 |     3.81 |    141.26 |   7+ 18+  1 |       22 |        N/A |   785 |       705 |      687.8 |    2 / 0 |       |


### Fixture: [DAC2020_bm06.dsn](../fixtures/DAC2020_boards/DAC2020_bm06.dsn)

Size: 22.9 kB · Layers: 2 · Nets: 38 · Components: 13 · Dimensions: 55 x 28 mm (15.4 cm²) · CAD: KiCad's Pcbnew (v9.0.6)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | GUI  |  106/ 126 ( 84.1%) |       2.22 |       4.60 |  1370.63 |   1377.45 |  20+ 23+  5 |        8 |        N/A |   892 |       155 |     1497.2 |    5 / 0 |       |
| 2.2.4   | CLI  |                N/A |        N/A |      17.31 |     7.84 |     25.15 |   0+ 18+  2 |        8 |        N/A |   882 |      1611 |       12.6 |   16 / 0 |       |
| 2.3.0   | CLI  |  113/ 124 ( 91.1%) |       2.14 |      25.87 |     5.68 |     33.69 |   5+ 18+  1 |        2 |        N/A |   963 |       627 |      199.6 |    2 / 0 |       |


### Fixture: [DAC2020_bm07.dsn](../fixtures/DAC2020_boards/DAC2020_bm07.dsn)

Size: 14.9 kB · Layers: 2 · Nets: 52 · Components: 13 · Dimensions: 22 x 60 mm (13.2 cm²) · CAD: KiCad's Pcbnew (v9.0.6)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | GUI  |   85/  87 ( 97.7%) |       1.14 |       2.99 |    36.00 |     40.13 |   2+  5+ 10 |        0 |        N/A |  1000 |       159 |       35.3 |    0 / 0 |       |
| 2.2.4   | CLI  |                N/A |        N/A |       4.02 |    40.50 |     44.52 |   0+  9+  3 |        0 |        N/A |  1000 |      1574 |        2.1 |   16 / 0 |       |
| 2.3.0   | CLI  |   85/  85 (100.0%) |       1.36 |      13.05 |     3.20 |     17.61 |   4+ 22+  1 |        3 |        N/A |   965 |       548 |      103.7 |    2 / 0 |       |


### Fixture: [DAC2020_bm08.dsn](../fixtures/DAC2020_boards/DAC2020_bm08.dsn)

Size: 5.5 kB · Layers: 2 · Nets: 15 · Components: 4 · Dimensions: 20.5 x 13.9 mm (2.8 cm²) · CAD: KiCad's Pcbnew (v9.0.6)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | GUI  |   30/  36 ( 83.3%) |       0.47 |       0.00 |     0.69 |      1.16 |   2+  0+  2 |        0 |        N/A |  1000 |       133 |        0.5 |    0 / 0 |       |
| 2.2.4   | CLI  |                N/A |        N/A |       0.59 |     2.64 |      3.23 |   0+  2+  2 |        0 |        N/A |  1000 |      1481 |        0.0 |   16 / 0 |       |
| 2.3.0   | CLI  |   30/  36 ( 83.3%) |       0.51 |       0.74 |     0.01 |      1.26 |   2+  2+  0 |        0 |        N/A |  1000 |       185 |        0.9 |    2 / 0 |       |


### Fixture: [DAC2020_bm09.dsn](../fixtures/DAC2020_boards/DAC2020_bm09.dsn)

Size: 25.1 kB · Layers: 16 · Nets: 70 · Components: 13 · Dimensions: 56.4 x 86.4 mm (48.7 cm²) · CAD: KiCad's Pcbnew (v9.0.6)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | GUI  |                N/A |       0.00 |       5.17 |    10.90 |     16.07 |   0+  2+  2 |        1 |        N/A |   991 |       125 |        5.3 |    0 / 0 |       |
| 2.2.4   | CLI  |                N/A |        N/A |       6.28 |    18.10 |     24.38 |   0+  2+  2 |        2 |        N/A |   983 |      1539 |        2.0 |   16 / 0 |       |
| 2.3.0   | CLI  |                N/A |        N/A |      15.00 |     0.04 |     15.04 |   0+ 18+  0 |        1 |        N/A |   991 |       429 |       48.8 |    2 / 0 |       |


### Fixture: [DAC2020_bm10.dsn](../fixtures/DAC2020_boards/DAC2020_bm10.dsn)

Size: 31.3 kB · Layers: 4 · Nets: 63 · Components: 21 · Dimensions: 86 x 71.5 mm (61.5 cm²) · CAD: KiCad's Pcbnew (v9.0.6)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | GUI  |  243/ 283 ( 85.9%) |       7.64 |      12.33 |   369.70 |    389.67 |  20+  3+  5 |        0 |        N/A |  1000 |       438 |      298.7 |    7 / 0 |       |
| 2.2.4   | CLI  |                N/A |        N/A |      28.31 |      N/A |     28.31 |   0+  6+  3 |        0 |        N/A |  1000 |      2007 |       20.1 |   16 / 0 |       |
| 2.3.0   | CLI  |  242/ 245 ( 98.8%) |       9.73 |      71.86 |     0.24 |     81.83 |  12+  4+  0 |        0 |        N/A |  1000 |       784 |      425.7 |    2 / 0 |       |


### Fixture: [DAC2020_bm11.dsn](../fixtures/DAC2020_boards/DAC2020_bm11.dsn)

Size: 26.2 kB · Layers: 4 · Nets: 35 · Components: 21 · Dimensions: 58 x 59.5 mm (34.5 cm²) · CAD: KiCad's Pcbnew (v9.0.6)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | GUI  |  142/ 193 ( 73.6%) |      10.56 |       6.77 |  1768.08 |   1785.41 |  20+ 25+  9 |        6 |        N/A |   919 |       320 |     1620.1 |    2 / 0 |       |
| 2.2.4   | CLI  |                N/A |        N/A |      16.87 |    20.26 |     37.13 |   0+ 18+  2 |        7 |        N/A |   900 |      1678 |       11.5 |   16 / 0 |       |
| 2.3.0   | CLI  |  154/ 157 ( 98.1%) |       3.18 |      84.85 |    10.44 |     98.47 |   3+ 18+  1 |        2 |        N/A |   987 |       754 |      494.0 |    2 / 0 |       |


## Group: [KiCad_10_demos](../fixtures/KiCad_10_demos)

### Fixture: [CM5_MINIMA_3.dsn](../fixtures/KiCad_10_demos/CM5_MINIMA_3.dsn)

Size: 146.8 kB · Layers: 6 · Nets: 220 · Components: 51 · Dimensions: 61.2 x 64.2 mm (39.3 cm²) · CAD: KiCad's Pcbnew (v10.0.2)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes     |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :-------- |
| 1.9.0   | GUI  |  294/ 593 ( 49.6%) |      43.87 |      17.62 |   218.79 |    280.28 |  20+  5+  3 |        4 |        N/A |   971 |       247 |      139.9 |   23 / 0 |           |
| 2.2.4   | CLI  |                N/A |        N/A |     175.07 |      N/A |    175.07 |   0+ 20+  2 |        6 |        N/A |   954 |      2018 |       85.8 |   16 / 0 |           |
| 2.3.0   | CLI  |  460/ 589 ( 78.1%) |      52.70 |    3516.58 |      N/A |   3569.28 |   5+  1+  0 |      N/A |        N/A |   896 |      1433 |       65.2 |    2 / 0 | TIMEOUT,  |


### Fixture: [complex_hierarchy.dsn](../fixtures/KiCad_10_demos/complex_hierarchy.dsn)

Size: 53.3 kB · Layers: 2 · Nets: 52 · Components: 21 · Dimensions: 100.7 x 80 mm (80.6 cm²) · CAD: KiCad's Pcbnew (v10.0.2)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | GUI  |                N/A |       0.00 |       7.28 |    67.02 |     74.30 |   0+ 27+  2 |        6 |        N/A |   938 |        92 |       52.3 |    2 / 0 |       |
| 2.2.4   | CLI  |                N/A |        N/A |       5.78 |     9.96 |     15.74 |   0+  7+  2 |        8 |        N/A |   912 |      1585 |        2.4 |   16 / 0 |       |
| 2.3.0   | CLI  |                N/A |        N/A |      15.78 |     1.47 |     17.25 |   0+ 18+  1 |        9 |        N/A |   911 |       547 |       70.9 |    4 / 0 |       |


### Fixture: [ecc83-pp.dsn](../fixtures/KiCad_10_demos/ecc83-pp.dsn)

Size: 34.8 kB · Layers: 2 · Nets: 13 · Components: 9 · Dimensions: 52.1 x 46.4 mm (24.2 cm²) · CAD: KiCad's Pcbnew (v10.0.2)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | GUI  |                N/A |       0.00 |       0.27 |     0.41 |      0.68 |   0+  1+  2 |        0 |        N/A |  1000 |        78 |        0.2 |    0 / 0 |       |
| 2.2.4   | CLI  |                N/A |        N/A |       0.34 |     1.90 |      2.24 |   0+  2+  2 |        0 |        N/A |  1000 |      1586 |        0.0 |   16 / 0 |       |
| 2.3.0   | CLI  |                N/A |        N/A |       0.44 |     0.02 |      0.46 |   0+  2+  0 |        0 |        N/A |  1000 |       103 |        0.0 |    2 / 0 |       |


### Fixture: [ecc83-pp_v2.dsn](../fixtures/KiCad_10_demos/ecc83-pp_v2.dsn)

Size: 38.2 kB · Layers: 2 · Nets: 13 · Components: 9 · Dimensions: 48.3 x 41.9 mm (20.2 cm²) · CAD: KiCad's Pcbnew (v10.0.2)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | GUI  |                N/A |       0.00 |       0.33 |     0.69 |      1.02 |   0+  1+  2 |        0 |        N/A |   771 |       143 |        1.2 |   14 / 0 |       |
| 2.2.4   | CLI  |                N/A |        N/A |       0.46 |     3.12 |      3.58 |   0+  2+  2 |        0 |        N/A |   771 |      1686 |        0.0 |   16 / 0 |       |
| 2.3.0   | CLI  |                N/A |        N/A |       0.49 |     0.75 |      1.24 |   0+  2+  1 |        0 |        N/A |   771 |       659 |        3.1 |    2 / 0 |       |


### Fixture: [interf_u.dsn](../fixtures/KiCad_10_demos/interf_u.dsn)

Size: 67.6 kB · Layers: 2 · Nets: 173 · Components: 19 · Dimensions: 115.6 x 108.2 mm (125.1 cm²) · CAD: KiCad's Pcbnew (v10.0.2)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | GUI  |   27/  62 ( 43.5%) |       1.26 |      38.83 |   214.73 |    254.82 |  20+ 28+  9 |        0 |        N/A |   938 |       121 |      178.9 |   13 / 0 |       |
| 2.2.4   | CLI  |                N/A |        N/A |      30.51 |    37.30 |     67.81 |   0+ 21+  2 |        2 |        N/A |   928 |      1642 |       20.5 |   16 / 0 |       |
| 2.3.0   | CLI  |   26/  62 ( 41.9%) |       1.17 |      70.14 |   121.26 |    192.57 |   7+ 19+  1 |        0 |        N/A |   938 |       744 |      979.5 |    2 / 0 |       |


### Fixture: [multichannel_mixer.dsn](../fixtures/KiCad_10_demos/multichannel_mixer.dsn)

Size: 49.2 kB · Layers: 2 · Nets: 80 · Components: 15 · Dimensions: 110 x 111 mm (122.1 cm²) · CAD: KiCad's Pcbnew (v10.0.2)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | GUI  |   13/ 192 (  6.8%) |       0.51 |       7.40 |     0.67 |      8.58 |   1+  2+  2 |       75 |        N/A |   212 |       265 |        6.8 |    0 / 0 |       |
| 2.2.4   | CLI  |                N/A |        N/A |      67.52 |     1.46 |     68.98 |   0+ 18+  2 |       75 |        N/A |   202 |      1318 |       59.5 |   16 / 0 |       |
| 2.3.0   | CLI  |   28/ 192 ( 14.6%) |       0.68 |      94.44 |     1.15 |     96.27 |   2+ 18+  1 |       75 |        N/A |   212 |       422 |      283.3 |    2 / 0 |       |


### Fixture: [multichannel_mixer-unrouted.dsn](../fixtures/KiCad_10_demos/multichannel_mixer-unrouted.dsn)

Size: 62 kB · Layers: 2 · Nets: 224 · Components: 15 · Dimensions: 110 x 111 mm (122.1 cm²) · CAD: KiCad's Pcbnew (v10.0.2)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes      |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :--------- |
| 1.9.0   | GUI  |                N/A |        N/A |        N/A |      N/A |       N/A |   0+  0+  0 |      N/A |        N/A |   N/A |         0 |        0.0 |    2 / 0 | LOAD ERROR |
| 2.2.4   | CLI  |                N/A |        N/A |      16.92 |     3.24 |     20.16 |   0+  2+  2 |       59 |        N/A |     0 |      1124 |       10.4 |   22 / 0 |            |
| 2.3.0   | CLI  |   25/ 192 ( 13.0%) |       1.00 |     366.73 |     3.07 |    370.80 |   2+ 18+  1 |       59 |        N/A |     0 |       592 |     1621.5 |    8 / 0 |            |


### Fixture: [pic_programmer.dsn](../fixtures/KiCad_10_demos/pic_programmer.dsn)

Size: 104.2 kB · Layers: 2 · Nets: 111 · Components: 29 · Dimensions: 160 x 99.1 mm (158.6 cm²) · CAD: KiCad's Pcbnew (v10.0.2)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | GUI  |    1/   2 ( 50.0%) |       0.31 |       1.85 |     4.88 |      7.04 |  20+  2+  2 |        2 |        N/A |   959 |       166 |        3.5 |    0 / 0 |       |
| 2.2.4   | CLI  |                N/A |        N/A |       2.33 |    15.86 |     18.19 |   0+  2+  2 |        2 |        N/A |   983 |      1537 |        0.8 |   16 / 0 |       |
| 2.3.0   | CLI  |    2/   2 (100.0%) |       0.34 |       3.34 |     0.03 |      3.71 |   2+  2+  0 |        0 |        N/A |   998 |       425 |        7.6 |    2 / 0 |       |


### Fixture: [sonde xilinx.dsn](../fixtures/KiCad_10_demos/sonde xilinx.dsn)

Size: 30.8 kB · Layers: 2 · Nets: 42 · Components: 10 · Dimensions: 80.4 x 43.2 mm (34.7 cm²) · CAD: KiCad's Pcbnew (v10.0.2)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes                             |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :-------------------------------- |
| 1.9.0   | GUI  |   19/  34 ( 55.9%) |       0.38 |       0.49 |     4.47 |      5.34 |   1+  1+  4 |        0 |        N/A |  1000 |       163 |        2.4 |   66 / 0 |                                   |
| 2.2.4   | CLI  |                N/A |        N/A |        N/A |      N/A |       N/A |   0+  0+  0 |      N/A |        N/A |   N/A |         0 |        0.0 |   12 / 6 | LOAD ERROR, FileNotFoundException |
| 2.3.0   | CLI  |   21/  34 ( 61.8%) |       0.55 |       1.75 |     8.59 |     10.89 |   3+  2+  1 |        0 |        N/A |  1000 |       697 |       55.6 |    2 / 0 |                                   |


### Fixture: [StickHub.dsn](../fixtures/KiCad_10_demos/StickHub.dsn)

Size: 83.4 kB · Layers: 2 · Nets: 47 · Components: 58 · Dimensions: 16.5 x 40 mm (6.6 cm²) · CAD: KiCad's Pcbnew (v10.0.2)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | GUI  |  167/ 273 ( 61.2%) |      20.06 |      13.82 |   734.68 |    768.56 |  20+ 29+  9 |        2 |        N/A |   990 |       208 |      518.1 |   44 / 0 |       |
| 2.2.4   | CLI  |                N/A |        N/A |      17.95 |    31.90 |     49.85 |   0+ 18+  2 |        5 |        N/A |   977 |      1623 |       10.3 |   16 / 0 |       |
| 2.3.0   | CLI  |  267/ 273 ( 97.8%) |      27.00 |     262.75 |    20.93 |    310.68 |  20+ 29+  1 |        2 |        N/A |   990 |       569 |      860.9 |    2 / 0 |       |


## Group: [PCBench](../fixtures/PCBench)

### Fixture: [unrouted.dsn](../fixtures/PCBench/16x12-bits-I2C_I2C_Servo/unrouted.dsn)

Size: 23.7 kB · Layers: 2 · Nets: 24 · Components: 55 · Dimensions: 73.66 x 45.72 mm (33.68 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      17.60 |      N/A |     17.60 |   0+  2+  0 |        0 |          0 |  1000 |       485 |    21524.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/1Bitsy_1bitsy/unrouted.dsn)

Size: 37.7 kB · Layers: 4 · Nets: 20 · Components: 108 · Dimensions: 36.8 x 20.32 mm (7.48 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     305.12 |      N/A |    305.12 |   0+  1+  0 |        0 |         45 |   944 |       716 |   586693.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/1-Wire-Wing-pcb_1-Wire_Wing/unrouted.dsn)

Size: 16.2 kB · Layers: 2 · Nets: 60 · Components: 23 · Dimensions: 69.85 x 21.59 mm (15.08 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      13.70 |      N/A |     13.70 |   0+  4+  0 |        0 |          0 |  1000 |       272 |    16809.7 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/2d_conduction_sk9822-matrix/unrouted.dsn)

Size: 27.6 kB · Layers: 2 · Nets: 112 · Components: 133 · Dimensions: 91.44 x 91.44 mm (83.61 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     256.45 |      N/A |    256.45 |   0+ 18+  0 |        2 |          0 |   995 |       641 |  1052753.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/4_digit_hex_display_4_digit_display/unrouted.dsn)

Size: 19.6 kB · Layers: 4 · Nets: 182 · Components: 46 · Dimensions: 62.23 x 21.59 mm (13.44 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     127.71 |      N/A |    127.71 |   0+  1+  0 |        4 |          0 |   963 |       534 |   330879.4 |    3 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/4N35-TTL-Serial-Optoisolator_4N35-TTL-Serial-Optoisolator/unrouted.dsn)

Size: 10.4 kB · Layers: 2 · Nets: 0 · Components: 17 · Dimensions: 40.64 x 22.86 mm (9.29 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.00 |      N/A |      2.00 |   0+  3+  0 |        0 |          0 |  1000 |        59 |      655.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/4-port-usb-hub_4port-usb-hub/unrouted.dsn)

Size: 31.8 kB · Layers: 2 · Nets: 15 · Components: 48 · Dimensions: 64.77 x 46.99 mm (30.44 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      30.04 |      N/A |     30.04 |   0+ 13+  0 |        0 |          0 |  1000 |       470 |    71169.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/59pct_keyboard/unrouted.dsn)

Size: 55.2 kB · Layers: 2 · Nets: 0 · Components: 195 · Dimensions: 332.49 x 94.11 mm (312.91 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     159.88 |      N/A |    159.88 |   0+  1+  0 |        4 |         40 |   961 |       560 |   426385.7 |    4 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/655_testboard/unrouted.dsn)

Size: 17.9 kB · Layers: 2 · Nets: 12 · Components: 30 · Dimensions: 39.17 x 62.92 mm (24.65 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      11.18 |      N/A |     11.18 |   0+  4+  0 |        0 |          0 |  1000 |       473 |    27013.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/6N137-TTL-Serial-Optoisolator_6N137-TTL-Serial-Optoisolator/unrouted.dsn)

Size: 11.6 kB · Layers: 2 · Nets: 0 · Components: 19 · Dimensions: 40.64 x 22.86 mm (9.29 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       7.53 |      N/A |      7.53 |   0+  1+  0 |        0 |          4 |   971 |       371 |    13992.1 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/6volt-5W-solar-cc_6vleadacidsolar/unrouted.dsn)

Size: 23.8 kB · Layers: 2 · Nets: 10 · Components: 65 · Dimensions: 50.0 x 50.0 mm (25.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      19.09 |      N/A |     19.09 |   0+  6+  0 |        0 |          0 |  1000 |       502 |    66364.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/74Logic_SA_ADC_SA-ADC/unrouted.dsn)

Size: 86.8 kB · Layers: 2 · Nets: 87 · Components: 191 · Dimensions: 95.89 x 69.22 mm (66.38 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     661.72 |      N/A |    661.72 |   0+  1+  0 |        1 |         32 |   983 |       861 |  2416968.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/8088_sbc2_pcb_8088_sbc2/unrouted.dsn)

Size: 69.5 kB · Layers: 2 · Nets: 82 · Components: 65 · Dimensions: 200.0 x 100.0 mm (200.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     120.74 |      N/A |    120.74 |   0+ 18+  0 |        1 |          2 |   996 |       689 |   342867.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/96boards-robomezzi_96boards-robomezzi/unrouted.dsn)

Size: 69.1 kB · Layers: 4 · Nets: 165 · Components: 119 · Dimensions: 85.0 x 54.0 mm (45.9 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     359.96 |      N/A |    359.96 |   0+  1+  0 |        4 |          6 |   982 |       795 |   845065.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/96boards-sensors_Sensors/unrouted.dsn)

Size: 45.6 kB · Layers: 2 · Nets: 14 · Components: 93 · Dimensions: 85.0 x 54.0 mm (45.9 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     274.61 |      N/A |    274.61 |   0+  1+  0 |        1 |          0 |   996 |       873 |   913674.7 |    4 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/a123-battery-integration_BCM/unrouted.dsn)

Size: 42.3 kB · Layers: 4 · Nets: 139 · Components: 67 · Dimensions: 66.04 x 73.66 mm (48.65 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      20.39 |      N/A |     20.39 |   0+  3+  0 |        0 |          6 |   993 |       499 |    69811.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ABOVISP_ABOVISP/unrouted.dsn)

Size: 15.5 kB · Layers: 2 · Nets: 3 · Components: 16 · Dimensions: 17.78 x 30.48 mm (5.42 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       7.45 |      N/A |      7.45 |   0+  1+  0 |        1 |         60 |   581 |       332 |    21014.1 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/abus-cfa1000-display-grabber_acs-display-grabber/unrouted.dsn)

Size: 37.2 kB · Layers: 2 · Nets: 21 · Components: 47 · Dimensions: 100.0 x 100.0 mm (100.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      39.86 |      N/A |     39.86 |   0+  7+  0 |        0 |          0 |  1000 |       495 |   141184.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ADC-DAC-16bit_ADC-DAC-16bit/unrouted.dsn)

Size: 16.8 kB · Layers: 2 · Nets: 9 · Components: 17 · Dimensions: 23.7 x 22.0 mm (5.21 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.56 |      N/A |      4.56 |   0+  3+  0 |        0 |          0 |  1000 |       475 |     7555.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ADC-PCM4202_ADC-PCM4202/unrouted.dsn)

Size: 51.5 kB · Layers: 2 · Nets: 61 · Components: 237 · Dimensions: 100.0 x 100.0 mm (100.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      64.90 |      N/A |     64.90 |   0+  3+  0 |        0 |          0 |  1000 |       566 |   269821.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ADC-PCM4202-SE_ADC-PCM4202-SE/unrouted.dsn)

Size: 49.6 kB · Layers: 2 · Nets: 59 · Components: 230 · Dimensions: 100.0 x 100.0 mm (100.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      77.14 |      N/A |     77.14 |   0+  4+  0 |        0 |          0 |  1000 |       541 |   313340.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/airqualitystation_hardware/unrouted.dsn)

Size: 34.8 kB · Layers: 2 · Nets: 18 · Components: 30 · Dimensions: 44.2 x 44.2 mm (19.54 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       7.94 |      N/A |      7.94 |   0+  5+  0 |        0 |          0 |  1000 |       492 |    18773.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/akuhei_akuhei/unrouted.dsn)

Size: 14.9 kB · Layers: 2 · Nets: 5 · Components: 9 · Dimensions: 22.99 x 23.43 mm (5.39 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      18.52 |      N/A |     18.52 |   0+  1+  0 |        2 |          0 |   944 |       409 |    41569.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Aleste-520EX_aleste/unrouted.dsn)

Size: 147.7 kB · Layers: 2 · Nets: 243 · Components: 313 · Dimensions: 334.01 x 193.04 mm (644.77 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |    1803.68 |      N/A |   1803.68 |   0+  2+  0 |      428 |        218 |   714 |      1477 |  3706147.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Amiga-A1012-PCB_Amiga-A1012/unrouted.dsn)

Size: 27.4 kB · Layers: 2 · Nets: 40 · Components: 24 · Dimensions: 73.75 x 78.25 mm (57.71 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      15.12 |      N/A |     15.12 |   0+  5+  0 |        0 |          0 |  1000 |       497 |    46898.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/AmpOne_dev-AmpOne/unrouted.dsn)

Size: 43.4 kB · Layers: 2 · Nets: 62 · Components: 164 · Dimensions: 100.0 x 100.0 mm (100.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      96.07 |      N/A |     96.07 |   0+ 18+  0 |        1 |          0 |   997 |       596 |   385678.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/analog_esr_meter_esr_meter_rev_a/unrouted.dsn)

Size: 117.7 kB · Layers: 2 · Nets: 23 · Components: 71 · Dimensions: 645.94 x 78.18 mm (505.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       6.81 |      N/A |      6.81 |   0+  2+  0 |        0 |          4 |   993 |       269 |    11571.1 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/analog_esr_meter_esr_meter_rev_b/unrouted.dsn)

Size: 118.1 kB · Layers: 2 · Nets: 23 · Components: 88 · Dimensions: 645.94 x 78.18 mm (505.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      56.89 |      N/A |     56.89 |   0+  1+  0 |        0 |         16 |   974 |       483 |   155579.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/analogevse_AnalogEVSE/unrouted.dsn)

Size: 27.8 kB · Layers: 2 · Nets: 42 · Components: 80 · Dimensions: 101.71 x 84.69 mm (86.14 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      39.55 |      N/A |     39.55 |   0+  1+  0 |        0 |         12 |   984 |       521 |   175340.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/AnalogThermometer_AnalogThermometer/unrouted.dsn)

Size: 15.1 kB · Layers: 2 · Nets: 13 · Components: 19 · Dimensions: 25.4 x 25.4 mm (6.45 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.43 |      N/A |      2.43 |   0+  3+  0 |        0 |          0 |  1000 |       283 |     3118.1 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/android_debug_cable_android_debug_cable/unrouted.dsn)

Size: 12.9 kB · Layers: 2 · Nets: 17 · Components: 11 · Dimensions: 25.46 x 10.46 mm (2.66 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      22.75 |      N/A |     22.75 |   0+  1+  0 |        0 |          8 |   954 |       536 |    76178.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/anima_MotorDrive/unrouted.dsn)

Size: 25 kB · Layers: 2 · Nets: 78 · Components: 62 · Dimensions: 54.61 x 63.5 mm (34.68 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      26.26 |      N/A |     26.26 |   0+  5+  0 |        0 |          0 |  1000 |       509 |    68752.7 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/antdroid-board_antdroid-board/unrouted.dsn)

Size: 58.3 kB · Layers: 2 · Nets: 62 · Components: 60 · Dimensions: 99.45 x 53.72 mm (53.42 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      58.43 |      N/A |     58.43 |   0+  1+  0 |       22 |          0 |   842 |       620 |   190563.2 |    3 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/apa102lantern_apa102-lantern-esp8266/unrouted.dsn)

Size: 39.7 kB · Layers: 2 · Nets: 22 · Components: 27 · Dimensions: 63.5 x 63.5 mm (40.32 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       7.12 |      N/A |      7.12 |   0+ 13+  0 |        0 |          0 |  1000 |       441 |    17219.1 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/apa102lantern_apa102-lantern-side/unrouted.dsn)

Size: 28.4 kB · Layers: 2 · Nets: 14 · Components: 12 · Dimensions: 25.4 x 109.22 mm (27.74 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.67 |      N/A |      2.67 |   0+  2+  0 |        0 |          0 |  1000 |       159 |     2932.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/APC_AtariPunkConsole/unrouted.dsn)

Size: 19.5 kB · Layers: 2 · Nets: 16 · Components: 30 · Dimensions: 90.0 x 43.0 mm (38.7 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.86 |      N/A |      1.86 |   0+  2+  0 |        0 |          0 |  1000 |        19 |     1049.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/APM-RPi-Shield_APM-RPi-Shield/unrouted.dsn)

Size: 28.7 kB · Layers: 2 · Nets: 36 · Components: 29 · Dimensions: 37.25 x 56.2 mm (20.93 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      50.87 |      N/A |     50.87 |   0+  1+  0 |        4 |          4 |   962 |       514 |   166813.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Apple-M0110-BT_Apple M0110/unrouted.dsn)

Size: 40.6 kB · Layers: 2 · Nets: 97 · Components: 132 · Dimensions: 275.28 x 97.92 mm (269.55 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      25.23 |      N/A |     25.23 |   0+  6+  0 |        0 |          8 |   993 |       444 |    54473.2 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/arduino_arduino leds/unrouted.dsn)

Size: 34.3 kB · Layers: 2 · Nets: 0 · Components: 29 · Dimensions: 99.06 x 49.53 mm (49.06 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.73 |      N/A |      3.73 |   0+  3+  0 |        0 |          0 |  1000 |       204 |     3170.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Arduino_Lipo_Storage_Discharger_Lipo_Storage_Discharger/unrouted.dsn)

Size: 42.1 kB · Layers: 2 · Nets: 31 · Components: 42 · Dimensions: 99.0 x 49.0 mm (48.51 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       5.27 |      N/A |      5.27 |   0+  3+  0 |        0 |          0 |  1000 |       384 |    11079.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ArduinoDueClone_ATSAM3X8EA/unrouted.dsn)

Size: 63 kB · Layers: 2 · Nets: 18 · Components: 75 · Dimensions: 160.0 x 100.0 mm (160.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     301.44 |      N/A |    301.44 |   0+ 11+  0 |       92 |          0 |   593 |       639 |   973717.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/arduino-led-driver_arduino-led-driver/unrouted.dsn)

Size: 37.5 kB · Layers: 2 · Nets: 57 · Components: 118 · Dimensions: 0.0 x 0.0 mm (0.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     235.98 |      N/A |    235.98 |   0+ 20+  0 |        1 |          0 |   996 |       816 |   926876.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Arduino-Theremin_arduino-theremin-v1/unrouted.dsn)

Size: 24.8 kB · Layers: 2 · Nets: 3 · Components: 15 · Dimensions: 71.12 x 53.34 mm (37.94 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       5.86 |      N/A |      5.86 |   0+  1+  0 |        1 |          0 |   963 |       324 |     9054.7 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/arf154_arf154/unrouted.dsn)

Size: 23.8 kB · Layers: 4 · Nets: 22 · Components: 30 · Dimensions: 18.29 x 46.23 mm (8.46 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     144.96 |      N/A |    144.96 |   0+  1+  0 |        0 |        122 |   732 |       587 |   571357.4 |    3 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Aria_Aria/unrouted.dsn)

Size: 67.2 kB · Layers: 4 · Nets: 162 · Components: 91 · Dimensions: 86.06 x 27.0 mm (23.24 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     469.46 |      N/A |    469.46 |   0+  1+  0 |       38 |         10 |   829 |       701 |  1436782.6 |    3 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/AS5043-Encoder_sensor-board/unrouted.dsn)

Size: 12.5 kB · Layers: 2 · Nets: 8 · Components: 17 · Dimensions: 35.56 x 35.56 mm (12.65 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.49 |      N/A |      2.49 |   0+  3+  0 |        0 |          0 |  1000 |       171 |     2541.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ATmega32_ExploreUltraAvrDevKit__autosave-MCU_BaseBoard/unrouted.dsn)

Size: 118.5 kB · Layers: 2 · Nets: 139 · Components: 149 · Dimensions: 180.0 x 125.0 mm (225.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     106.90 |      N/A |    106.90 |   0+ 18+  0 |        2 |          2 |   994 |       569 |   432878.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ATmega32_ExploreUltraAvrDevKit_40pin_AVRMCU/unrouted.dsn)

Size: 18.3 kB · Layers: 2 · Nets: 0 · Components: 21 · Dimensions: 40.0 x 75.0 mm (30.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       6.61 |      N/A |      6.61 |   0+  4+  0 |        0 |          0 |  1000 |       520 |    14161.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ATMEGA328-Motor-Board_ATMEGA328_Motor_Board/unrouted.dsn)

Size: 74.8 kB · Layers: 2 · Nets: 46 · Components: 259 · Dimensions: 77.0 x 100.0 mm (77.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     903.84 |      N/A |    903.84 |   0+  1+  0 |       10 |        122 |   931 |       929 |  2991281.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/atmegax8-protoboard_atmegax8-protoboard/unrouted.dsn)

Size: 12.7 kB · Layers: 2 · Nets: 0 · Components: 7 · Dimensions: 21.34 x 54.1 mm (11.54 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       5.31 |      N/A |      5.31 |   0+  1+  0 |        0 |         56 |   733 |       307 |    17034.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Atmel-ICE-Header-Adapter_ice header adapter pcb/unrouted.dsn)

Size: 30 kB · Layers: 2 · Nets: 1 · Components: 13 · Dimensions: 48.26 x 48.26 mm (23.29 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      24.58 |      N/A |     24.58 |   0+  1+  0 |        3 |          0 |   958 |       413 |    65963.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/atmel-programmer_atmel_programmer/unrouted.dsn)

Size: 28 kB · Layers: 2 · Nets: 71 · Components: 12 · Dimensions: 76.2 x 58.42 mm (44.52 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.32 |      N/A |      3.32 |   0+  3+  0 |        0 |          0 |  1000 |       396 |     4664.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Atreus_54percent_rev/unrouted.dsn)

Size: 59.4 kB · Layers: 2 · Nets: 151 · Components: 191 · Dimensions: 277.0 x 110.75 mm (306.78 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     182.03 |      N/A |    182.03 |   0+ 18+  0 |        1 |          0 |   998 |       602 |   574728.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ATtiny461Breakout_ATTiny461DevBoard/unrouted.dsn)

Size: 15.7 kB · Layers: 2 · Nets: 18 · Components: 12 · Dimensions: 24.13 x 33.02 mm (7.97 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       7.46 |      N/A |      7.46 |   0+  1+  0 |        1 |          0 |   974 |       492 |    22082.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/audio_relay_input_switch_relay_switch/unrouted.dsn)

Size: 28.8 kB · Layers: 2 · Nets: 5 · Components: 20 · Dimensions: 25.65 x 64.62 mm (16.58 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      19.11 |      N/A |     19.11 |   0+  1+  0 |        0 |          9 |   956 |       432 |    41499.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/audprog_audprog_v2/unrouted.dsn)

Size: 25.7 kB · Layers: 2 · Nets: 19 · Components: 33 · Dimensions: 45.0 x 30.0 mm (13.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       6.75 |      N/A |      6.75 |   0+  3+  0 |        0 |          0 |  1000 |       364 |    17589.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/autohat-board_autohat-rig/unrouted.dsn)

Size: 80.2 kB · Layers: 2 · Nets: 75 · Components: 96 · Dimensions: 75.4 x 114.0 mm (85.96 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      57.13 |      N/A |     57.13 |   0+  5+  0 |        0 |          8 |   994 |       598 |   221293.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/autohat-board_inverted-usd-adapter/unrouted.dsn)

Size: 3.9 kB · Layers: 2 · Nets: 0 · Components: 2 · Dimensions: 17.01 x 34.97 mm (5.95 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.80 |      N/A |      0.80 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/autohat-board_usd-adapter/unrouted.dsn)

Size: 3.9 kB · Layers: 2 · Nets: 0 · Components: 2 · Dimensions: 17.01 x 34.97 mm (5.95 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.03 |      N/A |      4.03 |   0+  1+  0 |        2 |          0 |   750 |       352 |     4934.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Avem_Hardware_Avem_demo/unrouted.dsn)

Size: 60.1 kB · Layers: 2 · Nets: 41 · Components: 36 · Dimensions: 73.46 x 40.77 mm (29.95 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      37.68 |      N/A |     37.68 |   0+  1+  0 |        1 |          0 |   987 |       528 |   143404.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/avr_frs_shield_avr_frs/unrouted.dsn)

Size: 32.6 kB · Layers: 2 · Nets: 35 · Components: 34 · Dimensions: 68.58 x 53.34 mm (36.58 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.28 |      N/A |      2.28 |   0+  2+  0 |        0 |          0 |  1000 |       408 |     3789.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/avr_ledprojector_avr_ledprojection/unrouted.dsn)

Size: 22 kB · Layers: 2 · Nets: 0 · Components: 111 · Dimensions: 50.0 x 50.0 mm (25.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      44.09 |      N/A |     44.09 |   0+  1+  0 |        2 |          0 |   989 |       485 |   189449.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/avr_ledprojector_avr_ledprojection-0402/unrouted.dsn)

Size: 21.4 kB · Layers: 2 · Nets: 0 · Components: 91 · Dimensions: 25.4 x 34.54 mm (8.77 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     105.28 |      N/A |    105.28 |   0+  1+  0 |        2 |          2 |   986 |       542 |   355707.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/avr-divecomputer_dc/unrouted.dsn)

Size: 38 kB · Layers: 2 · Nets: 0 · Components: 60 · Dimensions: 101.6 x 96.52 mm (98.06 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      17.63 |      N/A |     17.63 |   0+  4+  0 |        0 |          0 |  1000 |       522 |    48354.4 |    5 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/avr-fuser-32_adapter/unrouted.dsn)

Size: 21.2 kB · Layers: 2 · Nets: 0 · Components: 19 · Dimensions: 159.38 x 79.38 mm (126.52 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     125.62 |      N/A |    125.62 |   0+  1+  0 |        1 |          0 |   994 |       643 |   361966.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/avr-fuser-32_hvpp/unrouted.dsn)

Size: 54.8 kB · Layers: 2 · Nets: 0 · Components: 70 · Dimensions: 71.75 x 99.69 mm (71.53 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      46.66 |      N/A |     46.66 |   0+  1+  0 |        2 |          0 |   985 |       513 |   164764.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/AVR-ISP_level-shifter_AVR-ISP_level-shifter/unrouted.dsn)

Size: 11.8 kB · Layers: 2 · Nets: 4 · Components: 27 · Dimensions: 44.0 x 20.0 mm (8.8 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      10.37 |      N/A |     10.37 |   0+  1+  0 |        1 |          0 |   985 |       552 |    42146.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/AVR-ISP_pogo-plug_1.27mm_AVR-ISP_pogo-plug_1.27mm/unrouted.dsn)

Size: 7.3 kB · Layers: 2 · Nets: 0 · Components: 4 · Dimensions: 12.0 x 19.0 mm (2.28 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      11.49 |      N/A |     11.49 |   0+  2+  0 |        0 |         14 |   844 |       340 |    18749.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/AVR-Playground_hello_world/unrouted.dsn)

Size: 26.5 kB · Layers: 2 · Nets: 31 · Components: 4 · Dimensions: 50.8 x 52.07 mm (26.45 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.99 |      N/A |      1.99 |   0+  1+  0 |        1 |          0 |   800 |        35 |      680.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/AVR-ZIF-Programmer_AVR-ZIF-Prog/unrouted.dsn)

Size: 50.6 kB · Layers: 2 · Nets: 36 · Components: 35 · Dimensions: 96.52 x 76.2 mm (73.55 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       8.39 |      N/A |      8.39 |   0+  5+  0 |        0 |          0 |  1000 |       378 |    19381.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/AzizLight_AzizLight/unrouted.dsn)

Size: 18.7 kB · Layers: 2 · Nets: 27 · Components: 55 · Dimensions: 70.1 x 29.21 mm (20.48 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      10.59 |      N/A |     10.59 |   0+  4+  0 |        0 |          0 |  1000 |       506 |    31115.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/badge2015_badge2015/unrouted.dsn)

Size: 35.1 kB · Layers: 2 · Nets: 47 · Components: 57 · Dimensions: 38.1 x 0.0 mm (0.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      82.61 |      N/A |     82.61 |   0+  1+  0 |        4 |          0 |   963 |       493 |   277276.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/badge2016_Badge_init/unrouted.dsn)

Size: 59.8 kB · Layers: 2 · Nets: 7 · Components: 650 · Dimensions: 100.0 x 57.4 mm (57.4 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      90.12 |      N/A |     90.12 |   0+  2+  0 |        0 |         17 |   960 |       483 |   326973.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/balena-rover-wide-hat_resin-rover/unrouted.dsn)

Size: 117.5 kB · Layers: 2 · Nets: 75 · Components: 116 · Dimensions: 85.0 x 58.0 mm (49.3 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      48.99 |      N/A |     48.99 |   0+  5+  0 |        0 |          6 |   995 |       550 |   173310.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Baofeng-Interface_BaofengInterface/unrouted.dsn)

Size: 18.9 kB · Layers: 2 · Nets: 2 · Components: 69 · Dimensions: 25.4 x 25.4 mm (6.45 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      20.99 |      N/A |     20.99 |   0+  1+  0 |        0 |          4 |   989 |       445 |    91256.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Baofeng-Interface_BaofengInterfaceIsolated/unrouted.dsn)

Size: 32.2 kB · Layers: 2 · Nets: 9 · Components: 144 · Dimensions: 39.12 x 44.7 mm (17.49 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       9.34 |      N/A |      9.34 |   0+  2+  0 |        0 |          0 |  1000 |       456 |    22862.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/basic_esp_board_basic_esp_board/unrouted.dsn)

Size: 28.4 kB · Layers: 2 · Nets: 5 · Components: 27 · Dimensions: 66.94 x 44.45 mm (29.75 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      15.85 |      N/A |     15.85 |   0+ 13+  0 |        0 |          0 |  1000 |       478 |    45724.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/battmanpi_battmanpi/unrouted.dsn)

Size: 35.7 kB · Layers: 2 · Nets: 0 · Components: 80 · Dimensions: 65.0 x 56.0 mm (36.4 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     160.22 |      N/A |    160.22 |   0+  1+  0 |        1 |         12 |   978 |       539 |   184694.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/BB-PWR-3608_BB-PWR-3608_revA/unrouted.dsn)

Size: 13.9 kB · Layers: 2 · Nets: 7 · Components: 18 · Dimensions: 15.19 x 11.75 mm (1.78 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       5.12 |      N/A |      5.12 |   0+  1+  0 |        0 |         17 |   869 |       420 |    15501.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/BB-PWR-8009_BB-PWR-8009_revA/unrouted.dsn)

Size: 18.9 kB · Layers: 2 · Nets: 5 · Components: 17 · Dimensions: 11.43 x 11.43 mm (1.31 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       6.68 |      N/A |      6.68 |   0+  2+  0 |        0 |         18 |   843 |       420 |    24491.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/BB-PWR-8113_BB-PWR-8113_revA/unrouted.dsn)

Size: 24.4 kB · Layers: 2 · Nets: 7 · Components: 28 · Dimensions: 19.69 x 13.34 mm (2.63 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       7.05 |      N/A |      7.05 |   0+  1+  0 |        1 |         15 |   882 |       556 |    14128.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/BeaconBuddy_BeaconBuddy/unrouted.dsn)

Size: 33 kB · Layers: 2 · Nets: 37 · Components: 112 · Dimensions: 25.4 x 27.94 mm (7.1 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     193.67 |      N/A |    193.67 |   0+  1+  0 |        1 |        141 |   841 |       500 |   473070.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/beast-phat_beast-phat/unrouted.dsn)

Size: 30.7 kB · Layers: 2 · Nets: 40 · Components: 19 · Dimensions: 65.0 x 69.5 mm (45.17 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.51 |      N/A |      3.51 |   0+  6+  0 |        0 |          0 |  1000 |       396 |     8195.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bee-light-measurement-matrix_bee-light-measurement-matrix/unrouted.dsn)

Size: 39.8 kB · Layers: 2 · Nets: 32 · Components: 54 · Dimensions: 59.69 x 65.02 mm (38.81 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      24.86 |      N/A |     24.86 |   0+ 18+  0 |        1 |          0 |   994 |       625 |    88841.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/beer-gauge_beer-gauge/unrouted.dsn)

Size: 48.4 kB · Layers: 4 · Nets: 30 · Components: 74 · Dimensions: 69.85 x 95.25 mm (66.53 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     142.03 |      N/A |    142.03 |   0+  1+  0 |        1 |         25 |   969 |       868 |   554904.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/beer-gauge_sensorboard/unrouted.dsn)

Size: 13.1 kB · Layers: 2 · Nets: 2 · Components: 16 · Dimensions: 31.75 x 38.1 mm (12.1 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      18.11 |      N/A |     18.11 |   0+  1+  0 |        0 |         12 |   938 |       481 |    76265.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/beryl_rain_beryl_rain/unrouted.dsn)

Size: 14.6 kB · Layers: 2 · Nets: 5 · Components: 18 · Dimensions: 26.67 x 49.53 mm (13.21 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       5.89 |      N/A |      5.89 |   0+  4+  0 |        0 |          0 |  1000 |       429 |    10085.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/BGM111-External-Programmer_BGM111_Programmer/unrouted.dsn)

Size: 11.1 kB · Layers: 2 · Nets: 25 · Components: 4 · Dimensions: 31.98 x 21.77 mm (6.96 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.12 |      N/A |      2.12 |   0+  3+  0 |        0 |          0 |  1000 |       123 |     2380.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bikedar_bikedar/unrouted.dsn)

Size: 53.1 kB · Layers: 4 · Nets: 17 · Components: 52 · Dimensions: 50.0 x 50.0 mm (25.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      32.18 |      N/A |     32.18 |   0+  5+  0 |        0 |          6 |   991 |       575 |    91724.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/BirdAttractor_BirdAttractor_RevA/unrouted.dsn)

Size: 22.5 kB · Layers: 2 · Nets: 17 · Components: 15 · Dimensions: 48.26 x 48.26 mm (23.29 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       5.75 |      N/A |      5.75 |   0+  1+  0 |        0 |          2 |   987 |       631 |    19129.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/BirdAttractor_BirdAttractor_RevC/unrouted.dsn)

Size: 56.6 kB · Layers: 2 · Nets: 11 · Components: 45 · Dimensions: 76.2 x 53.34 mm (40.65 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       9.90 |      N/A |      9.90 |   0+  3+  0 |        0 |          0 |  1000 |       369 |    24082.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/BirthdayCakeKeyboard_10Key/unrouted.dsn)

Size: 34.6 kB · Layers: 2 · Nets: 50 · Components: 65 · Dimensions: 73.0 x 102.5 mm (74.83 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      71.14 |      N/A |     71.14 |   0+  8+  0 |        0 |          0 |  1000 |       565 |   176422.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Biscay_Blueeye_mcu/unrouted.dsn)

Size: 22.4 kB · Layers: 2 · Nets: 14 · Components: 19 · Dimensions: 50.0 x 50.0 mm (25.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      15.40 |      N/A |     15.40 |   0+  7+  0 |        0 |          0 |  1000 |       392 |    26190.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Biscay_Blueeye_sipm/unrouted.dsn)

Size: 34.3 kB · Layers: 4 · Nets: 84 · Components: 145 · Dimensions: 50.0 x 50.0 mm (25.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     180.39 |      N/A |    180.39 |   0+  1+  0 |        4 |          0 |   987 |       612 |   666415.1 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Biscay_Blueeye_sipm-comp/unrouted.dsn)

Size: 37.7 kB · Layers: 2 · Nets: 12 · Components: 57 · Dimensions: 50.0 x 50.0 mm (25.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      98.99 |      N/A |     98.99 |   0+ 18+  0 |        1 |          0 |   995 |       572 |   381616.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Biscay_Blueeye_sipm-fpga/unrouted.dsn)

Size: 31.9 kB · Layers: 2 · Nets: 6 · Components: 24 · Dimensions: 50.0 x 50.0 mm (25.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      61.90 |      N/A |     61.90 |   0+  9+  0 |        2 |          0 |   987 |       529 |   233124.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bishboria_ErgoDox/unrouted.dsn)

Size: 38.2 kB · Layers: 2 · Nets: 0 · Components: 65 · Dimensions: 182.84 x 158.87 mm (290.48 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     764.89 |      N/A |    764.89 |   0+  1+  0 |       23 |         36 |   915 |       679 |  1433707.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/BITxo_BITxo/unrouted.dsn)

Size: 20.7 kB · Layers: 2 · Nets: 7 · Components: 30 · Dimensions: 73.39 x 62.61 mm (45.95 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.85 |      N/A |      2.85 |   0+  2+  0 |        0 |          2 |   990 |       132 |     2611.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/blackmagic-isolated_mmp/unrouted.dsn)

Size: 27.2 kB · Layers: 2 · Nets: 39 · Components: 39 · Dimensions: 15.25 x 50.0 mm (7.62 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     301.66 |      N/A |    301.66 |   0+  1+  0 |        0 |          7 |   985 |       553 |   870744.9 |    1 / 2 | 4     |


### Fixture: [unrouted.dsn](../fixtures/PCBench/BLDC-controller_BLDC_controller/unrouted.dsn)

Size: 51.6 kB · Layers: 4 · Nets: 20 · Components: 262 · Dimensions: 49.0 x 32.0 mm (15.68 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     555.83 |      N/A |    555.83 |   0+  1+  0 |       16 |       1007 |   506 |       764 |  2037883.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bldc-gimbal-1d_gimbal-board/unrouted.dsn)

Size: 23.1 kB · Layers: 2 · Nets: 12 · Components: 34 · Dimensions: 81.0 x 25.0 mm (20.25 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      11.49 |      N/A |     11.49 |   0+  4+  0 |        0 |          0 |  1000 |       450 |    33060.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bldc-gimbal-1d_r1_gimbal-board/unrouted.dsn)

Size: 25.8 kB · Layers: 2 · Nets: 12 · Components: 34 · Dimensions: 79.0 x 45.0 mm (35.55 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       7.30 |      N/A |      7.30 |   0+  3+  0 |        0 |          0 |  1000 |       325 |    21502.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Blink-Eras_AVR_ISP_Pogo/unrouted.dsn)

Size: 4.7 kB · Layers: 2 · Nets: 0 · Components: 4 · Dimensions: 17.78 x 22.86 mm (4.06 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.57 |      N/A |      0.57 |   0+  3+  0 |        0 |          0 |  1000 |         0 |        0.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Blink-Eras_Blink Eras/unrouted.dsn)

Size: 13.6 kB · Layers: 2 · Nets: 8 · Components: 16 · Dimensions: 27.94 x 20.32 mm (5.68 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      15.12 |      N/A |     15.12 |   0+  1+  0 |        0 |          6 |   961 |       496 |    51681.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/blink-errr_blink-errr/unrouted.dsn)

Size: 21.3 kB · Layers: 2 · Nets: 5 · Components: 10 · Dimensions: 17.0 x 0.0 mm (0.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.29 |      N/A |      1.29 |   0+  2+  0 |        0 |          0 |  1000 |       104 |     1103.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/blinktronicator_.kicad_pcb/unrouted.dsn)

Size: 15.8 kB · Layers: 2 · Nets: 0 · Components: 37 · Dimensions: 23.57 x 23.55 mm (5.55 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      67.97 |      N/A |     67.97 |   0+  1+  0 |        2 |          2 |   968 |       651 |   215846.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/blinky-badge_blinky/unrouted.dsn)

Size: 21.3 kB · Layers: 2 · Nets: 10 · Components: 18 · Dimensions: 40.0 x 39.88 mm (15.95 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      61.12 |      N/A |     61.12 |   0+  1+  0 |        0 |        162 |   655 |       441 |   282472.7 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/BlueBerry-Zero_blueberry/unrouted.dsn)

Size: 34.5 kB · Layers: 2 · Nets: 63 · Components: 50 · Dimensions: 65.0 x 30.0 mm (19.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      42.91 |      N/A |     42.91 |   0+  1+  0 |        1 |          8 |   976 |       517 |   173659.1 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/BML-Badges_BML_01/unrouted.dsn)

Size: 21.2 kB · Layers: 2 · Nets: 0 · Components: 21 · Dimensions: 43.18 x 45.72 mm (19.74 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.99 |      N/A |      0.99 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/BML-Badges_BML-Badges/unrouted.dsn)

Size: 22.8 kB · Layers: 2 · Nets: 9 · Components: 21 · Dimensions: 91.44 x 38.1 mm (34.84 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.29 |      N/A |      1.29 |   0+  3+  0 |        0 |          0 |  1000 |       160 |     1157.7 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bms-8s50-ic_bms-8s50-ic/unrouted.dsn)

Size: 118.8 kB · Layers: 2 · Nets: 66 · Components: 168 · Dimensions: 110.0 x 60.0 mm (66.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     902.90 |      N/A |    902.90 |   0+  8+  0 |       42 |        128 |   850 |       684 |  2304694.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/BMS-bq76940_EvaluationsBoard/unrouted.dsn)

Size: 33.8 kB · Layers: 2 · Nets: 0 · Components: 134 · Dimensions: 67.94 x 75.57 mm (51.34 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      74.73 |      N/A |     74.73 |   0+ 18+  0 |        1 |          3 |   994 |       560 |   258712.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bmw-ibus-bluetooth_bmw_bt_cdcemu_analog/unrouted.dsn)

Size: 70.7 kB · Layers: 2 · Nets: 18 · Components: 51 · Dimensions: 49.0 x 30.0 mm (14.7 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      14.44 |      N/A |     14.44 |   0+  1+  0 |        1 |          4 |   978 |       564 |    59052.7 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bmw-ibus-bluetooth_bmw_bt_cdcemu_digital/unrouted.dsn)

Size: 48 kB · Layers: 2 · Nets: 34 · Components: 58 · Dimensions: 60.0 x 40.0 mm (24.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       9.55 |      N/A |      9.55 |   0+  4+  0 |        0 |          4 |   994 |       389 |    33480.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/board_armjtag_pmod_compatible_armjtag-pmod/unrouted.dsn)

Size: 12.5 kB · Layers: 2 · Nets: 3 · Components: 4 · Dimensions: 39.88 x 20.32 mm (8.1 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.03 |      N/A |      1.03 |   0+  2+  0 |        0 |          0 |  1000 |        19 |     1556.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Board-RZA1L_BoardRZA1/unrouted.dsn)

Size: 108 kB · Layers: 4 · Nets: 18 · Components: 154 · Dimensions: 85.47 x 79.88 mm (68.27 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     903.16 |      N/A |    903.16 |   0+  4+  0 |      131 |         32 |   727 |       773 |  2380387.0 |    3 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/boards_shift-register-demo-v2/unrouted.dsn)

Size: 51.5 kB · Layers: 2 · Nets: 19 · Components: 36 · Dimensions: 104.14 x 35.56 mm (37.03 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.91 |      N/A |      3.91 |   0+  3+  0 |        0 |          0 |  1000 |       352 |     6761.7 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/boatcontrol_CommonCathode60A/unrouted.dsn)

Size: 29.7 kB · Layers: 4 · Nets: 0 · Components: 18 · Dimensions: 153.0 x 114.0 mm (174.42 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      44.05 |      N/A |     44.05 |   0+  1+  0 |       98 |        256 |     0 |       545 |   150428.1 |    3 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/boatcontrol_NonLatchingNO30A/unrouted.dsn)

Size: 41 kB · Layers: 4 · Nets: 32 · Components: 27 · Dimensions: 153.0 x 114.0 mm (174.42 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     108.37 |      N/A |    108.37 |   0+  1+  0 |       21 |          0 |   756 |       610 |   306591.3 |    3 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bobc_control_panel/unrouted.dsn)

Size: 24.3 kB · Layers: 2 · Nets: 0 · Components: 35 · Dimensions: 91.0 x 73.0 mm (66.43 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      11.37 |      N/A |     11.37 |   0+  7+  0 |        0 |          0 |  1000 |       360 |    23641.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bobc_LCD-panel-adapter-lvc/unrouted.dsn)

Size: 12.1 kB · Layers: 2 · Nets: 0 · Components: 13 · Dimensions: 40.64 x 48.26 mm (19.61 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      21.57 |      N/A |     21.57 |   0+  1+  0 |        2 |          0 |   951 |       461 |    58859.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bobc_led_clock/unrouted.dsn)

Size: 60.7 kB · Layers: 2 · Nets: 0 · Components: 65 · Dimensions: 100.0 x 50.0 mm (50.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      38.21 |      N/A |     38.21 |   0+  7+  0 |        0 |          2 |   997 |       584 |   119157.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bobc_matrix_clock/unrouted.dsn)

Size: 20.7 kB · Layers: 2 · Nets: 93 · Components: 36 · Dimensions: 100.0 x 98.0 mm (98.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     274.71 |      N/A |    274.71 |   0+  1+  0 |       16 |          0 |   913 |       576 |   821937.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bobc_mbeduinopresso/unrouted.dsn)

Size: 49.4 kB · Layers: 2 · Nets: 0 · Components: 37 · Dimensions: 100.0 x 87.0 mm (87.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      57.19 |      N/A |     57.19 |   0+ 11+  0 |        0 |          0 |  1000 |       595 |   172136.7 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bobc_MS-F100/unrouted.dsn)

Size: 32.8 kB · Layers: 2 · Nets: 0 · Components: 33 · Dimensions: 50.8 x 17.78 mm (9.03 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     135.46 |      N/A |    135.46 |   0+  1+  0 |       14 |          5 |   866 |       627 |   432228.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Box0-hv-analog-breakoutboard_breakout/unrouted.dsn)

Size: 24.2 kB · Layers: 2 · Nets: 28 · Components: 57 · Dimensions: 70.0 x 50.0 mm (35.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     124.66 |      N/A |    124.66 |   0+  1+  0 |      134 |         12 |   120 |       505 |   321484.3 |    3 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bpnode-bb_BPnode-BB/unrouted.dsn)

Size: 18 kB · Layers: 2 · Nets: 1 · Components: 10 · Dimensions: 19.81 x 49.28 mm (9.76 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      10.90 |      N/A |     10.90 |   0+  1+  0 |        1 |          2 |   967 |       445 |    30773.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/breakout-boards_50-to-100/unrouted.dsn)

Size: 4.6 kB · Layers: 2 · Nets: 0 · Components: 2 · Dimensions: 15.24 x 17.78 mm (2.71 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.92 |      N/A |      1.92 |   0+  4+  0 |        0 |          0 |  1000 |       247 |     1269.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/breakout-boards_avr-isp-x2/unrouted.dsn)

Size: 3.3 kB · Layers: 2 · Nets: 6 · Components: 2 · Dimensions: 8.89 x 11.43 mm (1.02 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.97 |      N/A |      4.97 |   0+  1+  0 |        0 |         16 |   467 |       203 |     3595.7 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/breakout-boards_esp8266-jtag/unrouted.dsn)

Size: 11.8 kB · Layers: 2 · Nets: 2 · Components: 14 · Dimensions: 30.48 x 28.45 mm (8.67 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.14 |      N/A |      4.14 |   0+  6+  0 |        0 |          0 |  1000 |       243 |    11305.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/breakout-boards_swd-and-uart/unrouted.dsn)

Size: 4.8 kB · Layers: 2 · Nets: 4 · Components: 3 · Dimensions: 12.7 x 25.4 mm (3.23 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.91 |      N/A |      1.91 |   0+  6+  0 |        0 |          0 |  1000 |       151 |     1201.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/breakout-boards_swd-to-wires/unrouted.dsn)

Size: 5.2 kB · Layers: 2 · Nets: 1 · Components: 2 · Dimensions: 12.7 x 13.97 mm (1.77 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.20 |      N/A |      3.20 |   0+  1+  0 |        1 |          0 |   889 |       187 |     5624.2 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/breakout-boards_usb-5v-3v3/unrouted.dsn)

Size: 8.6 kB · Layers: 2 · Nets: 5 · Components: 10 · Dimensions: 25.0 x 18.0 mm (4.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.60 |      N/A |      4.60 |   0+  1+  0 |        0 |          3 |   974 |       367 |     7995.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bristle_bot_light_follow_bristle_bot/unrouted.dsn)

Size: 24.5 kB · Layers: 2 · Nets: 0 · Components: 17 · Dimensions: 50.04 x 35.05 mm (17.54 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.94 |      N/A |      2.94 |   0+  1+  0 |        1 |          0 |   952 |       308 |     3290.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Brushless_ESC_Brushless_ESC/unrouted.dsn)

Size: 40.2 kB · Layers: 2 · Nets: 26 · Components: 77 · Dimensions: 63.0 x 36.0 mm (22.68 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      28.14 |      N/A |     28.14 |   0+  6+  0 |        0 |          0 |  1000 |       526 |   110255.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/BrushlessESC_esc/unrouted.dsn)

Size: 41 kB · Layers: 2 · Nets: 29 · Components: 79 · Dimensions: 50.04 x 60.45 mm (30.25 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     302.23 |      N/A |    302.23 |   0+  1+  0 |        0 |       1368 |   372 |       732 |  1195882.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bt-tnc_bttnc2/unrouted.dsn)

Size: 63.5 kB · Layers: 2 · Nets: 95 · Components: 105 · Dimensions: 70.0 x 40.01 mm (28.01 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     302.61 |      N/A |    302.61 |   0+  1+  0 |        0 |         14 |   988 |       599 |  1174683.5 |    3 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bt-tnc_tnc/unrouted.dsn)

Size: 38.7 kB · Layers: 2 · Nets: 0 · Components: 55 · Dimensions: 56.57 x 31.0 mm (17.54 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      46.97 |      N/A |     46.97 |   0+  1+  0 |        2 |          2 |   978 |       512 |   173382.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/BUF-DiffToSE-ADAU1966_BUF-DiffToSE-ADAU1966/unrouted.dsn)

Size: 30.6 kB · Layers: 2 · Nets: 64 · Components: 165 · Dimensions: 100.0 x 100.0 mm (100.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      37.74 |      N/A |     37.74 |   0+  4+  0 |        0 |          0 |  1000 |       463 |   152014.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bullion_bullion/unrouted.dsn)

Size: 36.9 kB · Layers: 2 · Nets: 38 · Components: 21 · Dimensions: 45.14 x 33.54 mm (15.14 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      73.93 |      N/A |     73.93 |   0+  1+  0 |       18 |          0 |   609 |       418 |   183204.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bumps_bumps/unrouted.dsn)

Size: 86.3 kB · Layers: 2 · Nets: 71 · Components: 138 · Dimensions: 111.12 x 65.41 mm (72.68 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     259.96 |      N/A |    259.96 |   0+  1+  0 |        4 |        520 |   745 |       722 |   903572.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/busblaster-to-swd_busblaster-to-swd/unrouted.dsn)

Size: 14.5 kB · Layers: 2 · Nets: 2 · Components: 7 · Dimensions: 22.0 x 35.0 mm (7.7 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.63 |      N/A |      2.63 |   0+  3+  0 |        0 |          0 |  1000 |       183 |     2406.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bypass_crossmix_bypass_crossmix/unrouted.dsn)

Size: 26.5 kB · Layers: 2 · Nets: 33 · Components: 61 · Dimensions: 70.0 x 50.0 mm (35.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      39.37 |      N/A |     39.37 |   0+  1+  0 |        9 |          0 |   926 |       459 |   136930.2 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/CAL430FR_CAL430F/unrouted.dsn)

Size: 32.1 kB · Layers: 2 · Nets: 11 · Components: 30 · Dimensions: 36.0 x 36.0 mm (12.96 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     226.81 |      N/A |    226.81 |   0+  1+  0 |        4 |          0 |   957 |       540 |   629842.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/CAL430FR_CAL430F_watch/unrouted.dsn)

Size: 9.5 kB · Layers: 2 · Nets: 18 · Components: 8 · Dimensions: 36.0 x 45.5 mm (16.38 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.31 |      N/A |      4.31 |   0+  5+  0 |        0 |          0 |  1000 |       236 |     6097.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Camera-Modules_LG-G2-Camera-Shim/unrouted.dsn)

Size: 10.3 kB · Layers: 2 · Nets: 0 · Components: 4 · Dimensions: 18.54 x 9.65 mm (1.79 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      44.22 |      N/A |     44.22 |   0+  1+  0 |        4 |          0 |   902 |       456 |   144681.2 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Camera-Modules_LG-G2-G3-13M-Breakout/unrouted.dsn)

Size: 12.2 kB · Layers: 2 · Nets: 1 · Components: 8 · Dimensions: 45.72 x 68.58 mm (31.35 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      11.41 |      N/A |     11.41 |   0+  7+  0 |        0 |          0 |  1000 |       425 |    21853.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/can_firewall_hardware_CAN_Firewall/unrouted.dsn)

Size: 68.4 kB · Layers: 2 · Nets: 78 · Components: 80 · Dimensions: 68.0 x 58.0 mm (39.44 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     304.96 |      N/A |    304.96 |   0+  1+  0 |        0 |         18 |   984 |       587 |   902903.2 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/CANadapter_CANadapter/unrouted.dsn)

Size: 23.5 kB · Layers: 2 · Nets: 25 · Components: 28 · Dimensions: 93.98 x 22.86 mm (21.48 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      13.58 |      N/A |     13.58 |   0+  1+  0 |        3 |          4 |   944 |       440 |    49029.1 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/CapPCB_CapPcb/unrouted.dsn)

Size: 14.5 kB · Layers: 2 · Nets: 2 · Components: 7 · Dimensions: 26.42 x 12.7 mm (3.36 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes      |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :--------- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |        N/A |      N/A |       N/A |   0+  0+  0 |      N/A |        N/A |   N/A |         0 |        0.0 |    1 / 0 | LOAD ERROR |


### Fixture: [unrouted.dsn](../fixtures/PCBench/cap-soil-moisture-v2_soil-moisture2x4/unrouted.dsn)

Size: 41.7 kB · Layers: 2 · Nets: 15 · Components: 21 · Dimensions: 44.85 x 44.83 mm (20.11 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.40 |      N/A |      1.40 |   0+  2+  0 |        0 |          0 |  1000 |       176 |     1241.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/card_card/unrouted.dsn)

Size: 11.3 kB · Layers: 2 · Nets: 7 · Components: 10 · Dimensions: 55.0 x 85.0 mm (46.75 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      18.15 |      N/A |     18.15 |   0+  1+  0 |        4 |         21 |   744 |       481 |    49908.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/CATS_PiHat_v2/unrouted.dsn)

Size: 39.4 kB · Layers: 2 · Nets: 34 · Components: 36 · Dimensions: 64.0 x 55.0 mm (35.2 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      39.33 |      N/A |     39.33 |   0+  1+  0 |        6 |          0 |   941 |       498 |   155196.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/cat-trainer_feather32u4_mma8452_pcb/unrouted.dsn)

Size: 18.9 kB · Layers: 2 · Nets: 28 · Components: 11 · Dimensions: 56.0 x 57.0 mm (31.92 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       7.16 |      N/A |      7.16 |   0+  1+  0 |        1 |          4 |   936 |       477 |    11599.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/cat-trainer_teensy_base_pcb/unrouted.dsn)

Size: 41.7 kB · Layers: 2 · Nets: 20 · Components: 28 · Dimensions: 69.85 x 52.07 mm (36.37 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.82 |      N/A |      4.82 |   0+  3+  0 |        0 |          0 |  1000 |       468 |     6587.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/C-BISCUIT_buck-reg-5v/unrouted.dsn)

Size: 21.6 kB · Layers: 2 · Nets: 2 · Components: 32 · Dimensions: 77.6 x 31.8 mm (24.68 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.50 |      N/A |      3.50 |   0+  2+  0 |        0 |          0 |  1000 |       260 |     5922.2 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/C-BISCUIT_crowbar/unrouted.dsn)

Size: 14.7 kB · Layers: 2 · Nets: 3 · Components: 13 · Dimensions: 13.0 x 37.2 mm (4.84 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.85 |      N/A |      2.85 |   0+  3+  0 |        0 |          0 |  1000 |       115 |      892.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/cdi-tach_cdi-tach/unrouted.dsn)

Size: 77.8 kB · Layers: 2 · Nets: 18 · Components: 70 · Dimensions: 201.38 x 119.75 mm (241.15 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes      |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :--------- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |        N/A |      N/A |       N/A |   0+  0+  0 |      N/A |        N/A |   N/A |         0 |        0.0 |    1 / 0 | LOAD ERROR |


### Fixture: [unrouted.dsn](../fixtures/PCBench/cdm324_backpack_cdm324/unrouted.dsn)

Size: 14.8 kB · Layers: 2 · Nets: 13 · Components: 29 · Dimensions: 25.0 x 25.0 mm (6.25 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      18.79 |      N/A |     18.79 |   0+  1+  0 |        0 |         16 |   941 |       448 |    83818.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Cherry-Mx-Bitboard_Cherry Mx Bitboard/unrouted.dsn)

Size: 4.6 kB · Layers: 2 · Nets: 1 · Components: 12 · Dimensions: 19.05 x 19.05 mm (3.63 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.47 |      N/A |      0.47 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/chip_lcd_dip_CHIP_LCD_DIP/unrouted.dsn)

Size: 46.5 kB · Layers: 2 · Nets: 5 · Components: 26 · Dimensions: 41.4 x 61.09 mm (25.29 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     187.76 |      N/A |    187.76 |   0+  1+  0 |       33 |         41 |   695 |       729 |   578247.2 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ChirpHardware_chirp/unrouted.dsn)

Size: 24.4 kB · Layers: 4 · Nets: 38 · Components: 40 · Dimensions: 53.4 x 49.0 mm (26.17 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      57.27 |      N/A |     57.27 |   0+  1+  0 |        1 |          6 |   978 |       682 |   202057.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ciurlys_ciurlys/unrouted.dsn)

Size: 14.9 kB · Layers: 2 · Nets: 9 · Components: 17 · Dimensions: 41.0 x 12.0 mm (4.92 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      22.84 |      N/A |     22.84 |   0+  1+  0 |        7 |          4 |   771 |       554 |    69044.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/cjmcu2_CJMCU2/unrouted.dsn)

Size: 45.4 kB · Layers: 2 · Nets: 46 · Components: 68 · Dimensions: 57.78 x 57.15 mm (33.02 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     301.40 |      N/A |    301.40 |   0+ 11+  0 |       26 |          0 |   810 |       645 |   871973.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Class_D_Amp_class_D_ampl/unrouted.dsn)

Size: 83.5 kB · Layers: 2 · Nets: 59 · Components: 89 · Dimensions: 140.5 x 79.0 mm (111.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     103.19 |      N/A |    103.19 |   0+  1+  0 |       13 |          8 |   917 |       590 |   413747.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/cleanhawk250_cleanhawk250-pdb/unrouted.dsn)

Size: 112.1 kB · Layers: 2 · Nets: 35 · Components: 80 · Dimensions: 134.44 x 79.37 mm (106.71 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     252.41 |      N/A |    252.41 |   0+  1+  0 |       11 |         28 |   902 |       569 |   729276.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/clock_cb2/unrouted.dsn)

Size: 34.1 kB · Layers: 2 · Nets: 34 · Components: 41 · Dimensions: 110.49 x 38.1 mm (42.1 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      29.34 |      N/A |     29.34 |   0+  1+  0 |        5 |          4 |   949 |       505 |   114771.7 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/clock_lcdb4/unrouted.dsn)

Size: 12.9 kB · Layers: 2 · Nets: 2 · Components: 11 · Dimensions: 105.41 x 33.02 mm (34.81 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      13.15 |      N/A |     13.15 |   0+  1+  0 |        1 |          0 |   984 |       421 |    40535.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/clunet-switch1_switch/unrouted.dsn)

Size: 47.5 kB · Layers: 2 · Nets: 28 · Components: 36 · Dimensions: 0.0 x 0.0 mm (0.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      26.06 |      N/A |     26.06 |   0+  1+  0 |        1 |          6 |   968 |       464 |    71914.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/cnlohr_wiflier/unrouted.dsn)

Size: 27.2 kB · Layers: 2 · Nets: 0 · Components: 57 · Dimensions: 37.75 x 21.5 mm (8.12 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     241.16 |      N/A |    241.16 |   0+  1+  0 |       36 |         16 |   733 |       521 |   826542.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/cnlohr_wiflier_B/unrouted.dsn)

Size: 29.1 kB · Layers: 2 · Nets: 0 · Components: 61 · Dimensions: 37.75 x 21.5 mm (8.12 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     292.53 |      N/A |    292.53 |   0+  1+  0 |       31 |         19 |   780 |       554 |  1002562.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/cobwebb-junction-box_CobwebbJunctionBox/unrouted.dsn)

Size: 10.8 kB · Layers: 2 · Nets: 9 · Components: 8 · Dimensions: 150.0 x 50.0 mm (75.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.72 |      N/A |      0.72 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/CompactFlashBreakout_CompactFlashBreakout/unrouted.dsn)

Size: 12.1 kB · Layers: 4 · Nets: 18 · Components: 6 · Dimensions: 58.42 x 38.1 mm (22.26 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      10.77 |      N/A |     10.77 |   0+  8+  0 |        0 |          0 |  1000 |       311 |    23177.5 |    3 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/constant-current-h-bridge_constant_current_source/unrouted.dsn)

Size: 39.7 kB · Layers: 2 · Nets: 25 · Components: 46 · Dimensions: 0.0 x 0.0 mm (0.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      27.87 |      N/A |     27.87 |   0+  1+  0 |        2 |          0 |   983 |       447 |   115663.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/continuity-tester_continuity-tester/unrouted.dsn)

Size: 22.1 kB · Layers: 2 · Nets: 10 · Components: 14 · Dimensions: 25.4 x 25.4 mm (6.45 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       5.30 |      N/A |      5.30 |   0+  1+  0 |        1 |          0 |   967 |       376 |    14178.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/cookiecutter-xsproduct_{{cookiecutter.product_name}}/unrouted.dsn)

Size: 8.9 kB · Layers: 2 · Nets: 0 · Components: 6 · Dimensions: 49.53 x 20.32 mm (10.06 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.74 |      N/A |      2.74 |   0+  1+  0 |        1 |          0 |   937 |       283 |     3028.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/CoreOne-xCORE200-Original_CoreOne/unrouted.dsn)

Size: 103 kB · Layers: 4 · Nets: 133 · Components: 389 · Dimensions: 100.0 x 100.0 mm (100.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |    1807.96 |      N/A |   1807.96 |   0+  1+  0 |        2 |         33 |   989 |      1530 |  5588613.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/CPU-Power-Supply-Mod_CPU_Power_Supply_Mod/unrouted.dsn)

Size: 22.2 kB · Layers: 2 · Nets: 0 · Components: 38 · Dimensions: 0.0 x 0.0 mm (0.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       6.62 |      N/A |      6.62 |   0+  4+  0 |        0 |          0 |  1000 |       529 |    16264.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/crossover-schiit-stack_xover4schiit/unrouted.dsn)

Size: 6.2 kB · Layers: 2 · Nets: 0 · Components: 4 · Dimensions: 28.0 x 53.0 mm (14.84 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.49 |      N/A |      0.49 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/CubeSAT-Reaction-Wheel__autosave-GPIO to motor/unrouted.dsn)

Size: 10.8 kB · Layers: 2 · Nets: 28 · Components: 11 · Dimensions: 53.98 x 35.56 mm (19.2 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.47 |      N/A |      3.47 |   0+  4+  0 |        0 |          0 |  1000 |        52 |     4875.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/CubeSAT-Reaction-Wheel_Edison_Motor_Servo/unrouted.dsn)

Size: 6.8 kB · Layers: 2 · Nets: 23 · Components: 6 · Dimensions: 27.94 x 27.94 mm (7.81 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.66 |      N/A |      0.66 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Curryboard_Curryboard/unrouted.dsn)

Size: 40.6 kB · Layers: 2 · Nets: 11 · Components: 60 · Dimensions: 50.0 x 49.99 mm (25.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     301.52 |      N/A |    301.52 |   0+  1+  0 |        0 |         56 |   926 |       711 |  1276369.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/custom_cpu--ALU_custom_cpu--ALU/unrouted.dsn)

Size: 25.7 kB · Layers: 2 · Nets: 44 · Components: 25 · Dimensions: 100.0 x 60.0 mm (60.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      10.71 |      N/A |     10.71 |   0+  4+  0 |        0 |          4 |   990 |       452 |    19918.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/custom_cpu--register_custom_cpu--register/unrouted.dsn)

Size: 25.8 kB · Layers: 2 · Nets: 37 · Components: 47 · Dimensions: 100.0 x 63.0 mm (63.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      14.30 |      N/A |     14.30 |   0+  1+  0 |        0 |          4 |   989 |       581 |    65489.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/DA_Lamp_attiny44a_servo_i2c/unrouted.dsn)

Size: 10.9 kB · Layers: 2 · Nets: 9 · Components: 7 · Dimensions: 22.05 x 31.38 mm (6.92 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.46 |      N/A |      1.46 |   0+  2+  0 |        0 |          0 |  1000 |       207 |     1441.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/DAC-ADAU1966_DAC-ADAU1966/unrouted.dsn)

Size: 67.9 kB · Layers: 4 · Nets: 113 · Components: 306 · Dimensions: 100.0 x 100.0 mm (100.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     206.05 |      N/A |    206.05 |   0+  3+  0 |        0 |          0 |  1000 |       844 |   755896.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/DasBlinkinput_Das Blinkinput/unrouted.dsn)

Size: 18.5 kB · Layers: 4 · Nets: 13 · Components: 36 · Dimensions: 25.4 x 50.8 mm (12.9 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     121.29 |      N/A |    121.29 |   0+  1+  0 |        0 |          8 |   978 |       567 |   400978.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/data-manager_data-manager/unrouted.dsn)

Size: 55.4 kB · Layers: 2 · Nets: 49 · Components: 38 · Dimensions: 74.0 x 38.0 mm (28.12 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      35.89 |      N/A |     35.89 |   0+  1+  0 |        1 |          8 |   973 |       444 |   119001.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/DaWeather---Project__autosave-CarteDaWeather/unrouted.dsn)

Size: 16.6 kB · Layers: 2 · Nets: 0 · Components: 18 · Dimensions: 55.88 x 54.61 mm (30.52 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.43 |      N/A |      1.43 |   0+  2+  0 |        0 |          0 |  1000 |       124 |     1317.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/DC25_DC25/unrouted.dsn)

Size: 29.2 kB · Layers: 2 · Nets: 16 · Components: 28 · Dimensions: 101.6 x 55.88 mm (56.77 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      13.96 |      N/A |     13.96 |   0+  4+  0 |        0 |          0 |  1000 |       389 |    31282.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/dc25_spqr_badge_badge-kicad/unrouted.dsn)

Size: 86.4 kB · Layers: 2 · Nets: 124 · Components: 129 · Dimensions: 152.67 x 76.68 mm (117.07 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     303.76 |      N/A |    303.76 |   0+ 10+  0 |        3 |          4 |   986 |       785 |   918955.4 |    1 / 2 | 4     |


### Fixture: [unrouted.dsn](../fixtures/PCBench/decelerator4030_decelerator4030/unrouted.dsn)

Size: 106 kB · Layers: 2 · Nets: 0 · Components: 180 · Dimensions: 0.0 x 0.0 mm (0.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     904.23 |      N/A |    904.23 |   0+  1+  0 |      473 |          0 |   531 |       833 |  2606254.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Dekada_dekada/unrouted.dsn)

Size: 15.6 kB · Layers: 2 · Nets: 41 · Components: 43 · Dimensions: 49.8 x 33.0 mm (16.43 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     103.45 |      N/A |    103.45 |   0+  1+  0 |        0 |          8 |   986 |       561 |   222886.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Dekada_dekada_1210_example/unrouted.dsn)

Size: 15.9 kB · Layers: 2 · Nets: 41 · Components: 43 · Dimensions: 51.8 x 34.1 mm (17.66 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     106.09 |      N/A |    106.09 |   0+  1+  0 |        0 |          9 |   985 |       589 |   228195.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Dekada_dekada_TopoR_curves/unrouted.dsn)

Size: 15.4 kB · Layers: 2 · Nets: 41 · Components: 43 · Dimensions: 49.8 x 33.0 mm (16.43 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     106.21 |      N/A |    106.21 |   0+  1+  0 |        0 |          8 |   986 |       549 |   216421.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/denbit_basic/unrouted.dsn)

Size: 27.5 kB · Layers: 2 · Nets: 12 · Components: 21 · Dimensions: 50.0 x 50.0 mm (25.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       6.34 |      N/A |      6.34 |   0+  1+  0 |        0 |         12 |   944 |       460 |    20704.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/DerKnopf_digi-pot/unrouted.dsn)

Size: 22.1 kB · Layers: 2 · Nets: 22 · Components: 16 · Dimensions: 25.0 x 31.0 mm (7.75 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      26.57 |      N/A |     26.57 |   0+  1+  0 |        1 |          6 |   967 |       494 |    98558.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/DerKnopf_led-ring/unrouted.dsn)

Size: 43.7 kB · Layers: 2 · Nets: 0 · Components: 33 · Dimensions: 16.68 x 16.8 mm (2.8 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      29.66 |      N/A |     29.66 |   0+  8+  0 |        0 |          0 |  1000 |       452 |    69283.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/DerKnopf_power-supply/unrouted.dsn)

Size: 10.5 kB · Layers: 2 · Nets: 0 · Components: 14 · Dimensions: 55.25 x 22.86 mm (12.63 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.59 |      N/A |      4.59 |   0+  1+  0 |        0 |          4 |   967 |       435 |    14833.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/DerKnopf_remote-control/unrouted.dsn)

Size: 21.6 kB · Layers: 2 · Nets: 5 · Components: 13 · Dimensions: 13.0 x 0.0 mm (0.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.73 |      N/A |      1.73 |   0+  2+  0 |        0 |          1 |   990 |        39 |      505.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/deskbot_breakout/unrouted.dsn)

Size: 17 kB · Layers: 2 · Nets: 30 · Components: 14 · Dimensions: 26.67 x 53.98 mm (14.4 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       7.20 |      N/A |      7.20 |   0+  1+  0 |        2 |          0 |   941 |       484 |    18126.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/devttys0_IRis/unrouted.dsn)

Size: 19.9 kB · Layers: 2 · Nets: 13 · Components: 30 · Dimensions: 12.7 x 9.14 mm (1.16 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.70 |      N/A |      4.70 |   0+  3+  0 |        0 |          0 |  1000 |       304 |     5603.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/digital_clock_led_clock_3_and_4_digit/unrouted.dsn)

Size: 74 kB · Layers: 2 · Nets: 52 · Components: 138 · Dimensions: 152.41 x 101.59 mm (154.83 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      37.91 |      N/A |     37.91 |   0+  6+  0 |        0 |          0 |  1000 |       522 |   119268.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/digital_clock_led_clock_v1/unrouted.dsn)

Size: 64.8 kB · Layers: 2 · Nets: 58 · Components: 138 · Dimensions: 152.62 x 101.67 mm (155.17 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      33.37 |      N/A |     33.37 |   0+  6+  0 |        0 |          0 |  1000 |       398 |   111545.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/DiscoDanceFloorV1_BusTerminator/unrouted.dsn)

Size: 7.2 kB · Layers: 2 · Nets: 6 · Components: 8 · Dimensions: 18.61 x 17.02 mm (3.17 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.49 |      N/A |      1.49 |   0+  2+  0 |        0 |          0 |  1000 |        19 |      848.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/DiscoDanceFloorV1_DiscoDongle/unrouted.dsn)

Size: 12.9 kB · Layers: 2 · Nets: 25 · Components: 21 · Dimensions: 58.42 x 15.88 mm (9.28 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      60.57 |      N/A |     60.57 |   0+  1+  0 |        0 |          6 |   977 |       540 |   216566.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/disco-dongle_DiscoDongle/unrouted.dsn)

Size: 13.9 kB · Layers: 2 · Nets: 24 · Components: 22 · Dimensions: 49.4 x 15.8 mm (7.81 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      60.50 |      N/A |     60.50 |   0+  1+  0 |        0 |          4 |   985 |       539 |   222136.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/divergence_meter_dm_control/unrouted.dsn)

Size: 81.3 kB · Layers: 2 · Nets: 54 · Components: 107 · Dimensions: 89.0 x 47.0 mm (41.83 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     114.24 |      N/A |    114.24 |   0+  1+  0 |        8 |         24 |   951 |       597 |   452785.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/divergence_meter_dm_nixiebrd/unrouted.dsn)

Size: 41 kB · Layers: 2 · Nets: 34 · Components: 51 · Dimensions: 188.5 x 42.0 mm (79.17 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     100.51 |      N/A |    100.51 |   0+  1+  0 |        2 |          4 |   987 |       524 |   334522.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/DIYDAC_DIYDAC/unrouted.dsn)

Size: 5.6 kB · Layers: 2 · Nets: 16 · Components: 17 · Dimensions: 25.4 x 13.72 mm (3.48 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.83 |      N/A |      0.83 |   0+  1+  0 |        0 |          1 |   992 |         0 |        0.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/domotics_base-board-arranged/unrouted.dsn)

Size: 48.1 kB · Layers: 2 · Nets: 99 · Components: 115 · Dimensions: 271.99 x 163.99 mm (446.04 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     110.94 |      N/A |    110.94 |   0+  5+  0 |        0 |          4 |   998 |       731 |   301372.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/domotics_in-board/unrouted.dsn)

Size: 14.9 kB · Layers: 2 · Nets: 6 · Components: 14 · Dimensions: 60.0 x 80.0 mm (48.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       9.41 |      N/A |      9.41 |   0+  2+  0 |        0 |         56 |   671 |       328 |    29320.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/domotics_out-board/unrouted.dsn)

Size: 18.6 kB · Layers: 2 · Nets: 6 · Components: 22 · Dimensions: 60.0 x 80.0 mm (48.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      20.61 |      N/A |     20.61 |   0+  2+  0 |        0 |         56 |   776 |       492 |    81692.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/domotics_power-supply/unrouted.dsn)

Size: 28.6 kB · Layers: 2 · Nets: 24 · Components: 25 · Dimensions: 80.55 x 75.03 mm (60.44 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      39.27 |      N/A |     39.27 |   0+  2+  0 |        0 |        168 |   558 |       529 |   167548.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/domotics_relay-board/unrouted.dsn)

Size: 24.3 kB · Layers: 2 · Nets: 29 · Components: 26 · Dimensions: 60.0 x 80.0 mm (48.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       5.75 |      N/A |      5.75 |   0+  1+  0 |        1 |         52 |   807 |       464 |    16046.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/dorkyboard_keyboard/unrouted.dsn)

Size: 101.8 kB · Layers: 2 · Nets: 116 · Components: 433 · Dimensions: 436.82 x 114.55 mm (500.38 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |    1717.87 |      N/A |   1717.87 |   0+ 18+  0 |        5 |          0 |   994 |      1957 |  3426233.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/DoroidOscillo-Board_Android_Oscilloscope/unrouted.dsn)

Size: 64.4 kB · Layers: 4 · Nets: 39 · Components: 134 · Dimensions: 70.0 x 43.0 mm (30.1 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     302.74 |      N/A |    302.74 |   0+ 15+  0 |        5 |         42 |   957 |       842 |   917380.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/DPS-1200FB_Adapter_Adapter/unrouted.dsn)

Size: 37.7 kB · Layers: 2 · Nets: 2 · Components: 22 · Dimensions: 84.0 x 81.0 mm (68.04 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       7.71 |      N/A |      7.71 |   0+ 18+  0 |        1 |          0 |   992 |       462 |    23674.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/draco_draco/unrouted.dsn)

Size: 81.3 kB · Layers: 4 · Nets: 0 · Components: 172 · Dimensions: 47.5 x 62.0 mm (29.45 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     353.97 |      N/A |    353.97 |   0+  9+  0 |        0 |          6 |   997 |       886 |  1312436.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/drawduino_drawduino/unrouted.dsn)

Size: 36.5 kB · Layers: 2 · Nets: 6 · Components: 9 · Dimensions: 15.0 x 95.0 mm (14.25 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.19 |      N/A |      1.19 |   0+  2+  0 |        0 |          0 |  1000 |        15 |      743.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/DronPi_dronPi/unrouted.dsn)

Size: 77.3 kB · Layers: 2 · Nets: 103 · Components: 141 · Dimensions: 0.0 x 0.0 mm (0.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     611.07 |      N/A |    611.07 |   0+  1+  0 |       11 |         33 |   953 |       778 |  1950886.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/DSKY-alarm-panel-replica_DSKY_alarm/unrouted.dsn)

Size: 35.1 kB · Layers: 2 · Nets: 40 · Components: 88 · Dimensions: 141.83 x 150.11 mm (212.9 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      90.29 |      N/A |     90.29 |   0+ 18+  0 |        1 |          0 |   994 |       703 |   268782.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/DSP-ADAU1452_DSP-ADAU1452/unrouted.dsn)

Size: 81.3 kB · Layers: 4 · Nets: 116 · Components: 377 · Dimensions: 100.0 x 100.0 mm (100.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |    1810.29 |      N/A |   1810.29 |   0+  8+  0 |       32 |          0 |   957 |      1501 |  4601581.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/DualLM317BenchSupply_DualLM317BenchSupply/unrouted.dsn)

Size: 46.1 kB · Layers: 2 · Nets: 0 · Components: 52 · Dimensions: 99.06 x 99.69 mm (98.75 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      18.17 |      N/A |     18.17 |   0+  1+  0 |        0 |          8 |   982 |       516 |    89113.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/dust_sensor_dust_sensor/unrouted.dsn)

Size: 31.4 kB · Layers: 2 · Nets: 25 · Components: 32 · Dimensions: 50.0 x 45.0 mm (22.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      42.98 |      N/A |     42.98 |   0+  1+  0 |        1 |          0 |   987 |       432 |   141158.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/dustbox_Dustbox/unrouted.dsn)

Size: 14.8 kB · Layers: 2 · Nets: 13 · Components: 19 · Dimensions: 58.0 x 58.0 mm (33.64 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.79 |      N/A |      0.79 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/DustSensorShield_DustSensorShield/unrouted.dsn)

Size: 22.9 kB · Layers: 2 · Nets: 38 · Components: 13 · Dimensions: 80.01 x 53.34 mm (42.68 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      12.45 |      N/A |     12.45 |   0+  2+  0 |        0 |          6 |   929 |       528 |    36507.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/E202VAR-Natural-Radio-Receiver_e202var-vlf-radio-receiver/unrouted.dsn)

Size: 60.5 kB · Layers: 2 · Nets: 27 · Components: 51 · Dimensions: 55.88 x 81.28 mm (45.42 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      10.58 |      N/A |     10.58 |   0+  1+  0 |        1 |          0 |   989 |       601 |    36702.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/eBUS-Adapter_Groeger/unrouted.dsn)

Size: 22 kB · Layers: 2 · Nets: 8 · Components: 14 · Dimensions: 38.0 x 38.0 mm (14.44 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.03 |      N/A |      1.03 |   0+  2+  0 |        0 |          0 |  1000 |       167 |     1388.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/eBUS-Adapter_Henning/unrouted.dsn)

Size: 29 kB · Layers: 2 · Nets: 15 · Components: 21 · Dimensions: 68.0 x 38.0 mm (25.84 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.31 |      N/A |      1.31 |   0+  2+  0 |        0 |          0 |  1000 |        71 |     1662.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/edid-injector_edid-injector/unrouted.dsn)

Size: 27.4 kB · Layers: 2 · Nets: 40 · Components: 44 · Dimensions: 35.35 x 62.5 mm (22.09 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      70.72 |      N/A |     70.72 |   0+  1+  0 |        4 |         22 |   925 |       625 |   229999.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/eeg_brainboard_batteryv0/unrouted.dsn)

Size: 50.4 kB · Layers: 2 · Nets: 0 · Components: 65 · Dimensions: 86.36 x 54.61 mm (47.16 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     175.36 |      N/A |    175.36 |   0+  1+  0 |       10 |         10 |   924 |       713 |   539013.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/eeg_brainboard_wearable/unrouted.dsn)

Size: 42.6 kB · Layers: 4 · Nets: 11 · Components: 59 · Dimensions: 40.0 x 50.8 mm (20.32 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     221.07 |      N/A |    221.07 |   0+  1+  0 |       25 |          4 |   826 |       656 |   696440.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/eeg_brainboard_wearable_v2/unrouted.dsn)

Size: 66.1 kB · Layers: 4 · Nets: 39 · Components: 134 · Dimensions: 61.06 x 54.61 mm (33.34 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     303.10 |      N/A |    303.10 |   0+  7+  0 |       23 |          6 |   931 |       742 |  1043227.1 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/eeg_brainboardv0/unrouted.dsn)

Size: 62 kB · Layers: 4 · Nets: 29 · Components: 86 · Dimensions: 86.36 x 54.61 mm (47.16 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     301.45 |      N/A |    301.45 |   0+  1+  0 |        6 |         21 |   958 |       732 |   987762.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/eeg_brainboardv1/unrouted.dsn)

Size: 77.6 kB · Layers: 4 · Nets: 37 · Components: 116 · Dimensions: 86.36 x 54.61 mm (47.16 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     303.70 |      N/A |    303.70 |   0+  8+  0 |       27 |         21 |   906 |       702 |   969507.1 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/eeg_brainboardv2/unrouted.dsn)

Size: 65.1 kB · Layers: 4 · Nets: 27 · Components: 105 · Dimensions: 86.36 x 54.61 mm (47.16 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     302.27 |      N/A |    302.27 |   0+ 14+  0 |       15 |          0 |   945 |       693 |   920095.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/eeg_muscleboardv0/unrouted.dsn)

Size: 41.5 kB · Layers: 4 · Nets: 11 · Components: 54 · Dimensions: 33.17 x 36.58 mm (12.13 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     211.47 |      N/A |    211.47 |   0+  1+  0 |       18 |          0 |   873 |       640 |   722846.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Eggbot-Spherebot-polargraph-Controller_eggbot-spherebot-polargraph-controller/unrouted.dsn)

Size: 22.1 kB · Layers: 2 · Nets: 20 · Components: 17 · Dimensions: 83.82 x 43.18 mm (36.19 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.58 |      N/A |      4.58 |   0+  4+  0 |        0 |          0 |  1000 |       488 |     6286.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/eink-adapter_eink/unrouted.dsn)

Size: 41.2 kB · Layers: 2 · Nets: 5 · Components: 65 · Dimensions: 85.34 x 38.48 mm (32.84 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     293.86 |      N/A |    293.86 |   0+  1+  0 |       33 |         16 |   763 |       619 |   908120.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/elbsupply_elbsupply/unrouted.dsn)

Size: 66.1 kB · Layers: 2 · Nets: 62 · Components: 103 · Dimensions: 105.0 x 64.4 mm (67.62 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      53.37 |      N/A |     53.37 |   0+  6+  0 |        0 |          7 |   994 |       564 |   208729.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/elec_power_dispatch/unrouted.dsn)

Size: 88.2 kB · Layers: 2 · Nets: 58 · Components: 97 · Dimensions: 78.0 x 63.5 mm (49.53 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      87.06 |      N/A |     87.06 |   0+  1+  0 |        1 |         74 |   927 |       571 |   348994.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/elec_turbo_brushless/unrouted.dsn)

Size: 95 kB · Layers: 2 · Nets: 58 · Components: 98 · Dimensions: 70.0 x 50.0 mm (35.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     135.19 |      N/A |    135.19 |   0+ 18+  0 |        1 |          0 |   996 |       570 |   458482.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/elec-power-bms_OSBMS Balancer Rev1/unrouted.dsn)

Size: 28.5 kB · Layers: 2 · Nets: 53 · Components: 74 · Dimensions: 49.0 x 49.0 mm (24.01 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     162.90 |      N/A |    162.90 |   0+  1+  0 |        6 |          1 |   963 |       670 |   570234.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/elec-power-bms_rLoopPowerBms/unrouted.dsn)

Size: 34.5 kB · Layers: 2 · Nets: 41 · Components: 87 · Dimensions: 70.0 x 60.0 mm (42.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     239.69 |      N/A |    239.69 |   0+  1+  0 |        4 |          1 |   978 |       728 |   751298.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Electronics-MainBoard_MainBoard/unrouted.dsn)

Size: 81.3 kB · Layers: 2 · Nets: 32 · Components: 89 · Dimensions: 120.0 x 68.0 mm (81.6 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      81.76 |      N/A |     81.76 |   0+ 13+  0 |        0 |          6 |   996 |       659 |   328376.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/EncoderBoard_Enc_Pan_Led/unrouted.dsn)

Size: 22.3 kB · Layers: 2 · Nets: 19 · Components: 58 · Dimensions: 42.0 x 34.0 mm (14.28 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     123.22 |      N/A |    123.22 |   0+  1+  0 |        4 |          8 |   942 |       655 |   345005.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/epapercard_epapercard/unrouted.dsn)

Size: 27 kB · Layers: 2 · Nets: 29 · Components: 48 · Dimensions: 88.9 x 49.53 mm (44.03 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      99.16 |      N/A |     99.16 |   0+  1+  0 |        6 |          0 |   947 |       639 |   344902.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ErgodoneWithHole_ErgoDone/unrouted.dsn)

Size: 56.4 kB · Layers: 2 · Nets: 56 · Components: 71 · Dimensions: 182.84 x 158.87 mm (290.48 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     589.28 |      N/A |    589.28 |   0+  1+  0 |       20 |         32 |   926 |       713 |  1137693.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ESCPT_data/unrouted.dsn)

Size: 35.5 kB · Layers: 2 · Nets: 17 · Components: 58 · Dimensions: 76.2 x 95.25 mm (72.58 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      70.89 |      N/A |     70.89 |   0+  1+  0 |        1 |          4 |   989 |       523 |   252717.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ESP_BaPoTeSta_ESP_BaPoTeSta/unrouted.dsn)

Size: 29.1 kB · Layers: 2 · Nets: 19 · Components: 50 · Dimensions: 39.37 x 49.53 mm (19.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      37.04 |      N/A |     37.04 |   0+  1+  0 |        1 |          2 |   987 |       466 |   124038.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/esp_hat_esp_hat/unrouted.dsn)

Size: 38.1 kB · Layers: 2 · Nets: 14 · Components: 113 · Dimensions: 73.0 x 30.0 mm (21.9 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     214.09 |      N/A |    214.09 |   0+  1+  0 |       29 |        112 |   765 |       570 |   570536.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ESP_nRF_Relay_Relay_WiFi_nRF24/unrouted.dsn)

Size: 33.6 kB · Layers: 2 · Nets: 20 · Components: 15 · Dimensions: 50.8 x 50.8 mm (25.81 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       8.41 |      N/A |      8.41 |   0+  1+  0 |        1 |          0 |   972 |       417 |    20742.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/esp_toggl_togglbutton/unrouted.dsn)

Size: 10.4 kB · Layers: 2 · Nets: 9 · Components: 7 · Dimensions: 22.86 x 50.8 mm (11.61 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.09 |      N/A |      2.09 |   0+  1+  0 |        0 |          4 |   947 |       255 |     3436.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ESP_WiFiSwitch_WifiSwitch/unrouted.dsn)

Size: 13.7 kB · Layers: 2 · Nets: 15 · Components: 9 · Dimensions: 36.58 x 41.66 mm (15.24 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.17 |      N/A |      2.17 |   0+  3+  0 |        0 |          0 |  1000 |       120 |     3227.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ESP07-Breakout_ESP07-Breakout/unrouted.dsn)

Size: 28 kB · Layers: 2 · Nets: 6 · Components: 25 · Dimensions: 31.75 x 49.53 mm (15.73 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      12.95 |      N/A |     12.95 |   0+  1+  0 |        2 |          0 |   969 |       517 |    45716.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/esp12-appliance_mod_esp12-appliance-mod/unrouted.dsn)

Size: 24.6 kB · Layers: 2 · Nets: 22 · Components: 41 · Dimensions: 76.2 x 76.2 mm (58.06 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       5.44 |      N/A |      5.44 |   0+  4+  0 |        0 |          0 |  1000 |       360 |    12084.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/esp12-breakout_ESP12Breakout/unrouted.dsn)

Size: 7.1 kB · Layers: 2 · Nets: 20 · Components: 9 · Dimensions: 26.67 x 34.92 mm (9.31 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.03 |      N/A |      2.03 |   0+  4+  0 |        0 |          0 |  1000 |       171 |     3345.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ESP-12-breakout_ESP12E-breakout/unrouted.dsn)

Size: 22.3 kB · Layers: 2 · Nets: 7 · Components: 17 · Dimensions: 31.62 x 26.67 mm (8.43 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.90 |      N/A |      2.90 |   0+  3+  0 |        0 |          0 |  1000 |        47 |     3143.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ESP32-board_esp32_board/unrouted.dsn)

Size: 53.7 kB · Layers: 2 · Nets: 18 · Components: 53 · Dimensions: 32.0 x 91.0 mm (29.12 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     182.00 |      N/A |    182.00 |   0+  1+  0 |        4 |          4 |   963 |       547 |   337936.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/esp32-cantroller_EltekController/unrouted.dsn)

Size: 87.8 kB · Layers: 2 · Nets: 19 · Components: 36 · Dimensions: 65.0 x 37.5 mm (24.38 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes      |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :--------- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |        N/A |      N/A |       N/A |   0+  0+  0 |      N/A |        N/A |   N/A |         0 |        0.0 |    1 / 0 | LOAD ERROR |


### Fixture: [unrouted.dsn](../fixtures/PCBench/esp32-ethernet_esp32-ethernet/unrouted.dsn)

Size: 39.8 kB · Layers: 2 · Nets: 24 · Components: 40 · Dimensions: 54.0 x 40.0 mm (21.6 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      60.46 |      N/A |     60.46 |   0+  1+  0 |        2 |          4 |   976 |       511 |   234032.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/esp32-iot-uno_esp32-iot-uno-hw/unrouted.dsn)

Size: 72.3 kB · Layers: 2 · Nets: 38 · Components: 105 · Dimensions: 68.53 x 53.3 mm (36.53 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     303.13 |      N/A |    303.13 |   0+ 13+  0 |        9 |         12 |   947 |       664 |   888310.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ESP32-Module-Breakout_ESP32S-breakout/unrouted.dsn)

Size: 21 kB · Layers: 2 · Nets: 35 · Components: 9 · Dimensions: 49.33 x 26.44 mm (13.04 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       7.78 |      N/A |      7.78 |   0+  4+  0 |        0 |          0 |  1000 |       361 |    14891.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/esp32stack_esp32stack/unrouted.dsn)

Size: 31.3 kB · Layers: 2 · Nets: 34 · Components: 30 · Dimensions: 48.49 x 56.39 mm (27.34 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      80.28 |      N/A |     80.28 |   0+  1+  0 |        0 |          6 |   988 |       529 |   409692.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/esp8266_32x32panel_esp_12_f_595/unrouted.dsn)

Size: 20 kB · Layers: 2 · Nets: 0 · Components: 26 · Dimensions: 85.72 x 29.21 mm (25.04 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      44.30 |      N/A |     44.30 |   0+  1+  0 |        1 |          8 |   978 |       398 |   153696.4 |    3 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/esp8266_32x32panel_esp_12_f_595_ORDERED/unrouted.dsn)

Size: 20.1 kB · Layers: 2 · Nets: 4 · Components: 26 · Dimensions: 85.72 x 29.21 mm (25.04 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      46.53 |      N/A |     46.53 |   0+  1+  0 |        1 |          8 |   978 |       461 |   156831.8 |    3 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/esp8266_envmonitor_environment-monitor/unrouted.dsn)

Size: 19.1 kB · Layers: 2 · Nets: 4 · Components: 14 · Dimensions: 31.11 x 43.18 mm (13.43 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      11.62 |      N/A |     11.62 |   0+  1+  0 |        1 |          0 |   971 |       540 |    34829.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/esp8266_envmonitor_environment-monitor-1.2/unrouted.dsn)

Size: 21.9 kB · Layers: 2 · Nets: 20 · Components: 16 · Dimensions: 31.12 x 52.07 mm (16.2 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.96 |      N/A |      2.96 |   0+  3+  0 |        0 |          0 |  1000 |       187 |     2355.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/esp8266_envmonitor_environment-monitor-1.4/unrouted.dsn)

Size: 19.1 kB · Layers: 2 · Nets: 4 · Components: 14 · Dimensions: 31.11 x 43.18 mm (13.43 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      11.26 |      N/A |     11.26 |   0+  1+  0 |        1 |          0 |   971 |       560 |    35784.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/esp8266_esp-output/unrouted.dsn)

Size: 29 kB · Layers: 2 · Nets: 44 · Components: 70 · Dimensions: 158.6 x 51.8 mm (82.15 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      42.23 |      N/A |     42.23 |   0+  1+  0 |        3 |          0 |   974 |       564 |   146948.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/esp8266_link_test_esp_micro85-only/unrouted.dsn)

Size: 9.6 kB · Layers: 2 · Nets: 0 · Components: 20 · Dimensions: 12.45 x 15.11 mm (1.88 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      18.85 |      N/A |     18.85 |   0+  1+  0 |        5 |          5 |   857 |       478 |    65228.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/esp8266_link_test_esp12e-generics/unrouted.dsn)

Size: 9.1 kB · Layers: 2 · Nets: 0 · Components: 6 · Dimensions: 30.99 x 21.34 mm (6.61 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.24 |      N/A |      2.24 |   0+  3+  0 |        0 |          0 |  1000 |       328 |     3364.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/esp8266_network_speaker_esp_network_speaker/unrouted.dsn)

Size: 20 kB · Layers: 2 · Nets: 12 · Components: 50 · Dimensions: 24.13 x 36.32 mm (8.76 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     104.96 |      N/A |    104.96 |   0+  1+  0 |        0 |         10 |   980 |       594 |   472548.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/esp8266_wi07_3_adapter_esp/unrouted.dsn)

Size: 6.5 kB · Layers: 2 · Nets: 3 · Components: 5 · Dimensions: 22.1 x 21.46 mm (4.74 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.41 |      N/A |      1.41 |   0+  2+  0 |        0 |          0 |  1000 |       167 |      819.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/esp8266-12f-board_esp8266/unrouted.dsn)

Size: 18.1 kB · Layers: 2 · Nets: 1 · Components: 15 · Dimensions: 32.0 x 39.0 mm (12.48 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.19 |      N/A |      2.19 |   0+  2+  0 |        0 |          0 |  1000 |       276 |     3187.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/esp8266-external-temp-sensor_external-temp-sensor/unrouted.dsn)

Size: 16 kB · Layers: 2 · Nets: 2 · Components: 14 · Dimensions: 29.46 x 38.1 mm (11.22 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.52 |      N/A |      1.52 |   0+  2+  0 |        0 |          0 |  1000 |       115 |     1289.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ESP8266-MQTT-battery-monitor-hw_battery-monitor/unrouted.dsn)

Size: 24.3 kB · Layers: 2 · Nets: 15 · Components: 49 · Dimensions: 50.0 x 50.0 mm (25.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       8.00 |      N/A |      8.00 |   0+  5+  0 |        0 |          0 |  1000 |       521 |    23791.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ESP8266-WS2811-LEDs_ws2811controller_panels/unrouted.dsn)

Size: 6.3 kB · Layers: 2 · Nets: 0 · Components: 8 · Dimensions: 25.0 x 27.0 mm (6.75 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       5.26 |      N/A |      5.26 |   0+  1+  0 |        0 |          6 |   920 |       479 |    10061.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/espalarm_alarm/unrouted.dsn)

Size: 18.5 kB · Layers: 2 · Nets: 0 · Components: 39 · Dimensions: 85.6 x 85.6 mm (73.27 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      15.71 |      N/A |     15.71 |   0+  5+  0 |        0 |          0 |  1000 |       416 |    37346.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ESP-Breakout_ESP-Breakout/unrouted.dsn)

Size: 14.9 kB · Layers: 2 · Nets: 12 · Components: 17 · Dimensions: 50.8 x 20.32 mm (10.32 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.29 |      N/A |      3.29 |   0+  2+  0 |        0 |          0 |  1000 |        56 |     5877.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/espeverywhere__autosave-espeverywhere_breakout/unrouted.dsn)

Size: 7.2 kB · Layers: 2 · Nets: 0 · Components: 3 · Dimensions: 33.0 x 12.0 mm (3.96 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.18 |      N/A |      1.18 |   0+  2+  0 |        0 |          0 |  1000 |       119 |      828.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/espeverywhere_espeverywhere/unrouted.dsn)

Size: 31.9 kB · Layers: 2 · Nets: 27 · Components: 41 · Dimensions: 45.0 x 48.0 mm (21.6 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      15.43 |      N/A |     15.43 |   0+  6+  0 |        0 |          0 |  1000 |       368 |    40686.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ESPglobe_ESPglobe/unrouted.dsn)

Size: 12.1 kB · Layers: 2 · Nets: 3 · Components: 23 · Dimensions: 22.08 x 47.83 mm (10.56 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      34.84 |      N/A |     34.84 |   0+  1+  0 |        5 |          2 |   892 |       598 |    89248.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/espionage_esplight/unrouted.dsn)

Size: 21 kB · Layers: 2 · Nets: 16 · Components: 14 · Dimensions: 41.28 x 29.59 mm (12.21 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.09 |      N/A |      4.09 |   0+  2+  0 |        0 |          1 |   994 |       260 |     2307.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ESPkit-01_espkit-01/unrouted.dsn)

Size: 19.2 kB · Layers: 2 · Nets: 20 · Components: 24 · Dimensions: 20.0 x 35.7 mm (7.14 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      28.52 |      N/A |     28.52 |   0+  1+  0 |        3 |          6 |   933 |       478 |    94269.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/esp-leipa_esp-12/unrouted.dsn)

Size: 22.7 kB · Layers: 2 · Nets: 4 · Components: 9 · Dimensions: 26.67 x 44.45 mm (11.85 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.19 |      N/A |      4.19 |   0+  3+  0 |        0 |          0 |  1000 |       340 |     5763.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ESPLux_Board/unrouted.dsn)

Size: 29 kB · Layers: 2 · Nets: 18 · Components: 33 · Dimensions: 70.0 x 43.0 mm (30.1 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      95.93 |      N/A |     95.93 |   0+  2+  0 |        0 |          8 |   970 |       481 |   300424.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ESProto-One_ESProto/unrouted.dsn)

Size: 17.4 kB · Layers: 2 · Nets: 27 · Components: 32 · Dimensions: 50.0 x 40.0 mm (20.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.29 |      N/A |      3.29 |   0+  2+  0 |        0 |          0 |  1000 |       256 |     5033.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/esp-serial-terminal_esp-com/unrouted.dsn)

Size: 28.8 kB · Layers: 2 · Nets: 0 · Components: 27 · Dimensions: 66.23 x 34.56 mm (22.89 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       5.45 |      N/A |      5.45 |   0+  4+  0 |        0 |          0 |  1000 |       440 |    13798.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/EtherCAT_shield_v1_EtherCAT_shield_v1/unrouted.dsn)

Size: 49.8 kB · Layers: 2 · Nets: 90 · Components: 115 · Dimensions: 71.12 x 81.28 mm (57.81 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     301.93 |      N/A |    301.93 |   0+  5+  0 |       13 |         42 |   925 |       638 |   786069.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/EUC-VESC_electronics_BJT/unrouted.dsn)

Size: 67.6 kB · Layers: 4 · Nets: 93 · Components: 318 · Dimensions: 49.53 x 100.08 mm (49.57 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     383.71 |      N/A |    383.71 |   0+  1+  0 |       29 |        146 |   869 |       585 |  1343889.1 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/EUC-VESC_electronics_sept/unrouted.dsn)

Size: 77.5 kB · Layers: 4 · Nets: 102 · Components: 577 · Dimensions: 100.0 x 150.0 mm (150.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |    1571.89 |      N/A |   1571.89 |   0+  1+  0 |        8 |         28 |   982 |      1514 |  4088391.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/eurorack_headphones_eurorack_headphones/unrouted.dsn)

Size: 17.9 kB · Layers: 2 · Nets: 23 · Components: 30 · Dimensions: 97.5 x 41.5 mm (40.46 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      24.88 |      N/A |     24.88 |   0+  1+  0 |        0 |         12 |   966 |       436 |    98558.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/everled_everled/unrouted.dsn)

Size: 17.2 kB · Layers: 2 · Nets: 6 · Components: 9 · Dimensions: 25.0 x 30.0 mm (7.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.84 |      N/A |      2.84 |   0+  1+  0 |        0 |          6 |   940 |       207 |     5465.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ezusb-logicanalyzer_cypress_logic_analyzer/unrouted.dsn)

Size: 16.8 kB · Layers: 2 · Nets: 41 · Components: 13 · Dimensions: 35.56 x 40.64 mm (14.45 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.10 |      N/A |      3.10 |   0+  2+  0 |        0 |          0 |  1000 |       112 |     1376.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/f.60_keyboard/unrouted.dsn)

Size: 44.1 kB · Layers: 2 · Nets: 62 · Components: 129 · Dimensions: 285.75 x 95.25 mm (272.18 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      12.96 |      N/A |     12.96 |   0+  4+  0 |        0 |          0 |  1000 |       426 |    31045.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/f4fc_f4fc/unrouted.dsn)

Size: 54.6 kB · Layers: 4 · Nets: 63 · Components: 63 · Dimensions: 36.0 x 36.0 mm (12.96 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     304.41 |      N/A |    304.41 |   0+  8+  0 |       73 |         36 |   614 |       558 |   910100.3 |    3 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/fan_controller_fan_controller/unrouted.dsn)

Size: 29.3 kB · Layers: 2 · Nets: 31 · Components: 66 · Dimensions: 60.0 x 37.0 mm (22.2 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      10.63 |      N/A |     10.63 |   0+  5+  0 |        0 |          0 |  1000 |       429 |    31002.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/FaradayRF-Hardware_Faraday/unrouted.dsn)

Size: 61.5 kB · Layers: 4 · Nets: 29 · Components: 141 · Dimensions: 74.93 x 53.34 mm (39.97 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     902.82 |      N/A |    902.82 |   0+ 17+  0 |       50 |         35 |   836 |       676 |  2705081.0 |    3 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/fifogfx_c64cart/unrouted.dsn)

Size: 19 kB · Layers: 2 · Nets: 4 · Components: 6 · Dimensions: 58.42 x 80.01 mm (46.74 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     252.94 |      N/A |    252.94 |   0+  1+  0 |        3 |         44 |   887 |       528 |   326081.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/filament_extruder_extruder/unrouted.dsn)

Size: 30.8 kB · Layers: 2 · Nets: 8 · Components: 37 · Dimensions: 56.77 x 59.44 mm (33.74 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       5.79 |      N/A |      5.79 |   0+  3+  0 |        0 |          0 |  1000 |       548 |    15519.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/filament_extruder_sensor/unrouted.dsn)

Size: 11.2 kB · Layers: 2 · Nets: 3 · Components: 6 · Dimensions: 25.0 x 80.0 mm (20.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.71 |      N/A |      1.71 |   0+  2+  0 |        0 |          0 |  1000 |        15 |     1053.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/fingerprint-with-esp32_quet van tay/unrouted.dsn)

Size: 93.5 kB · Layers: 2 · Nets: 33 · Components: 9 · Dimensions: 68.53 x 53.3 mm (36.53 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.46 |      N/A |      2.46 |   0+  2+  0 |        0 |          0 |  1000 |       184 |     1398.2 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/firefly-jar_solar_lamp/unrouted.dsn)

Size: 26.3 kB · Layers: 2 · Nets: 9 · Components: 11 · Dimensions: 68.07 x 0.0 mm (0.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.96 |      N/A |      2.96 |   0+  1+  0 |        1 |          0 |   950 |       152 |     4122.7 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/FireMC_rados/unrouted.dsn)

Size: 112.9 kB · Layers: 2 · Nets: 119 · Components: 167 · Dimensions: 83.6 x 106.1 mm (88.7 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      99.27 |      N/A |     99.27 |   0+  5+  0 |        0 |          4 |   998 |       539 |   422714.2 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/FlashProgrammer_flash_programmer/unrouted.dsn)

Size: 35.6 kB · Layers: 2 · Nets: 50 · Components: 49 · Dimensions: 60.96 x 38.1 mm (23.23 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     108.22 |      N/A |    108.22 |   0+  1+  0 |        4 |          0 |   972 |       572 |   414995.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/flatpack2-adapter_EltekFlatpack2/unrouted.dsn)

Size: 55.1 kB · Layers: 2 · Nets: 0 · Components: 17 · Dimensions: 163.75 x 131.0 mm (214.51 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      62.48 |      N/A |     62.48 |   0+  1+  0 |        0 |         76 |   762 |       422 |    83252.2 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/flip32plus_flip32/unrouted.dsn)

Size: 43.8 kB · Layers: 2 · Nets: 0 · Components: 60 · Dimensions: 36.0 x 36.0 mm (12.96 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     285.90 |      N/A |    285.90 |   0+  1+  0 |        8 |          7 |   936 |       787 |   927028.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/FMCW_RADAR_Radar MCU/unrouted.dsn)

Size: 42.9 kB · Layers: 4 · Nets: 54 · Components: 104 · Dimensions: 36.91 x 32.94 mm (12.16 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     308.04 |      N/A |    308.04 |   0+  1+  0 |        0 |         48 |   963 |       636 |  1110880.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/FMCW_RADAR_Radar RF/unrouted.dsn)

Size: 55.9 kB · Layers: 4 · Nets: 72 · Components: 143 · Dimensions: 42.93 x 24.46 mm (10.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     780.40 |      N/A |    780.40 |   0+  1+  0 |        2 |        190 |   900 |       727 |  2944402.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/FogDrive_attiny45/unrouted.dsn)

Size: 24.3 kB · Layers: 2 · Nets: 3 · Components: 9 · Dimensions: 19.05 x 20.32 mm (3.87 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.23 |      N/A |      1.23 |   0+  1+  0 |        0 |          4 |   933 |       147 |     1271.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/FogDrive_attiny45_slim/unrouted.dsn)

Size: 23.4 kB · Layers: 2 · Nets: 3 · Components: 9 · Dimensions: 16.51 x 19.05 mm (3.15 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.93 |      N/A |      0.93 |   0+  1+  0 |        0 |          2 |   967 |         0 |        0.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/fp2_extension_sample_fp2_usb_breakout/unrouted.dsn)

Size: 3.4 kB · Layers: 2 · Nets: 4 · Components: 2 · Dimensions: 17.79 x 23.11 mm (4.11 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.46 |      N/A |      1.46 |   0+  1+  0 |        1 |          2 |   767 |       279 |     1533.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/freeDSP-CLASSIC-SMD-BALANCED_FreeDSP_BAL/unrouted.dsn)

Size: 100.4 kB · Layers: 2 · Nets: 101 · Components: 215 · Dimensions: 100.0 x 100.0 mm (100.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     659.28 |      N/A |    659.28 |   0+  1+  0 |        7 |          2 |   984 |       866 |  2481533.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/freeDSPx-AMP-x4_freeDSPx-AMPx4/unrouted.dsn)

Size: 58.4 kB · Layers: 2 · Nets: 72 · Components: 283 · Dimensions: 60.0 x 100.0 mm (60.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     657.24 |      N/A |    657.24 |   0+  1+  0 |       13 |        168 |   899 |       756 |  1994481.8 |    3 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/free-of-charge_BMS/unrouted.dsn)

Size: 44.1 kB · Layers: 2 · Nets: 0 · Components: 100 · Dimensions: 88.9 x 33.02 mm (29.35 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     301.41 |      N/A |    301.41 |   0+ 16+  0 |       14 |          0 |   951 |       752 |   932857.1 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/FreeSmartEEG__autosave-AD7779 for Intel Edison umdk/unrouted.dsn)

Size: 39.9 kB · Layers: 2 · Nets: 15 · Components: 83 · Dimensions: 70.91 x 127.03 mm (90.08 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     191.01 |      N/A |    191.01 |   0+  1+  0 |        5 |         94 |   888 |       546 |   671040.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/freeUSBi_USBi_Programmer/unrouted.dsn)

Size: 22.9 kB · Layers: 2 · Nets: 31 · Components: 24 · Dimensions: 40.0 x 40.0 mm (16.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.25 |      N/A |      4.25 |   0+  4+  0 |        0 |          0 |  1000 |       304 |     4692.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/FRM16_Relay_Module_I2C_Controller_relay_controller/unrouted.dsn)

Size: 13.2 kB · Layers: 2 · Nets: 36 · Components: 31 · Dimensions: 137.16 x 15.24 mm (20.9 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      89.32 |      N/A |     89.32 |   0+  1+  0 |        0 |         36 |   869 |       470 |   300672.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/front-end-modules_LimeSDR_Sony/unrouted.dsn)

Size: 82.9 kB · Layers: 6 · Nets: 352 · Components: 529 · Dimensions: 56.76 x 40.0 mm (22.7 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |    1185.28 |      N/A |   1185.28 |   0+  1+  0 |       17 |        392 |   889 |       693 |  4325528.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/fst-01_fst-01/unrouted.dsn)

Size: 31.6 kB · Layers: 2 · Nets: 2 · Components: 25 · Dimensions: 26.48 x 13.15 mm (3.48 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      45.30 |      N/A |     45.30 |   0+  1+  0 |       10 |         21 |   778 |       546 |   157429.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/FT231X_breakout_FTDI_FT231XS-U_Breakout/unrouted.dsn)

Size: 10.1 kB · Layers: 2 · Nets: 5 · Components: 13 · Dimensions: 37.08 x 21.84 mm (8.1 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      24.85 |      N/A |     24.85 |   0+  1+  0 |        2 |          0 |   950 |       397 |    55472.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/fuzzer_fuzzer/unrouted.dsn)

Size: 27 kB · Layers: 2 · Nets: 22 · Components: 19 · Dimensions: 33.02 x 33.02 mm (10.9 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      12.58 |      N/A |     12.58 |   0+  6+  0 |        0 |          0 |  1000 |       357 |    28822.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/gadget-speed-radar_gadget_speed_radar_rev2/unrouted.dsn)

Size: 17.4 kB · Layers: 2 · Nets: 13 · Components: 28 · Dimensions: 37.47 x 22.33 mm (8.37 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.63 |      N/A |      2.63 |   0+  2+  0 |        0 |          0 |  1000 |       319 |     4043.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Game-Boy-Zero-DMG-Controller-PCB-KiCad_Game-Boy-Zero-DMG-Controller-PCB/unrouted.dsn)

Size: 11 kB · Layers: 2 · Nets: 10 · Components: 22 · Dimensions: 81.28 x 52.32 mm (42.53 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      30.82 |      N/A |     30.82 |   0+  1+  0 |        1 |          0 |   989 |       601 |   125174.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/gamelights_leds/unrouted.dsn)

Size: 12.9 kB · Layers: 2 · Nets: 3 · Components: 15 · Dimensions: 24.3 x 24.0 mm (5.83 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      68.22 |      N/A |     68.22 |   0+  9+  0 |        1 |         24 |   807 |       303 |    56050.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/gdrom_adapter_board_adapter/unrouted.dsn)

Size: 24.4 kB · Layers: 2 · Nets: 5 · Components: 10 · Dimensions: 100.0 x 80.0 mm (80.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      92.01 |      N/A |     92.01 |   0+ 20+  0 |        1 |          0 |   991 |       537 |   215289.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/gepetto_circuito/unrouted.dsn)

Size: 18 kB · Layers: 2 · Nets: 8 · Components: 20 · Dimensions: 86.61 x 58.41 mm (50.59 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.67 |      N/A |      2.67 |   0+  3+  0 |        0 |          0 |  1000 |       420 |     3721.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/gpsclock_clock/unrouted.dsn)

Size: 11.8 kB · Layers: 2 · Nets: 0 · Components: 10 · Dimensions: 48.26 x 78.74 mm (38.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.35 |      N/A |      4.35 |   0+  4+  0 |        0 |          0 |  1000 |       192 |     6508.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/gsm-iot-core-hw_gsm-iot-core/unrouted.dsn)

Size: 55.2 kB · Layers: 2 · Nets: 34 · Components: 92 · Dimensions: 25.4 x 64.77 mm (16.45 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     303.01 |      N/A |    303.01 |   0+ 10+  0 |       17 |         76 |   871 |       746 |   951992.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/guitar_fret/unrouted.dsn)

Size: 22 kB · Layers: 2 · Nets: 13 · Components: 51 · Dimensions: 0.0 x 0.0 mm (0.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     171.46 |      N/A |    171.46 |   0+  1+  0 |        0 |          0 |  1000 |       552 |   753691.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/gwurrbus_gwurrbus-relay/unrouted.dsn)

Size: 33.4 kB · Layers: 2 · Nets: 20 · Components: 13 · Dimensions: 78.22 x 56.29 mm (44.03 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.06 |      N/A |      3.06 |   0+  2+  0 |        0 |          0 |  1000 |        96 |     1511.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/gwurrbus_pwm/unrouted.dsn)

Size: 38.1 kB · Layers: 2 · Nets: 14 · Components: 37 · Dimensions: 146.98 x 87.12 mm (128.05 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.98 |      N/A |      2.98 |   0+  2+  0 |        0 |          0 |  1000 |       364 |     3328.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/HACK_hack/unrouted.dsn)

Size: 33.8 kB · Layers: 2 · Nets: 13 · Components: 32 · Dimensions: 36.83 x 19.05 mm (7.02 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes      |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :--------- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |        N/A |      N/A |       N/A |   0+  0+  0 |      N/A |        N/A |   N/A |         0 |        0.0 |    1 / 0 | LOAD ERROR |


### Fixture: [unrouted.dsn](../fixtures/PCBench/hackaday_esp-14_power_meter__autosave-esp-14/unrouted.dsn)

Size: 6.5 kB · Layers: 2 · Nets: 22 · Components: 3 · Dimensions: 30.0 x 37.0 mm (11.1 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.00 |      N/A |      1.00 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/hackyflasher_Flasher/unrouted.dsn)

Size: 21 kB · Layers: 2 · Nets: 6 · Components: 14 · Dimensions: 67.31 x 99.06 mm (66.68 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.69 |      N/A |      3.69 |   0+  1+  0 |        1 |          0 |   937 |       340 |     4776.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/hamityanik_ULP-Weather-Logger/unrouted.dsn)

Size: 46.1 kB · Layers: 2 · Nets: 12 · Components: 94 · Dimensions: 37.0 x 66.0 mm (24.42 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      67.62 |      N/A |     67.62 |   0+  1+  0 |        1 |         26 |   965 |       637 |   295771.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/HamShield09_HamShield09/unrouted.dsn)

Size: 66.9 kB · Layers: 4 · Nets: 85 · Components: 173 · Dimensions: 53.3 x 68.58 mm (36.55 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     904.02 |      N/A |    904.02 |   0+ 14+  0 |       26 |         58 |   903 |       817 |  2490210.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Hangul-Clock_Hangul/unrouted.dsn)

Size: 25.7 kB · Layers: 2 · Nets: 65 · Components: 87 · Dimensions: 99.0 x 99.0 mm (98.01 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      54.11 |      N/A |     54.11 |   0+  4+  0 |        0 |          0 |  1000 |       536 |   219043.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Hardware_Playground_BL_PCB_latest/unrouted.dsn)

Size: 33 kB · Layers: 2 · Nets: 0 · Components: 159 · Dimensions: 136.91 x 67.06 mm (91.81 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      21.49 |      N/A |     21.49 |   0+  5+  0 |        0 |          0 |  1000 |       572 |    74400.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Hardware_Playground_buck_led_driver/unrouted.dsn)

Size: 19.9 kB · Layers: 2 · Nets: 7 · Components: 13 · Dimensions: 25.0 x 17.0 mm (4.25 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.94 |      N/A |      2.94 |   0+  1+  0 |        1 |         14 |   789 |       399 |     3544.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Hardware_Playground_E3_Pcb_rev0.3/unrouted.dsn)

Size: 95.2 kB · Layers: 2 · Nets: 0 · Components: 140 · Dimensions: 99.95 x 70.1 mm (70.06 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     304.98 |      N/A |    304.98 |   0+  5+  0 |       18 |         25 |   926 |       751 |   838751.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Hardware_Playground_esp8266_uno/unrouted.dsn)

Size: 45.2 kB · Layers: 2 · Nets: 10 · Components: 126 · Dimensions: 68.53 x 53.47 mm (36.64 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      43.03 |      N/A |     43.03 |   0+ 18+  0 |        1 |          0 |   995 |       556 |   158392.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Hardware_Playground_esp8266_uno_relay/unrouted.dsn)

Size: 65.1 kB · Layers: 2 · Nets: 3 · Components: 295 · Dimensions: 68.75 x 53.25 mm (36.61 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      19.16 |      N/A |     19.16 |   0+  1+  0 |        1 |         56 |   938 |       466 |    74475.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Hardware_Playground_heater_actuator_node/unrouted.dsn)

Size: 123.3 kB · Layers: 2 · Nets: 15 · Components: 990 · Dimensions: 69.1 x 65.53 mm (45.28 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |    1250.08 |      N/A |   1250.08 |   0+ 15+  0 |        0 |          0 |  1000 |       871 |  4849608.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Hardware_Playground_hy_adapter/unrouted.dsn)

Size: 6.4 kB · Layers: 2 · Nets: 0 · Components: 22 · Dimensions: 9.91 x 12.95 mm (1.28 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.48 |      N/A |      4.48 |   0+  2+  0 |        0 |         16 |   467 |       207 |     8190.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Hardware_Playground_led_strip_actuator_node/unrouted.dsn)

Size: 49 kB · Layers: 2 · Nets: 9 · Components: 209 · Dimensions: 36.8 x 36.7 mm (13.51 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     103.70 |      N/A |    103.70 |   0+  1+  0 |        1 |          4 |   993 |       670 |   413072.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Hardware_Playground_leds_array/unrouted.dsn)

Size: 108.4 kB · Layers: 2 · Nets: 0 · Components: 142 · Dimensions: 92.2 x 92.33 mm (85.13 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      13.79 |      N/A |     13.79 |   0+  2+  0 |        0 |          1 |   999 |       474 |    44950.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Hardware_Playground_livolo_1_channel_1way_eu_switch/unrouted.dsn)

Size: 53.4 kB · Layers: 2 · Nets: 15 · Components: 31 · Dimensions: 43.0 x 43.4 mm (18.66 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      48.91 |      N/A |     48.91 |   0+  1+  0 |        0 |         80 |   820 |       496 |   182488.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Hardware_Playground_livolo_2_channels_1way_eu_switch/unrouted.dsn)

Size: 129.1 kB · Layers: 2 · Nets: 14 · Components: 52 · Dimensions: 186.49 x 104.59 mm (195.05 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     403.21 |      N/A |    403.21 |   0+  1+  0 |        0 |          8 |   986 |       560 |  1323002.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Hardware_Playground_lm317_adj_supply/unrouted.dsn)

Size: 151.2 kB · Layers: 2 · Nets: 12 · Components: 39 · Dimensions: 112.25 x 60.75 mm (68.19 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.44 |      N/A |      2.44 |   0+  2+  0 |        0 |          0 |  1000 |       201 |     2890.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Hardware_Playground_minimal_node_rfm69w/unrouted.dsn)

Size: 26.8 kB · Layers: 2 · Nets: 16 · Components: 58 · Dimensions: 27.6 x 25.1 mm (6.93 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      73.42 |      N/A |     73.42 |   0+  1+  0 |        5 |          0 |   951 |       527 |   216961.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Hardware_Playground_multisensor_cr123/unrouted.dsn)

Size: 32.2 kB · Layers: 2 · Nets: 24 · Components: 64 · Dimensions: 48.2 x 22.16 mm (10.68 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     124.45 |      N/A |    124.45 |   0+  1+  0 |       26 |         58 |   679 |       499 |   401988.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Hardware_Playground_multisensor_cr2032/unrouted.dsn)

Size: 55.6 kB · Layers: 2 · Nets: 6 · Components: 147 · Dimensions: 14.99 x 9.91 mm (1.49 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     182.50 |      N/A |    182.50 |   0+  1+  0 |       10 |         19 |   933 |       707 |   616741.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Hardware_Playground_nrf52832_coin/unrouted.dsn)

Size: 46.1 kB · Layers: 2 · Nets: 0 · Components: 20 · Dimensions: 12.93 x 12.83 mm (1.66 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       8.31 |      N/A |      8.31 |   0+  1+  0 |        3 |          1 |   861 |       444 |    25634.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Hardware_Playground_NRF52832_Touch_Switch_Power_Supply/unrouted.dsn)

Size: 56.3 kB · Layers: 2 · Nets: 0 · Components: 56 · Dimensions: 21.23 x 20.88 mm (4.43 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      21.57 |      N/A |     21.57 |   0+ 16+  0 |        0 |          4 |   992 |       464 |    60797.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Hardware_Playground_nrf52832_uno/unrouted.dsn)

Size: 61.7 kB · Layers: 2 · Nets: 0 · Components: 36 · Dimensions: 68.58 x 53.34 mm (36.58 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      77.64 |      N/A |     77.64 |   0+  1+  0 |        3 |          0 |   969 |       506 |   221757.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Hardware_Playground_nrf5x_coin/unrouted.dsn)

Size: 48.3 kB · Layers: 2 · Nets: 0 · Components: 41 · Dimensions: 11.31 x 14.33 mm (1.62 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      65.43 |      N/A |     65.43 |   0+  1+  0 |        7 |          0 |   899 |       463 |   163594.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Hardware_Playground_orange_pi_zero_node/unrouted.dsn)

Size: 36.9 kB · Layers: 2 · Nets: 45 · Components: 164 · Dimensions: 46.0 x 48.0 mm (22.08 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      35.68 |      N/A |     35.68 |   0+ 18+  0 |        1 |          0 |   995 |       498 |   116664.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Hardware_Playground_pro_mini/unrouted.dsn)

Size: 29.7 kB · Layers: 2 · Nets: 17 · Components: 95 · Dimensions: 50.5 x 30.1 mm (15.2 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      20.28 |      N/A |     20.28 |   0+  1+  0 |        2 |          0 |   974 |       461 |    58998.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Hardware_Playground_rfm69w_adapter/unrouted.dsn)

Size: 25.9 kB · Layers: 2 · Nets: 8 · Components: 87 · Dimensions: 20.0 x 16.0 mm (3.2 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     308.95 |      N/A |    308.95 |   0+ 15+  0 |       16 |         39 |   791 |       456 |   549563.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Hardware_Playground_rpi_zero/unrouted.dsn)

Size: 45 kB · Layers: 2 · Nets: 32 · Components: 169 · Dimensions: 65.0 x 30.0 mm (19.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      45.19 |      N/A |     45.19 |   0+ 18+  0 |        2 |          0 |   991 |       513 |   177441.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Hardware_Playground_rpi_zero_ws2812/unrouted.dsn)

Size: 25.8 kB · Layers: 2 · Nets: 0 · Components: 40 · Dimensions: 65.0 x 30.0 mm (19.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.63 |      N/A |      2.63 |   0+  3+  0 |        0 |          0 |  1000 |       292 |     3643.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Hardware_Playground_samd51_micropython/unrouted.dsn)

Size: 26.8 kB · Layers: 2 · Nets: 0 · Components: 22 · Dimensions: 35.56 x 18.8 mm (6.69 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      60.82 |      N/A |     60.82 |   0+ 10+  0 |        8 |         64 |   746 |       533 |   161544.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Hardware_Playground_sd_wav_pcm5100/unrouted.dsn)

Size: 63.3 kB · Layers: 2 · Nets: 20 · Components: 329 · Dimensions: 39.7 x 49.98 mm (19.84 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     450.56 |      N/A |    450.56 |   0+  1+  0 |       23 |         20 |   932 |       699 |  1704565.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Hardware_Playground_serial_gw_ATMEGA328P/unrouted.dsn)

Size: 42 kB · Layers: 2 · Nets: 17 · Components: 115 · Dimensions: 44.45 x 19.81 mm (8.81 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     111.38 |      N/A |    111.38 |   0+  1+  0 |        1 |         14 |   978 |       589 |   442502.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Hardware_Playground_serial_gw_ATSAMD21E/unrouted.dsn)

Size: 40.3 kB · Layers: 2 · Nets: 13 · Components: 78 · Dimensions: 38.9 x 21.2 mm (8.25 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     114.71 |      N/A |    114.71 |   0+  1+  0 |        6 |          2 |   952 |       599 |   451369.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Hardware_Playground_serial_gw_maple_mini/unrouted.dsn)

Size: 50 kB · Layers: 2 · Nets: 33 · Components: 189 · Dimensions: 70.01 x 50.0 mm (35.01 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      22.73 |      N/A |     22.73 |   0+  7+  0 |        0 |          0 |  1000 |       551 |    72486.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Hardware_Playground_Touch_Switch_1ch_PCB/unrouted.dsn)

Size: 37.8 kB · Layers: 2 · Nets: 0 · Components: 49 · Dimensions: 43.43 x 43.18 mm (18.75 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      74.12 |      N/A |     74.12 |   0+  1+  0 |        1 |          0 |   991 |       634 |   270377.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Hardware_Playground_Touch_Switch_2ch_PCB/unrouted.dsn)

Size: 38.5 kB · Layers: 2 · Nets: 0 · Components: 57 · Dimensions: 43.43 x 43.18 mm (18.75 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     117.56 |      N/A |    117.56 |   0+  1+  0 |        2 |          2 |   979 |       575 |   379995.1 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Hardware_Playground_usb_shield/unrouted.dsn)

Size: 28.7 kB · Layers: 2 · Nets: 0 · Components: 201 · Dimensions: 70.05 x 30.0 mm (21.02 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      25.70 |      N/A |     25.70 |   0+  2+  0 |        0 |          0 |  1000 |       537 |    90992.1 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Hardware_Playground_wifi_lights/unrouted.dsn)

Size: 30.8 kB · Layers: 2 · Nets: 8 · Components: 33 · Dimensions: 46.5 x 48.0 mm (22.32 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       6.56 |      N/A |      6.56 |   0+  5+  0 |        0 |          0 |  1000 |       469 |    12280.7 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Hardware_Playground_ws281x_led_strip_controller/unrouted.dsn)

Size: 36.4 kB · Layers: 2 · Nets: 8 · Components: 118 · Dimensions: 44.8 x 23.35 mm (10.46 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     118.61 |      N/A |    118.61 |   0+  1+  0 |        3 |          0 |   983 |       674 |   409150.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/hardware-designs_cc430-debug-board/unrouted.dsn)

Size: 14.9 kB · Layers: 2 · Nets: 0 · Components: 14 · Dimensions: 49.0 x 36.0 mm (17.64 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      11.77 |      N/A |     11.77 |   0+ 13+  0 |        0 |          0 |  1000 |       504 |    27813.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/hardware-designs_c-trigger/unrouted.dsn)

Size: 5.7 kB · Layers: 2 · Nets: 0 · Components: 8 · Dimensions: 46.23 x 10.67 mm (4.93 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.43 |      N/A |      0.43 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/hardware-designs_energy-harvester/unrouted.dsn)

Size: 13 kB · Layers: 2 · Nets: 0 · Components: 18 · Dimensions: 46.23 x 15.75 mm (7.28 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.53 |      N/A |      2.53 |   0+  4+  0 |        0 |          0 |  1000 |       187 |     4115.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/hardware-designs_m-trigger/unrouted.dsn)

Size: 5.6 kB · Layers: 2 · Nets: 0 · Components: 7 · Dimensions: 46.23 x 15.75 mm (7.28 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.59 |      N/A |      0.59 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/hardware-designs_nixie-combo/unrouted.dsn)

Size: 22.6 kB · Layers: 2 · Nets: 0 · Components: 27 · Dimensions: 99.0 x 70.0 mm (69.3 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      14.37 |      N/A |     14.37 |   0+  5+  0 |        0 |          0 |  1000 |       409 |    31550.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/hardware-designs_nixie-power/unrouted.dsn)

Size: 17.5 kB · Layers: 2 · Nets: 0 · Components: 24 · Dimensions: 49.0 x 49.0 mm (24.01 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.77 |      N/A |      2.77 |   0+  2+  0 |        0 |          0 |  1000 |        43 |     2535.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/hardware-designs_si7021-board/unrouted.dsn)

Size: 5.6 kB · Layers: 2 · Nets: 0 · Components: 6 · Dimensions: 9.91 x 45.47 mm (4.51 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.22 |      N/A |      4.22 |   0+  2+  0 |        0 |         16 |   680 |       472 |     8605.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/hardware-designs_soil-moisture-sensor/unrouted.dsn)

Size: 8.8 kB · Layers: 2 · Nets: 0 · Components: 16 · Dimensions: 148.25 x 46.5 mm (68.94 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.61 |      N/A |      3.61 |   0+  6+  0 |        0 |          0 |  1000 |       472 |     3845.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/hardware-designs_solar-harvester/unrouted.dsn)

Size: 11.2 kB · Layers: 2 · Nets: 0 · Components: 18 · Dimensions: 46.23 x 15.75 mm (7.28 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.33 |      N/A |      2.33 |   0+  2+  0 |        0 |          1 |   993 |       223 |     1809.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/hardware-designs_spirit1-board/unrouted.dsn)

Size: 14.3 kB · Layers: 2 · Nets: 0 · Components: 23 · Dimensions: 27.0 x 24.0 mm (6.48 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      30.39 |      N/A |     30.39 |   0+  1+  0 |        2 |          6 |   940 |       506 |    87151.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/hardware-designs_spsgrf-board/unrouted.dsn)

Size: 5.7 kB · Layers: 2 · Nets: 0 · Components: 4 · Dimensions: 20.5 x 24.5 mm (5.02 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.71 |      N/A |      1.71 |   0+  2+  0 |        0 |          0 |  1000 |        75 |      560.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Hardware-done-with-kicad_AVRlearn/unrouted.dsn)

Size: 19.7 kB · Layers: 2 · Nets: 24 · Components: 13 · Dimensions: 68.58 x 50.8 mm (34.84 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.92 |      N/A |      2.92 |   0+  2+  0 |        0 |          0 |  1000 |       102 |      775.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/HaveSome_PCB_HaveSomePCB/unrouted.dsn)

Size: 6.6 kB · Layers: 2 · Nets: 3 · Components: 14 · Dimensions: 7.62 x 18.41 mm (1.4 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       5.81 |      N/A |      5.81 |   0+  1+  0 |        1 |         10 |   870 |       376 |    11783.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/headstage-adapter_headstage adapter/unrouted.dsn)

Size: 16.1 kB · Layers: 4 · Nets: 0 · Components: 12 · Dimensions: 21.45 x 52.85 mm (11.34 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     301.28 |      N/A |    301.28 |   0+  1+  0 |        0 |         22 |   960 |       921 |   833222.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Helium_helium_1-6/unrouted.dsn)

Size: 56.9 kB · Layers: 2 · Nets: 0 · Components: 112 · Dimensions: 86.99 x 65.02 mm (56.56 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     205.38 |      N/A |    205.38 |   0+  2+  0 |        4 |         15 |   973 |       777 |   725886.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/HellScribe_HellScribe/unrouted.dsn)

Size: 29.3 kB · Layers: 2 · Nets: 38 · Components: 51 · Dimensions: 173.74 x 28.45 mm (49.43 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      11.15 |      N/A |     11.15 |   0+  4+  0 |        0 |          0 |  1000 |       501 |    27962.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/helmholtz-servo_CurrentServo/unrouted.dsn)

Size: 55.5 kB · Layers: 2 · Nets: 42 · Components: 102 · Dimensions: 130.0 x 85.0 mm (110.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      50.39 |      N/A |     50.39 |   0+ 18+  0 |        1 |          4 |   992 |       567 |   202742.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/HES-V2__autosave-hes/unrouted.dsn)

Size: 24.3 kB · Layers: 2 · Nets: 20 · Components: 18 · Dimensions: 0.0 x 0.0 mm (0.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.41 |      N/A |      1.41 |   0+  2+  0 |        0 |          0 |  1000 |       227 |     1399.2 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/HES-V2_hes/unrouted.dsn)

Size: 24.3 kB · Layers: 2 · Nets: 20 · Components: 18 · Dimensions: 0.0 x 0.0 mm (0.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.07 |      N/A |      2.07 |   0+  2+  0 |        0 |          0 |  1000 |       223 |     1854.2 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/HID_PID_controller/unrouted.dsn)

Size: 43.2 kB · Layers: 2 · Nets: 0 · Components: 62 · Dimensions: 50.0 x 50.0 mm (25.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      13.47 |      N/A |     13.47 |   0+  5+  0 |        0 |          2 |   997 |       409 |    43111.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/HIDIRT-for-STM32F103C8T6-DevBoard_STM32F103C8T6-DEV-BOARD-addon/unrouted.dsn)

Size: 38.4 kB · Layers: 2 · Nets: 17 · Components: 33 · Dimensions: 53.0 x 61.0 mm (32.33 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       8.14 |      N/A |      8.14 |   0+  1+  0 |        0 |          8 |   972 |       452 |    29689.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/HiFiAudioCodecModule_HiFiAudioCodecModule/unrouted.dsn)

Size: 25 kB · Layers: 2 · Nets: 2 · Components: 26 · Dimensions: 25.4 x 25.4 mm (6.45 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      22.21 |      N/A |     22.21 |   0+  1+  0 |        1 |         19 |   933 |       465 |    64018.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/hillhacks2016_badge_badge1/unrouted.dsn)

Size: 36.2 kB · Layers: 2 · Nets: 1 · Components: 24 · Dimensions: 49.95 x 99.95 mm (49.93 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.54 |      N/A |      4.54 |   0+  3+  0 |        0 |          0 |  1000 |        93 |     5415.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/HillhacksLantern_LEDLantern/unrouted.dsn)

Size: 42.6 kB · Layers: 2 · Nets: 10 · Components: 22 · Dimensions: 20.0 x 121.0 mm (24.2 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       5.90 |      N/A |      5.90 |   0+  1+  0 |        2 |          0 |   929 |       172 |     9932.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/hm-mod-rpi-rtc_hm-mod-rpi-rtc/unrouted.dsn)

Size: 16.9 kB · Layers: 2 · Nets: 10 · Components: 25 · Dimensions: 44.45 x 21.84 mm (9.71 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.10 |      N/A |      3.10 |   0+  3+  0 |        0 |          0 |  1000 |       388 |     7788.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Hornbill-ESP32-DEV_Hornbill Devboard/unrouted.dsn)

Size: 42.8 kB · Layers: 2 · Nets: 35 · Components: 51 · Dimensions: 25.4 x 60.83 mm (15.45 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     306.46 |      N/A |    306.46 |   0+  1+  0 |        0 |         78 |   871 |       536 |   493237.1 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Hornbill-ESP32-Minima_Beacon32/unrouted.dsn)

Size: 28.9 kB · Layers: 2 · Nets: 21 · Components: 40 · Dimensions: 22.86 x 2.79 mm (0.64 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes      |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :--------- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |        N/A |      N/A |       N/A |   0+  0+  0 |      N/A |        N/A |   N/A |         0 |        0.0 |    1 / 0 | LOAD ERROR |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Horticulture_Light_EQ_3-Band_3 channel led driver rev0.4/unrouted.dsn)

Size: 47 kB · Layers: 2 · Nets: 25 · Components: 292 · Dimensions: 121.92 x 25.4 mm (30.97 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     182.97 |      N/A |    182.97 |   0+  1+  0 |        2 |        471 |   749 |       601 |   891098.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Horticulture_Light_EQ_3-Band_light/unrouted.dsn)

Size: 40.4 kB · Layers: 2 · Nets: 40 · Components: 390 · Dimensions: 551.18 x 12.7 mm (70.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     321.25 |      N/A |    321.25 |   0+  1+  0 |        6 |         52 |   965 |       699 |  1226063.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/hotstick_Wand/unrouted.dsn)

Size: 16.4 kB · Layers: 4 · Nets: 6 · Components: 20 · Dimensions: 50.0 x 7.0 mm (3.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      62.84 |      N/A |     62.84 |   0+  1+  0 |        5 |         21 |   839 |       526 |   220052.1 |    3 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/hw_trials_demo/unrouted.dsn)

Size: 21.5 kB · Layers: 2 · Nets: 4 · Components: 28 · Dimensions: 49.53 x 49.53 mm (24.53 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      21.16 |      N/A |     21.16 |   0+  1+  0 |        1 |          2 |   984 |       496 |    75112.7 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/HW-AC-Emeter_ac-power-monitor/unrouted.dsn)

Size: 34.5 kB · Layers: 2 · Nets: 20 · Components: 62 · Dimensions: 100.0 x 50.0 mm (50.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      27.97 |      N/A |     27.97 |   0+ 13+  0 |        0 |          0 |  1000 |       569 |    71976.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/HW-ESP8266_Roomba_ESP8266_Roomba/unrouted.dsn)

Size: 57.5 kB · Layers: 2 · Nets: 31 · Components: 68 · Dimensions: 100.0 x 30.01 mm (30.01 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     312.96 |      N/A |    312.96 |   0+ 17+  0 |        4 |         58 |   914 |       717 |   642122.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/HW-ESP8266_Roomba_ESP8266_Roomba_panel_x_3/unrouted.dsn)

Size: 53.7 kB · Layers: 2 · Nets: 31 · Components: 68 · Dimensions: 100.0 x 30.03 mm (30.03 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     145.53 |      N/A |    145.53 |   0+  1+  0 |        3 |          7 |   976 |       498 |   508915.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/hwstar_ac-power-monitor/unrouted.dsn)

Size: 34.5 kB · Layers: 2 · Nets: 20 · Components: 62 · Dimensions: 100.0 x 50.0 mm (50.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      24.37 |      N/A |     24.37 |   0+ 13+  0 |        0 |          0 |  1000 |       564 |    73400.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/HY-AI7688H-RevA_HY-AI7688H/unrouted.dsn)

Size: 110.9 kB · Layers: 2 · Nets: 26 · Components: 217 · Dimensions: 97.0 x 82.0 mm (79.54 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     189.03 |      N/A |    189.03 |   0+  1+  0 |        4 |         15 |   982 |       548 |   771970.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Hypfer-RGB-W-LED-Controller_led_strip_controller/unrouted.dsn)

Size: 42.5 kB · Layers: 2 · Nets: 1 · Components: 31 · Dimensions: 73.66 x 80.01 mm (58.94 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      13.74 |      N/A |     13.74 |   0+  1+  0 |        1 |          0 |   986 |       533 |    36932.7 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/I2CTempsensor_sensors/unrouted.dsn)

Size: 8.3 kB · Layers: 2 · Nets: 3 · Components: 6 · Dimensions: 21.59 x 21.59 mm (4.66 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.89 |      N/A |      0.89 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/i2lcd__autosave-i2lcd/unrouted.dsn)

Size: 18.4 kB · Layers: 2 · Nets: 5 · Components: 22 · Dimensions: 44.0 x 25.0 mm (11.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      51.79 |      N/A |     51.79 |   0+  1+  0 |        4 |          0 |   946 |       539 |   175763.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/i2lcd_i2lcd/unrouted.dsn)

Size: 18.4 kB · Layers: 2 · Nets: 5 · Components: 22 · Dimensions: 44.0 x 25.0 mm (11.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      60.33 |      N/A |     60.33 |   0+  1+  0 |        5 |          0 |   932 |       575 |   193880.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ice40_stm32_fsmc_test_pcb_fsmc_ice40/unrouted.dsn)

Size: 49.4 kB · Layers: 2 · Nets: 7 · Components: 44 · Dimensions: 76.2 x 48.0 mm (36.58 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes      |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :--------- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |        N/A |      N/A |       N/A |   0+  0+  0 |      N/A |        N/A |   N/A |         0 |        0.0 |    1 / 0 | LOAD ERROR |


### Fixture: [unrouted.dsn](../fixtures/PCBench/iCE40-DIO_ICE40-DIO_Rev_A/unrouted.dsn)

Size: 116.4 kB · Layers: 2 · Nets: 15 · Components: 51 · Dimensions: 50.0 x 50.0 mm (25.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     302.26 |      N/A |    302.26 |   0+  1+  0 |        0 |          1 |   999 |       707 |  1047463.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/iCE40-IO_ICE40-IO_Rev_A/unrouted.dsn)

Size: 121.7 kB · Layers: 2 · Nets: 20 · Components: 43 · Dimensions: 45.0 x 50.0 mm (22.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      38.67 |      N/A |     38.67 |   0+  1+  0 |        1 |          1 |   989 |       512 |   103727.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/icehat_icehat/unrouted.dsn)

Size: 36.5 kB · Layers: 4 · Nets: 59 · Components: 40 · Dimensions: 65.0 x 30.0 mm (19.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     301.53 |      N/A |    301.53 |   0+ 16+  0 |       11 |         42 |   855 |       593 |   585435.7 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ID-FIX_scanConnect/unrouted.dsn)

Size: 7.7 kB · Layers: 2 · Nets: 2 · Components: 8 · Dimensions: 35.39 x 31.22 mm (11.05 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.40 |      N/A |      0.40 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/IGN01A_IGN01A/unrouted.dsn)

Size: 19.5 kB · Layers: 2 · Nets: 4 · Components: 24 · Dimensions: 50.0 x 45.0 mm (22.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.43 |      N/A |      3.43 |   0+  2+  0 |        0 |          0 |  1000 |       596 |     9238.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/imfr-schematics_Telescopio/unrouted.dsn)

Size: 20.9 kB · Layers: 2 · Nets: 0 · Components: 19 · Dimensions: 201.93 x 119.38 mm (241.06 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.61 |      N/A |      3.61 |   0+  3+  0 |        0 |          0 |  1000 |       469 |     5724.7 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/induction-hob_temperature-sender/unrouted.dsn)

Size: 30.3 kB · Layers: 2 · Nets: 10 · Components: 44 · Dimensions: 50.0 x 25.0 mm (12.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      25.08 |      N/A |     25.08 |   0+  1+  0 |        3 |          0 |   965 |       512 |   106264.7 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/induction-hob_temperature-sensor/unrouted.dsn)

Size: 4.4 kB · Layers: 2 · Nets: 0 · Components: 12 · Dimensions: 7.4 x 7.1 mm (0.53 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       7.31 |      N/A |      7.31 |   0+  1+  0 |        2 |          5 |   800 |       420 |    21074.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Inhibition_amplifier.kicad_pcb-CNC version (with some problems)/unrouted.dsn)

Size: 37.3 kB · Layers: 2 · Nets: 116 · Components: 67 · Dimensions: 99.06 x 99.06 mm (98.13 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      45.93 |      N/A |     45.93 |   0+  1+  0 |        0 |         14 |   980 |       596 |   213986.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Inhibition_amplifier/unrouted.dsn)

Size: 35.9 kB · Layers: 2 · Nets: 116 · Components: 75 · Dimensions: 99.06 x 99.06 mm (98.13 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      60.44 |      N/A |     60.44 |   0+  1+  0 |        0 |          8 |   989 |       481 |   272950.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Inkjet__autosave-InkjetDriver/unrouted.dsn)

Size: 24.8 kB · Layers: 2 · Nets: 76 · Components: 19 · Dimensions: 36.83 x 88.9 mm (32.74 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       9.49 |      N/A |      9.49 |   0+  4+  0 |        0 |          0 |  1000 |       429 |    21118.2 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Inkjet_InkjetBreakout/unrouted.dsn)

Size: 5.4 kB · Layers: 2 · Nets: 13 · Components: 2 · Dimensions: 24.13 x 30.48 mm (7.35 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.25 |      N/A |      2.25 |   0+  2+  0 |        0 |          0 |  1000 |       183 |     2029.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Inkjet_InkjetDriver/unrouted.dsn)

Size: 24.8 kB · Layers: 2 · Nets: 76 · Components: 19 · Dimensions: 36.83 x 88.9 mm (32.74 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       8.85 |      N/A |      8.85 |   0+  4+  0 |        0 |          0 |  1000 |       343 |    21134.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Inkjet_PiezoDriver/unrouted.dsn)

Size: 16.1 kB · Layers: 2 · Nets: 57 · Components: 11 · Dimensions: 29.21 x 74.93 mm (21.89 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      14.03 |      N/A |     14.03 |   0+  1+  0 |        1 |          6 |   924 |       476 |    26927.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/iotuz-esp32-hardware_IoTuz/unrouted.dsn)

Size: 113 kB · Layers: 2 · Nets: 52 · Components: 154 · Dimensions: 150.0 x 95.0 mm (142.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     841.02 |      N/A |    841.02 |   0+  1+  0 |        1 |        162 |   924 |       764 |  1859037.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ipod-serial-adapter_s1_ipod_serial/unrouted.dsn)

Size: 128.7 kB · Layers: 2 · Nets: 12 · Components: 36 · Dimensions: 21.59 x 49.53 mm (10.69 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      28.98 |      N/A |     28.98 |   0+  7+  0 |        0 |          0 |  1000 |       552 |   119984.7 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/IR-Transponder-ATTiny85-v2_Transponder_v2/unrouted.dsn)

Size: 12.7 kB · Layers: 2 · Nets: 8 · Components: 12 · Dimensions: 19.0 x 23.0 mm (4.37 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.51 |      N/A |      3.51 |   0+  1+  0 |        1 |          0 |   952 |       555 |     8808.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/iso50_pcb/unrouted.dsn)

Size: 53 kB · Layers: 2 · Nets: 70 · Components: 105 · Dimensions: 274.92 x 71.44 mm (196.4 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      23.62 |      N/A |     23.62 |   0+  1+  0 |      217 |          3 |     0 |       576 |    91837.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ISO-port_ch340-usb-serial-isolated/unrouted.dsn)

Size: 22.2 kB · Layers: 2 · Nets: 0 · Components: 39 · Dimensions: 50.0 x 50.0 mm (25.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      21.51 |      N/A |     21.51 |   0+  1+  0 |        1 |          0 |   987 |       453 |    85556.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/jadonk_PocketBone/unrouted.dsn)

Size: 80.1 kB · Layers: 4 · Nets: 344 · Components: 65 · Dimensions: 55.0 x 35.0 mm (19.25 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     575.68 |      N/A |    575.68 |   0+  1+  0 |       40 |          2 |   800 |       760 |  1738630.6 |    3 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/JavierIH_kameshield/unrouted.dsn)

Size: 11.5 kB · Layers: 2 · Nets: 17 · Components: 11 · Dimensions: 31.75 x 67.94 mm (21.57 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       6.21 |      N/A |      6.21 |   0+  1+  0 |        0 |        138 |    48 |       667 |    18234.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/jdy-08-board_jdy-08/unrouted.dsn)

Size: 7.8 kB · Layers: 2 · Nets: 0 · Components: 5 · Dimensions: 31.75 x 38.73 mm (12.3 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       6.20 |      N/A |      6.20 |   0+  1+  0 |        1 |          0 |   966 |       468 |    18001.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/JLink-SWD_JLink-SWD/unrouted.dsn)

Size: 20.9 kB · Layers: 2 · Nets: 7 · Components: 16 · Dimensions: 32.26 x 30.48 mm (9.83 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.32 |      N/A |      3.32 |   0+  4+  0 |        0 |          1 |   996 |       291 |     5431.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/juno-chorus-clone_juno-chorus-clone/unrouted.dsn)

Size: 36.9 kB · Layers: 2 · Nets: 0 · Components: 172 · Dimensions: 149.86 x 101.6 mm (152.26 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      60.01 |      N/A |     60.01 |   0+ 20+  0 |        1 |          2 |   995 |       601 |   188817.7 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/karabas-nano_karabas-nano-revA/unrouted.dsn)

Size: 108.3 kB · Layers: 2 · Nets: 23 · Components: 136 · Dimensions: 101.6 x 99.7 mm (101.3 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     302.43 |      N/A |    302.43 |   0+  2+  0 |      116 |          0 |   808 |       904 |   949073.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/karabas-nano_karabas-nano-revB/unrouted.dsn)

Size: 112.7 kB · Layers: 2 · Nets: 26 · Components: 147 · Dimensions: 101.6 x 99.7 mm (101.3 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     904.07 |      N/A |    904.07 |   0+  5+  0 |       60 |          0 |   902 |      1202 |  2591648.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/karabas-nano_karabas-nano-revC/unrouted.dsn)

Size: 143.1 kB · Layers: 2 · Nets: 53 · Components: 158 · Dimensions: 101.6 x 99.7 mm (101.3 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     903.27 |      N/A |    903.27 |   0+  4+  0 |       85 |         33 |   863 |      1086 |  2576019.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/karabas-nano_karabas-nano-revG/unrouted.dsn)

Size: 131.5 kB · Layers: 2 · Nets: 70 · Components: 157 · Dimensions: 99.57 x 99.57 mm (99.14 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     907.52 |      N/A |    907.52 |   0+  4+  0 |       86 |         10 |   861 |      1167 |  2493280.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/karabas-nano_wifi_revA/unrouted.dsn)

Size: 20.5 kB · Layers: 2 · Nets: 66 · Components: 11 · Dimensions: 51.82 x 30.99 mm (16.06 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.22 |      N/A |      2.22 |   0+  3+  0 |        0 |          0 |  1000 |       232 |     2778.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kassenautomat.mdb-interface_mdb-interface/unrouted.dsn)

Size: 59 kB · Layers: 2 · Nets: 45 · Components: 80 · Dimensions: 100.0 x 100.0 mm (100.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      28.11 |      N/A |     28.11 |   0+  9+  0 |        0 |          0 |  1000 |       544 |   108813.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Kefersender_UKW TX/unrouted.dsn)

Size: 27.6 kB · Layers: 2 · Nets: 19 · Components: 40 · Dimensions: 38.5 x 27.0 mm (10.39 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      33.37 |      N/A |     33.37 |   0+  1+  0 |        7 |         10 |   897 |       539 |   146045.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kelvindmmwifi_kelvindmmwifi/unrouted.dsn)

Size: 12 kB · Layers: 2 · Nets: 19 · Components: 18 · Dimensions: 46.99 x 20.32 mm (9.55 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.27 |      N/A |      2.27 |   0+  2+  0 |        0 |          0 |  1000 |       151 |     4120.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/keyboard_converter_adapter_ibm4704_converter_adapter_ibm4704/unrouted.dsn)

Size: 8.8 kB · Layers: 2 · Nets: 7 · Components: 10 · Dimensions: 30.5 x 29.0 mm (8.85 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.06 |      N/A |      1.06 |   0+  2+  0 |        0 |          0 |  1000 |        71 |     1284.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/keyboard_converter_converter/unrouted.dsn)

Size: 20.7 kB · Layers: 2 · Nets: 9 · Components: 61 · Dimensions: 26.0 x 37.0 mm (9.62 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      87.09 |      N/A |     87.09 |   0+  1+  0 |        5 |         23 |   914 |       527 |   325735.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Keyboard_PCB_Keyboard/unrouted.dsn)

Size: 68.8 kB · Layers: 2 · Nets: 121 · Components: 391 · Dimensions: 290.0 x 190.0 mm (551.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     301.32 |      N/A |    301.32 |   0+ 11+  0 |        0 |          2 |   999 |       910 |  1011833.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Keyboard_tenkey/unrouted.dsn)

Size: 43.6 kB · Layers: 2 · Nets: 39 · Components: 58 · Dimensions: 75.95 x 96.39 mm (73.21 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      26.57 |      N/A |     26.57 |   0+  1+  0 |        2 |          4 |   970 |       465 |    92344.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/keytee_pcb/unrouted.dsn)

Size: 14.3 kB · Layers: 2 · Nets: 9 · Components: 38 · Dimensions: 32.5 x 11.3 mm (3.67 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     164.48 |      N/A |    164.48 |   0+  1+  0 |       22 |         58 |   421 |       453 |   401886.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kicad_bbb-melzi/unrouted.dsn)

Size: 9.2 kB · Layers: 2 · Nets: 1 · Components: 7 · Dimensions: 72.39 x 20.35 mm (14.73 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.88 |      N/A |      1.88 |   0+  3+  0 |        0 |          0 |  1000 |       175 |     1204.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kicad-esp8266-led-shld_ESP8266_LED_shld/unrouted.dsn)

Size: 38.2 kB · Layers: 2 · Nets: 11 · Components: 29 · Dimensions: 59.06 x 52.7 mm (31.12 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.76 |      N/A |      4.76 |   0+  4+  0 |        0 |          0 |  1000 |       357 |    10771.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kicad-guitar-preamp_Preamp-Instructables/unrouted.dsn)

Size: 20.1 kB · Layers: 2 · Nets: 3 · Components: 11 · Dimensions: 0.0 x 0.0 mm (0.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.59 |      N/A |      0.59 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/KiCad-Library_Teensy_test_layout/unrouted.dsn)

Size: 164.9 kB · Layers: 2 · Nets: 248 · Components: 1441 · Dimensions: 254.0 x 127.0 mm (322.58 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |    1809.78 |      N/A |   1809.78 |   0+  6+  0 |       18 |        111 |   968 |      1433 |  4569067.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/KiCad-Like-a-Pro-Tutorial_proj2-sevensegment/unrouted.dsn)

Size: 8.8 kB · Layers: 2 · Nets: 1 · Components: 11 · Dimensions: 45.97 x 24.64 mm (11.33 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.55 |      N/A |      3.55 |   0+  1+  0 |        1 |          0 |   960 |       368 |     6997.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/KiCad-Like-a-Pro-Tutorial_rf24-breakout-v1/unrouted.dsn)

Size: 6.5 kB · Layers: 2 · Nets: 0 · Components: 3 · Dimensions: 33.02 x 21.84 mm (7.21 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.64 |      N/A |      2.64 |   0+  1+  0 |        0 |          1 |   980 |       123 |     1588.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/KiCad-LTC6802-2_main/unrouted.dsn)

Size: 13.1 kB · Layers: 2 · Nets: 21 · Components: 9 · Dimensions: 50.8 x 40.64 mm (20.65 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.20 |      N/A |      3.20 |   0+  3+  0 |        0 |          0 |  1000 |       259 |     6316.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kicad-projects_BatCharge/unrouted.dsn)

Size: 11.2 kB · Layers: 2 · Nets: 8 · Components: 11 · Dimensions: 20.65 x 20.0 mm (4.13 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.24 |      N/A |      2.24 |   0+  2+  0 |        0 |          0 |  1000 |       119 |     3486.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kicad-projects_ili9341-breakout/unrouted.dsn)

Size: 8.5 kB · Layers: 2 · Nets: 18 · Components: 4 · Dimensions: 48.26 x 15.24 mm (7.35 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       7.20 |      N/A |      7.20 |   0+  1+  0 |        0 |          0 |  1000 |       404 |    20913.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kicad-workshop_fancyboard/unrouted.dsn)

Size: 22.7 kB · Layers: 2 · Nets: 25 · Components: 18 · Dimensions: 48.26 x 30.48 mm (14.71 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.60 |      N/A |      4.60 |   0+  2+  0 |        0 |          0 |  1000 |       512 |     9503.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kika-in-space_analog-test-board/unrouted.dsn)

Size: 9.6 kB · Layers: 2 · Nets: 11 · Components: 17 · Dimensions: 31.5 x 16.26 mm (5.12 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.65 |      N/A |      2.65 |   0+  4+  0 |        0 |          0 |  1000 |       255 |     2679.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kika-in-space_DS8500/unrouted.dsn)

Size: 18.6 kB · Layers: 2 · Nets: 11 · Components: 21 · Dimensions: 45.72 x 21.59 mm (9.87 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.57 |      N/A |      2.57 |   0+  2+  0 |        0 |          0 |  1000 |       300 |     3411.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kit2-led-cube_led_cube/unrouted.dsn)

Size: 13.1 kB · Layers: 2 · Nets: 17 · Components: 41 · Dimensions: 40.0 x 40.0 mm (16.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     301.80 |      N/A |    301.80 |   0+  2+  0 |        0 |         68 |   869 |       573 |  1255762.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace__autosave-nunchuk_breakout/unrouted.dsn)

Size: 18 kB · Layers: 2 · Nets: 3 · Components: 14 · Dimensions: 39.0 x 18.0 mm (7.02 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.37 |      N/A |      3.37 |   0+  1+  0 |        0 |          2 |   983 |        84 |     6990.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace__autosave-postcard/unrouted.dsn)

Size: 19.6 kB · Layers: 2 · Nets: 10 · Components: 23 · Dimensions: 100.0 x 100.0 mm (100.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.94 |      N/A |      1.94 |   0+  2+  0 |        0 |          0 |  1000 |       143 |     1142.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_12_24_boost_converter/unrouted.dsn)

Size: 46.2 kB · Layers: 2 · Nets: 7 · Components: 22 · Dimensions: 45.5 x 48.5 mm (22.07 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.76 |      N/A |      1.76 |   0+  2+  0 |        0 |          0 |  1000 |       124 |      717.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_120-channel-pogo-pin-board/unrouted.dsn)

Size: 36.3 kB · Layers: 4 · Nets: 1 · Components: 17 · Dimensions: 183.45 x 73.25 mm (134.38 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     301.43 |      N/A |    301.43 |   0+  5+  0 |       65 |          0 |   578 |       619 |   754723.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_120-channel-test-board/unrouted.dsn)

Size: 27.1 kB · Layers: 2 · Nets: 128 · Components: 258 · Dimensions: 76.01 x 50.79 mm (38.61 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     901.83 |      N/A |    901.83 |   0+  1+  0 |       21 |          0 |   916 |       883 |  3175928.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_12V5A_breakout/unrouted.dsn)

Size: 19.9 kB · Layers: 2 · Nets: 1 · Components: 18 · Dimensions: 48.5 x 45.5 mm (22.07 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.08 |      N/A |      1.08 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_4_switch_array/unrouted.dsn)

Size: 27.5 kB · Layers: 2 · Nets: 10 · Components: 22 · Dimensions: 45.5 x 48.5 mm (22.07 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.01 |      N/A |      1.01 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_40-channel-hv-switching-board/unrouted.dsn)

Size: 61.3 kB · Layers: 4 · Nets: 109 · Components: 204 · Dimensions: 84.0 x 92.0 mm (77.28 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     904.79 |      N/A |    904.79 |   0+ 11+  0 |        3 |          0 |   995 |      1043 |  2740765.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_8_switch_array/unrouted.dsn)

Size: 35.7 kB · Layers: 2 · Nets: 17 · Components: 36 · Dimensions: 45.5 x 71.0 mm (32.3 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.22 |      N/A |      2.22 |   0+  2+  0 |        0 |          0 |  1000 |       424 |        0.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_antenna_analyser/unrouted.dsn)

Size: 39.9 kB · Layers: 2 · Nets: 51 · Components: 34 · Dimensions: 100.0 x 50.0 mm (50.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       5.32 |      N/A |      5.32 |   0+  4+  0 |        0 |          2 |   994 |       160 |    10354.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_aquarius/unrouted.dsn)

Size: 49.2 kB · Layers: 2 · Nets: 34 · Components: 79 · Dimensions: 74.0 x 120.0 mm (88.8 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      55.08 |      N/A |     55.08 |   0+  7+  0 |        0 |          0 |  1000 |       490 |   192099.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_ardfpga/unrouted.dsn)

Size: 67.9 kB · Layers: 2 · Nets: 46 · Components: 76 · Dimensions: 69.04 x 53.8 mm (37.14 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     214.49 |      N/A |    214.49 |   0+  1+  0 |        4 |          0 |   981 |       735 |   755191.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_Arduino_uno_sheild/unrouted.dsn)

Size: 10.9 kB · Layers: 2 · Nets: 4 · Components: 16 · Dimensions: 68.58 x 53.28 mm (36.54 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.21 |      N/A |      4.21 |   0+  4+  0 |        0 |          0 |  1000 |       188 |     7164.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_BalthazarKeyboard3-keycaps/unrouted.dsn)

Size: 62.8 kB · Layers: 4 · Nets: 94 · Components: 199 · Dimensions: 290.58 x 127.0 mm (369.04 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     339.42 |      N/A |    339.42 |   0+  1+  0 |       10 |          2 |   967 |       651 |   957609.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_BalthazarPSU3/unrouted.dsn)

Size: 61.4 kB · Layers: 4 · Nets: 52 · Components: 84 · Dimensions: 0.0 x 0.0 mm (0.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     301.77 |      N/A |    301.77 |   0+  1+  0 |        0 |         78 |   930 |       566 |  1427195.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_bbq10breakout/unrouted.dsn)

Size: 9.6 kB · Layers: 2 · Nets: 1 · Components: 4 · Dimensions: 16.51 x 21.59 mm (3.56 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      22.31 |      N/A |     22.31 |   0+  1+  0 |        2 |          0 |   933 |       470 |    66498.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_beehive/unrouted.dsn)

Size: 76.6 kB · Layers: 2 · Nets: 39 · Components: 32 · Dimensions: 76.0 x 85.0 mm (64.6 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.94 |      N/A |      4.94 |   0+  3+  0 |        0 |          0 |  1000 |       316 |     6471.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_bobbycar/unrouted.dsn)

Size: 49.8 kB · Layers: 2 · Nets: 10 · Components: 29 · Dimensions: 61.72 x 26.92 mm (16.62 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       8.61 |      N/A |      8.61 |   0+  1+  0 |        1 |          6 |   962 |       452 |    29664.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_bomanz/unrouted.dsn)

Size: 34.4 kB · Layers: 2 · Nets: 26 · Components: 20 · Dimensions: 65.02 x 30.04 mm (19.53 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      22.93 |      N/A |     22.93 |   0+  1+  0 |        2 |          5 |   948 |       500 |    74502.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_BQ25570_Harvester/unrouted.dsn)

Size: 16.4 kB · Layers: 2 · Nets: 9 · Components: 20 · Dimensions: 12.7 x 20.83 mm (2.65 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      32.80 |      N/A |     32.80 |   0+  1+  0 |        6 |         40 |   741 |       444 |    96400.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_Brk40p/unrouted.dsn)

Size: 15.8 kB · Layers: 2 · Nets: 40 · Components: 6 · Dimensions: 53.09 x 51.82 mm (27.51 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      15.55 |      N/A |     15.55 |   0+  6+  0 |        0 |          0 |  1000 |       342 |    30799.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_CA3306E/unrouted.dsn)

Size: 16.1 kB · Layers: 2 · Nets: 0 · Components: 10 · Dimensions: 65.0 x 30.0 mm (19.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      27.85 |      N/A |     27.85 |   0+  1+  0 |        9 |          0 |   800 |       493 |    74375.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_CH330/unrouted.dsn)

Size: 11.7 kB · Layers: 2 · Nets: 3 · Components: 9 · Dimensions: 10.0 x 10.0 mm (1.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      10.92 |      N/A |     10.92 |   0+  1+  0 |        2 |         12 |   817 |       415 |    24682.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_ChaosLooper/unrouted.dsn)

Size: 28.5 kB · Layers: 2 · Nets: 29 · Components: 33 · Dimensions: 136.5 x 67.2 mm (91.73 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.74 |      N/A |      3.74 |   0+  2+  0 |        0 |          0 |  1000 |       404 |     6041.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_CO2/unrouted.dsn)

Size: 36.8 kB · Layers: 2 · Nets: 12 · Components: 31 · Dimensions: 26.67 x 48.89 mm (13.04 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      15.99 |      N/A |     15.99 |   0+  7+  0 |        0 |          2 |   996 |       468 |    47203.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_CocoMixtape_UGM_Kicad/unrouted.dsn)

Size: 96.3 kB · Layers: 2 · Nets: 9 · Components: 30 · Dimensions: 86.8 x 45.51 mm (39.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      19.18 |      N/A |     19.18 |   0+  1+  0 |        1 |         14 |   941 |       388 |    38853.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_Control%20Board/unrouted.dsn)

Size: 42.4 kB · Layers: 2 · Nets: 112 · Components: 90 · Dimensions: 154.0 x 175.0 mm (269.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      43.84 |      N/A |     43.84 |   0+  5+  0 |        0 |          0 |  1000 |       514 |   111754.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_cseduinov4/unrouted.dsn)

Size: 45.7 kB · Layers: 2 · Nets: 28 · Components: 22 · Dimensions: 48.26 x 50.8 mm (24.52 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.92 |      N/A |      2.92 |   0+  3+  0 |        0 |          0 |  1000 |       187 |     2764.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_d20_r0.1/unrouted.dsn)

Size: 75.6 kB · Layers: 4 · Nets: 0 · Components: 91 · Dimensions: 39.25 x 31.0 mm (12.17 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     302.86 |      N/A |    302.86 |   0+  7+  0 |       10 |         14 |   956 |       853 |  1028041.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_d20_tri_r0.2/unrouted.dsn)

Size: 50.4 kB · Layers: 4 · Nets: 0 · Components: 152 · Dimensions: 36.07 x 31.24 mm (11.27 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     901.13 |      N/A |    901.13 |   0+  1+  0 |      372 |          0 |   327 |       979 |  2163970.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_d20_tri_r0.3/unrouted.dsn)

Size: 49.6 kB · Layers: 4 · Nets: 0 · Components: 152 · Dimensions: 36.07 x 31.24 mm (11.27 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     901.93 |      N/A |    901.93 |   0+  1+  0 |      355 |          0 |   358 |      1031 |  2108166.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_d20_tri_r1.0/unrouted.dsn)

Size: 116.8 kB · Layers: 4 · Nets: 93 · Components: 152 · Dimensions: 36.07 x 31.24 mm (11.27 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     900.71 |      N/A |    900.71 |   0+  2+  0 |      511 |         11 |    70 |       580 |  2650545.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_DIY_detector/unrouted.dsn)

Size: 46 kB · Layers: 2 · Nets: 9 · Components: 35 · Dimensions: 75.0 x 26.0 mm (19.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      18.79 |      N/A |     18.79 |   0+  1+  0 |        0 |         10 |   959 |       464 |    36257.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_Driverino-Shield/unrouted.dsn)

Size: 47.2 kB · Layers: 2 · Nets: 16 · Components: 58 · Dimensions: 68.58 x 53.34 mm (36.58 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      39.55 |      N/A |     39.55 |   0+  1+  0 |        1 |         36 |   937 |       529 |   136825.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_dropbot_control_board/unrouted.dsn)

Size: 85.8 kB · Layers: 4 · Nets: 70 · Components: 141 · Dimensions: 115.0 x 92.0 mm (105.8 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     146.24 |      N/A |    146.24 |   0+  1+  0 |        4 |          5 |   984 |       664 |   658687.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_dropbot-front-panel/unrouted.dsn)

Size: 43.7 kB · Layers: 4 · Nets: 7 · Components: 29 · Dimensions: 108.0 x 68.75 mm (74.25 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     301.47 |      N/A |    301.47 |   0+  9+  0 |        6 |          0 |   971 |       669 |   805663.1 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_dynamixel_shield/unrouted.dsn)

Size: 31.2 kB · Layers: 2 · Nets: 25 · Components: 32 · Dimensions: 68.58 x 53.34 mm (36.58 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      17.18 |      N/A |     17.18 |   0+  1+  0 |        5 |          0 |   929 |       521 |    61471.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_EEZ%20DIB%20MCU%20r1B2/unrouted.dsn)

Size: 213.4 kB · Layers: 4 · Nets: 219 · Components: 194 · Dimensions: 216.4 x 145.8 mm (315.51 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     908.79 |      N/A |    908.79 |   0+  2+  0 |       56 |          9 |   909 |      1446 |  2459679.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_esp8266/unrouted.dsn)

Size: 39.8 kB · Layers: 2 · Nets: 0 · Components: 30 · Dimensions: 50.0 x 50.0 mm (25.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.45 |      N/A |      4.45 |   0+  3+  0 |        0 |          2 |   992 |       283 |     4553.1 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_esp8266-12-breakout/unrouted.dsn)

Size: 17.7 kB · Layers: 2 · Nets: 2 · Components: 15 · Dimensions: 32.39 x 39.37 mm (12.75 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.74 |      N/A |      4.74 |   0+  3+  0 |        0 |          0 |  1000 |       412 |     7827.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_ESP8266-PowerMonitor/unrouted.dsn)

Size: 28.5 kB · Layers: 2 · Nets: 6 · Components: 22 · Dimensions: 69.5 x 27.2 mm (18.9 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.61 |      N/A |      2.61 |   0+  2+  0 |        0 |          1 |   996 |       352 |     3157.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_ESPTINY86_Mixtape_version2/unrouted.dsn)

Size: 57 kB · Layers: 2 · Nets: 17 · Components: 34 · Dimensions: 99.0 x 64.81 mm (64.16 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     127.40 |      N/A |    127.40 |   0+  1+  0 |        6 |         12 |   892 |       493 |   344537.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_esptiny86_Stompbox/unrouted.dsn)

Size: 72.3 kB · Layers: 2 · Nets: 13 · Components: 28 · Dimensions: 54.75 x 106.4 mm (58.25 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       7.19 |      N/A |      7.19 |   0+  4+  0 |        0 |          0 |  1000 |       477 |    18376.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_f-91w/unrouted.dsn)

Size: 23.6 kB · Layers: 2 · Nets: 18 · Components: 37 · Dimensions: 24.4 x 24.38 mm (5.95 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     176.44 |      N/A |    176.44 |   0+  1+  0 |        6 |         52 |   833 |       561 |   516473.2 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_fast_diode_pcb/unrouted.dsn)

Size: 21.7 kB · Layers: 2 · Nets: 4 · Components: 7 · Dimensions: 48.89 x 29.21 mm (14.28 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.44 |      N/A |      3.44 |   0+  1+  0 |        0 |          2 |   979 |       171 |     8952.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_filaSens/unrouted.dsn)

Size: 26 kB · Layers: 2 · Nets: 2 · Components: 16 · Dimensions: 50.0 x 20.0 mm (10.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.76 |      N/A |      3.76 |   0+  4+  0 |        0 |          0 |  1000 |       163 |     5065.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_firefly/unrouted.dsn)

Size: 106.6 kB · Layers: 4 · Nets: 21 · Components: 80 · Dimensions: 85.0 x 70.5 mm (59.92 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      59.56 |      N/A |     59.56 |   0+  1+  0 |        1 |        163 |   855 |       569 |   253039.9 |    3 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_flypi/unrouted.dsn)

Size: 55.3 kB · Layers: 2 · Nets: 59 · Components: 53 · Dimensions: 57.2 x 146.6 mm (83.86 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     215.43 |      N/A |    215.43 |   0+  1+  0 |        0 |         20 |   970 |       589 |  1129445.1 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_flypi_v2/unrouted.dsn)

Size: 57 kB · Layers: 2 · Nets: 14 · Components: 35 · Dimensions: 76.0 x 85.0 mm (64.6 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       6.09 |      N/A |      6.09 |   0+  4+  0 |        0 |          0 |  1000 |       377 |    13674.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_gas_sensor/unrouted.dsn)

Size: 33.6 kB · Layers: 2 · Nets: 3 · Components: 15 · Dimensions: 45.5 x 32.5 mm (14.79 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.90 |      N/A |      0.90 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_GPSMux/unrouted.dsn)

Size: 18.8 kB · Layers: 2 · Nets: 8 · Components: 20 · Dimensions: 20.0 x 26.0 mm (5.2 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes      |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :--------- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |        N/A |      N/A |       N/A |   0+  0+  0 |      N/A |        N/A |   N/A |         0 |        0.0 |    1 / 0 | LOAD ERROR |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_grove_adaptor/unrouted.dsn)

Size: 7.1 kB · Layers: 2 · Nets: 2 · Components: 4 · Dimensions: 14.5 x 12.5 mm (1.81 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.34 |      N/A |      0.34 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_hack/unrouted.dsn)

Size: 33.8 kB · Layers: 2 · Nets: 13 · Components: 32 · Dimensions: 36.83 x 19.05 mm (7.02 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     301.59 |      N/A |    301.59 |   0+ 20+  0 |       42 |        126 |   467 |       543 |   783878.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_hbridge_driver/unrouted.dsn)

Size: 55.8 kB · Layers: 2 · Nets: 14 · Components: 37 · Dimensions: 71.0 x 45.5 mm (32.3 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.32 |      N/A |      2.32 |   0+  3+  0 |        0 |          0 |  1000 |       288 |     3696.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_hp_led_switch/unrouted.dsn)

Size: 31.6 kB · Layers: 2 · Nets: 14 · Components: 30 · Dimensions: 45.5 x 71.0 mm (32.3 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.59 |      N/A |      1.59 |   0+  2+  0 |        0 |          0 |  1000 |        87 |     1323.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_hum_temp_sensor/unrouted.dsn)

Size: 23.2 kB · Layers: 2 · Nets: 4 · Components: 12 · Dimensions: 48.5 x 27.5 mm (13.34 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.55 |      N/A |      0.55 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_ideal_diode/unrouted.dsn)

Size: 22.9 kB · Layers: 2 · Nets: 4 · Components: 54 · Dimensions: 40.0 x 25.0 mm (10.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      21.06 |      N/A |     21.06 |   0+  1+  0 |        0 |         18 |   954 |       592 |    95959.1 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_ir_sensor/unrouted.dsn)

Size: 16.7 kB · Layers: 2 · Nets: 2 · Components: 5 · Dimensions: 32.0 x 100.0 mm (32.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.46 |      N/A |      0.46 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_Lcr_addon/unrouted.dsn)

Size: 57.2 kB · Layers: 2 · Nets: 10 · Components: 17 · Dimensions: 85.09 x 55.12 mm (46.9 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.31 |      N/A |      3.31 |   0+  3+  0 |        0 |          0 |  1000 |       384 |     4610.1 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_LED%20Zappelin/unrouted.dsn)

Size: 53.7 kB · Layers: 2 · Nets: 39 · Components: 45 · Dimensions: 125.0 x 75.0 mm (93.75 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      15.29 |      N/A |     15.29 |   0+ 18+  0 |        1 |          0 |   992 |       421 |    42845.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_led_driver/unrouted.dsn)

Size: 92.6 kB · Layers: 2 · Nets: 12 · Components: 26 · Dimensions: 75.0 x 50.0 mm (37.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      12.43 |      N/A |     12.43 |   0+  1+  0 |        0 |          4 |   985 |       525 |    49661.1 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_level_shifter/unrouted.dsn)

Size: 24.2 kB · Layers: 2 · Nets: 0 · Components: 25 · Dimensions: 45.5 x 48.5 mm (22.07 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       5.60 |      N/A |      5.60 |   0+  1+  0 |        0 |          2 |   989 |       400 |    15381.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_Minisumo_V2.1/unrouted.dsn)

Size: 54.4 kB · Layers: 4 · Nets: 51 · Components: 31 · Dimensions: 95.0 x 78.0 mm (74.1 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      37.39 |      N/A |     37.39 |   0+  1+  0 |        1 |          0 |   979 |       496 |    80142.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_minisumo_v3/unrouted.dsn)

Size: 63.8 kB · Layers: 4 · Nets: 56 · Components: 30 · Dimensions: 98.91 x 78.0 mm (77.15 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     103.10 |      N/A |    103.10 |   0+  1+  0 |        2 |          0 |   986 |       571 |   307873.7 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_Mixtape_Barebone/unrouted.dsn)

Size: 10.7 kB · Layers: 2 · Nets: 9 · Components: 31 · Dimensions: 35.49 x 23.23 mm (8.24 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       5.37 |      N/A |      5.37 |   0+  1+  0 |        1 |         22 |   841 |       412 |    15883.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_neotron-32/unrouted.dsn)

Size: 155 kB · Layers: 4 · Nets: 118 · Components: 97 · Dimensions: 166.9 x 140.67 mm (234.78 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      73.29 |      N/A |     73.29 |   0+  5+  0 |        0 |          0 |  1000 |       618 |   205798.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_nunchuk_breakout/unrouted.dsn)

Size: 18.4 kB · Layers: 2 · Nets: 3 · Components: 14 · Dimensions: 39.0 x 18.0 mm (7.02 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.65 |      N/A |      3.65 |   0+  1+  0 |        0 |          3 |   975 |       380 |     8862.2 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_Oak/unrouted.dsn)

Size: 120.7 kB · Layers: 2 · Nets: 77 · Components: 76 · Dimensions: 100.0 x 40.0 mm (40.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     300.49 |      N/A |    300.49 |   0+  4+  0 |      106 |          0 |   243 |       654 |   460526.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_OpenSpritzer_1.3/unrouted.dsn)

Size: 71.8 kB · Layers: 2 · Nets: 39 · Components: 30 · Dimensions: 57.5 x 85.0 mm (48.88 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.04 |      N/A |      3.04 |   0+  3+  0 |        0 |          2 |   992 |        92 |     5455.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_OSO-BOOK-C1/unrouted.dsn)

Size: 70.4 kB · Layers: 2 · Nets: 41 · Components: 37 · Dimensions: 85.0 x 115.0 mm (97.75 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     201.89 |      N/A |    201.89 |   0+  1+  0 |       20 |        113 |   702 |       574 |   574864.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_OtterCastAudioV2/unrouted.dsn)

Size: 87.7 kB · Layers: 4 · Nets: 266 · Components: 226 · Dimensions: 25.0 x 49.27 mm (12.32 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     901.02 |      N/A |    901.02 |   0+  8+  0 |      103 |          6 |   587 |       628 |  2336425.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_OtterPill/unrouted.dsn)

Size: 53.9 kB · Layers: 2 · Nets: 57 · Components: 45 · Dimensions: 43.2 x 17.6 mm (7.6 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     300.94 |      N/A |    300.94 |   0+  1+  0 |       19 |          4 |   860 |       584 |   890230.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_OtterPillG/unrouted.dsn)

Size: 30.9 kB · Layers: 2 · Nets: 18 · Components: 38 · Dimensions: 43.2 x 17.6 mm (7.6 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     302.33 |      N/A |    302.33 |   0+  1+  0 |        0 |         64 |   883 |       582 |   856483.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_OtterScreen/unrouted.dsn)

Size: 38.8 kB · Layers: 2 · Nets: 15 · Components: 33 · Dimensions: 44.8 x 31.4 mm (14.07 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      20.92 |      N/A |     20.92 |   0+  6+  0 |        0 |          0 |  1000 |       500 |    75881.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_OutlineChaos/unrouted.dsn)

Size: 28.3 kB · Layers: 2 · Nets: 30 · Components: 31 · Dimensions: 150.5 x 81.5 mm (122.66 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.38 |      N/A |      4.38 |   0+  4+  0 |        0 |          0 |  1000 |       588 |    11924.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_PCBs/unrouted.dsn)

Size: 38 kB · Layers: 2 · Nets: 5 · Components: 28 · Dimensions: 45.19 x 53.89 mm (24.35 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.97 |      N/A |      2.97 |   0+  2+  0 |        0 |          0 |  1000 |       208 |     4137.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_peltier/unrouted.dsn)

Size: 60.9 kB · Layers: 2 · Nets: 16 · Components: 40 · Dimensions: 71.0 x 45.5 mm (32.3 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       6.39 |      N/A |      6.39 |   0+  1+  0 |        1 |          0 |   985 |       469 |    22098.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_piezo_amplifier/unrouted.dsn)

Size: 60.3 kB · Layers: 2 · Nets: 29 · Components: 43 · Dimensions: 0.0 x 0.0 mm (0.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      21.03 |      N/A |     21.03 |   0+  1+  0 |        2 |          0 |   973 |       436 |    56798.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_PIS/unrouted.dsn)

Size: 43.1 kB · Layers: 2 · Nets: 9 · Components: 24 · Dimensions: 26.67 x 48.89 mm (13.04 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      15.45 |      N/A |     15.45 |   0+  2+  0 |        0 |          8 |   969 |       517 |    58542.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_pmt_combiner/unrouted.dsn)

Size: 60.5 kB · Layers: 2 · Nets: 16 · Components: 19 · Dimensions: 80.0 x 64.5 mm (51.6 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.71 |      N/A |      1.71 |   0+  2+  0 |        0 |          0 |  1000 |       259 |     1198.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_postcard/unrouted.dsn)

Size: 19.6 kB · Layers: 2 · Nets: 10 · Components: 23 · Dimensions: 100.0 x 100.0 mm (100.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.88 |      N/A |      1.88 |   0+  2+  0 |        0 |          0 |  1000 |        20 |     1317.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_Potentiometer_mount_16LED/unrouted.dsn)

Size: 17.7 kB · Layers: 2 · Nets: 48 · Components: 22 · Dimensions: 97.5 x 41.5 mm (40.46 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       6.87 |      N/A |      6.87 |   0+  4+  0 |        0 |          0 |  1000 |       268 |    10109.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_Potentiometer_mount_24LED/unrouted.dsn)

Size: 18.5 kB · Layers: 2 · Nets: 72 · Components: 27 · Dimensions: 143.61 x 36.51 mm (52.43 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      28.23 |      N/A |     28.23 |   0+  7+  0 |        0 |          0 |  1000 |       490 |    62476.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_Potentiometer_mount_4LED/unrouted.dsn)

Size: 9.2 kB · Layers: 2 · Nets: 12 · Components: 7 · Dimensions: 40.0 x 30.0 mm (12.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.65 |      N/A |      0.65 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_Potentiometer_mount_8LED/unrouted.dsn)

Size: 8.2 kB · Layers: 2 · Nets: 12 · Components: 11 · Dimensions: 80.0 x 40.0 mm (32.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.74 |      N/A |      1.74 |   0+  4+  0 |        0 |          0 |  1000 |        64 |      545.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_power_supply/unrouted.dsn)

Size: 60.8 kB · Layers: 2 · Nets: 7 · Components: 20 · Dimensions: 45.5 x 48.5 mm (22.07 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.31 |      N/A |      1.31 |   0+  2+  0 |        0 |          0 |  1000 |        39 |     1192.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_PSLab/unrouted.dsn)

Size: 79.5 kB · Layers: 4 · Nets: 51 · Components: 144 · Dimensions: 72.9 x 53.6 mm (39.07 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     292.89 |      N/A |    292.89 |   0+  1+  0 |        3 |          8 |   986 |       716 |  1019900.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_push-on-hold-off/unrouted.dsn)

Size: 14.7 kB · Layers: 2 · Nets: 5 · Components: 17 · Dimensions: 22.86 x 12.7 mm (2.9 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       8.24 |      N/A |      8.24 |   0+ 11+  0 |        0 |          0 |  1000 |       496 |    22017.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_PWR/unrouted.dsn)

Size: 37.2 kB · Layers: 2 · Nets: 10 · Components: 19 · Dimensions: 26.67 x 53.34 mm (14.23 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      12.64 |      N/A |     12.64 |   0+  1+  0 |        5 |          6 |   793 |       420 |    27214.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_RPi_shield/unrouted.dsn)

Size: 14.4 kB · Layers: 2 · Nets: 26 · Components: 7 · Dimensions: 64.01 x 56.13 mm (35.93 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.21 |      N/A |      3.21 |   0+  1+  0 |        1 |          0 |   955 |       255 |     4518.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_sensor/unrouted.dsn)

Size: 52.7 kB · Layers: 2 · Nets: 34 · Components: 68 · Dimensions: 62.0 x 65.0 mm (40.3 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      77.75 |      N/A |     77.75 |   0+  1+  0 |        2 |          4 |   985 |       536 |   335056.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_solenoid_driver/unrouted.dsn)

Size: 31.8 kB · Layers: 2 · Nets: 13 · Components: 27 · Dimensions: 45.5 x 48.5 mm (22.07 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.16 |      N/A |      1.16 |   0+  2+  0 |        0 |          0 |  1000 |       203 |     1393.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_sop8breakout/unrouted.dsn)

Size: 4 kB · Layers: 2 · Nets: 8 · Components: 48 · Dimensions: 83.32 x 63.24 mm (52.69 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.45 |      N/A |      0.45 |   0+  1+  0 |        0 |          0 |  1000 |         0 |        0.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_spike_n_hold/unrouted.dsn)

Size: 88.2 kB · Layers: 2 · Nets: 26 · Components: 54 · Dimensions: 63.0 x 110.5 mm (69.61 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.53 |      N/A |      4.53 |   0+  2+  0 |        0 |          0 |  1000 |       197 |     7578.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_Spikeling_ESP32/unrouted.dsn)

Size: 69.2 kB · Layers: 2 · Nets: 44 · Components: 43 · Dimensions: 120.0 x 80.0 mm (96.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      16.62 |      N/A |     16.62 |   0+  1+  0 |        1 |          2 |   985 |       502 |    42816.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_Spikeling_V2.1/unrouted.dsn)

Size: 95.2 kB · Layers: 2 · Nets: 60 · Components: 51 · Dimensions: 120.0 x 80.0 mm (96.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      26.40 |      N/A |     26.40 |   0+  1+  0 |        1 |         20 |   955 |       391 |    73481.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_Spikeling_V2.2/unrouted.dsn)

Size: 94.9 kB · Layers: 2 · Nets: 60 · Components: 51 · Dimensions: 120.0 x 80.0 mm (96.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      23.99 |      N/A |     23.99 |   0+  1+  0 |        1 |         30 |   935 |       385 |    67532.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_Spikeling_V2.2c/unrouted.dsn)

Size: 87.5 kB · Layers: 2 · Nets: 60 · Components: 52 · Dimensions: 120.0 x 80.0 mm (96.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     114.63 |      N/A |    114.63 |   0+  2+  0 |        0 |         12 |   978 |       598 |   425603.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_ss_relay/unrouted.dsn)

Size: 23.4 kB · Layers: 2 · Nets: 22 · Components: 36 · Dimensions: 98.0 x 78.0 mm (76.44 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       5.98 |      N/A |      5.98 |   0+  2+  0 |        0 |          0 |  1000 |       325 |    11891.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_stack-light/unrouted.dsn)

Size: 105.5 kB · Layers: 2 · Nets: 91 · Components: 131 · Dimensions: 132.0 x 60.0 mm (79.2 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     190.05 |      N/A |    190.05 |   0+  1+  0 |        1 |          0 |   997 |       853 |   686445.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_sympetrum-v2%20FF1.1/unrouted.dsn)

Size: 58.4 kB · Layers: 2 · Nets: 40 · Components: 67 · Dimensions: 98.76 x 76.76 mm (75.81 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     300.97 |      N/A |    300.97 |   0+  7+  0 |       41 |         18 |   695 |       536 |   576445.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_sympetrum-v2%20NFF1.1/unrouted.dsn)

Size: 41.2 kB · Layers: 2 · Nets: 66 · Components: 49 · Dimensions: 40.0 x 65.0 mm (26.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     189.70 |      N/A |    189.70 |   0+  1+  0 |       14 |          0 |   878 |       566 |   531297.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_sympetrum-v2%20NFF1/unrouted.dsn)

Size: 34 kB · Layers: 2 · Nets: 63 · Components: 47 · Dimensions: 50.0 x 50.0 mm (25.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      27.74 |      N/A |     27.74 |   0+ 18+  0 |        1 |          0 |   991 |       540 |   122641.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_T32_ref/unrouted.dsn)

Size: 50.5 kB · Layers: 4 · Nets: 7 · Components: 89 · Dimensions: 71.12 x 25.4 mm (18.06 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      81.86 |      N/A |     81.86 |   0+  8+  0 |        0 |          0 |  1000 |       660 |   227887.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_teensy-fx/unrouted.dsn)

Size: 68 kB · Layers: 4 · Nets: 63 · Components: 82 · Dimensions: 60.0 x 100.0 mm (60.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     302.06 |      N/A |    302.06 |   0+  9+  0 |        5 |          0 |   974 |       864 |   674248.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_temp_breakout/unrouted.dsn)

Size: 16.9 kB · Layers: 2 · Nets: 1 · Components: 14 · Dimensions: 45.5 x 48.5 mm (22.07 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.00 |      N/A |      1.00 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_test-jig/unrouted.dsn)

Size: 57.5 kB · Layers: 2 · Nets: 43 · Components: 131 · Dimensions: 207.5 x 45.5 mm (94.41 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     301.86 |      N/A |    301.86 |   0+  1+  0 |        0 |        129 |   929 |       780 |  1366720.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_threeboard/unrouted.dsn)

Size: 28.7 kB · Layers: 2 · Nets: 25 · Components: 52 · Dimensions: 58.17 x 49.28 mm (28.67 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      13.98 |      N/A |     13.98 |   0+  5+  0 |        0 |          0 |  1000 |       433 |    38349.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_tomu/unrouted.dsn)

Size: 14.9 kB · Layers: 2 · Nets: 5 · Components: 16 · Dimensions: 13.0 x 11.0 mm (1.43 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      30.29 |      N/A |     30.29 |   0+  1+  0 |        6 |         20 |   767 |       461 |    96670.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_training_board/unrouted.dsn)

Size: 69.5 kB · Layers: 2 · Nets: 62 · Components: 77 · Dimensions: 99.08 x 99.08 mm (98.17 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      13.83 |      N/A |     13.83 |   0+ 18+  0 |        1 |          0 |   993 |       498 |    43481.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_training_board_v02/unrouted.dsn)

Size: 79 kB · Layers: 2 · Nets: 69 · Components: 76 · Dimensions: 100.0 x 100.0 mm (100.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       8.15 |      N/A |      8.15 |   0+  3+  0 |        0 |          0 |  1000 |       441 |    17355.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_trans_switch_volt_amp/unrouted.dsn)

Size: 46.2 kB · Layers: 2 · Nets: 108 · Components: 20 · Dimensions: 111.51 x 85.09 mm (94.88 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.11 |      N/A |      3.11 |   0+  3+  0 |        0 |          0 |  1000 |       180 |     3442.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_TS100C/unrouted.dsn)

Size: 61.2 kB · Layers: 4 · Nets: 79 · Components: 64 · Dimensions: 84.0 x 10.0 mm (8.4 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     173.24 |      N/A |    173.24 |   0+  1+  0 |       13 |         14 |   784 |       528 |   436882.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_tt_nano_HAT_b1/unrouted.dsn)

Size: 24.3 kB · Layers: 2 · Nets: 24 · Components: 21 · Dimensions: 35.56 x 49.53 mm (17.61 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.33 |      N/A |      2.33 |   0+  2+  0 |        0 |          0 |  1000 |       176 |     3411.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_tt_nano_HAT_b2/unrouted.dsn)

Size: 29 kB · Layers: 2 · Nets: 31 · Components: 25 · Dimensions: 34.0 x 59.0 mm (20.06 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      10.89 |      N/A |     10.89 |   0+  1+  0 |        1 |          0 |   982 |       488 |    31908.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_tt_opt101_module_b1/unrouted.dsn)

Size: 9 kB · Layers: 2 · Nets: 3 · Components: 4 · Dimensions: 15.24 x 22.86 mm (3.48 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.90 |      N/A |      0.90 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_TuneShroom/unrouted.dsn)

Size: 23.6 kB · Layers: 2 · Nets: 12 · Components: 44 · Dimensions: 89.43 x 96.35 mm (86.17 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      12.83 |      N/A |     12.83 |   0+  4+  0 |        0 |          0 |  1000 |       372 |    31312.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_Unifying/unrouted.dsn)

Size: 129 kB · Layers: 4 · Nets: 261 · Components: 132 · Dimensions: 0.0 x 0.0 mm (0.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     438.03 |      N/A |    438.03 |   0+  1+  0 |       48 |         42 |   662 |       695 |  1134262.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_USB-C-Screen-Adapter/unrouted.dsn)

Size: 63 kB · Layers: 4 · Nets: 33 · Components: 65 · Dimensions: 40.0 x 20.8 mm (8.32 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     302.65 |      N/A |    302.65 |   0+ 19+  0 |        3 |         14 |   971 |       682 |   871148.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_USB-C-Screen-Adapter-LDR6023SS/unrouted.dsn)

Size: 36.5 kB · Layers: 2 · Nets: 19 · Components: 42 · Dimensions: 31.0 x 34.35 mm (10.65 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      36.49 |      N/A |     36.49 |   0+  9+  0 |        0 |          6 |   991 |       461 |   131700.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_USBee32-S2/unrouted.dsn)

Size: 56.1 kB · Layers: 4 · Nets: 18 · Components: 53 · Dimensions: 28.14 x 68.26 mm (19.21 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     304.64 |      N/A |    304.64 |   0+  1+  0 |        0 |         12 |   985 |       669 |   720403.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_USBI2C01/unrouted.dsn)

Size: 28.2 kB · Layers: 2 · Nets: 26 · Components: 26 · Dimensions: 29.97 x 40.13 mm (12.03 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      25.15 |      N/A |     25.15 |   0+  1+  0 |        1 |          2 |   981 |       465 |    79710.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_USB-LED-Otter/unrouted.dsn)

Size: 19.2 kB · Layers: 2 · Nets: 28 · Components: 14 · Dimensions: 12.0 x 15.0 mm (1.8 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      40.64 |      N/A |     40.64 |   0+  1+  0 |        5 |         11 |   815 |       472 |   102480.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/kitspace_XassetteAsterisk/unrouted.dsn)

Size: 103.1 kB · Layers: 2 · Nets: 46 · Components: 145 · Dimensions: 56.0 x 56.0 mm (31.36 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     906.36 |      N/A |    906.36 |   0+  8+  0 |       40 |          2 |   909 |       903 |  2652704.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/KiwiSDR_PCB_ant/unrouted.dsn)

Size: 18.9 kB · Layers: 2 · Nets: 0 · Components: 85 · Dimensions: 138.0 x 38.0 mm (52.44 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      51.00 |      N/A |     51.00 |   0+  1+  0 |        2 |          5 |   981 |       562 |   186955.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/klangorium_logic_noise_playground/unrouted.dsn)

Size: 74.5 kB · Layers: 2 · Nets: 45 · Components: 160 · Dimensions: 200.0 x 100.0 mm (200.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      37.42 |      N/A |     37.42 |   0+  3+  0 |        0 |          0 |  1000 |       563 |   141157.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/komputer-klavier_KomputerKlavier/unrouted.dsn)

Size: 32.8 kB · Layers: 2 · Nets: 43 · Components: 19 · Dimensions: 58.42 x 50.8 mm (29.68 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       5.08 |      N/A |      5.08 |   0+  3+  0 |        0 |          0 |  1000 |       476 |    11208.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/KosselHotendBoard_KosselHotendPCB/unrouted.dsn)

Size: 6.5 kB · Layers: 2 · Nets: 5 · Components: 10 · Dimensions: 21.64 x 35.4 mm (7.66 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.81 |      N/A |      1.81 |   0+  3+  0 |        0 |          0 |  1000 |       175 |      658.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/L6235-PCB_L6235/unrouted.dsn)

Size: 24.8 kB · Layers: 2 · Nets: 10 · Components: 34 · Dimensions: 69.85 x 66.04 mm (46.13 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.69 |      N/A |      2.69 |   0+  2+  0 |        0 |          0 |  1000 |       419 |     2849.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/LadybugLiteBlue_HW_LadybugBlueLite/unrouted.dsn)

Size: 44.3 kB · Layers: 2 · Nets: 32 · Components: 71 · Dimensions: 57.5 x 52.5 mm (30.19 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      29.94 |      N/A |     29.94 |   0+ 18+  0 |        1 |          0 |   993 |       517 |   105546.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/LadyBugShield_LadyBugShield/unrouted.dsn)

Size: 28.2 kB · Layers: 2 · Nets: 0 · Components: 51 · Dimensions: 66.89 x 54.57 mm (36.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      51.11 |      N/A |     51.11 |   0+  1+  0 |        3 |          0 |   972 |       465 |   163462.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/LadyBugShield_LBS-TEST1/unrouted.dsn)

Size: 12.4 kB · Layers: 2 · Nets: 0 · Components: 31 · Dimensions: 39.17 x 36.63 mm (14.35 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       5.48 |      N/A |      5.48 |   0+  4+  0 |        0 |          1 |   996 |       328 |     9217.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/laptimer58_Chickadee/unrouted.dsn)

Size: 32 kB · Layers: 2 · Nets: 22 · Components: 39 · Dimensions: 34.5 x 67.25 mm (23.2 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      26.60 |      N/A |     26.60 |   0+  1+  0 |        1 |          0 |   987 |       486 |    98304.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/lattice-ice40-hx8kevb-sdram_hx8kevb-sdram/unrouted.dsn)

Size: 26.4 kB · Layers: 2 · Nets: 1 · Components: 17 · Dimensions: 56.0 x 74.1 mm (41.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      27.89 |      N/A |     27.89 |   0+ 18+  0 |        1 |          0 |   991 |       534 |   102074.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/LAUNCHXL-F28027-isolation-PCB_project1/unrouted.dsn)

Size: 10.7 kB · Layers: 2 · Nets: 1 · Components: 19 · Dimensions: 49.5 x 20.0 mm (9.9 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      38.93 |      N/A |     38.93 |   0+  1+  0 |        0 |          8 |   954 |       469 |    66002.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/LaundryMeasure_ac-ac/unrouted.dsn)

Size: 11.9 kB · Layers: 2 · Nets: 4 · Components: 6 · Dimensions: 42.16 x 24.38 mm (10.28 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.36 |      N/A |      0.36 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/lea-m8f-board_lea-m8f/unrouted.dsn)

Size: 21.3 kB · Layers: 2 · Nets: 11 · Components: 33 · Dimensions: 0.0 x 0.0 mm (0.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      42.23 |      N/A |     42.23 |   0+  1+  0 |        2 |          0 |   980 |       543 |   149224.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/led_array_atmega8_led_array/unrouted.dsn)

Size: 26.5 kB · Layers: 2 · Nets: 13 · Components: 63 · Dimensions: 99.7 x 49.53 mm (49.38 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       9.59 |      N/A |      9.59 |   0+  3+  0 |        0 |          0 |  1000 |       437 |    16673.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/LED_Clock_AM_led_clock/unrouted.dsn)

Size: 63.3 kB · Layers: 2 · Nets: 24 · Components: 65 · Dimensions: 100.0 x 50.0 mm (50.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      82.10 |      N/A |     82.10 |   0+  1+  0 |        2 |          2 |   985 |       531 |   270524.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/LED_port-status_LED_port-status/unrouted.dsn)

Size: 7.5 kB · Layers: 2 · Nets: 17 · Components: 21 · Dimensions: 23.75 x 12.5 mm (2.97 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      32.01 |      N/A |     32.01 |   0+  1+  0 |       62 |        128 |   356 |       405 |   106884.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/LED_port-status_LED_port-status-CA/unrouted.dsn)

Size: 12.1 kB · Layers: 2 · Nets: 0 · Components: 13 · Dimensions: 23.75 x 8.97 mm (2.13 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       8.75 |      N/A |      8.75 |   0+  1+  0 |        2 |          0 |   917 |       305 |    18069.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/LED_port-status_LED_port-status-CA-CC-combo/unrouted.dsn)

Size: 12.3 kB · Layers: 2 · Nets: 0 · Components: 15 · Dimensions: 23.75 x 8.97 mm (2.13 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       6.90 |      N/A |      6.90 |   0+  1+  0 |        1 |          0 |   958 |       280 |    13664.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/LED-grid-8x8_LED grid 8x8/unrouted.dsn)

Size: 17.8 kB · Layers: 2 · Nets: 37 · Components: 11 · Dimensions: 68.58 x 53.34 mm (36.58 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      13.64 |      N/A |     13.64 |   0+  2+  0 |        0 |          8 |   950 |       488 |    43220.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/LED-Square_PT4115_LED-Square_PT4115/unrouted.dsn)

Size: 20.1 kB · Layers: 2 · Nets: 0 · Components: 30 · Dimensions: 50.0 x 50.0 mm (25.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      20.07 |      N/A |     20.07 |   0+  1+  0 |        0 |         42 |   865 |       474 |    82004.2 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/LED-strip_PWM_LED-strip_PWM/unrouted.dsn)

Size: 16 kB · Layers: 2 · Nets: 0 · Components: 16 · Dimensions: 29.65 x 10.0 mm (2.96 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      37.90 |      N/A |     37.90 |   0+  1+  0 |        3 |          8 |   852 |       480 |    55793.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/led-wordclock_wordclock/unrouted.dsn)

Size: 45.1 kB · Layers: 2 · Nets: 57 · Components: 75 · Dimensions: 80.01 x 50.8 mm (40.65 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     134.13 |      N/A |    134.13 |   0+  1+  0 |        6 |          0 |   963 |       632 |   465206.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/LeeChee_1800/unrouted.dsn)

Size: 488.1 kB · Layers: 2 · Nets: 298 · Components: 587 · Dimensions: 378.41 x 156.86 mm (593.57 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes   |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :------ |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |    1813.22 |      N/A |   1813.22 |   0+  6+  0 |       23 |         64 |   969 |      1018 |  4622990.5 | 1886 / 0 | TIMEOUT |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Librecalc-Hardware__autosave-calculator/unrouted.dsn)

Size: 85.8 kB · Layers: 4 · Nets: 0 · Components: 146 · Dimensions: 99.8 x 49.85 mm (49.75 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     903.38 |      N/A |    903.38 |   0+  1+  0 |        0 |          5 |   998 |      1158 |  3075198.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/LiFePO4-Charge-Controller_LiFePO4-Charge-Controller/unrouted.dsn)

Size: 22.7 kB · Layers: 2 · Nets: 0 · Components: 51 · Dimensions: 66.22 x 60.98 mm (40.38 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       5.93 |      N/A |      5.93 |   0+  3+  0 |        0 |          0 |  1000 |       536 |    14975.2 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/light-painting-wand_light-wand/unrouted.dsn)

Size: 32.6 kB · Layers: 2 · Nets: 47 · Components: 24 · Dimensions: 50.8 x 38.1 mm (19.35 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      17.16 |      N/A |     17.16 |   0+  6+  0 |        0 |          1 |   997 |       448 |    54155.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/LimitSwitchesPlugin_LimitSwitchesPlugin/unrouted.dsn)

Size: 12.3 kB · Layers: 2 · Nets: 15 · Components: 8 · Dimensions: 66.04 x 55.88 mm (36.9 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.07 |      N/A |      2.07 |   0+  2+  0 |        0 |          0 |  1000 |        83 |     2409.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/linklayer_contact/unrouted.dsn)

Size: 30.1 kB · Layers: 2 · Nets: 46 · Components: 40 · Dimensions: 36.0 x 61.1 mm (22.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      25.60 |      N/A |     25.60 |   0+  1+  0 |        1 |          0 |   988 |       565 |   109504.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/LiPoSensor_lipo_sensor/unrouted.dsn)

Size: 25 kB · Layers: 2 · Nets: 19 · Components: 31 · Dimensions: 32.26 x 18.8 mm (6.06 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      10.90 |      N/A |     10.90 |   0+  6+  0 |        0 |          0 |  1000 |       389 |    29430.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/LizardBoard_lizard/unrouted.dsn)

Size: 10.5 kB · Layers: 2 · Nets: 11 · Components: 11 · Dimensions: 11.0 x 43.75 mm (4.81 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       6.09 |      N/A |      6.09 |   0+  1+  0 |        0 |          1 |   989 |       504 |    16014.7 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/LMS6002-Pmod_LMS6002-Pmod/unrouted.dsn)

Size: 55.7 kB · Layers: 2 · Nets: 14 · Components: 58 · Dimensions: 76.2 x 48.77 mm (37.16 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      93.75 |      N/A |     93.75 |   0+  6+  0 |        0 |          0 |  1000 |       518 |   358041.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/LogicBoxen_LogicBoxen/unrouted.dsn)

Size: 23.6 kB · Layers: 2 · Nets: 37 · Components: 47 · Dimensions: 50.0 x 35.0 mm (17.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      30.81 |      N/A |     30.81 |   0+ 13+  0 |        0 |          0 |  1000 |       483 |    99224.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/LoLin_Designs_LoLin/unrouted.dsn)

Size: 17.5 kB · Layers: 2 · Nets: 25 · Components: 10 · Dimensions: 56.51 x 66.04 mm (37.32 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.77 |      N/A |      0.77 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/LongPixel_AnalogDriverMini/unrouted.dsn)

Size: 26.4 kB · Layers: 2 · Nets: 8 · Components: 20 · Dimensions: 49.1 x 28.5 mm (13.99 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.66 |      N/A |      2.66 |   0+  2+  0 |        0 |          2 |   991 |       440 |     3633.2 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/LoRaCatKitty_NodeLoRaGroveKitty/unrouted.dsn)

Size: 45.1 kB · Layers: 2 · Nets: 52 · Components: 42 · Dimensions: 50.0 x 50.0 mm (25.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     116.17 |      N/A |    116.17 |   0+  1+  0 |        8 |          8 |   913 |       610 |   393234.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/LoRaCatTrack_GPSLoRa/unrouted.dsn)

Size: 31 kB · Layers: 2 · Nets: 37 · Components: 32 · Dimensions: 80.0 x 55.0 mm (44.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      22.46 |      N/A |     22.46 |   0+  1+  0 |        4 |         40 |   846 |       496 |    76903.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/LoRaPP_loramod/unrouted.dsn)

Size: 29.8 kB · Layers: 4 · Nets: 37 · Components: 78 · Dimensions: 50.0 x 18.0 mm (9.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     301.26 |      N/A |    301.26 |   0+  1+  0 |        0 |         73 |   922 |       616 |  1441131.2 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/LoRaWAN_GPS_lorawan_gps/unrouted.dsn)

Size: 38.2 kB · Layers: 2 · Nets: 25 · Components: 120 · Dimensions: 95.0 x 20.0 mm (19.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     302.68 |      N/A |    302.68 |   0+ 16+  0 |        5 |         39 |   944 |       697 |   937985.7 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/LoRaWAN_GPS_MINI-ULTRA-PRO/unrouted.dsn)

Size: 40.2 kB · Layers: 2 · Nets: 33 · Components: 62 · Dimensions: 25.4 x 55.88 mm (14.19 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     288.34 |      N/A |    288.34 |   0+  1+  0 |        2 |         93 |   882 |       650 |   792159.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/low-power-counter_lpcounter/unrouted.dsn)

Size: 66 kB · Layers: 2 · Nets: 0 · Components: 29 · Dimensions: 85.0 x 54.0 mm (45.9 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      38.64 |      N/A |     38.64 |   0+  1+  0 |        0 |          4 |   985 |       458 |    98529.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/LPC2148_Stick__autosave-LPC2148_stick/unrouted.dsn)

Size: 65.9 kB · Layers: 2 · Nets: 63 · Components: 73 · Dimensions: 132.0 x 51.0 mm (67.32 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     301.36 |      N/A |    301.36 |   0+ 16+  0 |        2 |          0 |   991 |       859 |   954781.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/LPC2148_Stick_LPC2148_stick/unrouted.dsn)

Size: 65.9 kB · Layers: 2 · Nets: 63 · Components: 73 · Dimensions: 132.0 x 51.0 mm (67.32 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     302.25 |      N/A |    302.25 |   0+ 14+  0 |        1 |          0 |   996 |       661 |   835171.1 |    3 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/LSU_4-9_O2_Sensor_Controller_LO2SC/unrouted.dsn)

Size: 22.2 kB · Layers: 2 · Nets: 9 · Components: 28 · Dimensions: 24.0 x 0.0 mm (0.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      51.99 |      N/A |     51.99 |   0+  1+  0 |        0 |         38 |   888 |       464 |   234215.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/LT3652EvalBoard_LT3652EvalBoard/unrouted.dsn)

Size: 39.2 kB · Layers: 2 · Nets: 17 · Components: 47 · Dimensions: 49.8 x 49.8 mm (24.8 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       6.41 |      N/A |      6.41 |   0+  3+  0 |        0 |          0 |  1000 |       260 |    11834.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/LVDS2TMDS_LVDS2TMDS/unrouted.dsn)

Size: 9.6 kB · Layers: 2 · Nets: 29 · Components: 26 · Dimensions: 43.75 x 19.0 mm (8.31 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.63 |      N/A |      4.63 |   0+  4+  0 |        0 |          0 |  1000 |       427 |     8894.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Lys_Lys/unrouted.dsn)

Size: 54.6 kB · Layers: 2 · Nets: 36 · Components: 78 · Dimensions: 53.0 x 79.0 mm (41.87 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     304.27 |      N/A |    304.27 |   0+ 18+  0 |        1 |       1252 |   489 |       773 |  1638088.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/m2-electronics_m2fc/unrouted.dsn)

Size: 83.5 kB · Layers: 4 · Nets: 150 · Components: 351 · Dimensions: 100.0 x 70.0 mm (70.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |    1089.16 |      N/A |   1089.16 |   0+ 20+  0 |        3 |         18 |   991 |       935 |  4279355.0 |    3 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/m2-electronics_m2pa/unrouted.dsn)

Size: 10.7 kB · Layers: 2 · Nets: 7 · Components: 20 · Dimensions: 60.0 x 39.0 mm (23.4 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      22.47 |      N/A |     22.47 |   0+  1+  0 |        0 |         60 |   793 |       536 |    91791.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/m2-electronics_m2pa-instrument/unrouted.dsn)

Size: 10.7 kB · Layers: 2 · Nets: 7 · Components: 20 · Dimensions: 60.0 x 39.0 mm (23.4 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      20.64 |      N/A |     20.64 |   0+  1+  0 |        0 |         60 |   793 |       448 |    90748.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/m2-electronics_m2pa-m3mount/unrouted.dsn)

Size: 13 kB · Layers: 2 · Nets: 7 · Components: 23 · Dimensions: 47.0 x 28.0 mm (13.16 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      21.56 |      N/A |     21.56 |   0+  1+  0 |        0 |         60 |   793 |       524 |    91286.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/m2-electronics_m2pa-tiny/unrouted.dsn)

Size: 9.8 kB · Layers: 2 · Nets: 7 · Components: 19 · Dimensions: 44.75 x 16.0 mm (7.16 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      18.43 |      N/A |     18.43 |   0+  1+  0 |        0 |         64 |   779 |       520 |    84399.2 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/m2-electronics_m2pogo/unrouted.dsn)

Size: 5.6 kB · Layers: 2 · Nets: 4 · Components: 8 · Dimensions: 44.4 x 81.8 mm (36.32 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.94 |      N/A |      0.94 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/m2-electronics_m2r/unrouted.dsn)

Size: 33.5 kB · Layers: 2 · Nets: 50 · Components: 105 · Dimensions: 56.0 x 56.0 mm (31.36 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      57.28 |      N/A |     57.28 |   0+  7+  0 |        0 |         10 |   991 |       627 |   206422.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/m2-electronics_m2rl/unrouted.dsn)

Size: 16.1 kB · Layers: 2 · Nets: 25 · Components: 31 · Dimensions: 40.0 x 34.0 mm (13.6 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.72 |      N/A |      4.72 |   0+  4+  0 |        0 |          1 |   996 |       428 |     8111.2 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/mac-pro-conversion_front-panel-power-adapter/unrouted.dsn)

Size: 17.6 kB · Layers: 2 · Nets: 10 · Components: 5 · Dimensions: 55.88 x 36.83 mm (20.58 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.51 |      N/A |      2.51 |   0+  1+  0 |        0 |         48 |   680 |       228 |     2307.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/mac-pro-conversion_front-panel-usb-adapter/unrouted.dsn)

Size: 5.7 kB · Layers: 2 · Nets: 3 · Components: 3 · Dimensions: 17.53 x 20.83 mm (3.65 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.47 |      N/A |      2.47 |   0+  2+  0 |        0 |          4 |   886 |       104 |     2903.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/MAGFest-2017-Swadges_magfest_badges/unrouted.dsn)

Size: 22.1 kB · Layers: 2 · Nets: 28 · Components: 35 · Dimensions: 99.8 x 50.02 mm (49.92 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      93.19 |      N/A |     93.19 |   0+  1+  0 |        0 |         20 |   956 |       584 |   400154.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/magic-table_etch-a-sketch/unrouted.dsn)

Size: 24.3 kB · Layers: 2 · Nets: 0 · Components: 31 · Dimensions: 98.0 x 75.0 mm (73.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       9.20 |      N/A |      9.20 |   0+  1+  0 |        0 |         52 |   807 |       293 |    31309.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/magic-table_etch-a-sketch_cyclone/unrouted.dsn)

Size: 23.8 kB · Layers: 2 · Nets: 0 · Components: 31 · Dimensions: 98.0 x 75.0 mm (73.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       6.37 |      N/A |      6.37 |   0+  1+  0 |        1 |          0 |   981 |       475 |    15263.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/MagSpoofPI_MagSpoofinThePi/unrouted.dsn)

Size: 31.3 kB · Layers: 2 · Nets: 38 · Components: 17 · Dimensions: 64.99 x 30.03 mm (19.52 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       8.28 |      N/A |      8.28 |   0+  1+  0 |        2 |         14 |   877 |       432 |    20014.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/makerspace-emonth_load_box/unrouted.dsn)

Size: 56.8 kB · Layers: 2 · Nets: 27 · Components: 59 · Dimensions: 94.79 x 80.78 mm (76.57 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      29.11 |      N/A |     29.11 |   0+ 18+  0 |        1 |          0 |   992 |       452 |   103217.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/makerspace-emonth_resistor_board/unrouted.dsn)

Size: 38.5 kB · Layers: 2 · Nets: 5 · Components: 16 · Dimensions: 92.71 x 64.14 mm (59.46 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      15.10 |      N/A |     15.10 |   0+  1+  0 |        1 |          0 |   975 |       347 |    26441.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/MAPone_MAP_append/unrouted.dsn)

Size: 52.6 kB · Layers: 2 · Nets: 6 · Components: 37 · Dimensions: 70.0 x 30.0 mm (21.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     302.70 |      N/A |    302.70 |   0+  2+  0 |        0 |         13 |   971 |       503 |   557860.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/MAPone_MAPone/unrouted.dsn)

Size: 52.8 kB · Layers: 2 · Nets: 6 · Components: 37 · Dimensions: 70.0 x 30.0 mm (21.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     109.54 |      N/A |    109.54 |   0+  1+  0 |        2 |         15 |   944 |       472 |   190825.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/marlin-neopixel-bridge_ATtiny85_Marneo/unrouted.dsn)

Size: 11.8 kB · Layers: 2 · Nets: 4 · Components: 8 · Dimensions: 34.8 x 18.03 mm (6.27 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.55 |      N/A |      2.55 |   0+  1+  0 |        3 |          0 |   812 |       171 |     3177.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/mavbridge_mavbridge/unrouted.dsn)

Size: 17.5 kB · Layers: 2 · Nets: 7 · Components: 38 · Dimensions: 34.0 x 23.75 mm (8.07 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      10.31 |      N/A |     10.31 |   0+  5+  0 |        0 |          0 |  1000 |       476 |    26009.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/MAVRIC_Hardware__autosave-Motherboard/unrouted.dsn)

Size: 50.5 kB · Layers: 2 · Nets: 131 · Components: 53 · Dimensions: 144.0 x 55.0 mm (79.2 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      21.46 |      N/A |     21.46 |   0+  4+  0 |        0 |          0 |  1000 |       426 |    69308.1 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/MAVRIC_Hardware_ArduinoPracticeBoard/unrouted.dsn)

Size: 21.3 kB · Layers: 2 · Nets: 2 · Components: 15 · Dimensions: 38.23 x 36.77 mm (14.06 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       7.55 |      N/A |      7.55 |   0+  5+  0 |        0 |          0 |  1000 |       379 |    18270.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/MAVRIC_Hardware_Motherboard/unrouted.dsn)

Size: 50.5 kB · Layers: 2 · Nets: 131 · Components: 53 · Dimensions: 144.0 x 55.0 mm (79.2 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      22.48 |      N/A |     22.48 |   0+  4+  0 |        0 |          0 |  1000 |       389 |    69494.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/MAVRIC_Hardware_pcieduino/unrouted.dsn)

Size: 24 kB · Layers: 2 · Nets: 0 · Components: 32 · Dimensions: 50.95 x 30.0 mm (15.29 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      97.85 |      N/A |     97.85 |   0+  1+  0 |        7 |         56 |   823 |       622 |   376294.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/MAVRIC_Hardware_SoilBoard/unrouted.dsn)

Size: 18.4 kB · Layers: 2 · Nets: 3 · Components: 16 · Dimensions: 27.94 x 38.73 mm (10.82 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.24 |      N/A |      3.24 |   0+  2+  0 |        0 |          0 |  1000 |       267 |     4877.1 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/maytal_Maytal/unrouted.dsn)

Size: 24.4 kB · Layers: 2 · Nets: 36 · Components: 11 · Dimensions: 94.1 x 94.7 mm (89.11 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.13 |      N/A |      4.13 |   0+  1+  0 |        1 |          0 |   950 |       436 |     6514.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/mdbwerk_mdbwerk/unrouted.dsn)

Size: 32.8 kB · Layers: 2 · Nets: 12 · Components: 45 · Dimensions: 51.8 x 24.9 mm (12.9 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      84.85 |      N/A |     84.85 |   0+  1+  0 |        6 |         13 |   900 |       529 |   236346.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/mearm-base-pcb_ServoPCB/unrouted.dsn)

Size: 27.2 kB · Layers: 2 · Nets: 4 · Components: 9 · Dimensions: 54.3 x 68.69 mm (37.3 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.93 |      N/A |      4.93 |   0+  3+  0 |        0 |          0 |  1000 |        77 |     2381.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/MeArm-Wireless-PCB_MeArmWireless/unrouted.dsn)

Size: 33.5 kB · Layers: 2 · Nets: 13 · Components: 31 · Dimensions: 65.0 x 56.0 mm (36.4 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     199.21 |      N/A |    199.21 |   0+  1+  0 |        0 |         16 |   971 |       556 |   594981.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Mechaduino-DR_Mechaduino DR 1.01/unrouted.dsn)

Size: 57.2 kB · Layers: 2 · Nets: 10 · Components: 70 · Dimensions: 57.0 x 57.0 mm (32.49 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     301.20 |      N/A |    301.20 |   0+  1+  0 |        0 |         20 |   978 |       626 |  1216825.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/mechkeys_1800-controller/unrouted.dsn)

Size: 37.9 kB · Layers: 2 · Nets: 19 · Components: 80 · Dimensions: 100.0 x 45.0 mm (45.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      28.57 |      N/A |     28.57 |   0+  3+  0 |        0 |          2 |   998 |       467 |    88791.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/mechkeys_58r-mxalps/unrouted.dsn)

Size: 43.6 kB · Layers: 2 · Nets: 69 · Components: 244 · Dimensions: 285.5 x 75.75 mm (216.27 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     678.02 |      N/A |    678.02 |   0+  1+  0 |       20 |         62 |   911 |       816 |  1638236.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/mechkeys_BluePad/unrouted.dsn)

Size: 32.8 kB · Layers: 2 · Nets: 43 · Components: 187 · Dimensions: 76.2 x 95.25 mm (72.58 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     366.50 |      N/A |    366.50 |   0+  1+  0 |       16 |         14 |   908 |       815 |  1079593.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/mechkeys_isp/unrouted.dsn)

Size: 11.5 kB · Layers: 2 · Nets: 4 · Components: 16 · Dimensions: 38.1 x 20.32 mm (7.74 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.81 |      N/A |      1.81 |   0+  2+  0 |        0 |          0 |  1000 |       151 |      963.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/mechkeys_lfk_revc/unrouted.dsn)

Size: 73.3 kB · Layers: 2 · Nets: 109 · Components: 319 · Dimensions: 367.75 x 93.25 mm (342.93 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     903.81 |      N/A |    903.81 |   0+  5+  0 |      135 |         70 |   798 |       956 |  2520706.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/mechkeys_lfk_revd/unrouted.dsn)

Size: 82.5 kB · Layers: 2 · Nets: 108 · Components: 339 · Dimensions: 367.75 x 93.25 mm (342.93 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     903.63 |      N/A |    903.63 |   0+  4+  0 |      147 |         77 |   789 |      1146 |  2542474.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/mechkeys_LFK78/unrouted.dsn)

Size: 84.3 kB · Layers: 2 · Nets: 98 · Components: 517 · Dimensions: 367.75 x 92.5 mm (340.17 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |    1804.22 |      N/A |   1804.22 |   0+  7+  0 |       96 |          8 |   891 |      1219 |  4892489.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/mechkeys_lfk78-jtag/unrouted.dsn)

Size: 4.9 kB · Layers: 2 · Nets: 10 · Components: 2 · Dimensions: 18.42 x 15.24 mm (2.81 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.72 |      N/A |      0.72 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/mechkeys_MF68+10/unrouted.dsn)

Size: 57.5 kB · Layers: 2 · Nets: 86 · Components: 263 · Dimensions: 370.46 x 94.44 mm (349.86 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes         |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :------------ |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |        N/A |      N/A |       N/A |   0+  0+  0 |      N/A |        N/A |   N/A |         0 |        0.0 |    3 / 2 | LOAD ERROR, 2 |


### Fixture: [unrouted.dsn](../fixtures/PCBench/medusa_medusa_cape/unrouted.dsn)

Size: 54.1 kB · Layers: 4 · Nets: 0 · Components: 73 · Dimensions: 54.61 x 86.36 mm (47.16 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     301.75 |      N/A |    301.75 |   0+  5+  0 |       69 |         97 |   731 |       682 |   795765.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/medusa_medusa_rs422_rx/unrouted.dsn)

Size: 17.6 kB · Layers: 2 · Nets: 4 · Components: 46 · Dimensions: 71.5 x 42.0 mm (30.03 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       6.71 |      N/A |      6.71 |   0+  5+  0 |        0 |          0 |  1000 |       392 |    18859.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/medusa_medusa_rs422_tx/unrouted.dsn)

Size: 25.7 kB · Layers: 2 · Nets: 0 · Components: 46 · Dimensions: 72.0 x 67.0 mm (48.24 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     195.65 |      N/A |    195.65 |   0+  1+  0 |        5 |          5 |   973 |       681 |   742208.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/memory-display_memory-display/unrouted.dsn)

Size: 15.9 kB · Layers: 2 · Nets: 1 · Components: 32 · Dimensions: 48.87 x 50.8 mm (24.83 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.39 |      N/A |      2.39 |   0+  3+  0 |        0 |          0 |  1000 |        39 |     2579.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/memsarray_mems_array/unrouted.dsn)

Size: 42.7 kB · Layers: 4 · Nets: 131 · Components: 61 · Dimensions: 350.0 x 190.0 mm (665.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     119.26 |      N/A |    119.26 |   0+ 18+  0 |        1 |          0 |   996 |       618 |   482085.7 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/memsarray_mems_modules/unrouted.dsn)

Size: 4 kB · Layers: 2 · Nets: 0 · Components: 6 · Dimensions: 10.7 x 11.49 mm (1.23 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      36.08 |      N/A |     36.08 |   0+  1+  0 |        5 |          8 |   340 |       300 |    34616.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/mEncoder_mencoder/unrouted.dsn)

Size: 7.9 kB · Layers: 2 · Nets: 5 · Components: 9 · Dimensions: 14.73 x 11.94 mm (1.76 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.00 |      N/A |      3.00 |   0+  1+  0 |        0 |          6 |   920 |       491 |     4062.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Microdox-PCB_Microdox/unrouted.dsn)

Size: 21.5 kB · Layers: 2 · Nets: 34 · Components: 38 · Dimensions: 153.31 x 135.48 mm (207.7 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     399.82 |      N/A |    399.82 |   0+  1+  0 |       14 |         12 |   926 |       649 |   972651.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Microdox-PCB_Microdox-LEDs/unrouted.dsn)

Size: 28.5 kB · Layers: 2 · Nets: 41 · Components: 47 · Dimensions: 153.31 x 135.48 mm (207.7 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     570.79 |      N/A |    570.79 |   0+  1+  0 |       15 |         12 |   926 |       602 |  1347839.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Microdox-PCB_Microdox-topMCU/unrouted.dsn)

Size: 18.4 kB · Layers: 2 · Nets: 29 · Components: 36 · Dimensions: 153.31 x 133.7 mm (204.98 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      67.07 |      N/A |     67.07 |   0+  1+  0 |        3 |          4 |   982 |       588 |   175769.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Microdox-PCB_pcbbackup/unrouted.dsn)

Size: 28.5 kB · Layers: 2 · Nets: 41 · Components: 47 · Dimensions: 153.31 x 135.48 mm (207.7 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     590.75 |      N/A |    590.75 |   0+  1+  0 |       17 |         12 |   918 |       693 |  1428925.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Microdox-PCB_Print-Microdox/unrouted.dsn)

Size: 22 kB · Layers: 2 · Nets: 34 · Components: 38 · Dimensions: 153.31 x 135.48 mm (207.7 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     427.42 |      N/A |    427.42 |   0+  1+  0 |       13 |         12 |   930 |       659 |  1040188.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/MicroMaple_MicroMaple/unrouted.dsn)

Size: 33.8 kB · Layers: 2 · Nets: 8 · Components: 39 · Dimensions: 38.1 x 38.1 mm (14.52 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     178.19 |      N/A |    178.19 |   0+  1+  0 |        5 |          0 |   955 |       686 |   530628.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/microphone_preamp/unrouted.dsn)

Size: 13.3 kB · Layers: 2 · Nets: 10 · Components: 26 · Dimensions: 61.4 x 17.2 mm (10.56 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.25 |      N/A |      2.25 |   0+  2+  0 |        0 |          0 |  1000 |       427 |     4301.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/microphone_preamp-power/unrouted.dsn)

Size: 7.9 kB · Layers: 2 · Nets: 6 · Components: 11 · Dimensions: 61.4 x 17.2 mm (10.56 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.75 |      N/A |      4.75 |   0+  1+  0 |        0 |          4 |   964 |       535 |    16455.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/mightyduino_mightyduino/unrouted.dsn)

Size: 24.6 kB · Layers: 2 · Nets: 7 · Components: 24 · Dimensions: 46.23 x 23.11 mm (10.68 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      61.10 |      N/A |     61.10 |   0+  7+  0 |        7 |         84 |   702 |       552 |   157120.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/mini_ice40_mini_ice40/unrouted.dsn)

Size: 35.1 kB · Layers: 4 · Nets: 79 · Components: 33 · Dimensions: 0.0 x 0.0 mm (0.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      40.47 |      N/A |     40.47 |   0+  5+  0 |        0 |          0 |  1000 |       446 |    98430.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/mini_mass_prog_bench_prog_rig/unrouted.dsn)

Size: 115.1 kB · Layers: 4 · Nets: 287 · Components: 441 · Dimensions: 600.0 x 110.0 mm (660.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes      |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :--------- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |        N/A |      N/A |       N/A |   0+  0+  0 |      N/A |        N/A |   N/A |         0 |        0.0 |    1 / 0 | LOAD ERROR |


### Fixture: [unrouted.dsn](../fixtures/PCBench/miniboard-opamp_miniboard-opamp/unrouted.dsn)

Size: 10.2 kB · Layers: 2 · Nets: 14 · Components: 26 · Dimensions: 35.56 x 21.59 mm (7.68 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      21.15 |      N/A |     21.15 |   0+  1+  0 |        0 |         20 |   905 |       537 |    81693.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/miniboard-stm32f0_miniboard-stm32f0/unrouted.dsn)

Size: 24.6 kB · Layers: 2 · Nets: 1 · Components: 27 · Dimensions: 39.05 x 19.43 mm (7.59 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      48.39 |      N/A |     48.39 |   0+  1+  0 |        0 |          8 |   975 |       457 |   189676.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/minievse_minievse/unrouted.dsn)

Size: 50.7 kB · Layers: 2 · Nets: 36 · Components: 54 · Dimensions: 101.6 x 50.8 mm (51.61 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       9.99 |      N/A |      9.99 |   0+  3+  0 |        0 |          0 |  1000 |       324 |    14333.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/minima-hardware_FrontPanel/unrouted.dsn)

Size: 65.6 kB · Layers: 2 · Nets: 26 · Components: 49 · Dimensions: 140.0 x 62.0 mm (86.8 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      58.21 |      N/A |     58.21 |   0+  1+  0 |        1 |         29 |   946 |       548 |   149285.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Minitel_bbb-adapter/unrouted.dsn)

Size: 49 kB · Layers: 2 · Nets: 84 · Components: 15 · Dimensions: 58.42 x 80.01 mm (46.74 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.78 |      N/A |      4.78 |   0+  4+  0 |        0 |          0 |  1000 |       504 |     8616.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Minitel_driver_board/unrouted.dsn)

Size: 24.8 kB · Layers: 2 · Nets: 39 · Components: 29 · Dimensions: 83.82 x 53.34 mm (44.71 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.17 |      N/A |      3.17 |   0+  3+  0 |        0 |          0 |  1000 |       268 |     6084.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Minitel_video_output_stage/unrouted.dsn)

Size: 26.8 kB · Layers: 2 · Nets: 14 · Components: 21 · Dimensions: 78.74 x 66.04 mm (52.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.20 |      N/A |      2.20 |   0+  2+  0 |        0 |          0 |  1000 |       175 |     1871.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Mini-Ultra-8-MHz_RS-MINI-ULTRA-8M/unrouted.dsn)

Size: 32.9 kB · Layers: 2 · Nets: 9 · Components: 24 · Dimensions: 18.41 x 39.37 mm (7.25 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      23.44 |      N/A |     23.44 |   0+  1+  0 |        1 |          7 |   968 |       559 |    82087.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/mini-whip_mini-whip/unrouted.dsn)

Size: 9.2 kB · Layers: 2 · Nets: 8 · Components: 17 · Dimensions: 91.0 x 32.0 mm (29.12 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.95 |      N/A |      1.95 |   0+  2+  0 |        0 |          0 |  1000 |        46 |      290.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/mini-whip_mini-whip-power-feed/unrouted.dsn)

Size: 9.6 kB · Layers: 2 · Nets: 5 · Components: 14 · Dimensions: 62.0 x 25.0 mm (15.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       6.92 |      N/A |      6.92 |   0+  1+  0 |        0 |          6 |   948 |       612 |    25810.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/MixSID_mixsid/unrouted.dsn)

Size: 50.2 kB · Layers: 2 · Nets: 52 · Components: 76 · Dimensions: 62.23 x 57.4 mm (35.72 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      96.95 |      N/A |     96.95 |   0+  1+  0 |        2 |          0 |   990 |       526 |   278485.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/mobile-sensor-pcb_mobile-sensor-pcb/unrouted.dsn)

Size: 74.6 kB · Layers: 2 · Nets: 144 · Components: 93 · Dimensions: 120.65 x 67.56 mm (81.51 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     194.77 |      N/A |    194.77 |   0+  1+  0 |        1 |         10 |   986 |       654 |   642874.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/MOD-MPU9150_mod-mpu9150/unrouted.dsn)

Size: 24.9 kB · Layers: 2 · Nets: 20 · Components: 17 · Dimensions: 22.22 x 16.51 mm (3.67 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      14.46 |      N/A |     14.46 |   0+  1+  0 |        2 |         10 |   895 |       458 |    44771.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/modular_bus_board_bus/unrouted.dsn)

Size: 62.2 kB · Layers: 2 · Nets: 3 · Components: 26 · Dimensions: 111.76 x 48.26 mm (53.94 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      19.16 |      N/A |     19.16 |   0+  1+  0 |        4 |        128 |   844 |       425 |    65998.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/mojo-nes_mojo-nes/unrouted.dsn)

Size: 15.7 kB · Layers: 2 · Nets: 18 · Components: 9 · Dimensions: 99.72 x 40.89 mm (40.78 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      59.98 |      N/A |     59.98 |   0+  1+  0 |        9 |          0 |   878 |       490 |   144154.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/MonApollo_analog-board/unrouted.dsn)

Size: 150 kB · Layers: 2 · Nets: 303 · Components: 506 · Dimensions: 200.0 x 150.0 mm (300.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes      |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :--------- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |        N/A |      N/A |       N/A |   0+  0+  0 |      N/A |        N/A |   N/A |         0 |        0.0 |    1 / 0 | LOAD ERROR |


### Fixture: [unrouted.dsn](../fixtures/PCBench/MonApollo_digital-board/unrouted.dsn)

Size: 83.7 kB · Layers: 2 · Nets: 227 · Components: 240 · Dimensions: 200.0 x 150.0 mm (300.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     905.89 |      N/A |    905.89 |   0+  5+  0 |       32 |         21 |   943 |      1303 |  2608115.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/MoonLander_MoonLander/unrouted.dsn)

Size: 37.8 kB · Layers: 2 · Nets: 35 · Components: 107 · Dimensions: 37.85 x 22.73 mm (8.6 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      88.27 |      N/A |     88.27 |   0+  1+  0 |       14 |         73 |   853 |       577 |   318751.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/moonpunchorg_pcieduino/unrouted.dsn)

Size: 24 kB · Layers: 2 · Nets: 0 · Components: 32 · Dimensions: 50.95 x 30.0 mm (15.29 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      94.04 |      N/A |     94.04 |   0+  1+  0 |        7 |         56 |   823 |       653 |   374784.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/mosavr_pcie_pi_module/unrouted.dsn)

Size: 27.2 kB · Layers: 2 · Nets: 39 · Components: 15 · Dimensions: 100.0 x 68.91 mm (68.91 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     141.27 |      N/A |    141.27 |   0+  1+  0 |        0 |         34 |   938 |       552 |   741731.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/mosavr_pcie1x_backplane/unrouted.dsn)

Size: 12.9 kB · Layers: 2 · Nets: 64 · Components: 8 · Dimensions: 93.0 x 46.0 mm (42.78 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.61 |      N/A |      4.61 |   0+  2+  0 |        0 |          0 |  1000 |       416 |     9948.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/motor_control_hardware_poncho/unrouted.dsn)

Size: 25.2 kB · Layers: 2 · Nets: 2 · Components: 34 · Dimensions: 97.04 x 58.31 mm (56.58 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      47.79 |      N/A |     47.79 |   0+  1+  0 |       37 |         28 |   461 |       486 |   148010.1 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/motor-3xdrv8833-hw_ver1/unrouted.dsn)

Size: 50.3 kB · Layers: 2 · Nets: 34 · Components: 73 · Dimensions: 61.0 x 59.0 mm (35.99 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     109.67 |      N/A |    109.67 |   0+ 18+  0 |        1 |          0 |   995 |       639 |   420231.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/motor-high-current-sensors_CurrentSensorPCB_line/unrouted.dsn)

Size: 71.2 kB · Layers: 2 · Nets: 76 · Components: 222 · Dimensions: 182.75 x 54.0 mm (98.69 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     905.38 |      N/A |    905.38 |   0+  2+  0 |        7 |         24 |   975 |      1080 |  3380384.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/mppt-2420-hc_mppt-2420-hc/unrouted.dsn)

Size: 202.6 kB · Layers: 4 · Nets: 42 · Components: 172 · Dimensions: 100.0 x 100.0 mm (100.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     114.25 |      N/A |    114.25 |   0+ 18+  0 |        1 |          1 |   996 |       633 |   366076.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/mppt-2420-hpx_mppt-2420-hpx/unrouted.dsn)

Size: 179.3 kB · Layers: 4 · Nets: 94 · Components: 191 · Dimensions: 120.0 x 100.0 mm (120.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     446.12 |      N/A |    446.12 |   0+  1+  0 |        3 |         17 |   984 |      1064 |  1328567.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/mppt-2420-rc_mppt-2420-rc/unrouted.dsn)

Size: 148.4 kB · Layers: 4 · Nets: 39 · Components: 121 · Dimensions: 67.7 x 86.4 mm (58.49 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     142.88 |      N/A |    142.88 |   0+ 20+  0 |        1 |          2 |   994 |       632 |   415885.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/MSGEQ7-Breakout-Board_MSGEQ7_Breakout_Board/unrouted.dsn)

Size: 10.3 kB · Layers: 2 · Nets: 6 · Components: 13 · Dimensions: 43.05 x 25.4 mm (10.93 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.82 |      N/A |      0.82 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/MySensorIRBlaster_IR Blaster/unrouted.dsn)

Size: 32.4 kB · Layers: 2 · Nets: 16 · Components: 54 · Dimensions: 47.79 x 24.01 mm (11.47 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      66.11 |      N/A |     66.11 |   0+  1+  0 |        5 |          8 |   945 |       576 |   213649.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/MySensors_SecureKeyfob_MySensors_RNDKeyfob/unrouted.dsn)

Size: 32.9 kB · Layers: 2 · Nets: 4 · Components: 26 · Dimensions: 27.0 x 42.9 mm (11.58 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      77.08 |      N/A |     77.08 |   0+  1+  0 |        2 |         18 |   921 |       600 |   202942.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/MySensorsNode_LE-BOX-0028/unrouted.dsn)

Size: 66.7 kB · Layers: 2 · Nets: 19 · Components: 66 · Dimensions: 76.0 x 96.0 mm (72.96 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     127.18 |      N/A |    127.18 |   0+  1+  0 |        2 |         28 |   959 |       617 |   412313.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/MYS-PMS7003_MYS-PMS7003/unrouted.dsn)

Size: 70.6 kB · Layers: 2 · Nets: 15 · Components: 43 · Dimensions: 107.0 x 68.0 mm (72.76 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      48.35 |      N/A |     48.35 |   0+  1+  0 |        3 |          6 |   966 |       452 |   179718.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/MySRaspiGW_MySRaspiGW/unrouted.dsn)

Size: 4.6 kB · Layers: 2 · Nets: 2 · Components: 5 · Dimensions: 11.5 x 11.9 mm (1.37 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       5.78 |      N/A |      5.78 |   0+  1+  0 |        3 |          3 |   723 |       252 |    10745.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/MySRaspiGW_MySRaspiGW_PA_LNA/unrouted.dsn)

Size: 4.6 kB · Layers: 2 · Nets: 2 · Components: 5 · Dimensions: 11.5 x 11.9 mm (1.37 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       5.94 |      N/A |      5.94 |   0+  1+  0 |        2 |          3 |   800 |       339 |     9155.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/MySRaspiGW_MySRaspiGW_PA_LNA_Pimoroni/unrouted.dsn)

Size: 4.7 kB · Layers: 2 · Nets: 2 · Components: 5 · Dimensions: 11.8 x 15.5 mm (1.83 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       5.40 |      N/A |      5.40 |   0+  1+  0 |        1 |          0 |   923 |       267 |     9858.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/MySRaspiGW_MySRaspiGW_Pimoroni/unrouted.dsn)

Size: 4.6 kB · Layers: 2 · Nets: 2 · Components: 5 · Dimensions: 11.5 x 16.2 mm (1.86 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       5.79 |      N/A |      5.79 |   0+  1+  0 |        1 |          0 |   923 |       267 |     7603.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/nan-15_pcb/unrouted.dsn)

Size: 17.5 kB · Layers: 2 · Nets: 24 · Components: 51 · Dimensions: 68.58 x 71.12 mm (48.77 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     169.30 |      N/A |    169.30 |   0+  1+  0 |        7 |          6 |   917 |       505 |   368887.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/nan-15_top-plate/unrouted.dsn)

Size: 38.5 kB · Layers: 2 · Nets: 12 · Components: 48 · Dimensions: 77.2 x 77.2 mm (59.6 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      58.61 |      N/A |     58.61 |   0+  1+  0 |       30 |          0 |   286 |       446 |   117568.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/nanoSwinSidC_nanoSwinSidC/unrouted.dsn)

Size: 21.5 kB · Layers: 2 · Nets: 6 · Components: 15 · Dimensions: 35.8 x 17.5 mm (6.26 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      59.59 |      N/A |     59.59 |   0+  1+  0 |        3 |         56 |   727 |       550 |   151418.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/nanoTracer_nanoTracer/unrouted.dsn)

Size: 57.5 kB · Layers: 2 · Nets: 17 · Components: 48 · Dimensions: 91.44 x 59.94 mm (54.81 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      16.84 |      N/A |     16.84 |   0+  1+  0 |        1 |          4 |   984 |       462 |    54135.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/navelino_navelino/unrouted.dsn)

Size: 34.5 kB · Layers: 2 · Nets: 31 · Components: 73 · Dimensions: 45.57 x 37.28 mm (16.99 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      50.62 |      N/A |     50.62 |   0+ 11+  0 |        0 |          3 |   994 |       534 |   133555.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/navelino-leaf_navelino-leaf/unrouted.dsn)

Size: 10.3 kB · Layers: 2 · Nets: 3 · Components: 13 · Dimensions: 54.02 x 62.5 mm (33.76 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.29 |      N/A |      3.29 |   0+  4+  0 |        0 |          0 |  1000 |       324 |     4375.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/NavigationThing_NavigationThing/unrouted.dsn)

Size: 52.3 kB · Layers: 2 · Nets: 24 · Components: 39 · Dimensions: 64.0 x 58.0 mm (37.12 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      35.11 |      N/A |     35.11 |   0+  1+  0 |        1 |          1 |   987 |       478 |   135275.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/NavigationThing_NavigationThingBacklight/unrouted.dsn)

Size: 4.6 kB · Layers: 2 · Nets: 4 · Components: 6 · Dimensions: 22.0 x 8.0 mm (1.76 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.93 |      N/A |      2.93 |   0+  1+  0 |        2 |          2 |   829 |       479 |     4269.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/NeoWall_NeoWall/unrouted.dsn)

Size: 19.7 kB · Layers: 2 · Nets: 4 · Components: 15 · Dimensions: 70.0 x 70.0 mm (49.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.91 |      N/A |      2.91 |   0+  3+  0 |        0 |          0 |  1000 |        96 |     2443.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Neptune-Hardware_DataAcquisitionBoard/unrouted.dsn)

Size: 9.4 kB · Layers: 2 · Nets: 11 · Components: 15 · Dimensions: 99.06 x 68.58 mm (67.94 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.35 |      N/A |      1.35 |   0+  2+  0 |        0 |          0 |  1000 |       183 |     1390.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/newer-motor-controllers_busparts/unrouted.dsn)

Size: 93.5 kB · Layers: 4 · Nets: 55 · Components: 760 · Dimensions: 102.0 x 100.0 mm (102.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     656.83 |      N/A |    656.83 |   0+  1+  0 |        1 |        677 |   847 |       557 |  2302336.8 |    5 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/newer-motor-controllers_design3/unrouted.dsn)

Size: 34.6 kB · Layers: 4 · Nets: 9 · Components: 32 · Dimensions: 100.0 x 50.0 mm (50.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      39.16 |      N/A |     39.16 |   0+  1+  0 |        0 |          8 |   972 |       514 |   138688.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/newer-motor-controllers_si31-3/unrouted.dsn)

Size: 94.7 kB · Layers: 4 · Nets: 55 · Components: 760 · Dimensions: 102.0 x 100.0 mm (102.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |    1804.92 |      N/A |   1804.92 |   0+  1+  0 |        0 |        677 |   848 |       729 |  6792125.5 |    5 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/newer-motor-controllers_stm32f407riser/unrouted.dsn)

Size: 35.8 kB · Layers: 4 · Nets: 86 · Components: 57 · Dimensions: 69.09 x 41.91 mm (28.96 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     190.26 |      N/A |    190.26 |   0+  1+  0 |       52 |          0 |   623 |       554 |   627261.2 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/nextbusclock_NextBusClockV1/unrouted.dsn)

Size: 33.4 kB · Layers: 2 · Nets: 45 · Components: 26 · Dimensions: 90.17 x 68.58 mm (61.84 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       7.53 |      N/A |      7.53 |   0+  5+  0 |        0 |          0 |  1000 |       348 |    15836.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/nichiden27_PISCIUM/unrouted.dsn)

Size: 27.3 kB · Layers: 2 · Nets: 108 · Components: 73 · Dimensions: 119.38 x 78.74 mm (94.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      13.17 |      N/A |     13.17 |   0+ 18+  0 |        1 |          2 |   991 |       485 |    43393.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/NightClock_ESPBoard/unrouted.dsn)

Size: 26.3 kB · Layers: 2 · Nets: 9 · Components: 25 · Dimensions: 49.53 x 45.72 mm (22.65 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.65 |      N/A |      4.65 |   0+  3+  0 |        0 |          0 |  1000 |       340 |     6509.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/nikon_gps_nikon_gps/unrouted.dsn)

Size: 24.4 kB · Layers: 2 · Nets: 6 · Components: 34 · Dimensions: 28.0 x 28.0 mm (7.84 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      59.57 |      N/A |     59.57 |   0+  1+  0 |        0 |          3 |   989 |       543 |   235204.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/NiMH-Charger_NiMH Charger/unrouted.dsn)

Size: 13.9 kB · Layers: 2 · Nets: 18 · Components: 24 · Dimensions: 93.98 x 62.87 mm (59.09 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.80 |      N/A |      2.80 |   0+  2+  0 |        0 |          0 |  1000 |       244 |     2368.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/nixie-clock_ab18x5-breakout/unrouted.dsn)

Size: 8.4 kB · Layers: 2 · Nets: 3 · Components: 7 · Dimensions: 18.29 x 15.75 mm (2.88 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       6.24 |      N/A |      6.24 |   0+  1+  0 |        0 |          1 |   989 |       548 |    12397.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/nodemcu-backstage_NodeMCU Backstage/unrouted.dsn)

Size: 14 kB · Layers: 2 · Nets: 22 · Components: 9 · Dimensions: 31.75 x 54.86 mm (17.42 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.79 |      N/A |      1.79 |   0+  2+  0 |        0 |          0 |  1000 |        27 |     1090.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/nodemcu-basecamp_NodeMCU Basecamp/unrouted.dsn)

Size: 19.3 kB · Layers: 2 · Nets: 0 · Components: 47 · Dimensions: 63.5 x 50.8 mm (32.26 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      22.58 |      N/A |     22.58 |   0+  4+  0 |        0 |          0 |  1000 |       461 |    71003.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/NodeMcu-v3-LiPoly-LiIon-Backpack_NodeMcuLiPolyBackpack/unrouted.dsn)

Size: 13.6 kB · Layers: 2 · Nets: 6 · Components: 16 · Dimensions: 19.84 x 26.16 mm (5.19 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      15.96 |      N/A |     15.96 |   0+  1+  0 |        0 |          2 |   983 |       408 |    26723.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/NoVo-Pi_NoVoPi/unrouted.dsn)

Size: 15.2 kB · Layers: 2 · Nets: 55 · Components: 24 · Dimensions: 54.7 x 66.2 mm (36.21 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.97 |      N/A |      1.97 |   0+  2+  0 |        0 |          0 |  1000 |        23 |     1020.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/NRC2016_banked_ram/unrouted.dsn)

Size: 44.5 kB · Layers: 2 · Nets: 18 · Components: 24 · Dimensions: 104.14 x 53.96 mm (56.19 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     153.99 |      N/A |    153.99 |   0+  1+  0 |       11 |          0 |   926 |       538 |   387589.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/NRC2016_usb_sio/unrouted.dsn)

Size: 55.3 kB · Layers: 2 · Nets: 21 · Components: 27 · Dimensions: 104.14 x 33.02 mm (34.39 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      45.26 |      N/A |     45.26 |   0+  1+  0 |        4 |          0 |   952 |       468 |   118544.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/NRC2016_z80/unrouted.dsn)

Size: 52.6 kB · Layers: 2 · Nets: 19 · Components: 37 · Dimensions: 104.77 x 111.76 mm (117.09 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     149.39 |      N/A |    149.39 |   0+  1+  0 |        7 |          0 |   976 |       616 |   434597.8 |    3 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/nRF24breakoutBoard_nRF24-breakout/unrouted.dsn)

Size: 18.5 kB · Layers: 2 · Nets: 8 · Components: 3 · Dimensions: 25.02 x 21.59 mm (5.4 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.85 |      N/A |      0.85 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/nrf2rfm69_nrf2rfm69/unrouted.dsn)

Size: 19.4 kB · Layers: 2 · Nets: 8 · Components: 5 · Dimensions: 16.46 x 30.0 mm (4.94 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.57 |      N/A |      4.57 |   0+  1+  0 |        1 |          0 |   917 |       304 |     7464.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/nrf51-kicad_nrf51x22_qfax_dcdc/unrouted.dsn)

Size: 13.6 kB · Layers: 2 · Nets: 15 · Components: 46 · Dimensions: 14.84 x 16.41 mm (2.44 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      26.89 |      N/A |     26.89 |   0+  1+  0 |        5 |         46 |   833 |       451 |    96356.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/nrf52-kicad_nrf51x22_qfax_dcdc/unrouted.dsn)

Size: 13.5 kB · Layers: 2 · Nets: 11 · Components: 54 · Dimensions: 17.25 x 15.04 mm (2.59 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      40.26 |      N/A |     40.26 |   0+  1+  0 |       19 |         51 |   676 |       486 |   118777.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/nunchuk_rf_hw_NunchukRF_V3/unrouted.dsn)

Size: 28.3 kB · Layers: 2 · Nets: 14 · Components: 44 · Dimensions: 26.92 x 46.74 mm (12.58 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     141.78 |      N/A |    141.78 |   0+  1+  0 |        2 |          8 |   966 |       527 |   419305.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/OLD-Stepper-motor-board-design-project_Stepper motor driver/unrouted.dsn)

Size: 15 kB · Layers: 2 · Nets: 8 · Components: 17 · Dimensions: 35.81 x 35.81 mm (12.82 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      13.34 |      N/A |     13.34 |   0+  1+  0 |        2 |          0 |   957 |       536 |    36713.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/oled-bmp280-touch_oled-bmp280-touch/unrouted.dsn)

Size: 14 kB · Layers: 2 · Nets: 6 · Components: 11 · Dimensions: 50.04 x 51.69 mm (25.87 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.31 |      N/A |      3.31 |   0+  3+  0 |        0 |          0 |  1000 |       204 |     2745.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Omega2-Berrydock_berrydock-mini/unrouted.dsn)

Size: 61.2 kB · Layers: 2 · Nets: 55 · Components: 88 · Dimensions: 50.0 x 50.0 mm (25.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     177.07 |      N/A |    177.07 |   0+  1+  0 |        2 |          6 |   985 |       692 |   638801.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Omega2-mini-dock_Omega2 mini-dock/unrouted.dsn)

Size: 21 kB · Layers: 2 · Nets: 0 · Components: 17 · Dimensions: 38.1 x 60.96 mm (23.23 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       5.57 |      N/A |      5.57 |   0+  3+  0 |        0 |          0 |  1000 |       376 |    11005.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/omega-dock-new_omega-dock-new/unrouted.dsn)

Size: 26.1 kB · Layers: 2 · Nets: 29 · Components: 21 · Dimensions: 42.9 x 26.4 mm (11.33 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      17.18 |      N/A |     17.18 |   0+  1+  0 |        0 |         70 |   714 |       512 |    65882.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/one-shift-register_one-shift-register/unrouted.dsn)

Size: 21.5 kB · Layers: 2 · Nets: 36 · Components: 25 · Dimensions: 68.58 x 53.34 mm (36.58 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.92 |      N/A |      4.92 |   0+  1+  0 |        0 |          8 |   952 |       441 |     9283.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/onion2-breakout_onion2 breakout/unrouted.dsn)

Size: 19.4 kB · Layers: 2 · Nets: 36 · Components: 14 · Dimensions: 39.62 x 51.82 mm (20.53 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.14 |      N/A |      4.14 |   0+  2+  0 |        0 |          0 |  1000 |       452 |     9216.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/OpAmpPassXsistorBenchSupply_OpAmpPassXsistorBenchSupply/unrouted.dsn)

Size: 56.2 kB · Layers: 2 · Nets: 0 · Components: 89 · Dimensions: 99.69 x 99.69 mm (99.38 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      23.80 |      N/A |     23.80 |   0+  1+  0 |        3 |          0 |   981 |       474 |    80203.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/OpenAVRc_Mega_2560 core mini_full_2.0/unrouted.dsn)

Size: 82.8 kB · Layers: 2 · Nets: 32 · Components: 110 · Dimensions: 89.54 x 59.69 mm (53.45 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     121.80 |      N/A |    121.80 |   0+ 14+  0 |        0 |          8 |   995 |       559 |   383147.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/OpenBot_openbot/unrouted.dsn)

Size: 25.2 kB · Layers: 2 · Nets: 0 · Components: 30 · Dimensions: 44.45 x 53.34 mm (23.71 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       6.53 |      N/A |      6.53 |   0+  5+  0 |        0 |          2 |   994 |       340 |    12839.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/opendous_Upconverter/unrouted.dsn)

Size: 92.4 kB · Layers: 2 · Nets: 0 · Components: 670 · Dimensions: 96.52 x 50.8 mm (49.03 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |    1417.59 |      N/A |   1417.59 |   0+  1+  0 |      106 |        170 |   832 |       853 |  4418799.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/OpenHardwareExG_ActiveElectrode_OpenHardwareExG_ActiveElectrode/unrouted.dsn)

Size: 11.4 kB · Layers: 4 · Nets: 0 · Components: 14 · Dimensions: 0.0 x 9.65 mm (0.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      25.11 |      N/A |     25.11 |   0+  1+  0 |        3 |          0 |   885 |       501 |    48548.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/OpenHardwareExG_Shield_OpenHardwareExG_Shield/unrouted.dsn)

Size: 52.7 kB · Layers: 4 · Nets: 82 · Components: 204 · Dimensions: 125.09 x 59.69 mm (74.67 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     317.11 |      N/A |    317.11 |   0+ 20+  0 |        3 |          0 |   992 |       727 |  1073591.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/OpenHardwareExG_Shield_OpenHardwareExG_Shield_Test_Board/unrouted.dsn)

Size: 39.2 kB · Layers: 4 · Nets: 0 · Components: 139 · Dimensions: 147.32 x 81.28 mm (119.74 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      99.83 |      N/A |     99.83 |   0+ 20+  0 |        2 |          0 |   992 |       484 |   308397.2 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/OpenHardwareExG_Shield_OpenHardwareExG_Shield_Test_Board_all_panelled/unrouted.dsn)

Size: 39.2 kB · Layers: 4 · Nets: 0 · Components: 139 · Dimensions: 147.32 x 81.28 mm (119.74 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      96.64 |      N/A |     96.64 |   0+ 20+  0 |        2 |          0 |   992 |       606 |   310213.2 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/OpenMPPT_OpenMPPT/unrouted.dsn)

Size: 28.8 kB · Layers: 2 · Nets: 12 · Components: 42 · Dimensions: 74.8 x 47.0 mm (35.16 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     162.41 |      N/A |    162.41 |   0+  1+  0 |        0 |         38 |   941 |       587 |   689282.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/open-source-multimeter_board/unrouted.dsn)

Size: 24.4 kB · Layers: 2 · Nets: 1 · Components: 23 · Dimensions: 29.72 x 83.06 mm (24.69 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      60.58 |      N/A |     60.58 |   0+  3+  0 |       55 |          0 |   491 |       453 |   151931.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Open-Source-Power-Supply_PowerSupply_PCB/unrouted.dsn)

Size: 116.6 kB · Layers: 2 · Nets: 20 · Components: 50 · Dimensions: 86.99 x 83.19 mm (72.37 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       7.06 |      N/A |      7.06 |   0+  3+  0 |        0 |          0 |  1000 |       422 |    11785.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Open-Source-Power-Supply_PowerSupply_PCB_backup/unrouted.dsn)

Size: 108.8 kB · Layers: 2 · Nets: 20 · Components: 55 · Dimensions: 86.99 x 83.19 mm (72.37 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       6.07 |      N/A |      6.07 |   0+  4+  0 |        0 |          0 |  1000 |       485 |    13509.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/OpenTheremin_V3_Shield_OpenThereminCC/unrouted.dsn)

Size: 46.4 kB · Layers: 2 · Nets: 30 · Components: 109 · Dimensions: 60.0 x 100.0 mm (60.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     301.18 |      N/A |    301.18 |   0+  1+  0 |        0 |         22 |   976 |       674 |  1124783.1 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/opentilt_opentilt1/unrouted.dsn)

Size: 10.4 kB · Layers: 2 · Nets: 0 · Components: 12 · Dimensions: 100.33 x 36.83 mm (36.95 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.04 |      N/A |      2.04 |   0+  3+  0 |        0 |          0 |  1000 |        84 |     2366.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/opentilt_opentilt2/unrouted.dsn)

Size: 6.4 kB · Layers: 2 · Nets: 0 · Components: 9 · Dimensions: 49.78 x 22.35 mm (11.13 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.38 |      N/A |      3.38 |   0+  1+  0 |        1 |          0 |   958 |       243 |     4693.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/OpenVNAVI_driver unit/unrouted.dsn)

Size: 85.6 kB · Layers: 2 · Nets: 33 · Components: 78 · Dimensions: 172.5 x 56.08 mm (96.74 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     145.76 |      N/A |    145.76 |   0+ 18+  0 |        2 |          0 |   993 |       577 |   536885.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/OpenVNAVI_motor unit/unrouted.dsn)

Size: 7.5 kB · Layers: 2 · Nets: 2 · Components: 6 · Dimensions: 17.53 x 17.14 mm (3.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.94 |      N/A |      2.94 |   0+  1+  0 |        1 |          0 |   900 |       127 |     1910.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/OricMouse_OricMouse/unrouted.dsn)

Size: 44.3 kB · Layers: 2 · Nets: 14 · Components: 43 · Dimensions: 85.09 x 71.12 mm (60.52 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      42.31 |      N/A |     42.31 |   0+  1+  0 |        2 |          2 |   984 |       514 |   131342.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/oshtimer_transponder/unrouted.dsn)

Size: 12.2 kB · Layers: 2 · Nets: 1 · Components: 7 · Dimensions: 15.62 x 12.45 mm (1.94 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.26 |      N/A |      4.26 |   0+  2+  0 |        0 |          4 |   933 |       335 |     7542.2 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/oskirby_logicbone/unrouted.dsn)

Size: 210.8 kB · Layers: 8 · Nets: 328 · Components: 275 · Dimensions: 86.36 x 54.61 mm (47.16 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     910.02 |      N/A |    910.02 |   0+  0+  0 |      438 |         16 |   629 |       743 |  2091196.8 |    3 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ottawa-badges-2016_ottawa-badges-2016/unrouted.dsn)

Size: 61.9 kB · Layers: 2 · Nets: 33 · Components: 105 · Dimensions: 49.0 x 100.0 mm (49.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     105.12 |      N/A |    105.12 |   0+  1+  0 |        1 |         58 |   937 |       479 |   347545.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ottawa-badges-2016_ottawa-badge-tagger-2016/unrouted.dsn)

Size: 22 kB · Layers: 2 · Nets: 3 · Components: 14 · Dimensions: 24.0 x 50.0 mm (12.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.10 |      N/A |      2.10 |   0+  2+  0 |        0 |          0 |  1000 |       387 |     3261.7 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ourglass__autosave-watch-v4/unrouted.dsn)

Size: 30.3 kB · Layers: 4 · Nets: 11 · Components: 43 · Dimensions: 35.08 x 14.0 mm (4.91 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     265.00 |      N/A |    265.00 |   0+  1+  0 |        0 |         82 |   878 |       576 |  1187436.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ourglass_watch-v4/unrouted.dsn)

Size: 30.3 kB · Layers: 4 · Nets: 11 · Components: 43 · Dimensions: 35.08 x 14.0 mm (4.91 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     250.96 |      N/A |    250.96 |   0+  1+  0 |        0 |         82 |   878 |       587 |  1188469.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/OvenController_Oven/unrouted.dsn)

Size: 34 kB · Layers: 2 · Nets: 31 · Components: 83 · Dimensions: 100.08 x 100.08 mm (100.16 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      77.71 |      N/A |     77.71 |   0+  1+  0 |       28 |         76 |   746 |       519 |   316076.7 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Own-Mailbox-Hardware_eth/unrouted.dsn)

Size: 83.7 kB · Layers: 4 · Nets: 82 · Components: 99 · Dimensions: 50.0 x 50.0 mm (25.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     303.03 |      N/A |    303.03 |   0+  1+  0 |        1 |         10 |   990 |       917 |  1003118.1 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Own-Mailbox-Hardware_mailbox/unrouted.dsn)

Size: 83.3 kB · Layers: 4 · Nets: 82 · Components: 99 · Dimensions: 50.0 x 50.0 mm (25.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     304.59 |      N/A |    304.59 |   0+  9+  0 |        2 |         10 |   986 |       956 |   885129.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Own-Mailbox-Hardware_mailbox-before/unrouted.dsn)

Size: 156 kB · Layers: 4 · Nets: 196 · Components: 154 · Dimensions: 50.0 x 50.0 mm (25.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     908.14 |      N/A |    908.14 |   0+  1+  0 |        0 |         51 |   976 |       889 |  3766471.0 |    3 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Own-Mailbox-Hardware_pierre/unrouted.dsn)

Size: 147.6 kB · Layers: 4 · Nets: 146 · Components: 141 · Dimensions: 50.0 x 50.0 mm (25.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     906.76 |      N/A |    906.76 |   0+  1+  0 |        2 |         36 |   978 |      1435 |  2654661.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ozinverter_ozinverterkicad/unrouted.dsn)

Size: 55.7 kB · Layers: 2 · Nets: 40 · Components: 68 · Dimensions: 169.44 x 86.0 mm (145.72 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      13.86 |      N/A |     13.86 |   0+ 18+  0 |        1 |          0 |   993 |       429 |    45577.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/P8000_WDC_Emulator_P8000_WDC_Emulator/unrouted.dsn)

Size: 90.1 kB · Layers: 2 · Nets: 73 · Components: 214 · Dimensions: 144.0 x 160.4 mm (230.98 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     236.93 |      N/A |    236.93 |   0+ 20+  0 |        1 |          0 |   997 |       640 |   667858.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Paperino_HW_paperino_breakout/unrouted.dsn)

Size: 9.3 kB · Layers: 2 · Nets: 2 · Components: 7 · Dimensions: 258.63 x 217.45 mm (562.39 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.04 |      N/A |      2.04 |   0+  2+  0 |        0 |          0 |  1000 |       200 |     1639.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Paperino_HW_paperino_module/unrouted.dsn)

Size: 13.2 kB · Layers: 2 · Nets: 10 · Components: 20 · Dimensions: 25.4 x 22.0 mm (5.59 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      26.64 |      N/A |     26.64 |   0+  1+  0 |        1 |          0 |   984 |       535 |    85035.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Paperino_HW_Paperino_shield/unrouted.dsn)

Size: 10.3 kB · Layers: 2 · Nets: 13 · Components: 7 · Dimensions: 70.0 x 36.9 mm (25.83 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.31 |      N/A |      3.31 |   0+  3+  0 |        0 |          0 |  1000 |       217 |     4995.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/PCB_constant_current_ac_hv/unrouted.dsn)

Size: 16.1 kB · Layers: 2 · Nets: 1 · Components: 12 · Dimensions: 81.0 x 22.0 mm (17.82 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.80 |      N/A |      0.80 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/pcb_HDMI2RGB_HDMI2RGB/unrouted.dsn)

Size: 66.5 kB · Layers: 4 · Nets: 51 · Components: 115 · Dimensions: 58.42 x 63.5 mm (37.1 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     302.74 |      N/A |    302.74 |   0+  6+  0 |       46 |        150 |   795 |       616 |   890445.8 |    3 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/PCB_serie_led_strip/unrouted.dsn)

Size: 6.2 kB · Layers: 2 · Nets: 11 · Components: 12 · Dimensions: 91.0 x 10.0 mm (9.1 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.53 |      N/A |      0.53 |   0+  1+  0 |        0 |          0 |  1000 |         0 |        0.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/PCB_small_halogen_replacement/unrouted.dsn)

Size: 20.4 kB · Layers: 2 · Nets: 34 · Components: 43 · Dimensions: 40.0 x 50.0 mm (20.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      22.12 |      N/A |     22.12 |   0+  1+  0 |        6 |          0 |   885 |       553 |    63724.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/pcb-covox-amp_pcb-covox-amp/unrouted.dsn)

Size: 31.8 kB · Layers: 2 · Nets: 16 · Components: 45 · Dimensions: 78.0 x 56.0 mm (43.68 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      27.65 |      N/A |     27.65 |   0+  1+  0 |        1 |          0 |   987 |       485 |    78504.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/pcb-covox-amp-v2_pcb-covox-amp-v2/unrouted.dsn)

Size: 36.4 kB · Layers: 2 · Nets: 27 · Components: 61 · Dimensions: 87.0 x 56.0 mm (48.72 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      19.11 |      N/A |     19.11 |   0+ 13+  0 |        0 |          0 |  1000 |       504 |    65793.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/pcb-ice40-vga_VGA expansion/unrouted.dsn)

Size: 13.6 kB · Layers: 2 · Nets: 38 · Components: 24 · Dimensions: 76.2 x 48.0 mm (36.58 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      64.09 |      N/A |     64.09 |   0+  1+  0 |        3 |         40 |   776 |       469 |    96452.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/pcb-ks0108-128x64-glcd_circuit/unrouted.dsn)

Size: 9.7 kB · Layers: 2 · Nets: 0 · Components: 14 · Dimensions: 45.72 x 12.7 mm (5.81 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      28.20 |      N/A |     28.20 |   0+  1+  0 |        5 |         42 |   721 |       541 |    72841.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/pcbproject_opensda_project_opensda/unrouted.dsn)

Size: 29.8 kB · Layers: 2 · Nets: 24 · Components: 50 · Dimensions: 66.04 x 38.1 mm (25.16 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     137.50 |      N/A |    137.50 |   0+  1+  0 |       30 |         15 |   703 |       501 |   421393.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/pcb-usb-ft245r-parallel-adapter_pcb-usb-ft245r-parallel-adapter/unrouted.dsn)

Size: 19.5 kB · Layers: 2 · Nets: 15 · Components: 23 · Dimensions: 49.0 x 57.0 mm (27.93 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      25.33 |      N/A |     25.33 |   0+  1+  0 |        0 |          4 |   987 |       569 |    95740.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/PCIE-to-MXM-Adapter_PCIEx1toMXM3.0/unrouted.dsn)

Size: 77 kB · Layers: 2 · Nets: 107 · Components: 34 · Dimensions: 170.17 x 148.1 mm (252.02 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     311.79 |      N/A |    311.79 |   0+  1+  0 |        3 |         33 |   951 |       729 |  1143088.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/PDB_OSD_HARDWARE_Quadcopter Power Board/unrouted.dsn)

Size: 37.4 kB · Layers: 4 · Nets: 42 · Components: 140 · Dimensions: 38.0 x 44.6 mm (16.95 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     301.83 |      N/A |    301.83 |   0+  1+  0 |       21 |        256 |   655 |       609 |   762100.2 |    7 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/perfplusplus_Ard-perf++/unrouted.dsn)

Size: 77.3 kB · Layers: 2 · Nets: 421 · Components: 457 · Dimensions: 68.58 x 53.34 mm (36.58 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes         |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :------------ |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |        N/A |      N/A |       N/A |   0+  0+  0 |      N/A |        N/A |   N/A |         0 |        0.0 |    3 / 2 | LOAD ERROR, 2 |


### Fixture: [unrouted.dsn](../fixtures/PCBench/pesho_pesho/unrouted.dsn)

Size: 32.1 kB · Layers: 2 · Nets: 46 · Components: 60 · Dimensions: 132.5 x 65.0 mm (86.12 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       8.50 |      N/A |      8.50 |   0+  4+  0 |        0 |          0 |  1000 |       517 |    20794.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/PGA2311_pga2311/unrouted.dsn)

Size: 21.5 kB · Layers: 2 · Nets: 3 · Components: 26 · Dimensions: 50.0 x 50.0 mm (25.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      15.07 |      N/A |     15.07 |   0+  1+  0 |        0 |          9 |   958 |       424 |    59937.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Phased-Array-Microphone-using-FPGA_SateliteMicrophone/unrouted.dsn)

Size: 13.9 kB · Layers: 2 · Nets: 4 · Components: 21 · Dimensions: 16.0 x 40.0 mm (6.4 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.27 |      N/A |      3.27 |   0+  3+  0 |        0 |          0 |  1000 |       143 |     3890.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/phone_amp_phone_amp/unrouted.dsn)

Size: 22.2 kB · Layers: 2 · Nets: 27 · Components: 42 · Dimensions: 11.62 x 56.15 mm (6.52 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      64.89 |      N/A |     64.89 |   0+  1+  0 |        4 |         31 |   884 |       535 |   218933.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/phone_rtty_interface_phone_rtty_rev_a/unrouted.dsn)

Size: 80.2 kB · Layers: 2 · Nets: 14 · Components: 35 · Dimensions: 50.0 x 50.0 mm (25.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.09 |      N/A |      2.09 |   0+  2+  0 |        0 |          0 |  1000 |        28 |      956.1 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/phone_rtty_interface_phone_rtty_rev_b/unrouted.dsn)

Size: 53.5 kB · Layers: 2 · Nets: 15 · Components: 38 · Dimensions: 50.0 x 50.0 mm (25.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       8.38 |      N/A |      8.38 |   0+  1+  0 |        1 |          0 |   985 |       484 |    18446.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Photodiode_dethead/unrouted.dsn)

Size: 16 kB · Layers: 2 · Nets: 8 · Components: 22 · Dimensions: 50.0 x 23.0 mm (11.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.74 |      N/A |      2.74 |   0+  2+  0 |        0 |          0 |  1000 |       267 |     1703.1 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/photon_Sprinkler_sprinkler/unrouted.dsn)

Size: 16.4 kB · Layers: 2 · Nets: 25 · Components: 17 · Dimensions: 83.82 x 53.39 mm (44.75 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.38 |      N/A |      3.38 |   0+  2+  0 |        0 |          0 |  1000 |        71 |     2332.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/pi_plant_MCP3002/unrouted.dsn)

Size: 14.6 kB · Layers: 2 · Nets: 15 · Components: 11 · Dimensions: 33.02 x 30.48 mm (10.06 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.82 |      N/A |      0.82 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/PiBook_pibook/unrouted.dsn)

Size: 75.3 kB · Layers: 2 · Nets: 24 · Components: 174 · Dimensions: 85.0 x 85.0 mm (72.25 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     451.98 |      N/A |    451.98 |   0+ 18+  0 |        2 |          7 |   992 |       830 |  1504153.2 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/piboy-zero_piggrl_zero_baseboard/unrouted.dsn)

Size: 51 kB · Layers: 2 · Nets: 35 · Components: 62 · Dimensions: 101.0 x 43.9 mm (44.34 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     302.56 |      N/A |    302.56 |   0+  1+  0 |        0 |         24 |   964 |       608 |   990380.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/PICA_pica/unrouted.dsn)

Size: 85.7 kB · Layers: 2 · Nets: 87 · Components: 135 · Dimensions: 114.3 x 94.92 mm (108.49 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     131.39 |      N/A |    131.39 |   0+  1+  0 |        1 |         20 |   987 |       559 |   550772.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/PICA_rampzbackup/unrouted.dsn)

Size: 57.4 kB · Layers: 2 · Nets: 66 · Components: 76 · Dimensions: 129.48 x 96.98 mm (125.57 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      53.21 |      N/A |     53.21 |   0+ 18+  0 |        1 |          0 |   996 |       527 |   187684.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/pico-pi-rel_pico-pi/unrouted.dsn)

Size: 71.9 kB · Layers: 4 · Nets: 71 · Components: 98 · Dimensions: 65.0 x 30.0 mm (19.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     184.14 |      N/A |    184.14 |   0+  1+  0 |        1 |         14 |   983 |       843 |   580474.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/PiPlay_AudioHAT/unrouted.dsn)

Size: 29.3 kB · Layers: 2 · Nets: 15 · Components: 43 · Dimensions: 65.0 x 30.0 mm (19.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      44.93 |      N/A |     44.93 |   0+  1+  0 |        2 |         65 |   861 |       475 |   185095.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/PiPlay_SDHat/unrouted.dsn)

Size: 21 kB · Layers: 2 · Nets: 6 · Components: 23 · Dimensions: 65.0 x 30.0 mm (19.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      40.52 |      N/A |     40.52 |   0+  1+  0 |        0 |         64 |   775 |       513 |   152541.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/PixyWirelessShield_Shield PIXY/unrouted.dsn)

Size: 5.2 kB · Layers: 2 · Nets: 5 · Components: 4 · Dimensions: 36.83 x 26.03 mm (9.59 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.80 |      N/A |      1.80 |   0+  2+  0 |        0 |          0 |  1000 |        43 |       54.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/PiZeroHub_PiZeroHub/unrouted.dsn)

Size: 30.2 kB · Layers: 2 · Nets: 24 · Components: 37 · Dimensions: 65.0 x 26.0 mm (16.9 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     120.51 |      N/A |    120.51 |   0+  1+  0 |        0 |          8 |   985 |       529 |   431478.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Pi-Zero-SD-and-Audio-Out_BaseBoard/unrouted.dsn)

Size: 33 kB · Layers: 2 · Nets: 19 · Components: 57 · Dimensions: 64.0 x 40.0 mm (25.6 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     302.76 |      N/A |    302.76 |   0+  1+  0 |        0 |         40 |   945 |       570 |   819311.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/pi-zero-stepper-board_pi-zero-stepper-board/unrouted.dsn)

Size: 54.1 kB · Layers: 2 · Nets: 39 · Components: 25 · Dimensions: 83.82 x 74.3 mm (62.28 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      14.92 |      N/A |     14.92 |   0+  1+  0 |        1 |          0 |   990 |       440 |    40854.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Planet_Vocab/unrouted.dsn)

Size: 16.1 kB · Layers: 2 · Nets: 0 · Components: 33 · Dimensions: 53.34 x 68.58 mm (36.58 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       7.36 |      N/A |      7.36 |   0+  1+  0 |        1 |          0 |   983 |       440 |    17596.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/PmodHDMIIn_PmodHDMIIn/unrouted.dsn)

Size: 22.1 kB · Layers: 2 · Nets: 11 · Components: 33 · Dimensions: 43.18 x 34.29 mm (14.81 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      25.43 |      N/A |     25.43 |   0+ 18+  0 |        1 |          0 |   991 |       463 |    83447.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/pmod-rgmii_pmod-rgmii-RTL8211E-module/unrouted.dsn)

Size: 37.1 kB · Layers: 4 · Nets: 0 · Components: 70 · Dimensions: 57.08 x 83.8 mm (47.83 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     302.31 |      N/A |    302.31 |   0+ 12+  0 |       24 |          0 |   887 |       672 |   864700.7 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/pmtoy-hardware-pcb_PMToy/unrouted.dsn)

Size: 41.9 kB · Layers: 2 · Nets: 26 · Components: 52 · Dimensions: 48.26 x 41.91 mm (20.23 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       9.76 |      N/A |      9.76 |   0+  3+  0 |        0 |          2 |   996 |       341 |    24710.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/PocketBone_pocketbone-kicad/unrouted.dsn)

Size: 80.1 kB · Layers: 4 · Nets: 344 · Components: 65 · Dimensions: 55.0 x 35.0 mm (19.25 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     622.08 |      N/A |    622.08 |   0+  1+  0 |       43 |          2 |   784 |       829 |  1691036.2 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/pocketbone-kicad_pocketbone-kicad/unrouted.dsn)

Size: 57.6 kB · Layers: 4 · Nets: 33 · Components: 65 · Dimensions: 55.0 x 35.0 mm (19.25 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     301.76 |      N/A |    301.76 |   0+ 11+  0 |       45 |          2 |   767 |       673 |   764850.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/PoEPi-hardware__autosave-pizero-poe/unrouted.dsn)

Size: 74.1 kB · Layers: 2 · Nets: 66 · Components: 85 · Dimensions: 93.8 x 30.0 mm (28.14 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     200.45 |      N/A |    200.45 |   0+  1+  0 |        6 |          8 |   961 |       533 |   621942.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/PoEPi-hardware_pizero-poe/unrouted.dsn)

Size: 71.6 kB · Layers: 2 · Nets: 61 · Components: 77 · Dimensions: 81.8 x 30.0 mm (24.54 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     189.69 |      N/A |    189.69 |   0+  1+  0 |        6 |          9 |   957 |       593 |   533583.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/polearm-weapon_rod/unrouted.dsn)

Size: 21.1 kB · Layers: 2 · Nets: 5 · Components: 29 · Dimensions: 34.29 x 124.46 mm (42.68 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      14.32 |      N/A |     14.32 |   0+  1+  0 |        1 |         12 |   943 |       458 |    45366.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/poncho_fpga_fpga-dongle/unrouted.dsn)

Size: 95 kB · Layers: 2 · Nets: 72 · Components: 114 · Dimensions: 85.8 x 137.0 mm (117.55 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     303.24 |      N/A |    303.24 |   0+  2+  0 |       95 |          1 |   769 |       793 |   832741.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ponchoeduciaaiot_ponchoeduciaaiot/unrouted.dsn)

Size: 71.3 kB · Layers: 2 · Nets: 83 · Components: 43 · Dimensions: 87.0 x 81.2 mm (70.64 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      19.85 |      N/A |     19.85 |   0+  1+  0 |        2 |         50 |   896 |       560 |    72916.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ponyser-pcb_Ponyser/unrouted.dsn)

Size: 7.7 kB · Layers: 2 · Nets: 0 · Components: 12 · Dimensions: 33.02 x 22.86 mm (7.55 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.03 |      N/A |      1.03 |   0+  2+  0 |        0 |          0 |  1000 |        87 |     1331.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/POV_POV/unrouted.dsn)

Size: 29.7 kB · Layers: 2 · Nets: 4 · Components: 16 · Dimensions: 69.5 x 23.5 mm (16.33 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.03 |      N/A |      2.03 |   0+  4+  0 |        0 |          0 |  1000 |       360 |     2519.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/powerfreq-hardware_powerfreq/unrouted.dsn)

Size: 38.2 kB · Layers: 2 · Nets: 24 · Components: 32 · Dimensions: 117.29 x 92.29 mm (108.25 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      21.77 |      N/A |     21.77 |   0+  1+  0 |        1 |          7 |   950 |       530 |    37014.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/PowerGloveUHID_main_board/unrouted.dsn)

Size: 59.4 kB · Layers: 2 · Nets: 29 · Components: 57 · Dimensions: 113.54 x 55.12 mm (62.58 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      12.67 |      N/A |     12.67 |   0+  4+  0 |        0 |          0 |  1000 |       471 |    34109.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/PowerGloveUHID_sensor_board/unrouted.dsn)

Size: 18.1 kB · Layers: 2 · Nets: 2 · Components: 18 · Dimensions: 48.26 x 43.18 mm (20.84 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.71 |      N/A |      1.71 |   0+  3+  0 |        0 |          0 |  1000 |        80 |     1118.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/powersupply_5v_v1_powersupply_5v_v1/unrouted.dsn)

Size: 31 kB · Layers: 2 · Nets: 7 · Components: 57 · Dimensions: 160.1 x 100.0 mm (160.1 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      11.83 |      N/A |     11.83 |   0+  1+  0 |        0 |          6 |   982 |       505 |    47813.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Practicas-Curso-Kicad_Ej2/unrouted.dsn)

Size: 23.6 kB · Layers: 2 · Nets: 19 · Components: 15 · Dimensions: 40.64 x 60.96 mm (24.77 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.43 |      N/A |      3.43 |   0+  3+  0 |        0 |          0 |  1000 |       372 |     6794.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Practicas-Curso-Kicad_Ejercicio_2/unrouted.dsn)

Size: 22.8 kB · Layers: 2 · Nets: 19 · Components: 15 · Dimensions: 0.0 x 0.0 mm (0.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.56 |      N/A |      4.56 |   0+  3+  0 |        0 |          0 |  1000 |       208 |     8089.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Practicas-Curso-Kicad_Ejercicio2/unrouted.dsn)

Size: 24 kB · Layers: 2 · Nets: 11 · Components: 15 · Dimensions: 40.64 x 60.96 mm (24.77 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       7.12 |      N/A |      7.12 |   0+  3+  0 |        0 |          0 |  1000 |       303 |     8359.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Practicas-Curso-Kicad_Fusari_Diego/unrouted.dsn)

Size: 9.1 kB · Layers: 2 · Nets: 5 · Components: 6 · Dimensions: 27.31 x 16.51 mm (4.51 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.65 |      N/A |      0.65 |   0+  1+  0 |        0 |          2 |   950 |         0 |        0.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Practicas-Curso-Kicad_Ivan-Dmytrow/unrouted.dsn)

Size: 9.1 kB · Layers: 2 · Nets: 5 · Components: 6 · Dimensions: 27.31 x 17.78 mm (4.86 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.59 |      N/A |      0.59 |   0+  1+  0 |        0 |          2 |   950 |         0 |        0.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Practicas-Curso-Kicad_mainenti_miguel/unrouted.dsn)

Size: 9.1 kB · Layers: 2 · Nets: 5 · Components: 6 · Dimensions: 27.18 x 19.05 mm (5.18 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.66 |      N/A |      0.66 |   0+  1+  0 |        0 |          2 |   950 |         0 |        0.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Practicas-Curso-Kicad_PRY_EJ1/unrouted.dsn)

Size: 9.1 kB · Layers: 2 · Nets: 5 · Components: 6 · Dimensions: 34.29 x 17.78 mm (6.1 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.35 |      N/A |      0.35 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Practicas-Curso-Kicad_Salguero_Federico2/unrouted.dsn)

Size: 23.7 kB · Layers: 2 · Nets: 11 · Components: 15 · Dimensions: 40.64 x 60.96 mm (24.77 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       5.85 |      N/A |      5.85 |   0+  3+  0 |        0 |          0 |  1000 |       328 |    11309.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/prog-cc-100mA_prog-cc-100mA/unrouted.dsn)

Size: 21.7 kB · Layers: 2 · Nets: 14 · Components: 40 · Dimensions: 49.53 x 49.53 mm (24.53 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      11.33 |      N/A |     11.33 |   0+  1+  0 |        7 |          0 |   894 |       460 |    37480.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/protoarray_proto_array/unrouted.dsn)

Size: 48 kB · Layers: 2 · Nets: 109 · Components: 76 · Dimensions: 99.06 x 129.54 mm (128.32 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     111.68 |      N/A |    111.68 |   0+  1+  0 |        1 |         12 |   982 |       569 |   382665.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Protoboard-DIP-for-CHIP_chip-dip-protoboard/unrouted.dsn)

Size: 22 kB · Layers: 2 · Nets: 0 · Components: 121 · Dimensions: 40.64 x 55.25 mm (22.45 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      19.83 |      N/A |     19.83 |   0+  1+  0 |        2 |         92 |   773 |       490 |    56934.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ProtoHead_v4_Protohead_v4_FAB_rc1/unrouted.dsn)

Size: 11.3 kB · Layers: 2 · Nets: 11 · Components: 7 · Dimensions: 53.0 x 14.0 mm (7.42 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      11.95 |      N/A |     11.95 |   0+  1+  0 |        0 |         10 |   941 |       459 |    35958.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Prototyping_Workshop_Prototyping_PCB/unrouted.dsn)

Size: 57.4 kB · Layers: 2 · Nets: 33 · Components: 47 · Dimensions: 160.0 x 32.59 mm (52.14 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       6.63 |      N/A |      6.63 |   0+  3+  0 |        0 |          0 |  1000 |       497 |     9428.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/PSU-5V-6V-Dual9V_PSU-5V-6V-Dual9V/unrouted.dsn)

Size: 28.1 kB · Layers: 2 · Nets: 20 · Components: 103 · Dimensions: 100.0 x 100.0 mm (100.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      10.11 |      N/A |     10.11 |   0+  2+  0 |        0 |          0 |  1000 |       385 |    34933.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/PsuFanController_FanController/unrouted.dsn)

Size: 16.1 kB · Layers: 2 · Nets: 8 · Components: 12 · Dimensions: 43.22 x 25.55 mm (11.04 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.84 |      N/A |      0.84 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/psx2vectrex_vectrexmando/unrouted.dsn)

Size: 33.2 kB · Layers: 2 · Nets: 0 · Components: 41 · Dimensions: 33.02 x 57.4 mm (18.95 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      14.12 |      N/A |     14.12 |   0+  4+  0 |        0 |          0 |  1000 |       545 |    47459.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/pulse_v1_pulse/unrouted.dsn)

Size: 14.2 kB · Layers: 2 · Nets: 0 · Components: 16 · Dimensions: 25.4 x 10.41 mm (2.64 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      13.54 |      N/A |     13.54 |   0+  1+  0 |        2 |          6 |   886 |       444 |    28766.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/pwm-2420-lus_pwm-2420-lus/unrouted.dsn)

Size: 176.8 kB · Layers: 2 · Nets: 47 · Components: 130 · Dimensions: 90.0 x 85.0 mm (76.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     192.08 |      N/A |    192.08 |   0+  1+  0 |        6 |         79 |   931 |       890 |   744550.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/PWRmeter_PWMeter/unrouted.dsn)

Size: 24.6 kB · Layers: 2 · Nets: 12 · Components: 20 · Dimensions: 100.0 x 50.0 mm (50.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.20 |      N/A |      4.20 |   0+  3+  0 |        0 |          0 |  1000 |       284 |     5319.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/qrp_swr_meter_swr_meter_rev_a/unrouted.dsn)

Size: 37.1 kB · Layers: 2 · Nets: 13 · Components: 26 · Dimensions: 51.5 x 77.0 mm (39.66 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       6.76 |      N/A |      6.76 |   0+  1+  0 |        0 |          8 |   961 |       328 |    16202.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/QRPCard_QRPCard/unrouted.dsn)

Size: 23.4 kB · Layers: 2 · Nets: 19 · Components: 33 · Dimensions: 87.12 x 76.71 mm (66.83 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      12.21 |      N/A |     12.21 |   0+  3+  0 |        0 |          0 |  1000 |       420 |    19327.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/R1002_R1002/unrouted.dsn)

Size: 17.7 kB · Layers: 2 · Nets: 0 · Components: 13 · Dimensions: 47.0 x 33.3 mm (15.65 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      62.30 |      N/A |     62.30 |   0+ 18+  0 |        1 |         49 |   874 |       441 |    87200.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/R1007_R1007/unrouted.dsn)

Size: 22.2 kB · Layers: 4 · Nets: 4 · Components: 36 · Dimensions: 69.0 x 38.6 mm (26.63 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      11.22 |      N/A |     11.22 |   0+  3+  0 |        0 |          4 |   991 |       381 |    27606.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Radar-System_fmcw_radar/unrouted.dsn)

Size: 23.4 kB · Layers: 4 · Nets: 18 · Components: 30 · Dimensions: 51.0 x 25.0 mm (12.75 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      77.28 |      N/A |     77.28 |   0+  1+  0 |        5 |         11 |   904 |       556 |   240214.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/radio_antenna-iridium/unrouted.dsn)

Size: 28.4 kB · Layers: 4 · Nets: 21 · Components: 37 · Dimensions: 60.0 x 60.0 mm (36.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      50.38 |      N/A |     50.38 |   0+  1+  0 |        0 |         14 |   966 |       534 |   233703.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/radio_saw_dcc6c/unrouted.dsn)

Size: 7 kB · Layers: 4 · Nets: 4 · Components: 7 · Dimensions: 28.4 x 20.0 mm (5.68 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       8.70 |      N/A |      8.70 |   0+  2+  0 |        0 |         10 |   895 |       512 |    23595.0 |    2 / 0 | 1     |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Radio-FM-TEA5767_radio tea5767/unrouted.dsn)

Size: 30.3 kB · Layers: 2 · Nets: 0 · Components: 33 · Dimensions: 0.0 x 0.0 mm (0.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      19.89 |      N/A |     19.89 |   0+  1+  0 |        1 |         16 |   949 |       440 |    61670.7 |    2 / 0 | 1     |


### Fixture: [unrouted.dsn](../fixtures/PCBench/RainbowFox_RainbowFox/unrouted.dsn)

Size: 79.2 kB · Layers: 2 · Nets: 79 · Components: 316 · Dimensions: 304.8 x 95.25 mm (290.32 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     902.83 |      N/A |    902.83 |   0+  5+  0 |      103 |        118 |   822 |      1209 |  2236100.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/raspberry_pi_pullup_button_pullup_shutdown_button(revB)/unrouted.dsn)

Size: 8.9 kB · Layers: 2 · Nets: 0 · Components: 4 · Dimensions: 6.35 x 21.84 mm (1.39 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       6.54 |      N/A |      6.54 |   0+  2+  0 |        0 |          8 |   680 |       299 |     5986.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/raspberry_pi_pullup_button_pullup_shutdown_button/unrouted.dsn)

Size: 8.2 kB · Layers: 2 · Nets: 0 · Components: 4 · Dimensions: 4.83 x 23.62 mm (1.14 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.85 |      N/A |      0.85 |   0+  1+  0 |        0 |         11 |   560 |         0 |        0.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/raspberrypi-3-usb-hub_usb_hub/unrouted.dsn)

Size: 22.7 kB · Layers: 2 · Nets: 25 · Components: 21 · Dimensions: 127.0 x 60.96 mm (77.42 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      12.05 |      N/A |     12.05 |   0+  1+  0 |       10 |         12 |   790 |       465 |    34341.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/RaspberryPi-PoE_PoELLi_PI/unrouted.dsn)

Size: 46.8 kB · Layers: 2 · Nets: 50 · Components: 46 · Dimensions: 85.0 x 56.0 mm (47.6 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.44 |      N/A |      4.44 |   0+  2+  0 |        0 |          1 |   998 |       402 |    11289.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/raspberry-pi-rfm69_rfm69-rpi/unrouted.dsn)

Size: 41.4 kB · Layers: 2 · Nets: 44 · Components: 21 · Dimensions: 65.0 x 56.0 mm (36.4 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     167.91 |      N/A |    167.91 |   0+  1+  0 |        7 |         23 |   878 |       592 |   368380.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Raspberry-Pi-Soft-Power-Controller_Switching Supply TPS563208 MCI/unrouted.dsn)

Size: 7.8 kB · Layers: 2 · Nets: 2 · Components: 10 · Dimensions: 45.0 x 20.0 mm (9.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.05 |      N/A |      1.05 |   0+  3+  0 |        0 |          0 |  1000 |        35 |     1716.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Raspberry-Pi-Soft-Power-Controller_Zero Current Soft Power/unrouted.dsn)

Size: 17.9 kB · Layers: 2 · Nets: 3 · Components: 27 · Dimensions: 64.6 x 28.5 mm (18.41 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.79 |      N/A |      1.79 |   0+  2+  0 |        0 |          0 |  1000 |       163 |     1418.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/raspican_raspican/unrouted.dsn)

Size: 30.5 kB · Layers: 2 · Nets: 32 · Components: 22 · Dimensions: 85.0 x 56.0 mm (47.6 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       6.48 |      N/A |      6.48 |   0+  1+  0 |        0 |          6 |   976 |       516 |    25916.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/RASPMINI_RASPmini/unrouted.dsn)

Size: 27 kB · Layers: 2 · Nets: 19 · Components: 15 · Dimensions: 65.0 x 56.0 mm (36.4 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       5.70 |      N/A |      5.70 |   0+  5+  0 |        0 |          0 |  1000 |       425 |    11555.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/rc2014_bank_switcher_z80_cpm_mmu/unrouted.dsn)

Size: 22.9 kB · Layers: 2 · Nets: 0 · Components: 17 · Dimensions: 102.87 x 22.86 mm (23.52 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       8.73 |      N/A |      8.73 |   0+  1+  0 |        1 |          0 |   982 |       488 |    20744.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/RC2014_RC2014 IDE/unrouted.dsn)

Size: 72.6 kB · Layers: 2 · Nets: 4 · Components: 12 · Dimensions: 99.06 x 49.53 mm (49.06 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      18.21 |      N/A |     18.21 |   0+  1+  0 |        0 |         78 |   726 |       526 |    75204.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/RC2014_RC2014 RAM/unrouted.dsn)

Size: 65 kB · Layers: 2 · Nets: 7 · Components: 11 · Dimensions: 99.06 x 49.53 mm (49.06 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      25.85 |      N/A |     25.85 |   0+  1+  0 |        0 |         92 |   688 |       470 |    98318.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/RC2014_RC2014 Tandy Sound Card/unrouted.dsn)

Size: 107.9 kB · Layers: 2 · Nets: 10 · Components: 32 · Dimensions: 99.06 x 49.53 mm (49.06 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      40.91 |      N/A |     40.91 |   0+  1+  0 |        2 |         92 |   837 |       579 |   127889.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/reach-nrf51822-hardware_reach-target-hw/unrouted.dsn)

Size: 37.2 kB · Layers: 2 · Nets: 7 · Components: 62 · Dimensions: 41.91 x 27.94 mm (11.71 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     207.89 |      N/A |    207.89 |   0+  1+  0 |       32 |         15 |   768 |       552 |   725926.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/recalbox-gpio-board__autosave-board/unrouted.dsn)

Size: 23.9 kB · Layers: 2 · Nets: 26 · Components: 123 · Dimensions: 210.82 x 86.36 mm (182.06 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      54.90 |      N/A |     54.90 |   0+ 13+  0 |        0 |          0 |  1000 |       604 |   196663.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/recalbox-gpio-board_board/unrouted.dsn)

Size: 23.9 kB · Layers: 2 · Nets: 26 · Components: 123 · Dimensions: 210.82 x 86.36 mm (182.06 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      54.58 |      N/A |     54.58 |   0+ 13+  0 |        0 |          0 |  1000 |       578 |   198279.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/red-scout_red-scout-aa/unrouted.dsn)

Size: 26.2 kB · Layers: 2 · Nets: 26 · Components: 41 · Dimensions: 60.0 x 97.5 mm (58.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       9.17 |      N/A |      9.17 |   0+  5+  0 |        0 |          0 |  1000 |       441 |    31120.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ReST32_combined/unrouted.dsn)

Size: 28.3 kB · Layers: 2 · Nets: 1 · Components: 53 · Dimensions: 64.75 x 43.5 mm (28.17 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      17.02 |      N/A |     17.02 |   0+  8+  0 |        0 |          1 |   998 |       517 |    57037.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ReST32_ReST RRD-FGC-Adapter/unrouted.dsn)

Size: 20 kB · Layers: 2 · Nets: 0 · Components: 40 · Dimensions: 28.75 x 43.5 mm (12.51 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       7.14 |      N/A |      7.14 |   0+  4+  0 |        0 |          0 |  1000 |       436 |    20462.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ReST32_ReST SD-Module/unrouted.dsn)

Size: 19.6 kB · Layers: 2 · Nets: 1 · Components: 15 · Dimensions: 33.0 x 40.0 mm (13.2 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.08 |      N/A |      4.08 |   0+  6+  0 |        0 |          1 |   994 |       460 |    12066.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ReST32_ReST/unrouted.dsn)

Size: 80.7 kB · Layers: 2 · Nets: 77 · Components: 163 · Dimensions: 100.0 x 100.0 mm (100.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     322.66 |      N/A |    322.66 |   0+ 18+  0 |        3 |          4 |   992 |       941 |  1343096.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Retro1DecodingModules_AddressDecoderModule/unrouted.dsn)

Size: 14.5 kB · Layers: 4 · Nets: 12 · Components: 5 · Dimensions: 51.05 x 18.03 mm (9.2 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.82 |      N/A |      2.82 |   0+  4+  0 |        0 |          0 |  1000 |       309 |     3247.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/retrocon_bbb-adapter/unrouted.dsn)

Size: 49 kB · Layers: 2 · Nets: 84 · Components: 15 · Dimensions: 58.42 x 80.01 mm (46.74 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.13 |      N/A |      4.13 |   0+  4+  0 |        0 |          0 |  1000 |       448 |    10253.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/retrocon_driver_board/unrouted.dsn)

Size: 24.8 kB · Layers: 2 · Nets: 39 · Components: 29 · Dimensions: 83.82 x 53.34 mm (44.71 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.50 |      N/A |      2.50 |   0+  3+  0 |        0 |          0 |  1000 |        55 |     4545.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/retroreflectors_CONGFLOCK/unrouted.dsn)

Size: 4 kB · Layers: 2 · Nets: 0 · Components: 7 · Dimensions: 6.35 x 6.35 mm (0.4 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.03 |      N/A |      2.03 |   0+  1+  0 |        6 |         17 |    60 |       535 |     3746.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/retroreflectors_FLAMENCOFLOCK/unrouted.dsn)

Size: 4.9 kB · Layers: 2 · Nets: 0 · Components: 5 · Dimensions: 31.75 x 31.75 mm (10.08 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.73 |      N/A |      2.73 |   0+  2+  0 |        0 |          6 |   933 |       319 |     5830.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/retroreflectors_RUMBAFLOCK/unrouted.dsn)

Size: 4.2 kB · Layers: 2 · Nets: 0 · Components: 7 · Dimensions: 6.35 x 6.35 mm (0.4 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.87 |      N/A |      1.87 |   0+  1+  0 |        7 |         12 |     0 |       283 |     1311.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/retroreflectors_SALSAFLOCK/unrouted.dsn)

Size: 8.3 kB · Layers: 2 · Nets: 0 · Components: 15 · Dimensions: 30.8 x 52.8 mm (16.26 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      23.41 |      N/A |     23.41 |   0+  1+  0 |       13 |         14 |   605 |       545 |    74460.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/retroreflectors_TANGOFLOCK/unrouted.dsn)

Size: 5.4 kB · Layers: 2 · Nets: 0 · Components: 5 · Dimensions: 31.75 x 31.75 mm (10.08 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.52 |      N/A |      1.52 |   0+  1+  0 |        0 |          4 |   943 |       239 |     1435.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/RF_SIGNAL_GENERATOR_HARDWARE_RF Signal Generator/unrouted.dsn)

Size: 45.5 kB · Layers: 4 · Nets: 57 · Components: 98 · Dimensions: 46.61 x 22.86 mm (10.66 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     306.21 |      N/A |    306.21 |   0+  1+  0 |        0 |        138 |   907 |       556 |  1247507.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/rf-biscuit_rf_biscuit/unrouted.dsn)

Size: 16.9 kB · Layers: 2 · Nets: 15 · Components: 64 · Dimensions: 37.0 x 24.0 mm (8.88 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      75.90 |      N/A |     75.90 |   0+  1+  0 |        0 |        112 |   785 |       583 |   408215.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/rfcx-sentinel-pcb_Mainboard/unrouted.dsn)

Size: 55.6 kB · Layers: 2 · Nets: 59 · Components: 102 · Dimensions: 83.82 x 63.5 mm (53.23 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      43.02 |      N/A |     43.02 |   0+  5+  0 |        0 |          0 |  1000 |       562 |   160527.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/rfcx-sentinel-pcb_MPPT/unrouted.dsn)

Size: 42.8 kB · Layers: 2 · Nets: 33 · Components: 88 · Dimensions: 83.82 x 63.5 mm (53.23 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      76.35 |      N/A |     76.35 |   0+  1+  0 |       12 |         10 |   910 |       553 |   343480.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/rfidBoard_POE/unrouted.dsn)

Size: 64.2 kB · Layers: 4 · Nets: 69 · Components: 127 · Dimensions: 100.0 x 50.0 mm (50.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     113.87 |      N/A |    113.87 |   0+ 13+  0 |        0 |          1 |   999 |       828 |   438160.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/rfidBoard_rfid/unrouted.dsn)

Size: 20.9 kB · Layers: 2 · Nets: 31 · Components: 34 · Dimensions: 75.0 x 50.0 mm (37.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.35 |      N/A |      3.35 |   0+  2+  0 |        0 |          0 |  1000 |       380 |     6694.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/RFM69HCW_ATSHA204A_Breakout_RFM69HCW_ATSHA204A_Breakout/unrouted.dsn)

Size: 14 kB · Layers: 2 · Nets: 0 · Components: 9 · Dimensions: 29.21 x 24.77 mm (7.24 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       5.45 |      N/A |      5.45 |   0+  1+  0 |        1 |          2 |   958 |       575 |    18173.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/RFM69-PowerMonitor_RFM69-PowerMonitor/unrouted.dsn)

Size: 33.7 kB · Layers: 2 · Nets: 12 · Components: 19 · Dimensions: 29.0 x 57.0 mm (16.53 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       6.23 |      N/A |      6.23 |   0+  1+  0 |        1 |          0 |   973 |       381 |    19054.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/rgb2ypbpr_rgb2ypbpr/unrouted.dsn)

Size: 96 kB · Layers: 2 · Nets: 16 · Components: 49 · Dimensions: 70.8 x 89.2 mm (63.15 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      68.46 |      N/A |     68.46 |   0+  2+  0 |        0 |          6 |   989 |       527 |   326839.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/rgb-led_rgb_led/unrouted.dsn)

Size: 8.9 kB · Layers: 2 · Nets: 0 · Components: 9 · Dimensions: 55.12 x 45.21 mm (24.92 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.60 |      N/A |      2.60 |   0+  1+  0 |        4 |         41 |     0 |       351 |     2817.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/rgb-led_rgb-led-v2/unrouted.dsn)

Size: 22.4 kB · Layers: 2 · Nets: 5 · Components: 22 · Dimensions: 90.0 x 50.0 mm (45.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       9.16 |      N/A |      9.16 |   0+  1+  0 |        2 |          0 |   969 |       488 |    28596.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/RGBMatrixPanelCPLD-PhotonBackpack_RGBMatrixPanel_CPLD/unrouted.dsn)

Size: 46 kB · Layers: 2 · Nets: 29 · Components: 52 · Dimensions: 71.12 x 59.69 mm (42.45 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     148.09 |      N/A |    148.09 |   0+  1+  0 |        2 |         80 |   909 |       695 |   457313.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/RGBMatrixPanelCPLD-PhotonBackpack_RGBMatrixPanel_CPLD_negative/unrouted.dsn)

Size: 30.5 kB · Layers: 2 · Nets: 21 · Components: 28 · Dimensions: 71.12 x 50.8 mm (36.13 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     101.79 |      N/A |    101.79 |   0+  1+  0 |        0 |         56 |   910 |       566 |   470181.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/RGBMatrixPanelCPLD-PhotonBackpack_RGBMatrixPanel_CPLD_positive/unrouted.dsn)

Size: 30.6 kB · Layers: 2 · Nets: 21 · Components: 28 · Dimensions: 71.12 x 50.8 mm (36.13 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     104.16 |      N/A |    104.16 |   0+  1+  0 |        0 |         56 |   910 |       554 |   478370.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/rgb-strip-controller__autosave-rgb-strip/unrouted.dsn)

Size: 31.7 kB · Layers: 2 · Nets: 6 · Components: 20 · Dimensions: 41.66 x 40.54 mm (16.89 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.90 |      N/A |      1.90 |   0+  2+  0 |        0 |          0 |  1000 |       156 |      719.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/rjw57_cpu-board/unrouted.dsn)

Size: 65.2 kB · Layers: 2 · Nets: 71 · Components: 69 · Dimensions: 99.06 x 99.06 mm (98.13 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     246.73 |      N/A |    246.73 |   0+ 10+  0 |        0 |          0 |  1000 |       616 |   841583.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/rk86kngmd_rk86kngmd/unrouted.dsn)

Size: 45.7 kB · Layers: 2 · Nets: 106 · Components: 39 · Dimensions: 226.25 x 67.76 mm (153.31 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes   |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :------ |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     313.70 |      N/A |    313.70 |   0+ 14+  0 |        8 |         60 |   909 |       734 |   507961.9 |    2 / 0 | TIMEOUT |


### Fixture: [unrouted.dsn](../fixtures/PCBench/RN2483shield_RN2483shield/unrouted.dsn)

Size: 16.8 kB · Layers: 2 · Nets: 2 · Components: 8 · Dimensions: 68.9 x 53.6 mm (36.93 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      25.60 |      N/A |     25.60 |   0+  1+  0 |        4 |          0 |   917 |       589 |    70256.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/RoBoC_CameraAdaptor/unrouted.dsn)

Size: 8.7 kB · Layers: 2 · Nets: 4 · Components: 6 · Dimensions: 0.0 x 0.0 mm (0.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.05 |      N/A |      2.05 |   0+  2+  0 |        0 |          0 |  1000 |       139 |     3961.1 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/RoBoC_RoboticsMKII/unrouted.dsn)

Size: 54.7 kB · Layers: 2 · Nets: 32 · Components: 94 · Dimensions: 49.53 x 99.06 mm (49.06 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     302.14 |      N/A |    302.14 |   0+  1+  0 |        0 |          2 |   998 |       776 |  1217179.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/rodi-pcb_rodi-pcb/unrouted.dsn)

Size: 42 kB · Layers: 2 · Nets: 43 · Components: 81 · Dimensions: 50.0 x 50.0 mm (25.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      10.84 |      N/A |     10.84 |   0+  4+  0 |        0 |          6 |   992 |       457 |    38513.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/roomba-ESP12E_roomba-esp/unrouted.dsn)

Size: 16.8 kB · Layers: 2 · Nets: 2 · Components: 11 · Dimensions: 33.0 x 40.0 mm (13.2 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.39 |      N/A |      4.39 |   0+  3+  0 |        0 |          2 |   990 |       292 |     8445.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/rotary-encoder-breakout_rotary-encoder-breakout/unrouted.dsn)

Size: 5.7 kB · Layers: 2 · Nets: 4 · Components: 6 · Dimensions: 17.17 x 20.57 mm (3.53 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.31 |      N/A |      2.31 |   0+  1+  0 |        0 |         18 |   700 |       147 |     4129.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/rotorcontrol_nanomount/unrouted.dsn)

Size: 8.9 kB · Layers: 2 · Nets: 21 · Components: 2 · Dimensions: 50.8 x 45.72 mm (23.23 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.56 |      N/A |      1.56 |   0+  3+  0 |        0 |          0 |  1000 |       103 |     1153.7 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/royer_royer/unrouted.dsn)

Size: 19.6 kB · Layers: 2 · Nets: 1 · Components: 26 · Dimensions: 40.64 x 59.69 mm (24.26 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.97 |      N/A |      4.97 |   0+  1+  0 |        1 |          2 |   971 |       460 |    15908.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/roz_roz-power-board/unrouted.dsn)

Size: 42.8 kB · Layers: 2 · Nets: 28 · Components: 87 · Dimensions: 80.0 x 42.0 mm (33.6 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     156.06 |      N/A |    156.06 |   0+  1+  0 |        3 |          3 |   981 |       642 |   658258.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/RPi-ATtiny85-Programmer_rpi_attiny85_programmer/unrouted.dsn)

Size: 27.8 kB · Layers: 2 · Nets: 13 · Components: 11 · Dimensions: 39.62 x 22.35 mm (8.86 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.99 |      N/A |      0.99 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/RPi-PWM-Fan-interface_RPi PWM Fan interface/unrouted.dsn)

Size: 9.1 kB · Layers: 2 · Nets: 9 · Components: 5 · Dimensions: 20.57 x 18.8 mm (3.87 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.53 |      N/A |      0.53 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/RSL_RSL/unrouted.dsn)

Size: 46.8 kB · Layers: 2 · Nets: 24 · Components: 49 · Dimensions: 160.5 x 80.75 mm (129.6 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      12.87 |      N/A |     12.87 |   0+ 18+  0 |        1 |          0 |   992 |       485 |    43858.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/rufs__autosave-simple_kicad_schema_and_pcb_v1/unrouted.dsn)

Size: 7.9 kB · Layers: 2 · Nets: 1 · Components: 3 · Dimensions: 35.56 x 11.43 mm (4.06 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.34 |      N/A |      0.34 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/rufs_aprs_tracker/unrouted.dsn)

Size: 29.8 kB · Layers: 2 · Nets: 13 · Components: 43 · Dimensions: 77.0 x 42.2 mm (32.49 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      31.13 |      N/A |     31.13 |   0+  1+  0 |        0 |          6 |   985 |       536 |   163983.1 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/rufs_dra818v_breakout_board/unrouted.dsn)

Size: 8.5 kB · Layers: 2 · Nets: 9 · Components: 4 · Dimensions: 48.3 x 30.7 mm (14.83 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.84 |      N/A |      1.84 |   0+  3+  0 |        0 |          0 |  1000 |        87 |      944.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/rufs_simple_kicad_schema_and_pcb_v1/unrouted.dsn)

Size: 7.9 kB · Layers: 2 · Nets: 1 · Components: 3 · Dimensions: 35.56 x 11.43 mm (4.06 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.32 |      N/A |      0.32 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/rufs_smart_psu/unrouted.dsn)

Size: 19.3 kB · Layers: 2 · Nets: 17 · Components: 26 · Dimensions: 21.0 x 75.0 mm (15.75 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      10.29 |      N/A |     10.29 |   0+  1+  0 |        1 |          0 |   981 |       517 |    39205.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/rufs_spv1040_power_controller/unrouted.dsn)

Size: 18.3 kB · Layers: 2 · Nets: 8 · Components: 22 · Dimensions: 37.8 x 22.1 mm (8.35 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       9.40 |      N/A |      9.40 |   0+  1+  0 |        0 |          7 |   964 |       680 |    43669.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/rvi_v2x_hardware_V2X/unrouted.dsn)

Size: 110.5 kB · Layers: 2 · Nets: 116 · Components: 198 · Dimensions: 85.0 x 56.0 mm (47.6 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     907.84 |      N/A |    907.84 |   0+  8+  0 |       17 |         21 |   956 |      1053 |  3049147.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/RX5808_diversityModule/unrouted.dsn)

Size: 27.7 kB · Layers: 2 · Nets: 23 · Components: 44 · Dimensions: 56.5 x 25.0 mm (14.12 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      84.16 |      N/A |     84.16 |   0+  1+  0 |        0 |         18 |   966 |       477 |   420740.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/RX5808_rx5808_4button/unrouted.dsn)

Size: 39 kB · Layers: 2 · Nets: 20 · Components: 54 · Dimensions: 50.0 x 29.5 mm (14.75 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      63.16 |      N/A |     63.16 |   0+  1+  0 |        1 |         11 |   975 |       545 |   257470.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/rxadc_14_rxadc_14/unrouted.dsn)

Size: 31.2 kB · Layers: 4 · Nets: 47 · Components: 51 · Dimensions: 32.77 x 39.12 mm (12.82 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      25.77 |      N/A |     25.77 |   0+  6+  0 |        0 |          6 |   991 |       506 |    68971.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/S1G-Mod_JST_Adapter/unrouted.dsn)

Size: 10.2 kB · Layers: 2 · Nets: 13 · Components: 7 · Dimensions: 30.75 x 28.0 mm (8.61 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.19 |      N/A |      1.19 |   0+  1+  0 |        0 |          2 |   900 |       183 |     1068.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/S1G-Mod_S1G_Mod_868/unrouted.dsn)

Size: 61.2 kB · Layers: 4 · Nets: 91 · Components: 91 · Dimensions: 60.0 x 33.3 mm (19.98 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     292.77 |      N/A |    292.77 |   0+  1+  0 |       29 |        110 |   798 |       661 |  1139988.0 |    3 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/S4A-Mini-board_s4a-mini-board/unrouted.dsn)

Size: 42.9 kB · Layers: 2 · Nets: 9 · Components: 27 · Dimensions: 53.34 x 49.53 mm (26.42 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      33.25 |      N/A |     33.25 |   0+  1+  0 |        0 |          0 |  1000 |       448 |   108053.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/SAMPad_SAMPad/unrouted.dsn)

Size: 52 kB · Layers: 2 · Nets: 41 · Components: 154 · Dimensions: 73.15 x 116.01 mm (84.86 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     902.45 |      N/A |    902.45 |   0+ 17+  0 |       47 |        167 |   777 |       903 |  2384151.2 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/sbc_sbc/unrouted.dsn)

Size: 55.7 kB · Layers: 4 · Nets: 51 · Components: 91 · Dimensions: 67.9 x 30.4 mm (20.64 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     302.99 |      N/A |    302.99 |   0+  4+  0 |       78 |         24 |   798 |       672 |  1040751.1 |    3 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/sb-serial-usb_ch340/unrouted.dsn)

Size: 15.5 kB · Layers: 2 · Nets: 4 · Components: 19 · Dimensions: 20.0 x 45.0 mm (9.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       7.77 |      N/A |      7.77 |   0+  6+  0 |        0 |          0 |  1000 |       380 |    17718.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/scimpy_amp/unrouted.dsn)

Size: 57.3 kB · Layers: 2 · Nets: 20 · Components: 51 · Dimensions: 104.14 x 51.82 mm (53.97 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      14.33 |      N/A |     14.33 |   0+ 20+  0 |        0 |          0 |  1000 |       420 |    44301.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/scimpy_crossover/unrouted.dsn)

Size: 20.8 kB · Layers: 2 · Nets: 10 · Components: 29 · Dimensions: 50.04 x 34.8 mm (17.41 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.82 |      N/A |      3.82 |   0+  4+  0 |        0 |          0 |  1000 |       352 |     5814.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/scimpy_powersupply/unrouted.dsn)

Size: 40.2 kB · Layers: 2 · Nets: 0 · Components: 13 · Dimensions: 51.82 x 46.99 mm (24.35 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.67 |      N/A |      0.67 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/scimpy_volumebuffer/unrouted.dsn)

Size: 23.1 kB · Layers: 2 · Nets: 14 · Components: 35 · Dimensions: 59.18 x 36.07 mm (21.35 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.75 |      N/A |      3.75 |   0+  4+  0 |        0 |          0 |  1000 |       492 |     6991.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/SensebenderMicro2_SensebenderMicro2/unrouted.dsn)

Size: 29 kB · Layers: 2 · Nets: 17 · Components: 86 · Dimensions: 21.5 x 32.0 mm (6.88 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     127.37 |      N/A |    127.37 |   0+  1+  0 |        6 |         44 |   907 |       669 |   485267.8 |    3 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/sensorboard_DiffIR.kicad_pcb_narrow/unrouted.dsn)

Size: 25.3 kB · Layers: 2 · Nets: 11 · Components: 17 · Dimensions: 33.02 x 24.77 mm (8.18 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.56 |      N/A |      1.56 |   0+  2+  0 |        0 |          0 |  1000 |       183 |     1497.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/sensorboard_DiffIR/unrouted.dsn)

Size: 23.1 kB · Layers: 2 · Nets: 13 · Components: 19 · Dimensions: 30.23 x 26.42 mm (7.99 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.57 |      N/A |      3.57 |   0+ 13+  0 |        0 |          0 |  1000 |       324 |     9103.7 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Sensory_Adaptation_Bot_sa_bot/unrouted.dsn)

Size: 63.7 kB · Layers: 2 · Nets: 27 · Components: 67 · Dimensions: 120.0 x 120.0 mm (144.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     132.84 |      N/A |    132.84 |   0+  1+  0 |        2 |          4 |   983 |       581 |   481653.2 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/server_lader_server_lader/unrouted.dsn)

Size: 39.1 kB · Layers: 2 · Nets: 27 · Components: 66 · Dimensions: 100.0 x 80.0 mm (80.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      48.80 |      N/A |     48.80 |   0+  1+  0 |        1 |          8 |   984 |       485 |   227175.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/shieldfoxuino_sigfoxuino - sauvegarde/unrouted.dsn)

Size: 32.8 kB · Layers: 2 · Nets: 0 · Components: 43 · Dimensions: 68.99 x 53.52 mm (36.92 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      31.39 |      N/A |     31.39 |   0+ 14+  0 |        0 |          0 |  1000 |       518 |   118034.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/shieldfoxuino_sigfoxuino/unrouted.dsn)

Size: 35.9 kB · Layers: 2 · Nets: 0 · Components: 45 · Dimensions: 68.99 x 53.52 mm (36.92 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      19.71 |      N/A |     19.71 |   0+  5+  0 |        0 |          0 |  1000 |       495 |    64809.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Shift-in-32-HC165_shift-in/unrouted.dsn)

Size: 15.5 kB · Layers: 2 · Nets: 3 · Components: 28 · Dimensions: 109.22 x 38.1 mm (41.61 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      13.28 |      N/A |     13.28 |   0+  6+  0 |        0 |          0 |  1000 |       432 |    58610.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Shift-out-32-HC595_Shift-out/unrouted.dsn)

Size: 13.6 kB · Layers: 2 · Nets: 3 · Components: 27 · Dimensions: 116.84 x 33.02 mm (38.58 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      29.72 |      N/A |     29.72 |   0+ 18+  0 |        1 |          0 |   994 |       478 |   131022.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/shutter_Shutter V4/unrouted.dsn)

Size: 41.3 kB · Layers: 2 · Nets: 17 · Components: 25 · Dimensions: 41.27 x 64.77 mm (26.73 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       9.27 |      N/A |      9.27 |   0+  1+  0 |        3 |          4 |   934 |       540 |    26469.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/silverduino-board_kicad/unrouted.dsn)

Size: 18.6 kB · Layers: 2 · Nets: 19 · Components: 16 · Dimensions: 68.58 x 53.34 mm (36.58 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.71 |      N/A |      4.71 |   0+  1+  0 |        0 |          8 |   947 |       532 |     9797.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Simple_84_Keyboard_84/unrouted.dsn)

Size: 34.6 kB · Layers: 2 · Nets: 100 · Components: 194 · Dimensions: 307.34 x 114.3 mm (351.29 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     128.17 |      N/A |    128.17 |   0+  1+  0 |        2 |          6 |   989 |       736 |   498004.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/SimpleCPLD_SimpleCPLD/unrouted.dsn)

Size: 16.8 kB · Layers: 4 · Nets: 4 · Components: 16 · Dimensions: 51.18 x 18.29 mm (9.36 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     104.37 |      N/A |    104.37 |   0+  1+  0 |        0 |          0 |  1000 |       557 |   407020.7 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/SIUSBSC_SIUSBSC/unrouted.dsn)

Size: 20.2 kB · Layers: 2 · Nets: 4 · Components: 31 · Dimensions: 25.4 x 25.4 mm (6.45 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      58.30 |      N/A |     58.30 |   0+  1+  0 |        0 |          5 |   985 |       555 |   308075.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/slushengine-modelx_slushengine_modelx/unrouted.dsn)

Size: 121 kB · Layers: 2 · Nets: 120 · Components: 223 · Dimensions: 261.0 x 100.0 mm (261.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     907.78 |      N/A |    907.78 |   0+  1+  0 |        0 |        424 |   867 |       891 |  3474276.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/small-scope-electronics_small-scope/unrouted.dsn)

Size: 37.7 kB · Layers: 2 · Nets: 0 · Components: 46 · Dimensions: 57.78 x 53.34 mm (30.82 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.77 |      N/A |      2.77 |   0+  3+  0 |        0 |          0 |  1000 |       367 |     4975.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/SmartLaserCO2-PCB__autosave-SmartLaserShield/unrouted.dsn)

Size: 23.6 kB · Layers: 2 · Nets: 0 · Components: 40 · Dimensions: 79.0 x 53.0 mm (41.87 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       8.86 |      N/A |      8.86 |   0+  3+  0 |        0 |          0 |  1000 |       365 |    22252.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/SmartLaserCO2-PCB_LaserPointer/unrouted.dsn)

Size: 9.9 kB · Layers: 2 · Nets: 0 · Components: 5 · Dimensions: 36.5 x 37.5 mm (13.69 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.32 |      N/A |      0.32 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/SmartLaserCO2-PCB_OptAdjust/unrouted.dsn)

Size: 7 kB · Layers: 2 · Nets: 0 · Components: 7 · Dimensions: 42.0 x 42.0 mm (17.64 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.36 |      N/A |      0.36 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/SmartLaserCO2-PCB_SmartLaserShield/unrouted.dsn)

Size: 23.5 kB · Layers: 2 · Nets: 0 · Components: 40 · Dimensions: 79.0 x 53.0 mm (41.87 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       9.07 |      N/A |      9.07 |   0+  3+  0 |        0 |          0 |  1000 |       549 |    25821.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/SmartLaserCO2-PCB_WaterCool/unrouted.dsn)

Size: 7.3 kB · Layers: 2 · Nets: 0 · Components: 9 · Dimensions: 36.58 x 37.34 mm (13.66 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.63 |      N/A |      0.63 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/SmartLaserMiniShield_SmartLaserMiniShield/unrouted.dsn)

Size: 22.8 kB · Layers: 2 · Nets: 0 · Components: 33 · Dimensions: 81.0 x 51.5 mm (41.72 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       5.23 |      N/A |      5.23 |   0+  3+  0 |        0 |          0 |  1000 |       300 |    12339.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/smart-meter_SmartMeter/unrouted.dsn)

Size: 38.2 kB · Layers: 2 · Nets: 36 · Components: 260 · Dimensions: 64.14 x 72.39 mm (46.43 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     161.70 |      N/A |    161.70 |   0+ 20+  0 |        1 |          7 |   993 |       710 |   519648.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Smart-Watch_BON-PCB/unrouted.dsn)

Size: 4.3 kB · Layers: 2 · Nets: 1 · Components: 13 · Dimensions: 35.56 x 35.56 mm (12.65 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.94 |      N/A |      0.94 |   0+  1+  0 |        0 |          6 |   891 |         0 |        0.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Smart-Watch_SmartWatch/unrouted.dsn)

Size: 37.8 kB · Layers: 4 · Nets: 1 · Components: 89 · Dimensions: 35.56 x 35.56 mm (12.65 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     301.26 |      N/A |    301.26 |   0+  9+  0 |       50 |          9 |   779 |       655 |  1034710.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/SMDBreakouts_smd_breakout/unrouted.dsn)

Size: 21.9 kB · Layers: 2 · Nets: 16 · Components: 8 · Dimensions: 21.59 x 21.46 mm (4.63 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.93 |      N/A |      2.93 |   0+  2+  0 |        0 |          0 |  1000 |        40 |     3464.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/SMDBreakouts_smd_breakout_quad/unrouted.dsn)

Size: 21.9 kB · Layers: 2 · Nets: 16 · Components: 8 · Dimensions: 21.59 x 21.46 mm (4.63 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.03 |      N/A |      3.03 |   0+  2+  0 |        0 |          0 |  1000 |       251 |     7338.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/sms-cart-32k_cart/unrouted.dsn)

Size: 21.5 kB · Layers: 2 · Nets: 0 · Components: 7 · Dimensions: 66.04 x 40.0 mm (26.42 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       6.90 |      N/A |      6.90 |   0+  4+  0 |        0 |          0 |  1000 |       168 |    12826.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/smt-zvs-driver_IH10/unrouted.dsn)

Size: 20.4 kB · Layers: 2 · Nets: 0 · Components: 28 · Dimensions: 23.75 x 3.0 mm (0.71 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      68.83 |      N/A |     68.83 |   0+  1+  0 |        0 |        462 |   295 |       502 |   256421.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/smt-zvs-driver_IH10-mc/unrouted.dsn)

Size: 15.4 kB · Layers: 2 · Nets: 0 · Components: 30 · Dimensions: 23.75 x 3.0 mm (0.71 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       8.37 |      N/A |      8.37 |   0+  1+  0 |        0 |         12 |   900 |       436 |    18433.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/smt-zvs-driver_IH10-sl/unrouted.dsn)

Size: 13.6 kB · Layers: 2 · Nets: 0 · Components: 27 · Dimensions: 23.75 x 3.0 mm (0.71 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       8.95 |      N/A |      8.95 |   0+  1+  0 |        0 |         30 |   829 |       584 |    28753.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/SNAP-Badge_SNAP_badge/unrouted.dsn)

Size: 60.3 kB · Layers: 2 · Nets: 59 · Components: 157 · Dimensions: 63.5 x 127.0 mm (80.64 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     902.90 |      N/A |    902.90 |   0+ 19+  0 |       44 |          0 |   872 |       750 |  2803477.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/snappi-zero_snappi-zero/unrouted.dsn)

Size: 21.7 kB · Layers: 2 · Nets: 3 · Components: 13 · Dimensions: 65.0 x 33.0 mm (21.45 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      61.11 |      N/A |     61.11 |   0+  2+  0 |        0 |          8 |   952 |       481 |   103371.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/soil-moisture-sensor-analog_analog-moist-sensor/unrouted.dsn)

Size: 13.4 kB · Layers: 2 · Nets: 10 · Components: 29 · Dimensions: 15.24 x 114.2 mm (17.4 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.45 |      N/A |      4.45 |   0+  3+  0 |        0 |          0 |  1000 |       524 |     9737.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Solar_Project_Solar/unrouted.dsn)

Size: 89.8 kB · Layers: 4 · Nets: 74 · Components: 132 · Dimensions: 143.76 x 80.26 mm (115.38 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     302.57 |      N/A |    302.57 |   0+ 14+  0 |        4 |         18 |   977 |       947 |   793470.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Solare-BQ24210_Solare-BQ24210/unrouted.dsn)

Size: 18.4 kB · Layers: 2 · Nets: 8 · Components: 15 · Dimensions: 27.1 x 15.9 mm (4.31 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.92 |      N/A |      1.92 |   0+  2+  0 |        0 |          0 |  1000 |        47 |     1050.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/solar-lanterns_proto1/unrouted.dsn)

Size: 10.2 kB · Layers: 2 · Nets: 6 · Components: 12 · Dimensions: 40.0 x 20.0 mm (8.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.43 |      N/A |      2.43 |   0+  1+  0 |        0 |         20 |   810 |       414 |     6060.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/solar-lora_solar-lora/unrouted.dsn)

Size: 36.6 kB · Layers: 2 · Nets: 18 · Components: 64 · Dimensions: 60.0 x 60.0 mm (36.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      81.35 |      N/A |     81.35 |   0+  1+  0 |       16 |         11 |   869 |       560 |   326551.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/sonic3_feram_adapter_sonic3_feram_adapter/unrouted.dsn)

Size: 12 kB · Layers: 2 · Nets: 26 · Components: 6 · Dimensions: 17.78 x 30.48 mm (5.42 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      39.65 |      N/A |     39.65 |   0+  1+  0 |        0 |         48 |   718 |       480 |    89591.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/SparkSwitch_SparkProtectionSwitch/unrouted.dsn)

Size: 6.9 kB · Layers: 2 · Nets: 7 · Components: 15 · Dimensions: 40.0 x 32.0 mm (12.8 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.23 |      N/A |      1.23 |   0+  2+  0 |        0 |          0 |  1000 |        23 |     1667.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/SPEX_HAB_Mainboard_Hardware__autosave-Power/unrouted.dsn)

Size: 62.5 kB · Layers: 2 · Nets: 34 · Components: 120 · Dimensions: 77.2 x 72.43 mm (55.92 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     245.71 |      N/A |    245.71 |   0+  1+  0 |        2 |         28 |   972 |       797 |   934542.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/SPEX_HAB_Mainboard_Hardware__autosave-Spex-Mainboard-Hardware/unrouted.dsn)

Size: 76.1 kB · Layers: 4 · Nets: 93 · Components: 134 · Dimensions: 100.0 x 80.0 mm (80.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     166.74 |      N/A |    166.74 |   0+  7+  0 |        0 |          2 |   999 |       762 |   603874.1 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/SPEX_HAB_Mainboard_Hardware_Power/unrouted.dsn)

Size: 67.5 kB · Layers: 2 · Nets: 34 · Components: 123 · Dimensions: 77.2 x 72.43 mm (55.92 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     260.13 |      N/A |    260.13 |   0+  1+  0 |        2 |         28 |   972 |       772 |   934085.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/SPEX_HAB_Mainboard_Hardware_Spex-Mainboard-Hardware/unrouted.dsn)

Size: 78.5 kB · Layers: 4 · Nets: 93 · Components: 134 · Dimensions: 100.0 x 80.0 mm (80.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     195.99 |      N/A |    195.99 |   0+ 13+  0 |        0 |          0 |  1000 |       731 |   734739.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/SPEX_HAB_Mainboard_Hardware_Spex-Mainboard-Hardware_rev1/unrouted.dsn)

Size: 78.6 kB · Layers: 4 · Nets: 93 · Components: 134 · Dimensions: 100.0 x 80.0 mm (80.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     195.32 |      N/A |    195.32 |   0+ 13+  0 |        0 |          0 |  1000 |       779 |   735492.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/SpindleController_spindle_controller/unrouted.dsn)

Size: 129.5 kB · Layers: 2 · Nets: 28 · Components: 69 · Dimensions: 66.26 x 114.35 mm (75.77 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      19.46 |      N/A |     19.46 |   0+  1+  0 |        1 |          4 |   987 |       439 |    71671.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/SPI-shift-register-CS_SPI shift register CS/unrouted.dsn)

Size: 16.7 kB · Layers: 2 · Nets: 0 · Components: 22 · Dimensions: 114.3 x 29.21 mm (33.39 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      15.99 |      N/A |     15.99 |   0+  1+  0 |        1 |         22 |   944 |       411 |    40739.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/spisolator_spisolator/unrouted.dsn)

Size: 10 kB · Layers: 2 · Nets: 0 · Components: 8 · Dimensions: 16.51 x 21.59 mm (3.56 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.48 |      N/A |      3.48 |   0+  4+  0 |        0 |          0 |  1000 |       387 |     6111.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/split-pcb-throughole_splanck throughhole/unrouted.dsn)

Size: 14.7 kB · Layers: 2 · Nets: 35 · Components: 51 · Dimensions: 106.68 x 72.39 mm (77.23 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.67 |      N/A |      3.67 |   0+  3+  0 |        0 |          0 |  1000 |       196 |     6251.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/srambo_1_srambo_1/unrouted.dsn)

Size: 31.9 kB · Layers: 2 · Nets: 5 · Components: 30 · Dimensions: 113.0 x 42.0 mm (47.46 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      85.99 |      N/A |     85.99 |   0+ 20+  0 |        1 |          0 |   992 |       597 |   280981.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ssr-wifi_adapter/unrouted.dsn)

Size: 24.6 kB · Layers: 2 · Nets: 0 · Components: 25 · Dimensions: 83.0 x 86.0 mm (71.38 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.97 |      N/A |      2.97 |   0+  2+  0 |        0 |          0 |  1000 |       156 |     2768.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Starburst-One_alpha/unrouted.dsn)

Size: 6.5 kB · Layers: 2 · Nets: 18 · Components: 3 · Dimensions: 15.24 x 22.86 mm (3.48 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.29 |      N/A |      4.29 |   0+  1+  0 |        0 |         36 |   640 |       503 |    10300.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/starfish_starfish/unrouted.dsn)

Size: 34.7 kB · Layers: 2 · Nets: 12 · Components: 41 · Dimensions: 38.0 x 24.0 mm (9.12 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     160.86 |      N/A |    160.86 |   0+  1+  0 |       14 |          0 |   875 |       556 |   506292.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Starling__autosave-Starling WiPSU ver_0.1/unrouted.dsn)

Size: 29.4 kB · Layers: 2 · Nets: 9 · Components: 26 · Dimensions: 60.0 x 19.55 mm (11.73 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.13 |      N/A |      0.13 |   0+  1+  0 |        0 |          0 |     0 |         0 |        0.0 |    3 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Starling_matrix/unrouted.dsn)

Size: 20.7 kB · Layers: 2 · Nets: 27 · Components: 29 · Dimensions: 26.37 x 65.55 mm (17.29 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     107.51 |      N/A |    107.51 |   0+  1+  0 |        0 |         28 |   927 |       517 |   460953.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Starling_Starling_V1/unrouted.dsn)

Size: 33.8 kB · Layers: 2 · Nets: 27 · Components: 25 · Dimensions: 60.1 x 23.8 mm (14.3 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      51.92 |      N/A |     51.92 |   0+  1+  0 |        1 |          0 |   987 |       483 |   158835.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/starsynctrackers_reset_switch/unrouted.dsn)

Size: 6.7 kB · Layers: 2 · Nets: 5 · Components: 9 · Dimensions: 15.24 x 24.77 mm (3.77 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.90 |      N/A |      0.90 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/StickIt-MB_StickIt-Hat/unrouted.dsn)

Size: 42.9 kB · Layers: 2 · Nets: 11 · Components: 42 · Dimensions: 65.0 x 56.0 mm (36.4 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      15.58 |      N/A |     15.58 |   0+  7+  0 |        0 |          6 |   991 |       511 |    47438.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/stlinkv2_breakout_stlink_breakout/unrouted.dsn)

Size: 5.2 kB · Layers: 2 · Nets: 0 · Components: 2 · Dimensions: 34.8 x 15.24 mm (5.3 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.52 |      N/A |      1.52 |   0+  1+  0 |        1 |          0 |   941 |       311 |     1690.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/stm32_ccd_camera_ccd/unrouted.dsn)

Size: 21.8 kB · Layers: 2 · Nets: 36 · Components: 30 · Dimensions: 18.5 x 0.0 mm (0.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      71.13 |      N/A |     71.13 |   0+  1+  0 |        1 |          0 |   990 |       626 |   270145.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/stm32_mech_keyboard_TCKB/unrouted.dsn)

Size: 82.6 kB · Layers: 2 · Nets: 66 · Components: 242 · Dimensions: 285.0 x 94.6 mm (269.61 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     903.56 |      N/A |    903.56 |   0+  6+  0 |       42 |         46 |   883 |      1399 |  2140371.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/stm32_nucleo-64_proto_shield_nucleo_pb/unrouted.dsn)

Size: 38.4 kB · Layers: 2 · Nets: 32 · Components: 273 · Dimensions: 91.44 x 91.44 mm (83.61 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      53.52 |      N/A |     53.52 |   0+ 18+  0 |        2 |          0 |   995 |       571 |   215433.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/STM32F303_LQFP48_STM32_LQFP48/unrouted.dsn)

Size: 44.1 kB · Layers: 2 · Nets: 2 · Components: 19 · Dimensions: 50.0 x 26.0 mm (13.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      60.33 |      N/A |     60.33 |   0+  1+  0 |        0 |         80 |   802 |       489 |   187176.1 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/STM32F373_LQFP48_STM32_LQFP48/unrouted.dsn)

Size: 43.2 kB · Layers: 2 · Nets: 2 · Components: 20 · Dimensions: 50.0 x 26.4 mm (13.2 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      60.46 |      N/A |     60.46 |   0+  1+  0 |        0 |         90 |   783 |       517 |   237042.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/stm32-lpc__autosave-stm32-lpc/unrouted.dsn)

Size: 25 kB · Layers: 2 · Nets: 0 · Components: 19 · Dimensions: 26.67 x 28.83 mm (7.69 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      60.42 |      N/A |     60.42 |   0+  1+  0 |        2 |         14 |   939 |       621 |   213426.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/stm32-lpc_stm32-lpc/unrouted.dsn)

Size: 25 kB · Layers: 2 · Nets: 0 · Components: 19 · Dimensions: 26.67 x 28.83 mm (7.69 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      50.77 |      N/A |     50.77 |   0+  1+  0 |        1 |         14 |   952 |       685 |   178132.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/stm32-network-rs232_stm32-network-rs232/unrouted.dsn)

Size: 38.5 kB · Layers: 2 · Nets: 63 · Components: 57 · Dimensions: 85.2 x 47.2 mm (40.21 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     153.50 |      N/A |    153.50 |   0+  1+  0 |        8 |         17 |   923 |       651 |   533318.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/stubby_hex/unrouted.dsn)

Size: 44.4 kB · Layers: 2 · Nets: 0 · Components: 60 · Dimensions: 49.53 x 49.53 mm (24.53 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      70.31 |      N/A |     70.31 |   0+  1+  0 |        2 |          9 |   977 |       572 |   251541.7 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/stubby_stubby/unrouted.dsn)

Size: 48.6 kB · Layers: 2 · Nets: 0 · Components: 74 · Dimensions: 49.53 x 49.53 mm (24.53 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     158.42 |      N/A |    158.42 |   0+  1+  0 |        3 |         26 |   956 |       591 |   368566.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/SunLeaf_SunLeaf_V2/unrouted.dsn)

Size: 76.6 kB · Layers: 4 · Nets: 90 · Components: 158 · Dimensions: 77.5 x 40.0 mm (31.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     600.34 |      N/A |    600.34 |   0+  1+  0 |       41 |         16 |   873 |       778 |  2164735.2 |    3 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/supply-ldo-adj-single_supply-ldo-adj-single/unrouted.dsn)

Size: 14.3 kB · Layers: 2 · Nets: 12 · Components: 15 · Dimensions: 31.75 x 39.37 mm (12.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      13.31 |      N/A |     13.31 |   0+  1+  0 |        0 |          6 |   979 |       552 |    75620.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/sv650sds_sds_tool/unrouted.dsn)

Size: 13.8 kB · Layers: 2 · Nets: 25 · Components: 17 · Dimensions: 29.34 x 39.88 mm (11.7 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.81 |      N/A |      1.81 |   0+  2+  0 |        0 |          0 |  1000 |       167 |     1498.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/sweet-sixteen_sweet-sixteen/unrouted.dsn)

Size: 62.7 kB · Layers: 2 · Nets: 164 · Components: 87 · Dimensions: 116.0 x 105.0 mm (121.8 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     100.98 |      N/A |    100.98 |   0+  7+  0 |        0 |         14 |   991 |       602 |   429500.1 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/SynthDrumTrigger_Synth Drum Trigger/unrouted.dsn)

Size: 9.6 kB · Layers: 2 · Nets: 15 · Components: 31 · Dimensions: 52.0 x 48.0 mm (24.96 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.05 |      N/A |      4.05 |   0+  1+  0 |        1 |          0 |   980 |       431 |    13273.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/T962A-UpgradeBoard_T962A-UpgradeBoard/unrouted.dsn)

Size: 30.1 kB · Layers: 2 · Nets: 12 · Components: 37 · Dimensions: 35.4 x 57.11 mm (20.22 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     139.42 |      N/A |    139.42 |   0+  1+  0 |       11 |          6 |   885 |       586 |   502100.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/TB6600StepperDriver_DEW_TB6600-V1/unrouted.dsn)

Size: 109.7 kB · Layers: 2 · Nets: 0 · Components: 75 · Dimensions: 86.36 x 66.67 mm (57.58 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     113.42 |      N/A |    113.42 |   0+  1+  0 |       24 |          0 |   854 |       635 |   392662.2 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/tbd_tbd/unrouted.dsn)

Size: 15.4 kB · Layers: 2 · Nets: 8 · Components: 12 · Dimensions: 20.27 x 11.99 mm (2.43 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      23.25 |      N/A |     23.25 |   0+  1+  0 |        6 |          6 |   820 |       454 |    80453.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/TCKB_Kicad_TCKB/unrouted.dsn)

Size: 100.8 kB · Layers: 2 · Nets: 92 · Components: 383 · Dimensions: 285.0 x 94.6 mm (269.61 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |    1807.36 |      N/A |   1807.36 |   0+ 12+  0 |       29 |         47 |   936 |      1347 |  4373415.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/tdstat_TDstatv2/unrouted.dsn)

Size: 36.2 kB · Layers: 2 · Nets: 29 · Components: 56 · Dimensions: 92.71 x 54.61 mm (50.63 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      15.32 |      N/A |     15.32 |   0+  6+  0 |        0 |          0 |  1000 |       478 |    55624.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/technoshield-ui-hw_technoshield/unrouted.dsn)

Size: 49.6 kB · Layers: 2 · Nets: 63 · Components: 49 · Dimensions: 85.85 x 99.95 mm (85.81 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      24.48 |      N/A |     24.48 |   0+ 18+  0 |        1 |          0 |   992 |       593 |    92610.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Teensy-3.5-Breakout-Boaard_PropShield_Uno/unrouted.dsn)

Size: 25.9 kB · Layers: 2 · Nets: 47 · Components: 19 · Dimensions: 68.58 x 53.34 mm (36.58 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       9.23 |      N/A |      9.23 |   0+  1+  0 |        1 |          6 |   933 |       453 |    28046.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Teensy-3.5-Breakout-Boaard_Teensy 35 Breakout v1/unrouted.dsn)

Size: 38.2 kB · Layers: 2 · Nets: 87 · Components: 41 · Dimensions: 99.06 x 106.68 mm (105.68 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      13.02 |      N/A |     13.02 |   0+  5+  0 |        0 |          0 |  1000 |       461 |    48557.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Teensy-3.5-Breakout-Boaard_Teensy 35 Breakout v2/unrouted.dsn)

Size: 39.5 kB · Layers: 2 · Nets: 88 · Components: 42 · Dimensions: 92.96 x 99.57 mm (92.56 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      12.06 |      N/A |     12.06 |   0+  5+  0 |        0 |          0 |  1000 |       456 |    49715.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Teensy-3.5-Breakout-Boaard_Teensy_Uno/unrouted.dsn)

Size: 26.9 kB · Layers: 2 · Nets: 60 · Components: 28 · Dimensions: 138.43 x 67.31 mm (93.18 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      31.02 |      N/A |     31.02 |   0+ 18+  0 |        1 |          0 |   994 |       588 |    87659.7 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Teensy-3.5-Breakout-Boaard_TeensyMegaIoShield/unrouted.dsn)

Size: 30.1 kB · Layers: 2 · Nets: 29 · Components: 47 · Dimensions: 101.6 x 59.3 mm (60.25 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     165.87 |      N/A |    165.87 |   0+  1+  0 |        5 |          0 |   976 |       682 |   541994.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Teensy-3.5-Breakout-Boaard_Test/unrouted.dsn)

Size: 12 kB · Layers: 2 · Nets: 54 · Components: 9 · Dimensions: 71.12 x 80.01 mm (56.9 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.15 |      N/A |      3.15 |   0+  2+  0 |        0 |          0 |  1000 |       223 |    10264.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Teensy-Hats_Teensy-7-Segment-Hat/unrouted.dsn)

Size: 11 kB · Layers: 2 · Nets: 4 · Components: 7 · Dimensions: 37.08 x 18.03 mm (6.69 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.83 |      N/A |      3.83 |   0+  1+  0 |        0 |         30 |   750 |       475 |     9084.7 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Teensy-Hats_Teensy-LCD-LiDAR-Hat/unrouted.dsn)

Size: 22 kB · Layers: 2 · Nets: 3 · Components: 16 · Dimensions: 80.01 x 36.19 mm (28.96 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.98 |      N/A |      2.98 |   0+  3+  0 |        0 |          0 |  1000 |       207 |     3982.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Teensy-Hats_Temperature-Monitor-Hat/unrouted.dsn)

Size: 19.3 kB · Layers: 2 · Nets: 14 · Components: 8 · Dimensions: 80.26 x 36.07 mm (28.95 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       6.96 |      N/A |      6.96 |   0+  1+  0 |        2 |          0 |   931 |       308 |    16102.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/TeensyProtoboard_TeensyProtoboard/unrouted.dsn)

Size: 18.5 kB · Layers: 2 · Nets: 3 · Components: 43 · Dimensions: 48.26 x 62.23 mm (30.03 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.60 |      N/A |      3.60 |   0+  2+  0 |        0 |          0 |  1000 |       388 |     9461.7 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/teensy-touch_teensy-touch/unrouted.dsn)

Size: 13.5 kB · Layers: 2 · Nets: 20 · Components: 15 · Dimensions: 66.04 x 59.94 mm (39.58 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.57 |      N/A |      3.57 |   0+  5+  0 |        0 |          0 |  1000 |       333 |     3935.7 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/teensy-weather-badge_teensyi2c/unrouted.dsn)

Size: 15.9 kB · Layers: 2 · Nets: 28 · Components: 12 · Dimensions: 50.95 x 51.66 mm (26.32 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.75 |      N/A |      4.75 |   0+  1+  0 |        1 |          0 |   957 |       496 |     8260.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/teensy-wifi-weather-logger_teensyi2c/unrouted.dsn)

Size: 15.9 kB · Layers: 2 · Nets: 28 · Components: 12 · Dimensions: 50.95 x 51.66 mm (26.32 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.77 |      N/A |      4.77 |   0+  1+  0 |        1 |          0 |   957 |       348 |     8146.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/temp_logger_esp8266_temp_logger/unrouted.dsn)

Size: 11.8 kB · Layers: 2 · Nets: 0 · Components: 17 · Dimensions: 34.04 x 33.78 mm (11.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       1.16 |      N/A |      1.16 |   0+  2+  0 |        0 |          0 |  1000 |       131 |     1038.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/temperature-alarm_controller/unrouted.dsn)

Size: 39 kB · Layers: 2 · Nets: 4 · Components: 19 · Dimensions: 111.12 x 62.23 mm (69.15 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.07 |      N/A |      3.07 |   0+  4+  0 |        0 |          0 |  1000 |       556 |     7150.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/tepmachcha_tepmachcha/unrouted.dsn)

Size: 34.6 kB · Layers: 2 · Nets: 38 · Components: 6 · Dimensions: 50.29 x 51.82 mm (26.06 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.23 |      N/A |      2.23 |   0+  1+  0 |        1 |          2 |   883 |       308 |     3513.2 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/tessel-ice40__autosave-project/unrouted.dsn)

Size: 40.6 kB · Layers: 2 · Nets: 7 · Components: 20 · Dimensions: 43.18 x 27.94 mm (12.06 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      60.27 |      N/A |     60.27 |   0+  1+  0 |        2 |          0 |   975 |       564 |   208125.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/thegrid_pole/unrouted.dsn)

Size: 34.8 kB · Layers: 2 · Nets: 3 · Components: 25 · Dimensions: 15.0 x 39.0 mm (5.85 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      60.24 |      N/A |     60.24 |   0+  2+  0 |        0 |         23 |   926 |       545 |   325793.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/thegrid_pole_panellised/unrouted.dsn)

Size: 36.8 kB · Layers: 2 · Nets: 3 · Components: 50 · Dimensions: 15.0 x 39.0 mm (5.85 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     124.38 |      N/A |    124.38 |   0+  2+  0 |        0 |        183 |   742 |       514 |   680587.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/thegrid_shield/unrouted.dsn)

Size: 42.8 kB · Layers: 2 · Nets: 38 · Components: 29 · Dimensions: 50.0 x 50.0 mm (25.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       6.22 |      N/A |      6.22 |   0+  3+  0 |        0 |          2 |   994 |       453 |    17976.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/thegrid_thegrid/unrouted.dsn)

Size: 23 kB · Layers: 2 · Nets: 32 · Components: 54 · Dimensions: 49.78 x 99.82 mm (49.69 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.31 |      N/A |      3.31 |   0+  2+  0 |        0 |          0 |  1000 |       392 |     6790.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Theia_Theia/unrouted.dsn)

Size: 14.9 kB · Layers: 2 · Nets: 0 · Components: 16 · Dimensions: 54.0 x 40.0 mm (21.6 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.72 |      N/A |      4.72 |   0+  1+  0 |        1 |          0 |   972 |       348 |    11674.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/thingBot-LoRa_thingBot-LoRa_v1P0/unrouted.dsn)

Size: 17.7 kB · Layers: 2 · Nets: 8 · Components: 45 · Dimensions: 24.38 x 32.93 mm (8.03 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      53.36 |      N/A |     53.36 |   0+  1+  0 |        0 |         46 |   858 |       456 |   120220.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/thingBot-LoRa_thingBot-LoRa_v1P1/unrouted.dsn)

Size: 18.3 kB · Layers: 2 · Nets: 8 · Components: 45 · Dimensions: 24.38 x 32.93 mm (8.03 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      55.53 |      N/A |     55.53 |   0+  1+  0 |        0 |         46 |   847 |       545 |   117580.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ThinkerShield_ThinkerShield/unrouted.dsn)

Size: 28.4 kB · Layers: 2 · Nets: 8 · Components: 48 · Dimensions: 69.0 x 69.0 mm (47.61 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      33.82 |      N/A |     33.82 |   0+  1+  0 |        0 |         24 |   945 |       569 |   165696.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Thremal-Printer_thermal printer/unrouted.dsn)

Size: 37 kB · Layers: 2 · Nets: 23 · Components: 17 · Dimensions: 81.91 x 42.93 mm (35.16 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       6.72 |      N/A |      6.72 |   0+  3+  0 |        0 |          0 |  1000 |       396 |    17369.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ti_cc3200_quadcopter_cc3200_quad/unrouted.dsn)

Size: 49.7 kB · Layers: 4 · Nets: 49 · Components: 104 · Dimensions: 40.05 x 45.05 mm (18.04 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     247.55 |      N/A |    247.55 |   0+  1+  0 |        1 |        624 |   544 |       743 |  1062106.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/timecircuits-hardware_BTTF-TimeCircuits/unrouted.dsn)

Size: 43.2 kB · Layers: 2 · Nets: 58 · Components: 102 · Dimensions: 320.0 x 50.0 mm (160.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     301.91 |      N/A |    301.91 |   0+ 12+  0 |        0 |          0 |  1000 |       636 |  1130758.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/tiny-8088_Computer/unrouted.dsn)

Size: 69.7 kB · Layers: 2 · Nets: 137 · Components: 54 · Dimensions: 147.32 x 128.27 mm (188.97 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      97.16 |      N/A |     97.16 |   0+ 18+  0 |        2 |          0 |   993 |       606 |   331804.7 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/tinyDriverP_tinybot/unrouted.dsn)

Size: 26.2 kB · Layers: 2 · Nets: 19 · Components: 36 · Dimensions: 41.28 x 44.45 mm (18.35 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      46.54 |      N/A |     46.54 |   0+  1+  0 |        3 |         21 |   901 |       555 |   147284.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/tinyFISH_tinyBRUSH/unrouted.dsn)

Size: 11.2 kB · Layers: 2 · Nets: 9 · Components: 24 · Dimensions: 13.03 x 9.35 mm (1.22 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      62.75 |      N/A |     62.75 |   0+ 15+  0 |        1 |         38 |   713 |       428 |    67713.2 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/tinyFISH_tinyFISH/unrouted.dsn)

Size: 50.9 kB · Layers: 2 · Nets: 22 · Components: 88 · Dimensions: 20.0 x 20.0 mm (4.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     304.40 |      N/A |    304.40 |   0+  8+  0 |       33 |         75 |   760 |       562 |  1162480.6 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/tinyisp-micro_tinyispmicro/unrouted.dsn)

Size: 12.1 kB · Layers: 2 · Nets: 0 · Components: 16 · Dimensions: 36.83 x 16.51 mm (6.08 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      12.38 |      N/A |     12.38 |   0+  1+  0 |        1 |          3 |   970 |       469 |    54063.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/tinymuseum_museum/unrouted.dsn)

Size: 18.4 kB · Layers: 2 · Nets: 2 · Components: 18 · Dimensions: 60.0 x 45.5 mm (27.3 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      15.70 |      N/A |     15.70 |   0+  1+  0 |        2 |          0 |   959 |       461 |    45919.7 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/TinySimon_TinySimon/unrouted.dsn)

Size: 21 kB · Layers: 2 · Nets: 8 · Components: 20 · Dimensions: 14.33 x 0.0 mm (0.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      36.50 |      N/A |     36.50 |   0+  1+  0 |        3 |          2 |   911 |       469 |    90930.2 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/TinyTracker_ub-minimal/unrouted.dsn)

Size: 29.1 kB · Layers: 4 · Nets: 34 · Components: 65 · Dimensions: 38.0 x 16.0 mm (6.08 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      69.10 |      N/A |     69.10 |   0+  1+  0 |        4 |         22 |   948 |       618 |   302786.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/TLPHnodeV2_TLPHnodeV2/unrouted.dsn)

Size: 45 kB · Layers: 4 · Nets: 11 · Components: 35 · Dimensions: 28.0 x 20.5 mm (5.74 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     180.39 |      N/A |    180.39 |   0+  2+  0 |        0 |          7 |   985 |       557 |   914456.6 |    3 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/TMC261-stepstick_TMC261-stepstick-v1.1/unrouted.dsn)

Size: 38.2 kB · Layers: 2 · Nets: 36 · Components: 41 · Dimensions: 100.96 x 54.61 mm (55.13 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     272.57 |      N/A |    272.57 |   0+  1+  0 |       14 |          0 |   942 |       714 |   922046.4 |   33 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/TMC261-stepstick_TMC261-stepstick-v1/unrouted.dsn)

Size: 10.3 kB · Layers: 2 · Nets: 7 · Components: 22 · Dimensions: 18.16 x 18.95 mm (3.44 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      60.24 |      N/A |     60.24 |   0+ 14+  0 |       30 |         30 |   419 |       609 |   168721.7 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/tmk_HHKB_controller/unrouted.dsn)

Size: 43.2 kB · Layers: 2 · Nets: 35 · Components: 95 · Dimensions: 141.5 x 36.0 mm (50.94 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      64.46 |      N/A |     64.46 |   0+  8+  0 |        0 |          7 |   993 |       552 |   241157.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/TOBS_HybridChargeController/unrouted.dsn)

Size: 64.8 kB · Layers: 2 · Nets: 39 · Components: 103 · Dimensions: 87.0 x 87.0 mm (75.69 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     300.90 |      N/A |    300.90 |   0+  1+  0 |        0 |         42 |   967 |       567 |  1636081.2 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ToslinkCNC__autosave-ToslinkCNC_OneAxis/unrouted.dsn)

Size: 31 kB · Layers: 2 · Nets: 7 · Components: 43 · Dimensions: 50.0 x 52.0 mm (26.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      55.76 |      N/A |     55.76 |   0+  1+  0 |        7 |          0 |   944 |       509 |   217180.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ToslinkCNC_Toslink PlanetCNC ECO shield/unrouted.dsn)

Size: 12.9 kB · Layers: 2 · Nets: 0 · Components: 5 · Dimensions: 59.05 x 60.0 mm (35.43 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       9.54 |      N/A |      9.54 |   0+  1+  0 |        1 |          0 |   962 |       357 |    18961.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ToslinkCNC_toslink_arduino_shield/unrouted.dsn)

Size: 17.4 kB · Layers: 2 · Nets: 0 · Components: 20 · Dimensions: 82.1 x 53.7 mm (44.09 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      26.87 |      N/A |     26.87 |   0+  1+  0 |        3 |          0 |   962 |       506 |    82260.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/TPS63001-Breakout_TPS63001 Breakout 25x25/unrouted.dsn)

Size: 12 kB · Layers: 2 · Nets: 7 · Components: 14 · Dimensions: 25.0 x 25.0 mm (6.25 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       8.03 |      N/A |      8.03 |   0+  1+  0 |        1 |          8 |   910 |       356 |    21992.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/TPS63001-Breakout_TPS63001 Breakout/unrouted.dsn)

Size: 13.2 kB · Layers: 2 · Nets: 7 · Components: 16 · Dimensions: 20.5 x 20.0 mm (4.1 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      22.08 |      N/A |     22.08 |   0+  1+  0 |        0 |         10 |   935 |       455 |    38900.7 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/TripleDelay2399_TripleDelay2399/unrouted.dsn)

Size: 44.1 kB · Layers: 2 · Nets: 0 · Components: 157 · Dimensions: 85.0 x 80.0 mm (68.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      12.72 |      N/A |     12.72 |   0+  4+  0 |        0 |          0 |  1000 |       396 |    44270.7 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Ttl_wlan_radar/unrouted.dsn)

Size: 50.6 kB · Layers: 4 · Nets: 71 · Components: 128 · Dimensions: 54.99 x 24.96 mm (13.73 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     301.63 |      N/A |    301.63 |   0+  1+  0 |        3 |         15 |   979 |       793 |  1086143.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/TTNEnschedeMote_ArduinoNanoRN2483/unrouted.dsn)

Size: 13.4 kB · Layers: 2 · Nets: 2 · Components: 6 · Dimensions: 46.99 x 33.02 mm (15.52 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      13.81 |      N/A |     13.81 |   0+  1+  0 |        0 |          0 |  1000 |       481 |    58034.7 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/TurbiGnUSB_Turbidignusb/unrouted.dsn)

Size: 22 kB · Layers: 2 · Nets: 10 · Components: 29 · Dimensions: 30.0 x 80.0 mm (24.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.39 |      N/A |      4.39 |   0+  3+  0 |        0 |          0 |  1000 |       451 |     8331.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/two-daisy-chained-shift-registers_two-shift-registers/unrouted.dsn)

Size: 31.7 kB · Layers: 2 · Nets: 53 · Components: 42 · Dimensions: 68.58 x 53.34 mm (36.58 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      11.28 |      N/A |     11.28 |   0+  1+  0 |        1 |          8 |   959 |       492 |    37661.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/TX5823_TX5823/unrouted.dsn)

Size: 72.6 kB · Layers: 4 · Nets: 90 · Components: 98 · Dimensions: 50.0 x 50.0 mm (25.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     302.22 |      N/A |    302.22 |   0+  1+  0 |        3 |        368 |   728 |       762 |  1137212.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/type5_type5/unrouted.dsn)

Size: 13.6 kB · Layers: 2 · Nets: 55 · Components: 4 · Dimensions: 85.0 x 70.0 mm (59.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       8.29 |      N/A |      8.29 |   0+ 11+  0 |        0 |          0 |  1000 |       404 |    18737.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/typec-charger_TypeC-DC-Charger/unrouted.dsn)

Size: 54.2 kB · Layers: 4 · Nets: 59 · Components: 99 · Dimensions: 48.0 x 56.0 mm (26.88 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     221.30 |      N/A |    221.30 |   0+  1+  0 |        2 |         41 |   960 |       757 |   716127.3 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/uC3Moy_uC3Moy/unrouted.dsn)

Size: 32.1 kB · Layers: 2 · Nets: 11 · Components: 25 · Dimensions: 35.5 x 68.44 mm (24.3 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       9.98 |      N/A |      9.98 |   0+  1+  0 |        3 |         10 |   878 |       377 |    28069.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/UCCBPCB_USB_CAN_CONVERTER_BASIC/unrouted.dsn)

Size: 24.5 kB · Layers: 2 · Nets: 9 · Components: 22 · Dimensions: 26.61 x 14.61 mm (3.89 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      39.52 |      N/A |     39.52 |   0+  1+  0 |        2 |         48 |   841 |       553 |   152718.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/uedaino_uedaino/unrouted.dsn)

Size: 25.8 kB · Layers: 2 · Nets: 2 · Components: 31 · Dimensions: 50.0 x 65.0 mm (32.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      20.18 |      N/A |     20.18 |   0+  4+  0 |        0 |          0 |  1000 |       465 |    53268.5 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/uext-esp32_UEXT_ESP32/unrouted.dsn)

Size: 19.4 kB · Layers: 2 · Nets: 32 · Components: 10 · Dimensions: 26.5 x 24.0 mm (6.36 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       7.28 |      N/A |      7.28 |   0+  1+  0 |        2 |          2 |   896 |       355 |    15050.2 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ULPI-Pmod_ULPI-Pmod/unrouted.dsn)

Size: 27.8 kB · Layers: 2 · Nets: 11 · Components: 17 · Dimensions: 40.64 x 26.67 mm (10.84 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      60.77 |      N/A |     60.77 |   0+  1+  0 |        0 |          6 |   982 |       566 |   225138.8 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/UltrasonicSystem_Schematic/unrouted.dsn)

Size: 77.1 kB · Layers: 2 · Nets: 67 · Components: 94 · Dimensions: 145.0 x 110.0 mm (159.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      25.48 |      N/A |     25.48 |   0+  6+  0 |        0 |          2 |   998 |       437 |    84376.4 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/UniversalBoard4Nucleo_Nucleo_Universal_Board/unrouted.dsn)

Size: 26 kB · Layers: 2 · Nets: 0 · Components: 68 · Dimensions: 70.0 x 57.54 mm (40.28 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      13.14 |      N/A |     13.14 |   0+ 18+  0 |        1 |          0 |   991 |       565 |    50221.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/UProgrammer-Hardware_Programmer/unrouted.dsn)

Size: 41.1 kB · Layers: 2 · Nets: 45 · Components: 78 · Dimensions: 76.2 x 50.8 mm (38.71 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     267.74 |      N/A |    267.74 |   0+  1+  0 |       20 |         30 |   862 |       709 |  1013601.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Usb_dac_dac/unrouted.dsn)

Size: 37 kB · Layers: 2 · Nets: 57 · Components: 91 · Dimensions: 74.4 x 99.6 mm (74.1 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      18.62 |      N/A |     18.62 |   0+  3+  0 |        0 |          0 |  1000 |       442 |    80559.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/usb_rs232c_usb_rs232c_rev_a/unrouted.dsn)

Size: 20.5 kB · Layers: 2 · Nets: 0 · Components: 33 · Dimensions: 34.7 x 14.4 mm (5.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     121.05 |      N/A |    121.05 |   0+  1+  0 |        0 |          9 |   971 |       478 |   230569.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/usb2serial-CH340G_USB2TTL-CH340G/unrouted.dsn)

Size: 14.4 kB · Layers: 2 · Nets: 9 · Components: 20 · Dimensions: 18.0 x 25.0 mm (4.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.90 |      N/A |      2.90 |   0+  2+  0 |        0 |          0 |  1000 |       388 |     3226.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/USB-Adapter_USB Adapter/unrouted.dsn)

Size: 13.2 kB · Layers: 2 · Nets: 10 · Components: 19 · Dimensions: 47.0 x 23.0 mm (10.81 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       9.92 |      N/A |      9.92 |   0+  1+  0 |        0 |         32 |   848 |       524 |    32118.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Usb-Serial-Breakout-Cp2102_cp2102/unrouted.dsn)

Size: 9.9 kB · Layers: 2 · Nets: 7 · Components: 10 · Dimensions: 21.84 x 15.75 mm (3.44 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      13.99 |      N/A |     13.99 |   0+  1+  0 |        3 |         26 |   658 |       421 |    36410.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/USBtin_USBtin/unrouted.dsn)

Size: 65.1 kB · Layers: 2 · Nets: 18 · Components: 21 · Dimensions: 32.0 x 65.0 mm (20.8 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       5.32 |      N/A |      5.32 |   0+  1+  0 |        2 |          0 |   963 |       352 |    19994.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/USB-TypeC-breakout-board_USB-TypeC-breakout-board/unrouted.dsn)

Size: 8.3 kB · Layers: 2 · Nets: 16 · Components: 2 · Dimensions: 50.0 x 50.0 mm (25.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      35.05 |      N/A |     35.05 |   0+  1+  0 |        3 |          0 |   912 |       496 |    91837.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/uSKY_uSKY/unrouted.dsn)

Size: 14.5 kB · Layers: 2 · Nets: 26 · Components: 36 · Dimensions: 11.16 x 8.39 mm (0.94 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      58.99 |      N/A |     58.99 |   0+  1+  0 |       19 |         28 |   603 |       500 |   238564.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/VariableGainAmplifier_AnalogFrontEnd/unrouted.dsn)

Size: 33.3 kB · Layers: 2 · Nets: 26 · Components: 57 · Dimensions: 63.55 x 43.43 mm (27.6 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       6.70 |      N/A |      6.70 |   0+  2+  0 |        0 |          0 |  1000 |       280 |    24594.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/vatx_vatx/unrouted.dsn)

Size: 40.6 kB · Layers: 2 · Nets: 7 · Components: 31 · Dimensions: 50.0 x 50.1 mm (25.05 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      13.83 |      N/A |     13.83 |   0+  1+  0 |        1 |          8 |   959 |       567 |    53560.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/VB-IoT1_VB-IoT1_v1/unrouted.dsn)

Size: 188.1 kB · Layers: 4 · Nets: 93 · Components: 178 · Dimensions: 66.0 x 86.0 mm (56.76 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     907.66 |      N/A |    907.66 |   0+  2+  0 |       42 |          5 |   927 |      1453 |  2381133.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/VC4000MultiROM_MultiRomCard/unrouted.dsn)

Size: 40.6 kB · Layers: 2 · Nets: 80 · Components: 55 · Dimensions: 160.0 x 100.0 mm (160.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      93.25 |      N/A |     93.25 |   0+  1+  0 |        2 |          0 |   991 |       533 |   328134.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/vdcmon_vdcmon/unrouted.dsn)

Size: 19.9 kB · Layers: 2 · Nets: 16 · Components: 48 · Dimensions: 70.0 x 60.0 mm (42.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       5.47 |      N/A |      5.47 |   0+  3+  0 |        0 |          0 |  1000 |       552 |    12436.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Vento_Vento/unrouted.dsn)

Size: 75.6 kB · Layers: 4 · Nets: 64 · Components: 97 · Dimensions: 81.0 x 27.0 mm (21.87 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     301.01 |      N/A |    301.01 |   0+ 10+  0 |       55 |         10 |   779 |       632 |   874529.9 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/vhf-radio_exp-1/unrouted.dsn)

Size: 31.7 kB · Layers: 4 · Nets: 43 · Components: 90 · Dimensions: 50.8 x 50.8 mm (25.81 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     301.69 |      N/A |    301.69 |   0+  1+  0 |        0 |         20 |   979 |       686 |  1502310.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/VM-sensor-PT1000_vm-sensor-pt100/unrouted.dsn)

Size: 39.6 kB · Layers: 2 · Nets: 6 · Components: 27 · Dimensions: 36.0 x 50.5 mm (18.18 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      13.14 |      N/A |     13.14 |   0+  1+  0 |        2 |          0 |   960 |       556 |    51636.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/wavegen_rev3/unrouted.dsn)

Size: 80.4 kB · Layers: 4 · Nets: 22 · Components: 289 · Dimensions: 55.88 x 76.2 mm (42.58 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     903.22 |      N/A |    903.22 |   0+ 14+  0 |       66 |        228 |   795 |       849 |  2582251.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/wavegen_waveform-generator/unrouted.dsn)

Size: 50.8 kB · Layers: 2 · Nets: 43 · Components: 57 · Dimensions: 50.8 x 50.8 mm (25.81 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     143.35 |      N/A |    143.35 |   0+  1+  0 |       10 |          0 |   932 |       600 |   571950.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/wavegen_waveform-generator-rev1/unrouted.dsn)

Size: 45.2 kB · Layers: 2 · Nets: 33 · Components: 53 · Dimensions: 50.8 x 50.8 mm (25.81 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      85.46 |      N/A |     85.46 |   0+  1+  0 |        4 |          1 |   970 |       645 |   328663.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/wavegen_wavegen/unrouted.dsn)

Size: 45.2 kB · Layers: 2 · Nets: 33 · Components: 53 · Dimensions: 50.8 x 50.8 mm (25.81 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      85.87 |      N/A |     85.87 |   0+  1+  0 |        4 |          1 |   970 |       598 |   326773.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/wavegen_wavegen-alt/unrouted.dsn)

Size: 46.6 kB · Layers: 2 · Nets: 43 · Components: 53 · Dimensions: 50.8 x 50.8 mm (25.81 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     103.69 |      N/A |    103.69 |   0+  1+  0 |        2 |          0 |   986 |       764 |   471323.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/WeatherSpot_vreg_pressure/unrouted.dsn)

Size: 4.5 kB · Layers: 2 · Nets: 5 · Components: 6 · Dimensions: 16.0 x 14.01 mm (2.24 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.58 |      N/A |      0.58 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/WeatherSpot_Weather_station_unit/unrouted.dsn)

Size: 24.4 kB · Layers: 2 · Nets: 35 · Components: 42 · Dimensions: 50.55 x 25.4 mm (12.84 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      38.46 |      N/A |     38.46 |   0+  1+  0 |        1 |          0 |   989 |       549 |   141586.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/WemosLoraTracker_wemosGPSrfm/unrouted.dsn)

Size: 14.6 kB · Layers: 2 · Nets: 6 · Components: 20 · Dimensions: 26.42 x 82.8 mm (21.88 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      21.49 |      N/A |     21.49 |   0+  1+  0 |        0 |          5 |   981 |       525 |    98412.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/WHCS-Base-Station_base-station/unrouted.dsn)

Size: 31.8 kB · Layers: 2 · Nets: 0 · Components: 69 · Dimensions: 85.73 x 87.25 mm (74.8 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      31.02 |      N/A |     31.02 |   0+  7+  0 |        0 |          0 |  1000 |       511 |    96507.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/wifi_pants_wifi_pants/unrouted.dsn)

Size: 39.6 kB · Layers: 2 · Nets: 39 · Components: 132 · Dimensions: 65.0 x 30.0 mm (19.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      63.93 |      N/A |     63.93 |   0+  1+  0 |        6 |         54 |   896 |       448 |   211271.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/wifi_pstn_cid_tel_interf/unrouted.dsn)

Size: 56.3 kB · Layers: 2 · Nets: 33 · Components: 55 · Dimensions: 86.36 x 66.04 mm (57.03 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      18.51 |      N/A |     18.51 |   0+  1+  0 |        2 |          2 |   979 |       498 |    82051.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/wifi-iot-core-hw_wifi-iot-core-hw/unrouted.dsn)

Size: 30 kB · Layers: 2 · Nets: 15 · Components: 52 · Dimensions: 31.75 x 58.42 mm (18.55 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     300.74 |      N/A |    300.74 |   0+ 14+  0 |       41 |          0 |   703 |       569 |   890356.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/wifiLCD_wifilcd/unrouted.dsn)

Size: 19.3 kB · Layers: 2 · Nets: 9 · Components: 23 · Dimensions: 30.0 x 41.0 mm (12.3 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      31.62 |      N/A |     31.62 |   0+  1+  0 |        0 |          4 |   985 |       497 |   135006.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Wifi-Play-Board_Wifi_PB/unrouted.dsn)

Size: 102.9 kB · Layers: 2 · Nets: 70 · Components: 207 · Dimensions: 100.0 x 49.7 mm (49.7 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     332.62 |      N/A |    332.62 |   0+ 20+  0 |        1 |          8 |   993 |       733 |  1450541.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Wkaku_wkaku/unrouted.dsn)

Size: 28.7 kB · Layers: 2 · Nets: 14 · Components: 17 · Dimensions: 45.21 x 48.26 mm (21.82 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.89 |      N/A |      3.89 |   0+  1+  0 |        1 |          6 |   933 |       475 |     6439.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/wordclock_ledboard2/unrouted.dsn)

Size: 22.9 kB · Layers: 2 · Nets: 100 · Components: 109 · Dimensions: 161.0 x 170.0 mm (273.7 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      30.89 |      N/A |     30.89 |   0+  2+  0 |        0 |          0 |  1000 |       527 |   107627.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/WordClock_v2.0_WordClock_v2/unrouted.dsn)

Size: 16.2 kB · Layers: 2 · Nets: 17 · Components: 25 · Dimensions: 105.55 x 33.62 mm (35.49 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.66 |      N/A |      3.66 |   0+  2+  0 |        0 |          0 |  1000 |       468 |     6463.2 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/WS2811LEDMatrix_matrixcontrol/unrouted.dsn)

Size: 35.1 kB · Layers: 2 · Nets: 30 · Components: 43 · Dimensions: 50.0 x 50.0 mm (25.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     184.95 |      N/A |    184.95 |   0+  1+  0 |        0 |         26 |   954 |       570 |   803556.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/wt-rpm_C.H.I.P/unrouted.dsn)

Size: 19.6 kB · Layers: 2 · Nets: 18 · Components: 40 · Dimensions: 40.64 x 55.25 mm (22.45 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      39.09 |      N/A |     39.09 |   0+  1+  0 |        0 |         84 |   773 |       534 |   162382.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/wt-rpm_rasp_side/unrouted.dsn)

Size: 19.3 kB · Layers: 2 · Nets: 0 · Components: 84 · Dimensions: 99.7 x 99.7 mm (99.4 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      13.26 |      N/A |     13.26 |   0+ 18+  0 |        1 |          0 |   993 |       561 |    41803.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/xmasOrn_xmasOrn/unrouted.dsn)

Size: 97 kB · Layers: 2 · Nets: 25 · Components: 272 · Dimensions: 81.71 x 95.86 mm (78.33 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     116.49 |      N/A |    116.49 |   0+  8+  0 |        0 |          5 |   997 |       642 |   436304.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/xt_ide_slot_8_support_slot_8_support/unrouted.dsn)

Size: 5.5 kB · Layers: 2 · Nets: 7 · Components: 11 · Dimensions: 15.49 x 35.56 mm (5.51 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       3.84 |      N/A |      3.84 |   0+  1+  0 |        0 |          4 |   953 |       495 |     7126.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/xwhatits-capsense-controller_amkey-usb/unrouted.dsn)

Size: 31 kB · Layers: 2 · Nets: 0 · Components: 39 · Dimensions: 123.0 x 82.25 mm (101.17 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     164.63 |      N/A |    164.63 |   0+  1+  0 |        1 |         10 |   983 |       618 |   493812.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/xwhatits-capsense-controller_beamspring-usb/unrouted.dsn)

Size: 30.5 kB · Layers: 2 · Nets: 0 · Components: 36 · Dimensions: 145.5 x 21.25 mm (30.92 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     239.86 |      N/A |    239.86 |   0+  1+  0 |       22 |         69 |   787 |       667 |   763088.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/xwhatits-capsense-controller_displaywriter-usb/unrouted.dsn)

Size: 32.7 kB · Layers: 2 · Nets: 0 · Components: 92 · Dimensions: 119.75 x 32.5 mm (38.92 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     163.02 |      N/A |    163.02 |   0+  1+  0 |        8 |          0 |   965 |       763 |   648551.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/xwhatits-capsense-controller_model-f-3178-adaptor/unrouted.dsn)

Size: 7.2 kB · Layers: 2 · Nets: 0 · Components: 2 · Dimensions: 127.0 x 6.5 mm (8.26 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       7.08 |      N/A |      7.08 |   0+  2+  0 |        0 |         60 |   657 |       439 |    25365.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/xwhatits-capsense-controller_model-f-usb/unrouted.dsn)

Size: 32.5 kB · Layers: 2 · Nets: 0 · Components: 92 · Dimensions: 140.0 x 28.5 mm (39.9 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     192.29 |      N/A |    192.29 |   0+  1+  0 |        9 |         26 |   933 |       639 |   715645.1 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/xwhatits-capsense-controller_solenoid-driver/unrouted.dsn)

Size: 22 kB · Layers: 2 · Nets: 0 · Components: 21 · Dimensions: 45.25 x 21.25 mm (9.62 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      63.91 |      N/A |     63.91 |   0+  1+  0 |        0 |          4 |   984 |       504 |    70921.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/yamsek.kicad_i2c_bridge_primary/unrouted.dsn)

Size: 11 kB · Layers: 2 · Nets: 4 · Components: 17 · Dimensions: 16.0 x 28.0 mm (4.48 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      20.88 |      N/A |     20.88 |   0+  1+  0 |        0 |          8 |   953 |       529 |    73254.6 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/yamsek.kicad_i2c_bridge_secondary/unrouted.dsn)

Size: 10.4 kB · Layers: 2 · Nets: 1 · Components: 14 · Dimensions: 16.0 x 28.0 mm (4.48 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       8.83 |      N/A |      8.83 |   0+  1+  0 |        0 |          5 |   963 |       484 |    25643.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/yamsek.kicad_matrix_attiny861a/unrouted.dsn)

Size: 10.3 kB · Layers: 2 · Nets: 8 · Components: 10 · Dimensions: 16.0 x 29.0 mm (4.64 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      31.91 |      N/A |     31.91 |   0+  1+  0 |        1 |         18 |   882 |       453 |    86337.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/yamsek.kicad_matrix_mcp23017/unrouted.dsn)

Size: 11 kB · Layers: 2 · Nets: 17 · Components: 11 · Dimensions: 16.0 x 29.0 mm (4.64 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |      36.30 |      N/A |     36.30 |   0+  1+  0 |        0 |         24 |   874 |       485 |   133444.5 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/youtubeplaybutton_playbutton/unrouted.dsn)

Size: 47.2 kB · Layers: 2 · Nets: 35 · Components: 192 · Dimensions: 148.09 x 103.94 mm (153.92 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     204.13 |      N/A |    204.13 |   0+ 13+  0 |        0 |          6 |   997 |       900 |   719479.8 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Youyue-858D-plus-FAN-speed-mod_youyue-858d-plus-fan-speed-mod/unrouted.dsn)

Size: 10.1 kB · Layers: 2 · Nets: 7 · Components: 22 · Dimensions: 17.0 x 30.4 mm (5.17 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       2.29 |      N/A |      2.29 |   0+  3+  0 |        0 |          1 |   994 |       371 |     3830.4 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Youyue-858D-plus-MCU-adapter_youyue-858d-plus-mcu-adapter/unrouted.dsn)

Size: 11.2 kB · Layers: 2 · Nets: 21 · Components: 14 · Dimensions: 20.3 x 45.9 mm (9.32 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.02 |      N/A |      4.02 |   0+  3+  0 |        0 |          0 |  1000 |       224 |    11735.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/z2amiller_sensorboard/unrouted.dsn)

Size: 16.5 kB · Layers: 2 · Nets: 8 · Components: 25 · Dimensions: 48.9 x 19.31 mm (9.44 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       4.85 |      N/A |      4.85 |   0+  3+  0 |        0 |          0 |  1000 |       316 |    10232.7 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/z2amiller_sensorboard_programmer/unrouted.dsn)

Size: 7.6 kB · Layers: 2 · Nets: 7 · Components: 8 · Dimensions: 26.03 x 18.41 mm (4.79 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       0.85 |      N/A |      0.85 |   0+  2+  0 |        0 |          0 |  1000 |         0 |        0.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/zx-sizif-128_sizif128/unrouted.dsn)

Size: 132.2 kB · Layers: 2 · Nets: 28 · Components: 112 · Dimensions: 140.0 x 85.0 mm (119.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     301.08 |      N/A |    301.08 |   0+ 16+  0 |        3 |          6 |   987 |       760 |   830893.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/zx-sizif-512-ext_sizif512ext/unrouted.dsn)

Size: 197.7 kB · Layers: 2 · Nets: 45 · Components: 156 · Dimensions: 213.0 x 64.0 mm (136.32 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     901.89 |      N/A |    901.89 |   0+  7+  0 |       46 |         94 |   870 |      1396 |  3023217.2 |    4 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/zx-sizif-512-wifi_sizif512-wifi/unrouted.dsn)

Size: 15.3 kB · Layers: 2 · Nets: 4 · Components: 11 · Dimensions: 21.9 x 29.5 mm (6.46 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |       6.09 |      N/A |      6.09 |   0+  1+  0 |        0 |          3 |   973 |       632 |    20664.9 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/zx-sizif-xxs_sizif-xxs/unrouted.dsn)

Size: 91.3 kB · Layers: 2 · Nets: 23 · Components: 95 · Dimensions: 100.0 x 24.48 mm (24.48 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     301.47 |      N/A |    301.47 |   0+  6+  0 |       45 |         43 |   809 |       751 |   978293.3 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/zx-tsid_zx-tsid/unrouted.dsn)

Size: 73.9 kB · Layers: 2 · Nets: 30 · Components: 67 · Dimensions: 73.8 x 37.3 mm (27.53 cm²) · CAD: KiCad's Pcbnew (v)

| Version         | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :-------------- | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| v2.3.1-SNAPSHOT | CLI  |                N/A |        N/A |     300.72 |      N/A |    300.72 |   0+  9+  0 |       33 |          0 |   843 |       713 |  1052440.0 |    1 / 0 |       |


