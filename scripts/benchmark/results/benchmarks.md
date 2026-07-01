# Freerouting Nightly Benchmarks Report
Generated on: 2026-07-01 09:35:44
System: AMD Ryzen 5 3600 6-Core Processor (6 Cores, 31.9 GB RAM)

This report lists the latest benchmark run results for each Freerouting version and fixture combination.

## Summary Table (Best Results per Fixture)

| Fixture Group                                | Fixture                                                                                         | Best Version    | Unrouted | Violations | Score | CPU Time (s) | Peak Heap (MB) |
| :------------------------------------------- | :---------------------------------------------------------------------------------------------- | :-------------- | -------: | ---------: | ----: | -----------: | -------------: |
| [DAC2020_boards](../fixtures/DAC2020_boards) | [DAC2020_bm01.dsn](../fixtures/DAC2020_boards/DAC2020_bm01.dsn)                                 | **2.2.4**       |        0 |         87 |   911 |       121.05 |           2010 |
| [DAC2020_boards](../fixtures/DAC2020_boards) | [DAC2020_bm02.dsn](../fixtures/DAC2020_boards/DAC2020_bm02.dsn)                                 | **1.9.0**       |        0 |          0 |  1000 |        35.25 |            124 |
| [DAC2020_boards](../fixtures/DAC2020_boards) | [DAC2020_bm04.dsn](../fixtures/DAC2020_boards/DAC2020_bm04.dsn)                                 | **1.9.0**       |        2 |          4 |   980 |      1771.71 |            402 |
| [DAC2020_boards](../fixtures/DAC2020_boards) | [DAC2020_bm05.dsn](../fixtures/DAC2020_boards/DAC2020_bm05.dsn)                                 | **s2026.06.24** |       25 |          0 |   766 |       130.39 |            903 |
| [DAC2020_boards](../fixtures/DAC2020_boards) | [DAC2020_bm06.dsn](../fixtures/DAC2020_boards/DAC2020_bm06.dsn)                                 | **s2026.06.24** |        4 |          8 |   933 |        29.56 |            763 |
| [DAC2020_boards](../fixtures/DAC2020_boards) | [DAC2020_bm07.dsn](../fixtures/DAC2020_boards/DAC2020_bm07.dsn)                                 | **1.9.0**       |        0 |          0 |  1000 |        39.52 |            154 |
| [DAC2020_boards](../fixtures/DAC2020_boards) | [DAC2020_bm08.dsn](../fixtures/DAC2020_boards/DAC2020_bm08.dsn)                                 | **1.9.0**       |        0 |          4 |   968 |         0.91 |            133 |
| [DAC2020_boards](../fixtures/DAC2020_boards) | [DAC2020_bm09.dsn](../fixtures/DAC2020_boards/DAC2020_bm09.dsn)                                 | **s2026.06.30** |        0 |          0 |  1000 |        35.66 |            124 |
| [DAC2020_boards](../fixtures/DAC2020_boards) | [DAC2020_bm10.dsn](../fixtures/DAC2020_boards/DAC2020_bm10.dsn)                                 | **1.9.0**       |        0 |          0 |  1000 |       363.94 |            379 |
| [DAC2020_boards](../fixtures/DAC2020_boards) | [DAC2020_bm11.dsn](../fixtures/DAC2020_boards/DAC2020_bm11.dsn)                                 | **s2026.06.30** |        2 |         17 |   966 |        62.62 |            419 |
| [KiCad_10_demos](../fixtures/KiCad_10_demos) | [CM5_MINIMA_3.dsn](../fixtures/KiCad_10_demos/CM5_MINIMA_3.dsn)                                 | **1.9.0**       |        4 |         36 |   968 |       260.89 |            220 |
| [KiCad_10_demos](../fixtures/KiCad_10_demos) | [complex_hierarchy.dsn](../fixtures/KiCad_10_demos/complex_hierarchy.dsn)                       | **1.9.0**       |        6 |          0 |   938 |        71.00 |             49 |
| [KiCad_10_demos](../fixtures/KiCad_10_demos) | [ecc83-pp_v2.dsn](../fixtures/KiCad_10_demos/ecc83-pp_v2.dsn)                                   | **s2026.06.30** |        0 |         24 |   771 |         1.08 |            231 |
| [KiCad_10_demos](../fixtures/KiCad_10_demos) | [ecc83-pp.dsn](../fixtures/KiCad_10_demos/ecc83-pp.dsn)                                         | **1.9.0**       |        0 |          0 |  1000 |         0.60 |             81 |
| [KiCad_10_demos](../fixtures/KiCad_10_demos) | [interf_u.dsn](../fixtures/KiCad_10_demos/interf_u.dsn)                                         | **s2026.06.30** |        0 |         62 |   938 |       715.85 |            533 |
| [KiCad_10_demos](../fixtures/KiCad_10_demos) | [kit-dev-coldfire-xilinx_5213.dsn](../fixtures/KiCad_10_demos/kit-dev-coldfire-xilinx_5213.dsn) | **s2026.06.30** |        2 |          4 |   980 |      2278.81 |            568 |
| [KiCad_10_demos](../fixtures/KiCad_10_demos) | [multichannel_mixer-unrouted.dsn](../fixtures/KiCad_10_demos/multichannel_mixer-unrouted.dsn)   | **s2026.06.30** |       59 |        612 |     0 |       244.97 |            655 |
| [KiCad_10_demos](../fixtures/KiCad_10_demos) | [multichannel_mixer.dsn](../fixtures/KiCad_10_demos/multichannel_mixer.dsn)                     | **1.9.0**       |       75 |          0 |   212 |         8.13 |            288 |
| [KiCad_10_demos](../fixtures/KiCad_10_demos) | [pic_programmer.dsn](../fixtures/KiCad_10_demos/pic_programmer.dsn)                             | **s2026.06.30** |        0 |          1 |   998 |        13.03 |            244 |
| [KiCad_10_demos](../fixtures/KiCad_10_demos) | [RoyalBlue54L-Feather.dsn](../fixtures/KiCad_10_demos/RoyalBlue54L-Feather.dsn)                 | **1.9.0**       |       67 |        571 |   890 |      1711.13 |            240 |
| [KiCad_10_demos](../fixtures/KiCad_10_demos) | [sonde xilinx.dsn](../fixtures/KiCad_10_demos/sonde xilinx.dsn)                                 | **s2026.06.30** |      N/A |        N/A |   N/A |         0.00 |              0 |
| [KiCad_10_demos](../fixtures/KiCad_10_demos) | [StickHub.dsn](../fixtures/KiCad_10_demos/StickHub.dsn)                                         | **s2026.06.24** |        1 |         13 |   984 |      1794.76 |            853 |
| [KiCad_10_demos](../fixtures/KiCad_10_demos) | [video.dsn](../fixtures/KiCad_10_demos/video.dsn)                                               | **1.9.0**       |       74 |         96 |   982 |      1774.43 |           1082 |
| [KiCad_10_demos](../fixtures/KiCad_10_demos) | [vme-wren.dsn](../fixtures/KiCad_10_demos/vme-wren.dsn)                                         | **2.2.4**       |     1243 |         79 |   155 |      1779.28 |           5354 |


