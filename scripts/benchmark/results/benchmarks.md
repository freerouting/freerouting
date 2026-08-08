# Freerouting Nightly Benchmarks Report
Generated on: 2026-08-08 09:46:09
System: AMD Ryzen 5 3600 6-Core Processor (6 Cores, 31.9 GB RAM)

This report lists the latest benchmark run results for each Freerouting version and fixture combination.

## Summary Table (Best Results per Fixture)

| Version     | Fixture Count | Failures | Non-perfect | Avg. Score |
| :---------- | ------------: | -------: | ----------: | ---------: |
| 1.9.0       |             2 |        1 |           1 | **1000.0** |
| 2.0.1       |             1 |        1 |           0 |        N/A |
| 2.1.0       |             1 |        1 |           0 |        N/A |
| 2.2.4       |             2 |        2 |           0 |        N/A |
| 2.3.0       |            20 |        2 |          20 |      915.1 |
| s2026.08.08 |            20 |        2 |          20 |      914.8 |


## Group: [DAC2020_boards](../fixtures/DAC2020_boards)

### Fixture: [DAC2020_bm01.dsn](../fixtures/DAC2020_boards/DAC2020_bm01.dsn)

Size: 30.5 kB · Layers: 2 · Nets: 99 · Components: 20 · Dimensions: 101.6 x 53.3 mm (54.2 cm²) · CAD: KiCad's Pcbnew (v9.0.6)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :---- |
| 2.3.0       | CLI  |  186/ 187 ( 99.5%) |            3.78 |          250.87 |              10.19 |         264.84 |   4+ 21+  1 |        4 |          2 |   979 |            920 |           1168.6 |    2 / 0 |       |
| s2026.08.08 | CLI  |  186/ 187 ( 99.5%) |            4.19 |          379.83 |              12.11 |         396.13 |   4+ 28+  1 |        5 |          1 |   974 |           1142 |           1798.2 |    4 / 0 |       |


### Fixture: [DAC2020_bm02.dsn](../fixtures/DAC2020_boards/DAC2020_bm02.dsn)

Size: 79.7 kB · Layers: 2 · Nets: 34 · Components: 13 · Dimensions: 50.8 x 22.9 mm (11.6 cm²) · CAD: KiCad's Pcbnew (v9.0.6)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :---- |
| 2.3.0       | CLI  |   38/  38 (100.0%) |            1.32 |            2.27 |               0.04 |           3.63 |   7+  2+  0 |        0 |          4 |  1000 |            456 |              7.2 |    2 / 0 |       |
| s2026.08.08 | CLI  |   38/  38 (100.0%) |            1.18 |            2.07 |               0.04 |           3.29 |   7+  2+  0 |        0 |          4 |  1000 |            532 |              8.0 |    2 / 0 |       |


### Fixture: [DAC2020_bm04.dsn](../fixtures/DAC2020_boards/DAC2020_bm04.dsn)

Size: 27 kB · Layers: 16 · Nets: 80 · Components: 16 · Dimensions: 43.9 x 35.1 mm (15.4 cm²) · CAD: KiCad's Pcbnew (v9.0.6)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :---- |
| 2.3.0       | CLI  |  157/ 192 ( 81.8%) |            6.05 |          533.42 |              20.19 |         559.66 |   4+ 22+  1 |        3 |          0 |   979 |           1754 |           2433.6 |    2 / 0 |       |
| s2026.08.08 | CLI  |  157/ 192 ( 81.8%) |            5.45 |          486.66 |              17.90 |         510.01 |   4+ 22+  1 |        3 |          0 |   979 |           1698 |           2421.8 |    2 / 0 |       |


### Fixture: [DAC2020_bm05.dsn](../fixtures/DAC2020_boards/DAC2020_bm05.dsn)

