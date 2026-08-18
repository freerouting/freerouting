# Freerouting Nightly Benchmarks Report
Generated on: 2026-08-18 17:50:31
System: AMD Ryzen 5 3600 6-Core Processor (6 Cores, 31.9 GB RAM)

This report lists the latest benchmark run results for each Freerouting version and fixture combination.

## Summary Table (Best Results per Fixture)

| Version | Fixture Count | Failures | Non-perfect | Avg. Score |
| :------ | ------------: | -------: | ----------: | ---------: |
| 1.9.0   |            20 |        1 |          19 |      902.9 |
| 2.2.4   |            20 |        2 |          19 |      891.5 |
| 2.3.0   |            20 |        2 |          20 |  **915.1** |


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