## Group: [DAC2020_boards](../fixtures/DAC2020_boards)

### Fixture: [DAC2020_bm01.dsn](../fixtures/DAC2020_boards/DAC2020_bm01.dsn)

Size: 30.5 kB · Layers: 2 · Nets: 99 · Components: 20 · Dimensions: 101.6 x 53.3 mm (54.2 cm²) · CAD: KiCad's Pcbnew (v9.0.6)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :---- |
| 1.9.0       | GUI  |  186/ 212 ( 87.7%) |            4.92 |           44.91 |            1208.77 |        1258.60 |  20+ 10+ 18 |        0 |         87 |   911 |            354 |           1351.2 |   42 / 0 |       |
| 2.2.4       | CLI  |                N/A |             N/A |          186.07 |                N/A |         186.07 |   0+ 16+  9 |        0 |         87 |   911 |           2010 |             77.9 |    6 / 0 |       |
| s2026.06.24 | CLI  |  187/ 212 ( 88.2%) |            4.36 |          118.07 |            1595.93 |        1718.36 |   5+ 14+  5 |        0 |         87 |   911 |           1379 |           1628.0 |    2 / 0 |       |
| s2026.06.30 | CLI  |  187/ 187 (100.0%) |            2.97 |           80.05 |              16.36 |          99.38 |   5+ 22+  1 |        3 |         87 |   895 |            355 |            102.4 |    2 / 0 |       |


### Fixture: [DAC2020_bm02.dsn](../fixtures/DAC2020_boards/DAC2020_bm02.dsn)

Size: 79.7 kB · Layers: 2 · Nets: 34 · Components: 13 · Dimensions: 50.8 x 22.9 mm (11.6 cm²) · CAD: KiCad's Pcbnew (v9.0.6)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :---- |
| 1.9.0       | GUI  |   37/  45 ( 82.2%) |            1.93 |            0.56 |              33.62 |          36.11 |  20+  4+ 13 |        0 |          0 |  1000 |            124 |             34.9 |    0 / 0 |       |
| 2.2.4       | CLI  |                N/A |             N/A |            1.93 |            3572.88 |        3574.81 |   0+  5+674 |        1 |          0 |   971 |           1677 |              0.4 |    6 / 0 |       |
| s2026.06.24 | CLI  |   38/  45 ( 84.4%) |            0.94 |            2.12 |              48.24 |          51.30 |   3+  4+  6 |        0 |          0 |  1000 |            661 |             44.2 |    0 / 0 |       |
| s2026.06.30 | CLI  |   38/  38 (100.0%) |            2.48 |            3.58 |              11.27 |          17.33 |  20+  4+  4 |        0 |          0 |  1000 |            261 |             15.0 |    2 / 0 |       |