Size: 16.8 kB · Layers: 2 · Nets: 54 · Components: 9 · Dimensions: 40 x 41 mm (16.4 cm²) · CAD: KiCad's Pcbnew (v9.0.6)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :---- |
| 2.3.0       | CLI  |  119/ 138 ( 86.2%) |            3.44 |          134.01 |               3.81 |         141.26 |   7+ 18+  1 |       22 |          0 |   785 |            705 |            687.8 |    2 / 0 |       |
| s2026.08.08 | CLI  |  119/ 138 ( 86.2%) |            3.45 |          130.56 |               3.84 |         137.85 |   7+ 18+  1 |       22 |          0 |   785 |            708 |            686.9 |    2 / 0 |       |


### Fixture: [DAC2020_bm06.dsn](../fixtures/DAC2020_boards/DAC2020_bm06.dsn)

Size: 22.9 kB · Layers: 2 · Nets: 38 · Components: 13 · Dimensions: 55 x 28 mm (15.4 cm²) · CAD: KiCad's Pcbnew (v9.0.6)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :---- |
| 2.3.0       | CLI  |  113/ 124 ( 91.1%) |            2.14 |           25.87 |               5.68 |          33.69 |   5+ 18+  1 |        2 |          8 |   963 |            627 |            199.6 |    2 / 0 |       |
| s2026.08.08 | CLI  |  113/ 124 ( 91.1%) |            2.12 |           27.29 |               5.63 |          35.04 |   5+ 18+  1 |        2 |          8 |   963 |            677 |            202.5 |    2 / 0 |       |


### Fixture: [DAC2020_bm07.dsn](../fixtures/DAC2020_boards/DAC2020_bm07.dsn)

Size: 14.9 kB · Layers: 2 · Nets: 52 · Components: 13 · Dimensions: 22 x 60 mm (13.2 cm²) · CAD: KiCad's Pcbnew (v9.0.6)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes      |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :--------- |
| 1.9.0       | GUI  |   85/  87 ( 97.7%) |            1.19 |            3.56 |              40.96 |          45.71 |   2+  6+ 10 |        0 |          0 |  1000 |            146 |             36.2 |    0 / 0 |            |
| 2.0.1       | N/A  |                N/A |             N/A |             N/A |                N/A |            N/A |   0+  0+  0 |        0 |          0 |   N/A |              0 |              0.0 |   0 / 16 | LOAD ERROR |
| 2.1.0       | CLI  |                N/A |             N/A |             N/A |                N/A |            N/A |   0+202+  0 |        0 |          0 |   N/A |              0 |              0.0 |   1 / 10 | LOAD ERROR |
| 2.2.4       | N/A  |                N/A |             N/A |             N/A |                N/A |            N/A |   0+  0+  0 |        0 |          0 |   N/A |              0 |              0.0 |    2 / 0 | LOAD ERROR |
| 2.3.0       | CLI  |   85/  85 (100.0%) |            1.36 |           13.05 |               3.20 |          17.61 |   4+ 22+  1 |        3 |          0 |   965 |            548 |            103.7 |    2 / 0 |            |
| s2026.08.08 | CLI  |   85/  85 (100.0%) |            1.32 |           12.42 |               3.04 |          16.78 |   4+ 22+  1 |        3 |          0 |   965 |            584 |            100.7 |    2 / 0 |            |


### Fixture: [DAC2020_bm08.dsn](../fixtures/DAC2020_boards/DAC2020_bm08.dsn)

Size: 5.5 kB · Layers: 2 · Nets: 15 · Components: 4 · Dimensions: 20.5 x 13.9 mm (2.8 cm²) · CAD: KiCad's Pcbnew (v9.0.6)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :---- |
| 2.3.0       | CLI  |   30/  36 ( 83.3%) |            0.51 |            0.74 |               0.01 |           1.26 |   2+  2+  0 |        0 |          1 |  1000 |            185 |              0.9 |    2 / 0 |       |
| s2026.08.08 | CLI  |   30/  36 ( 83.3%) |            0.47 |            0.69 |               0.01 |           1.17 |   2+  2+  0 |        0 |          1 |  1000 |            199 |              0.9 |    2 / 0 |       |