### Fixture: [DAC2020_bm04.dsn](../fixtures/DAC2020_boards/DAC2020_bm04.dsn)

Size: 27 kB · Layers: 16 · Nets: 80 · Components: 16 · Dimensions: 43.9 x 35.1 mm (15.4 cm²) · CAD: KiCad's Pcbnew (v9.0.6)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :---- |
| 1.9.0       | GUI  |  159/ 198 ( 80.3%) |            6.46 |          100.12 |            1708.37 |        1814.95 |  20+ 24+  3 |        2 |          4 |   980 |            402 |           1309.7 |    2 / 0 |       |
| 2.2.4       | CLI  |                N/A |             N/A |          145.63 |              26.88 |         172.51 |   0+ 18+  2 |        4 |          4 |   945 |           1926 |            107.0 |    6 / 0 |       |
| s2026.06.24 | CLI  |  150/ 198 ( 75.8%) |           38.37 |          365.65 |              10.09 |         414.11 |  20+ 18+  2 |        3 |          4 |   959 |           1852 |            298.4 |    0 / 0 |       |
| s2026.06.30 | CLI  |  155/ 192 ( 80.7%) |           12.44 |          134.37 |              32.80 |         179.61 |  20+ 18+  2 |        2 |          4 |   980 |            600 |            152.2 |    2 / 0 |       |


### Fixture: [DAC2020_bm05.dsn](../fixtures/DAC2020_boards/DAC2020_bm05.dsn)

Size: 16.8 kB · Layers: 2 · Nets: 54 · Components: 9 · Dimensions: 40 x 41 mm (16.4 cm²) · CAD: KiCad's Pcbnew (v9.0.6)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :---- |
| 1.9.0       | GUI  |   88/ 138 ( 63.8%) |            4.46 |           76.40 |            1720.13 |        1800.99 |  20+ 33+  4 |       37 |          0 |   589 |            198 |           2096.1 |   42 / 0 |       |
| 2.2.4       | CLI  |                N/A |             N/A |         1800.97 |                N/A |        1800.97 |   0+163+  0 |       37 |          0 |   551 |           1265 |           1303.2 |    6 / 0 |       |
| s2026.06.24 | CLI  |  121/ 138 ( 87.7%) |            4.10 |          142.78 |               3.06 |         149.94 |  20+ 20+  3 |       25 |          0 |   766 |            903 |            117.2 |    0 / 0 |       |
| s2026.06.30 | CLI  |  117/ 138 ( 84.8%) |            2.02 |          105.94 |               3.93 |         111.89 |   6+ 19+  2 |       26 |          0 |   748 |            300 |            114.7 |    2 / 0 |       |


### Fixture: [DAC2020_bm06.dsn](../fixtures/DAC2020_boards/DAC2020_bm06.dsn)

Size: 22.9 kB · Layers: 2 · Nets: 38 · Components: 13 · Dimensions: 55 x 28 mm (15.4 cm²) · CAD: KiCad's Pcbnew (v9.0.6)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :---- |
| 1.9.0       | GUI  |  106/ 126 ( 84.1%) |            3.19 |            7.51 |            1524.08 |        1534.78 |  20+ 23+  5 |        8 |          8 |   892 |            167 |           1500.8 |    5 / 0 |       |
| 2.2.4       | CLI  |                N/A |             N/A |           21.02 |               7.32 |          28.34 |   0+ 27+  2 |        8 |          8 |   882 |           1683 |             16.9 |    6 / 0 |       |
| s2026.06.24 | CLI  |  111/ 126 ( 88.1%) |            2.09 |           26.70 |               3.23 |          32.02 |   5+ 23+  2 |        4 |          8 |   933 |            763 |             24.1 |    0 / 0 |       |
| s2026.06.30 | CLI  |  111/ 124 ( 89.5%) |            1.62 |           19.53 |               6.82 |          27.97 |   5+ 18+  2 |        6 |          8 |   912 |            284 |             24.7 |    2 / 0 |       |


### Fixture: [DAC2020_bm07.dsn](../fixtures/DAC2020_boards/DAC2020_bm07.dsn)

Size: 14.9 kB · Layers: 2 · Nets: 52 · Components: 13 · Dimensions: 22 x 60 mm (13.2 cm²) · CAD: KiCad's Pcbnew (v9.0.6)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes                |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :------------------- |
| 1.9.0       | GUI  |   85/  87 ( 97.7%) |            1.17 |            3.30 |              37.00 |          41.47 |   2+  5+ 10 |        0 |          0 |  1000 |            154 |             35.8 |    0 / 0 |                      |
| 2.0.1       | CLI  |                N/A |             N/A |             N/A |                N/A |            N/A |   0+  7+  0 |        1 |          0 |   988 |           1127 |              0.0 |   1 / 32 | NoSuchFieldException |
| 2.1.0       | CLI  |                N/A |             N/A |             N/A |                N/A |            N/A |   0+811+  3 |        2 |          0 |   977 |           1428 |              0.0 |   1 / 20 | NoSuchFieldException |
| 2.2.4       | CLI  |                N/A |             N/A |            4.26 |              44.84 |          49.10 |   0+  8+  7 |        0 |          0 |  1000 |           1471 |              2.0 |    3 / 0 |                      |
| s2026.06.23 | CLI  |   84/  87 ( 96.6%) |            8.81 |           16.11 |                N/A |          24.92 |   0+ 18+  2 |        3 |          0 |   965 |           1380 |             20.1 |    0 / 0 |                      |
| s2026.06.24 | CLI  |   84/  87 ( 96.6%) |            8.61 |           15.51 |               1.57 |          25.69 |  20+ 18+  2 |        3 |          0 |   965 |            788 |             25.4 |    0 / 0 |                      |
| s2026.06.30 | CLI  |   85/  85 (100.0%) |            2.33 |            7.24 |               3.83 |          13.40 |  17+ 18+  2 |        1 |          0 |   988 |            248 |              8.2 |    2 / 0 |                      |


### Fixture: [DAC2020_bm08.dsn](../fixtures/DAC2020_boards/DAC2020_bm08.dsn)

Size: 5.5 kB · Layers: 2 · Nets: 15 · Components: 4 · Dimensions: 20.5 x 13.9 mm (2.8 cm²) · CAD: KiCad's Pcbnew (v9.0.6)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :---- |
| 1.9.0       | GUI  |   30/  36 ( 83.3%) |            0.39 |            0.00 |               0.54 |           0.93 |   2+  0+  2 |        0 |          4 |   968 |            133 |              0.5 |    0 / 0 |       |
| 2.2.4       | CLI  |                N/A |             N/A |            0.51 |               2.30 |           2.81 |   0+  2+  2 |        0 |          4 |   968 |           1538 |              0.0 |    6 / 0 |       |
| s2026.06.24 | CLI  |   30/  36 ( 83.3%) |            0.62 |            0.74 |               1.54 |           2.90 |   3+  2+  2 |        0 |          4 |   968 |            546 |              1.1 |    0 / 0 |       |
| s2026.06.30 | CLI  |   30/  36 ( 83.3%) |            0.43 |            0.53 |               0.86 |           1.82 |   2+  2+  1 |        0 |          4 |   968 |            177 |              0.8 |    2 / 0 |       |


### Fixture: [DAC2020_bm09.dsn](../fixtures/DAC2020_boards/DAC2020_bm09.dsn)

Size: 25.1 kB · Layers: 16 · Nets: 70 · Components: 13 · Dimensions: 56.4 x 86.4 mm (48.7 cm²) · CAD: KiCad's Pcbnew (v9.0.6)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :---- |
| 1.9.0       | GUI  |                N/A |            0.00 |            4.09 |              10.68 |          14.77 |   0+  2+  2 |        1 |          0 |   991 |            164 |              5.2 |    0 / 0 |       |
| 2.2.4       | CLI  |                N/A |             N/A |            6.68 |              49.10 |          55.78 |   0+  3+  1 |        0 |          0 |  1000 |           1718 |              2.4 |    6 / 0 |       |
| s2026.06.24 | CLI  |                N/A |             N/A |            9.09 |              38.08 |          47.17 |   0+  3+  1 |        0 |          0 |  1000 |            753 |             14.5 |    0 / 0 |       |
| s2026.06.30 | CLI  |                N/A |             N/A |            7.26 |              28.97 |          36.23 |   0+  3+  1 |        0 |          0 |  1000 |            124 |             14.5 |    2 / 0 |       |


### Fixture: [DAC2020_bm10.dsn](../fixtures/DAC2020_boards/DAC2020_bm10.dsn)

Size: 31.3 kB · Layers: 4 · Nets: 63 · Components: 21 · Dimensions: 86 x 71.5 mm (61.5 cm²) · CAD: KiCad's Pcbnew (v9.0.6)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :---- |
| 1.9.0       | GUI  |  243/ 283 ( 85.9%) |            7.53 |           11.63 |             348.92 |         368.08 |  20+  3+  5 |        0 |          0 |  1000 |            379 |            298.3 |    7 / 0 |       |
| 2.2.4       | CLI  |                N/A |             N/A |           28.42 |                N/A |          28.42 |   0+  6+ 12 |        0 |          0 |  1000 |           2102 |             21.7 |    6 / 0 |       |
| s2026.06.24 | CLI  |  244/ 283 ( 86.2%) |            4.17 |           26.15 |            1774.68 |        1805.00 |   4+  6+ 10 |        0 |          0 |  1000 |           1616 |           1236.8 |    0 / 0 |       |
| s2026.06.30 | CLI  |  245/ 245 (100.0%) |            5.32 |           28.20 |            1772.11 |        1805.63 |   7+  5+  8 |        0 |          0 |  1000 |            400 |           1397.2 |    2 / 0 |       |


### Fixture: [DAC2020_bm11.dsn](../fixtures/DAC2020_boards/DAC2020_bm11.dsn)