### Fixture: [DAC2020_bm09.dsn](../fixtures/DAC2020_boards/DAC2020_bm09.dsn)

Size: 25.1 kB · Layers: 16 · Nets: 70 · Components: 13 · Dimensions: 56.4 x 86.4 mm (48.7 cm²) · CAD: KiCad's Pcbnew (v9.0.6)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :---- |
| 2.3.0       | CLI  |                N/A |             N/A |           15.00 |               0.04 |          15.04 |   0+ 18+  0 |        1 |          0 |   991 |            429 |             48.8 |    2 / 0 |       |
| s2026.08.08 | CLI  |                N/A |             N/A |           13.64 |               0.03 |          13.67 |   0+ 18+  0 |        1 |          0 |   991 |            301 |             45.0 |    2 / 0 |       |


### Fixture: [DAC2020_bm10.dsn](../fixtures/DAC2020_boards/DAC2020_bm10.dsn)

Size: 31.3 kB · Layers: 4 · Nets: 63 · Components: 21 · Dimensions: 86 x 71.5 mm (61.5 cm²) · CAD: KiCad's Pcbnew (v9.0.6)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :---- |
| 2.3.0       | CLI  |  242/ 245 ( 98.8%) |            9.73 |           71.86 |               0.24 |          81.83 |  12+  4+  0 |        0 |          8 |  1000 |            784 |            425.7 |    2 / 0 |       |
| s2026.08.08 | CLI  |  242/ 245 ( 98.8%) |            9.55 |           66.96 |               0.20 |          76.71 |  12+  4+  0 |        0 |          8 |  1000 |            854 |            415.9 |    2 / 0 |       |


### Fixture: [DAC2020_bm11.dsn](../fixtures/DAC2020_boards/DAC2020_bm11.dsn)

Size: 26.2 kB · Layers: 4 · Nets: 35 · Components: 21 · Dimensions: 58 x 59.5 mm (34.5 cm²) · CAD: KiCad's Pcbnew (v9.0.6)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :---- |
| 2.3.0       | CLI  |  154/ 157 ( 98.1%) |            3.18 |           84.85 |              10.44 |          98.47 |   3+ 18+  1 |        2 |          0 |   987 |            754 |            494.0 |    2 / 0 |       |
| s2026.08.08 | CLI  |  154/ 157 ( 98.1%) |            3.19 |           82.12 |               9.84 |          95.15 |   3+ 18+  1 |        2 |          0 |   987 |            768 |            497.1 |    2 / 0 |       |


## Group: [KiCad_10_demos](../fixtures/KiCad_10_demos)

### Fixture: [CM5_MINIMA_3.dsn](../fixtures/KiCad_10_demos/CM5_MINIMA_3.dsn)

Size: 146.8 kB · Layers: 6 · Nets: 220 · Components: 51 · Dimensions: 61.2 x 64.2 mm (39.3 cm²) · CAD: KiCad's Pcbnew (v10.0.2)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes     |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :-------- |
| 2.3.0       | CLI  |  460/ 589 ( 78.1%) |           52.70 |         3516.58 |                N/A |        3569.28 |   5+  1+  0 |       31 |         57 |   896 |           1433 |             65.2 |    2 / 0 | TIMEOUT,  |
| s2026.08.08 | CLI  |  460/ 589 ( 78.1%) |           51.41 |         3500.30 |                N/A |        3551.71 |   5+  1+  0 |       30 |         57 |   899 |           1464 |             65.0 |    2 / 0 | TIMEOUT,  |


### Fixture: [complex_hierarchy.dsn](../fixtures/KiCad_10_demos/complex_hierarchy.dsn)