Size: 26.2 kB · Layers: 4 · Nets: 35 · Components: 21 · Dimensions: 58 x 59.5 mm (34.5 cm²) · CAD: KiCad's Pcbnew (v9.0.6)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :---- |
| 1.9.0       | GUI  |  142/ 193 ( 73.6%) |           10.20 |            6.36 |            1660.04 |        1676.60 |  20+ 25+  9 |        6 |         17 |   897 |             94 |           1597.2 |    2 / 0 |       |
| 2.2.4       | CLI  |                N/A |             N/A |           16.43 |              19.54 |          35.97 |   0+ 18+  2 |        7 |         17 |   866 |           1674 |             11.1 |    6 / 0 |       |
| s2026.06.24 | CLI  |  157/ 193 ( 81.3%) |            2.88 |           15.04 |              14.40 |          32.32 |   5+  4+  3 |        2 |         17 |   966 |            829 |             19.0 |    2 / 0 |       |
| s2026.06.30 | CLI  |  156/ 157 ( 99.4%) |            2.71 |           37.49 |              23.89 |          64.09 |   5+ 18+  2 |        2 |         17 |   966 |            419 |             57.2 |    2 / 0 |       |


## Group: [KiCad_10_demos](../fixtures/KiCad_10_demos)

### Fixture: [CM5_MINIMA_3.dsn](../fixtures/KiCad_10_demos/CM5_MINIMA_3.dsn)

Size: 146.8 kB · Layers: 6 · Nets: 220 · Components: 51 · Dimensions: 61.2 x 64.2 mm (39.3 cm²) · CAD: KiCad's Pcbnew (v10.0.2)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes   |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :------ |
| 1.9.0       | GUI  |  294/ 593 ( 49.6%) |           41.74 |           16.85 |             205.75 |         264.34 |  20+  5+  3 |        4 |         36 |   968 |            220 |            136.5 |   23 / 0 |         |
| 2.2.4       | CLI  |                N/A |             N/A |          110.80 |                N/A |         110.80 |   0+  9+  2 |        4 |         36 |   955 |           1957 |             60.6 |    6 / 0 |         |
| s2026.06.24 | CLI  |                N/A |             N/A |             N/A |                N/A |            N/A |   1+  0+  0 |        0 |          0 |   N/A |            990 |              0.0 |    2 / 0 | TIMEOUT |
| s2026.06.30 | CLI  |                N/A |             N/A |             N/A |                N/A |            N/A |   1+  0+  0 |        0 |          0 |   N/A |            639 |              0.0 |    2 / 0 | TIMEOUT |


### Fixture: [complex_hierarchy.dsn](../fixtures/KiCad_10_demos/complex_hierarchy.dsn)

Size: 53.3 kB · Layers: 2 · Nets: 52 · Components: 21 · Dimensions: 100.7 x 80 mm (80.6 cm²) · CAD: KiCad's Pcbnew (v10.0.2)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :---- |
| 1.9.0       | GUI  |                N/A |            0.00 |            7.42 |              65.35 |          72.77 |   0+ 27+  2 |        6 |          0 |   938 |             49 |             52.5 |    2 / 0 |       |
| 2.2.4       | CLI  |                N/A |             N/A |           11.34 |               9.14 |          20.48 |   0+ 18+  2 |       10 |          0 |   903 |           1750 |              7.2 |    6 / 0 |       |
| s2026.06.24 | CLI  |                N/A |             N/A |           13.75 |               1.68 |          15.43 |   0+ 18+  2 |        9 |          0 |   920 |            678 |              9.9 |    2 / 0 |       |
| s2026.06.30 | CLI  |                N/A |             N/A |           13.12 |               1.60 |          14.72 |   0+ 18+  2 |        9 |          0 |   920 |            220 |             10.4 |    2 / 0 |       |


### Fixture: [ecc83-pp_v2.dsn](../fixtures/KiCad_10_demos/ecc83-pp_v2.dsn)

Size: 38.2 kB · Layers: 2 · Nets: 13 · Components: 9 · Dimensions: 48.3 x 41.9 mm (20.2 cm²) · CAD: KiCad's Pcbnew (v10.0.2)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :---- |
| 1.9.0       | GUI  |                N/A |            0.00 |            0.35 |               0.69 |           1.04 |   0+  1+  2 |        0 |         24 |   771 |            126 |              1.2 |   14 / 0 |       |
| 2.2.4       | CLI  |                N/A |             N/A |            0.37 |               2.74 |           3.11 |   0+  2+  2 |        0 |         24 |   771 |           1665 |              0.0 |    6 / 0 |       |
| s2026.06.24 | CLI  |                N/A |             N/A |            0.44 |               1.27 |           1.71 |   0+  2+  2 |        0 |         24 |   771 |            676 |              2.0 |    2 / 0 |       |
| s2026.06.30 | CLI  |                N/A |             N/A |            0.37 |               1.16 |           1.53 |   0+  2+  2 |        0 |         24 |   771 |            231 |              2.0 |    2 / 0 |       |


### Fixture: [ecc83-pp.dsn](../fixtures/KiCad_10_demos/ecc83-pp.dsn)

Size: 34.8 kB · Layers: 2 · Nets: 13 · Components: 9 · Dimensions: 52.1 x 46.4 mm (24.2 cm²) · CAD: KiCad's Pcbnew (v10.0.2)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :---- |
| 1.9.0       | GUI  |                N/A |            0.00 |            0.26 |               0.39 |           0.65 |   0+  1+  2 |        0 |          0 |  1000 |             81 |              0.2 |    0 / 0 |       |
| 2.2.4       | CLI  |                N/A |             N/A |            0.33 |               2.74 |           3.07 |   0+  2+  3 |        0 |          0 |  1000 |           1599 |              0.0 |    6 / 0 |       |
| s2026.06.24 | CLI  |                N/A |             N/A |            0.41 |               0.32 |           0.73 |   0+  2+  1 |        0 |          0 |  1000 |            359 |              0.1 |    2 / 0 |       |
| s2026.06.30 | CLI  |                N/A |             N/A |            0.34 |               0.31 |           0.65 |   0+  2+  1 |        0 |          0 |  1000 |            155 |              0.1 |    2 / 0 |       |


### Fixture: [interf_u.dsn](../fixtures/KiCad_10_demos/interf_u.dsn)

Size: 67.6 kB · Layers: 2 · Nets: 173 · Components: 19 · Dimensions: 115.6 x 108.2 mm (125.1 cm²) · CAD: KiCad's Pcbnew (v10.0.2)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :---- |
| 1.9.0       | GUI  |   27/  62 ( 43.5%) |            1.23 |           36.59 |             194.33 |         232.15 |  20+ 28+  9 |        0 |         62 |   938 |            211 |            175.0 |   13 / 0 |       |
| 2.2.4       | CLI  |                N/A |             N/A |           18.63 |             328.20 |         346.83 |   0+ 10+  4 |        0 |         62 |   938 |           1675 |             12.0 |    6 / 0 |       |
| s2026.06.24 | CLI  |   28/  62 ( 45.2%) |            1.29 |           39.33 |             217.85 |         258.47 |   3+ 12+  3 |        0 |         62 |   938 |            841 |            346.4 |    2 / 0 |       |
| s2026.06.30 | CLI  |   27/  62 ( 43.5%) |            3.24 |           34.44 |             684.69 |         722.37 |  20+ 12+ 10 |        0 |         62 |   938 |            533 |           1161.4 |    2 / 0 |       |


### Fixture: [kit-dev-coldfire-xilinx_5213.dsn](../fixtures/KiCad_10_demos/kit-dev-coldfire-xilinx_5213.dsn)

Size: 153.5 kB · Layers: 4 · Nets: 278 · Components: 52 · Dimensions: 157.5 x 91.4 mm (144 cm²) · CAD: KiCad's Pcbnew (v10.0.2)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes   |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :------ |
| 1.9.0       | GUI  |  444/ 552 ( 80.4%) |          173.65 |          172.33 |                N/A |         345.98 |  20+ 31+  0 |       12 |          4 |   993 |            133 |            142.1 |  571 / 0 | TIMEOUT |
| 2.2.4       | CLI  |                N/A |             N/A |          423.57 |                N/A |         423.57 |   0+ 24+  1 |        4 |          4 |   954 |           2508 |            216.9 |    6 / 0 |         |
| s2026.06.24 | CLI  |  441/ 552 ( 79.9%) |          218.63 |          613.92 |             225.23 |        1057.78 |  20+ 25+  2 |        2 |          4 |   971 |           1435 |            459.4 |    2 / 0 |         |
| s2026.06.30 | CLI  |  471/ 549 ( 85.8%) |          494.69 |         1806.20 |               0.01 |        2300.90 |  20+ 19+  0 |        2 |          4 |   980 |            568 |            803.1 |    2 / 0 |         |


### Fixture: [multichannel_mixer-unrouted.dsn](../fixtures/KiCad_10_demos/multichannel_mixer-unrouted.dsn)

Size: 62 kB · Layers: 2 · Nets: 224 · Components: 15 · Dimensions: 110 x 111 mm (122.1 cm²) · CAD: KiCad's Pcbnew (v10.0.2)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes   |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :------ |
| 1.9.0       | GUI  |                N/A |             N/A |             N/A |                N/A |            N/A |   0+  0+  0 |        0 |          0 |   N/A |              0 |              0.0 |    2 / 0 | TIMEOUT |
| 2.2.4       | CLI  |                N/A |             N/A |           16.16 |               3.14 |          19.30 |   0+  2+  2 |       59 |        614 |     0 |           1268 |             11.0 |   12 / 0 |         |
| s2026.06.24 | CLI  |   13/ 192 (  6.8%) |            1.57 |           26.73 |               1.97 |          30.27 |   2+  2+  3 |       59 |        612 |     0 |           1012 |             71.4 |    8 / 0 |         |
| s2026.06.30 | CLI  |   25/ 192 ( 13.0%) |            1.99 |          241.57 |               4.56 |         248.12 |   2+ 18+  2 |       59 |        612 |     0 |            655 |            709.1 |    8 / 0 |         |