Size: 53.3 kB · Layers: 2 · Nets: 52 · Components: 21 · Dimensions: 100.7 x 80 mm (80.6 cm²) · CAD: KiCad's Pcbnew (v10.0.2)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :---- |
| 2.3.0       | CLI  |                N/A |             N/A |           15.78 |               1.47 |          17.25 |   0+ 18+  1 |        9 |          0 |   911 |            547 |             70.9 |    4 / 0 |       |
| s2026.08.08 | CLI  |                N/A |             N/A |           14.19 |               1.36 |          15.55 |   0+ 18+  1 |        9 |          0 |   911 |            552 |             72.7 |    4 / 0 |       |


### Fixture: [ecc83-pp_v2.dsn](../fixtures/KiCad_10_demos/ecc83-pp_v2.dsn)

Size: 38.2 kB · Layers: 2 · Nets: 13 · Components: 9 · Dimensions: 48.3 x 41.9 mm (20.2 cm²) · CAD: KiCad's Pcbnew (v10.0.2)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :---- |
| 2.3.0       | CLI  |                N/A |             N/A |            0.49 |               0.75 |           1.24 |   0+  2+  1 |        0 |         24 |   771 |            659 |              3.1 |    2 / 0 |       |
| s2026.08.08 | CLI  |                N/A |             N/A |            0.48 |               0.75 |           1.23 |   0+  2+  1 |        0 |         24 |   771 |            608 |              3.1 |    2 / 0 |       |


### Fixture: [ecc83-pp.dsn](../fixtures/KiCad_10_demos/ecc83-pp.dsn)

Size: 34.8 kB · Layers: 2 · Nets: 13 · Components: 9 · Dimensions: 52.1 x 46.4 mm (24.2 cm²) · CAD: KiCad's Pcbnew (v10.0.2)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :---- |
| 2.3.0       | CLI  |                N/A |             N/A |            0.44 |               0.02 |           0.46 |   0+  2+  0 |        0 |          0 |  1000 |            103 |              0.0 |    2 / 0 |       |
| s2026.08.08 | CLI  |                N/A |             N/A |            0.37 |               0.01 |           0.38 |   0+  2+  0 |        0 |          0 |  1000 |            104 |              0.0 |    2 / 0 |       |


### Fixture: [interf_u.dsn](../fixtures/KiCad_10_demos/interf_u.dsn)

Size: 67.6 kB · Layers: 2 · Nets: 173 · Components: 19 · Dimensions: 115.6 x 108.2 mm (125.1 cm²) · CAD: KiCad's Pcbnew (v10.0.2)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :---- |
| 2.3.0       | CLI  |   26/  62 ( 41.9%) |            1.17 |           70.14 |             121.26 |         192.57 |   7+ 19+  1 |        0 |         62 |   938 |            744 |            979.5 |    2 / 0 |       |
| s2026.08.08 | CLI  |   26/  62 ( 41.9%) |            1.02 |           68.40 |             115.35 |         184.77 |   7+ 19+  1 |        0 |         62 |   938 |            623 |            981.8 |    2 / 0 |       |


### Fixture: [multichannel_mixer-unrouted.dsn](../fixtures/KiCad_10_demos/multichannel_mixer-unrouted.dsn)

Size: 62 kB · Layers: 2 · Nets: 224 · Components: 15 · Dimensions: 110 x 111 mm (122.1 cm²) · CAD: KiCad's Pcbnew (v10.0.2)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes      |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :--------- |
| 1.9.0       | GUI  |                N/A |             N/A |             N/A |                N/A |            N/A |   0+  0+  0 |        0 |          0 |   N/A |              0 |              0.0 |    2 / 0 | LOAD ERROR |
| 2.3.0       | CLI  |   25/ 192 ( 13.0%) |            1.00 |          366.73 |               3.07 |         370.80 |   2+ 18+  1 |       59 |        612 |     0 |            592 |           1621.5 |    8 / 0 |            |
| s2026.08.08 | CLI  |   25/ 192 ( 13.0%) |            0.93 |          324.52 |               2.90 |         328.35 |   2+ 18+  1 |       59 |        612 |     0 |            562 |           1487.6 |    8 / 0 |            |