### Fixture: [multichannel_mixer.dsn](../fixtures/KiCad_10_demos/multichannel_mixer.dsn)

Size: 49.2 kB · Layers: 2 · Nets: 80 · Components: 15 · Dimensions: 110 x 111 mm (122.1 cm²) · CAD: KiCad's Pcbnew (v10.0.2)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :---- |
| 1.9.0       | GUI  |   13/ 192 (  6.8%) |            0.52 |            7.09 |               0.65 |           8.26 |   1+  2+  2 |       75 |          0 |   212 |            288 |              6.7 |    0 / 0 |       |
| 2.2.4       | CLI  |                N/A |             N/A |           69.63 |               1.40 |          71.03 |   0+ 18+  2 |       75 |          0 |   202 |           1430 |             68.9 |    6 / 0 |       |
| s2026.06.24 | CLI  |   13/ 192 (  6.8%) |            0.55 |           10.36 |               0.70 |          11.61 |   2+  2+  3 |       75 |          0 |   212 |            689 |             10.3 |    2 / 0 |       |
| s2026.06.30 | CLI  |   28/ 192 ( 14.6%) |            0.74 |           82.80 |               1.74 |          85.28 |   2+ 18+  2 |       75 |          0 |   212 |            348 |             91.5 |    2 / 0 |       |


### Fixture: [pic_programmer.dsn](../fixtures/KiCad_10_demos/pic_programmer.dsn)

Size: 104.2 kB · Layers: 2 · Nets: 111 · Components: 29 · Dimensions: 160 x 99.1 mm (158.6 cm²) · CAD: KiCad's Pcbnew (v10.0.2)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :---- |
| 1.9.0       | GUI  |    1/   2 ( 50.0%) |            0.33 |            1.79 |               4.89 |           7.01 |  20+  2+  2 |        2 |          1 |   959 |            158 |              3.4 |    0 / 0 |       |
| 2.2.4       | CLI  |                N/A |             N/A |            2.83 |              15.94 |          18.77 |   0+  4+  2 |        2 |          1 |   983 |           1586 |              0.9 |    6 / 0 |       |
| s2026.06.24 | CLI  |    2/   2 (100.0%) |            0.30 |            2.79 |              10.86 |          13.95 |   2+  3+  1 |        0 |          1 |   998 |            577 |              7.2 |    2 / 0 |       |
| s2026.06.30 | CLI  |    2/   2 (100.0%) |            0.28 |            2.78 |              10.80 |          13.86 |   2+  3+  1 |        0 |          1 |   998 |            244 |              7.2 |    2 / 0 |       |


### Fixture: [RoyalBlue54L-Feather.dsn](../fixtures/KiCad_10_demos/RoyalBlue54L-Feather.dsn)

Size: 101.7 kB · Layers: 8 · Nets: 95 · Components: 37 · Dimensions: 58.4 x 22.9 mm (13.4 cm²) · CAD: KiCad's Pcbnew (v10.0.2)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes   |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :------ |
| 1.9.0       | GUI  |  161/ 303 ( 53.1%) |           47.51 |         1760.42 |                N/A |        1807.93 |  20+ 12+  0 |       67 |        571 |   890 |            240 |            908.2 | 1599 / 0 | TIMEOUT |
| 2.2.4       | CLI  |                N/A |             N/A |         1809.00 |                N/A |        1809.00 |   0+  5+  0 |       69 |        224 |   780 |           1125 |           1462.5 |    6 / 0 | TIMEOUT |
| s2026.06.24 | CLI  |                N/A |             N/A |             N/A |                N/A |            N/A |   1+  0+  0 |        0 |          0 |   N/A |            988 |              0.0 |    2 / 0 | TIMEOUT |
| s2026.06.30 | CLI  |                N/A |             N/A |             N/A |                N/A |            N/A |   1+  0+  0 |        0 |          0 |   N/A |            567 |              0.0 |    2 / 0 | TIMEOUT |


### Fixture: [sonde xilinx.dsn](../fixtures/KiCad_10_demos/sonde xilinx.dsn)

Size: 30.8 kB · Layers: 2 · Nets: 42 · Components: 10 · Dimensions: 80.4 x 43.2 mm (34.7 cm²) · CAD: KiCad's Pcbnew (v10.0.2)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes                             |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :-------------------------------- |
| 1.9.0       | GUI  |                N/A |             N/A |             N/A |                N/A |            N/A |   0+  0+  0 |        0 |          0 |   N/A |              0 |              0.0 |    1 / 4 | FileNotFoundException             |
| 2.2.4       | CLI  |                N/A |             N/A |             N/A |                N/A |            N/A |   0+  0+  0 |        0 |          0 |   N/A |              0 |              0.0 |   10 / 6 | LOAD ERROR, FileNotFoundException |
| s2026.06.24 | CLI  |                N/A |             N/A |             N/A |                N/A |            N/A |   0+  0+  0 |        0 |          0 |   N/A |              0 |              0.0 |    8 / 6 | LOAD ERROR, FileNotFoundException |
| s2026.06.30 | CLI  |                N/A |             N/A |             N/A |                N/A |            N/A |   0+  0+  0 |        0 |          0 |   N/A |              0 |              0.0 |    8 / 6 | LOAD ERROR, FileNotFoundException |