### Fixture: [multichannel_mixer.dsn](../fixtures/KiCad_10_demos/multichannel_mixer.dsn)

Size: 49.2 kB · Layers: 2 · Nets: 80 · Components: 15 · Dimensions: 110 x 111 mm (122.1 cm²) · CAD: KiCad's Pcbnew (v10.0.2)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :---- |
| 2.3.0       | CLI  |   28/ 192 ( 14.6%) |            0.68 |           94.44 |               1.15 |          96.27 |   2+ 18+  1 |       75 |          0 |   212 |            422 |            283.3 |    2 / 0 |       |
| s2026.08.08 | CLI  |   28/ 192 ( 14.6%) |            0.65 |           87.77 |               1.11 |          89.53 |   2+ 18+  1 |       75 |          0 |   212 |            464 |            281.3 |    2 / 0 |       |


### Fixture: [pic_programmer.dsn](../fixtures/KiCad_10_demos/pic_programmer.dsn)

Size: 104.2 kB · Layers: 2 · Nets: 111 · Components: 29 · Dimensions: 160 x 99.1 mm (158.6 cm²) · CAD: KiCad's Pcbnew (v10.0.2)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :---- |
| 2.3.0       | CLI  |    2/   2 (100.0%) |            0.34 |            3.34 |               0.03 |           3.71 |   2+  2+  0 |        0 |          1 |   998 |            425 |              7.6 |    2 / 0 |       |
| s2026.08.08 | CLI  |    2/   2 (100.0%) |            0.23 |            3.23 |               0.03 |           3.49 |   2+  2+  0 |        0 |          1 |   998 |            282 |              8.2 |    2 / 0 |       |


### Fixture: [sonde xilinx.dsn](../fixtures/KiCad_10_demos/sonde xilinx.dsn)

Size: 30.8 kB · Layers: 2 · Nets: 42 · Components: 10 · Dimensions: 80.4 x 43.2 mm (34.7 cm²) · CAD: KiCad's Pcbnew (v10.0.2)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes                             |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :-------------------------------- |
| 2.2.4       | CLI  |                N/A |             N/A |             N/A |                N/A |            N/A |   0+  0+  0 |        0 |          0 |   N/A |              0 |              0.0 |   12 / 6 | LOAD ERROR, FileNotFoundException |
| 2.3.0       | CLI  |   21/  34 ( 61.8%) |            0.55 |            1.75 |               8.59 |          10.89 |   3+  2+  1 |        0 |          0 |  1000 |            697 |             55.6 |    2 / 0 |                                   |
| s2026.08.08 | CLI  |   21/  34 ( 61.8%) |            0.52 |            1.63 |               7.51 |           9.66 |   3+  2+  1 |        0 |          0 |  1000 |            376 |             55.7 |    2 / 0 |                                   |


### Fixture: [StickHub.dsn](../fixtures/KiCad_10_demos/StickHub.dsn)

Size: 83.4 kB · Layers: 2 · Nets: 47 · Components: 58 · Dimensions: 16.5 x 40 mm (6.6 cm²) · CAD: KiCad's Pcbnew (v10.0.2)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :---- |
| 2.3.0       | CLI  |  267/ 273 ( 97.8%) |           27.00 |          262.75 |              20.93 |         310.68 |  20+ 29+  1 |        2 |          5 |   990 |            569 |            860.9 |    2 / 0 |       |
| s2026.08.08 | CLI  |  267/ 273 ( 97.8%) |           25.74 |          248.73 |              19.54 |         294.01 |  20+ 29+  1 |        2 |          5 |   990 |            567 |            867.1 |    2 / 0 |       |