### Fixture: [StickHub.dsn](../fixtures/KiCad_10_demos/StickHub.dsn)

Size: 83.4 kB · Layers: 2 · Nets: 47 · Components: 58 · Dimensions: 16.5 x 40 mm (6.6 cm²) · CAD: KiCad's Pcbnew (v10.0.2)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes   |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :------ |
| 1.9.0       | GUI  |  167/ 273 ( 61.2%) |           19.10 |           13.47 |             717.87 |         750.44 |  20+ 29+  9 |        2 |          2 |   990 |            138 |            535.1 |   44 / 0 |         |
| 2.2.4       | CLI  |                N/A |             N/A |           25.87 |              31.08 |          56.95 |   0+ 26+  2 |        4 |          2 |   977 |           1613 |             16.6 |    4 / 0 |         |
| s2026.06.24 | CLI  |  169/ 273 ( 61.9%) |           27.09 |          195.58 |            1604.03 |        1826.70 |  13+ 23+  3 |        1 |         13 |   984 |            853 |           2025.4 |    2 / 0 | TIMEOUT |
| s2026.06.30 | CLI  |  269/ 273 ( 98.5%) |           55.07 |          314.15 |             124.99 |         494.21 |  20+ 28+  3 |        4 |          2 |   977 |            330 |            425.8 |    2 / 0 |         |


### Fixture: [video.dsn](../fixtures/KiCad_10_demos/video.dsn)

Size: 228.7 kB · Layers: 4 · Nets: 588 · Components: 53 · Dimensions: 312 x 106.7 mm (332.9 cm²) · CAD: KiCad's Pcbnew (v10.0.2)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes   |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :------ |
| 1.9.0       | GUI  | 1017/1206 ( 84.3%) |          298.23 |         1502.82 |                N/A |        1801.05 |  20+  5+  0 |       74 |         96 |   982 |           1082 |            896.0 |   54 / 0 | TIMEOUT |
| 2.2.4       | CLI  |                N/A |             N/A |        16227.91 |                N/A |       16227.91 |   0+  3+  0 |      125 |          0 |   962 |           2378 |            580.9 |    6 / 0 |         |
| s2026.06.24 | CLI  | 1001/1206 ( 83.0%) |          766.46 |         1801.81 |               0.03 |        2568.30 |  20+  1+  0 |      174 |        282 |   815 |           2923 |           1059.5 |    2 / 0 |         |
| s2026.06.30 | CLI  |  894/1160 ( 77.1%) |         1815.59 |         1817.85 |               0.01 |        3633.45 |   3+  0+  0 |      317 |        545 |   351 |            295 |           1252.3 |    2 / 0 |         |


### Fixture: [vme-wren.dsn](../fixtures/KiCad_10_demos/vme-wren.dsn)

Size: 1552.3 kB · Layers: 12 · Nets: 1800 · Components: 150 · Dimensions: 160.1 x 233.5 mm (373.8 cm²) · CAD: KiCad's Pcbnew (v10.0.2)

| Version     | Mode | Fanout             | Fanout Time (s) | Router Time (s) | Optimizer Time (s) | Total Time (s) | Passes      | Unrouted | Violations | Score | Peak Heap (MB) | Total Alloc (GB) | Warn/Err | Notes   |
| :---------- | :--- | -----------------: | --------------: | --------------: | -----------------: | -------------: | ----------: | -------: | ---------: | ----: | -------------: | ---------------: | -------: | :------ |
| 1.9.0       | GUI  |                N/A |             N/A |             N/A |                N/A |            N/A |   0+  0+  0 |        0 |          0 |   N/A |              0 |              0.0 |    0 / 0 | TIMEOUT |
| 2.2.4       | CLI  |                N/A |             N/A |         1806.08 |                N/A |        1806.08 |   0+  1+  0 |     1243 |         79 |   155 |           5354 |            379.2 |    4 / 0 |         |
| s2026.06.24 | CLI  |    0/6353 (  0.0%) |         1803.66 |         1804.40 |               0.07 |        3608.13 |   1+  0+  0 |     1305 |         79 |    79 |           1279 |            794.0 |    2 / 0 |         |
| s2026.06.30 | CLI  |  291/6306 (  4.6%) |         1801.20 |         1810.87 |               0.07 |        3612.14 |   1+  0+  0 |     4737 |         79 |    79 |            378 |            667.2 |    2 / 0 | TIMEOUT |


