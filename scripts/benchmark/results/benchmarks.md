# Freerouting Nightly Benchmarks Report
Generated on: 2026-08-20 13:23:22
System: AMD Ryzen 5 3600 6-Core Processor (6 Cores, 31.9 GB RAM)

This report lists the latest benchmark run results for each Freerouting version and fixture combination.

## Summary

### Summary Table (All Tiers Combined)
Comprehensive performance across all benchmark fixtures.

| Version | Fixtures | Clean (0 DRC)      | Fully-Routed       | Timeouts           | Failures           | Avg. Score |
| :------ | -------: | -----------------: | -----------------: | -----------------: | -----------------: | ---------: |
| 1.9.0   |      165 |   61/ 165 ( 37.0%) |   83/ 165 ( 50.3%) |   14/ 165 (  8.5%) |   14/ 165 (  8.5%) |  **966.3** |


### Tier A: Canary Gate
Fast-solving 2-layer boards (0 unrouted, 0 clearance violations expected).

| Version | Fixtures | Clean (0 DRC)      | Fully-Routed       | Timeouts           | Failures           | Avg. Score |
| :------ | -------: | -----------------: | -----------------: | -----------------: | -----------------: | ---------: |
| 1.9.0   |       24 |   21/  24 ( 87.5%) |   21/  24 ( 87.5%) |    0/  24 (  0.0%) |    0/  24 (  0.0%) |  **997.1** |


### Tier B: Routine Benchmarks
Standard 2-4 layer boards evaluated for routine optimization progress.

| Version | Fixtures | Clean (0 DRC)      | Fully-Routed       | Timeouts           | Failures           | Avg. Score |
| :------ | -------: | -----------------: | -----------------: | -----------------: | -----------------: | ---------: |
| 1.9.0   |      118 |   38/ 118 ( 32.2%) |   56/ 118 ( 47.5%) |    8/ 118 (  6.8%) |    8/ 118 (  6.8%) |  **955.7** |


### Tier C: Complex / Multi-Layer
Dense and 6+ layer boards requiring deeper pathfinding.

| Version | Fixtures | Clean (0 DRC)      | Fully-Routed       | Timeouts           | Failures           | Avg. Score |
| :------ | -------: | -----------------: | -----------------: | -----------------: | -----------------: | ---------: |
| 1.9.0   |       21 |    2/  21 (  9.5%) |    5/  21 ( 23.8%) |    5/  21 ( 23.8%) |    5/  21 ( 23.8%) |  **991.0** |


### Tier D: Extreme Stress / Diagnostic
High net-count and large surface-area stress boards.

| Version | Fixtures | Clean (0 DRC)      | Fully-Routed       | Timeouts           | Failures           | Avg. Score |
| :------ | -------: | -----------------: | -----------------: | -----------------: | -----------------: | ---------: |
| 1.9.0   |        2 |    0/   2 (  0.0%) |    1/   2 ( 50.0%) |    1/   2 ( 50.0%) |    1/   2 ( 50.0%) | **1000.0** |


## Group: [PCBench](../fixtures/PCBench)

### Fixture: [unrouted.dsn](../fixtures/PCBench/16x12-bits-I2C_I2C_Servo/unrouted.dsn)

Size: 23.7 kB · Layers: 2 · Nets: 24 · Components: 55 · Dimensions: 73.66 x 45.72 mm (33.68 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      16.28 |     3.32 |     19.60 |   0+ 20+  2 |        2 |          0 |   983 |       121 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/1Bitsy_1bitsy/unrouted.dsn)

Size: 37.7 kB · Layers: 4 · Nets: 20 · Components: 108 · Dimensions: 36.8 x 20.32 mm (7.48 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      63.86 |    12.08 |     75.94 |   0+ 20+  2 |        8 |          0 |   950 |       209 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/1-Wire-Wing-pcb_1-Wire_Wing/unrouted.dsn)

Size: 16.2 kB · Layers: 2 · Nets: 60 · Components: 23 · Dimensions: 69.85 x 21.59 mm (15.08 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       4.40 |     2.08 |      6.48 |   0+  5+  2 |        2 |          0 |   960 |        52 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/2d_conduction_sk9822-matrix/unrouted.dsn)

Size: 27.6 kB · Layers: 2 · Nets: 112 · Components: 133 · Dimensions: 91.44 x 91.44 mm (83.61 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      80.04 |    26.82 |    106.86 |   0+ 20+  2 |       58 |          0 |   849 |       171 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/4_digit_hex_display_4_digit_display/unrouted.dsn)

Size: 19.6 kB · Layers: 4 · Nets: 182 · Components: 46 · Dimensions: 62.23 x 21.59 mm (13.44 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      41.00 |     5.89 |     46.89 |   0+ 20+  2 |        2 |          0 |   981 |        71 |     8172.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/4N35-TTL-Serial-Optoisolator_4N35-TTL-Serial-Optoisolator/unrouted.dsn)

Size: 10.4 kB · Layers: 2 · Nets: 0 · Components: 17 · Dimensions: 40.64 x 22.86 mm (9.29 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       0.59 |     0.98 |      1.57 |   0+  2+  2 |        0 |          0 |  1000 |        72 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/4-port-usb-hub_4port-usb-hub/unrouted.dsn)

Size: 31.8 kB · Layers: 2 · Nets: 15 · Components: 48 · Dimensions: 64.77 x 46.99 mm (30.44 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       6.39 |    48.63 |     55.02 |   0+  5+  6 |        0 |          0 |  1000 |        70 |     8172.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/59pct_keyboard/unrouted.dsn)

Size: 55.2 kB · Layers: 2 · Nets: 0 · Components: 195 · Dimensions: 332.49 x 94.11 mm (312.91 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes           |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :-------------- |
| 1.9.0   | N/A  |                N/A |        N/A |        N/A |      N/A |       N/A |   0+  0+  0 |      N/A |        N/A |   N/A |         0 |        0.0 |    4 / 0 | FAILED, TIMEOUT |


### Fixture: [unrouted.dsn](../fixtures/PCBench/655_testboard/unrouted.dsn)

Size: 17.9 kB · Layers: 2 · Nets: 12 · Components: 30 · Dimensions: 39.17 x 62.92 mm (24.65 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       8.90 |     1.12 |     10.02 |   0+  7+  2 |        1 |          0 |   988 |        57 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/6N137-TTL-Serial-Optoisolator_6N137-TTL-Serial-Optoisolator/unrouted.dsn)

Size: 11.6 kB · Layers: 2 · Nets: 0 · Components: 19 · Dimensions: 40.64 x 22.86 mm (9.29 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       1.24 |     3.60 |      4.84 |   0+  4+  4 |        0 |          0 |  1000 |        72 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/6volt-5W-solar-cc_6vleadacidsolar/unrouted.dsn)

Size: 23.8 kB · Layers: 2 · Nets: 10 · Components: 65 · Dimensions: 50.0 x 50.0 mm (25.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       4.39 |    91.93 |     96.32 |   0+  4+ 10 |        0 |          0 |  1000 |       205 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/74Logic_SA_ADC_SA-ADC/unrouted.dsn)

Size: 86.8 kB · Layers: 2 · Nets: 87 · Components: 191 · Dimensions: 95.89 x 69.22 mm (66.38 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes           |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :-------------- |
| 1.9.0   | N/A  |                N/A |        N/A |     115.58 |      N/A |    115.58 |   0+ 18+  3 |      N/A |        N/A |   N/A |         0 |        0.0 |    5 / 0 | FAILED, TIMEOUT |


### Fixture: [unrouted.dsn](../fixtures/PCBench/8088_sbc2_pcb_8088_sbc2/unrouted.dsn)

Size: 69.5 kB · Layers: 2 · Nets: 82 · Components: 65 · Dimensions: 200.0 x 100.0 mm (200.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      40.05 |    48.17 |     88.22 |   0+ 20+  2 |        1 |          2 |   997 |       126 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/96boards-robomezzi_96boards-robomezzi/unrouted.dsn)

Size: 69.1 kB · Layers: 4 · Nets: 165 · Components: 119 · Dimensions: 85.0 x 54.0 mm (45.9 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      34.95 |    25.98 |     60.93 |   0+  4+  2 |        2 |         10 |   993 |       155 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/96boards-sensors_Sensors/unrouted.dsn)

Size: 45.6 kB · Layers: 2 · Nets: 14 · Components: 93 · Dimensions: 85.0 x 54.0 mm (45.9 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |     170.57 |    27.65 |    198.22 |   0+ 20+  2 |        5 |          0 |   983 |       245 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/a123-battery-integration_BCM/unrouted.dsn)

Size: 42.3 kB · Layers: 4 · Nets: 139 · Components: 67 · Dimensions: 66.04 x 73.66 mm (48.65 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       7.80 |    85.24 |     93.04 |   0+  6+  5 |        0 |          6 |  1000 |        93 |     8172.0 |   84 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ABOVISP_ABOVISP/unrouted.dsn)

Size: 15.5 kB · Layers: 2 · Nets: 3 · Components: 16 · Dimensions: 17.78 x 30.48 mm (5.42 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       0.88 |     3.67 |      4.55 |   0+  2+  6 |        0 |          0 |  1000 |        88 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/abus-cfa1000-display-grabber_acs-display-grabber/unrouted.dsn)

Size: 37.2 kB · Layers: 2 · Nets: 21 · Components: 47 · Dimensions: 100.0 x 100.0 mm (100.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      14.39 |   293.43 |    307.82 |   0+  5+ 14 |        0 |          0 |  1000 |       181 |     8172.0 |   33 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ADC-DAC-16bit_ADC-DAC-16bit/unrouted.dsn)

Size: 16.8 kB · Layers: 2 · Nets: 9 · Components: 17 · Dimensions: 23.7 x 22.0 mm (5.21 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       1.18 |     3.62 |      4.80 |   0+  2+  3 |        0 |          0 |  1000 |        48 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ADC-PCM4202_ADC-PCM4202/unrouted.dsn)

Size: 51.5 kB · Layers: 2 · Nets: 61 · Components: 237 · Dimensions: 100.0 x 100.0 mm (100.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      17.90 |    37.03 |     54.93 |   0+ 20+  2 |        2 |          0 |   996 |       217 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ADC-PCM4202-SE_ADC-PCM4202-SE/unrouted.dsn)

Size: 49.6 kB · Layers: 2 · Nets: 59 · Components: 230 · Dimensions: 100.0 x 100.0 mm (100.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      17.93 |    33.38 |     51.31 |   0+  6+  2 |        1 |          0 |   998 |       192 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/airqualitystation_hardware/unrouted.dsn)

Size: 34.8 kB · Layers: 2 · Nets: 18 · Components: 30 · Dimensions: 44.2 x 44.2 mm (19.54 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       2.47 |    40.72 |     43.19 |   0+  4+ 11 |        0 |          0 |  1000 |       147 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/akuhei_akuhei/unrouted.dsn)

Size: 14.9 kB · Layers: 2 · Nets: 5 · Components: 9 · Dimensions: 22.99 x 23.43 mm (5.39 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       2.34 |     0.35 |      2.69 |   0+ 20+  2 |        2 |          0 |   946 |       135 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Aleste-520EX_aleste/unrouted.dsn)

Size: 147.7 kB · Layers: 2 · Nets: 243 · Components: 313 · Dimensions: 334.01 x 193.04 mm (644.77 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes           |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :-------------- |
| 1.9.0   | N/A  |                N/A |        N/A |        N/A |      N/A |       N/A |   0+  0+  0 |      N/A |        N/A |   N/A |         0 |        0.0 |  170 / 0 | FAILED, TIMEOUT |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Amiga-A1012-PCB_Amiga-A1012/unrouted.dsn)

Size: 27.4 kB · Layers: 2 · Nets: 40 · Components: 24 · Dimensions: 73.75 x 78.25 mm (57.71 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       9.93 |   290.01 |    299.94 |   0+  7+ 16 |        0 |          0 |  1000 |       157 |     8172.0 |    3 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/AmpOne_dev-AmpOne/unrouted.dsn)

Size: 43.4 kB · Layers: 2 · Nets: 62 · Components: 164 · Dimensions: 100.0 x 100.0 mm (100.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      11.36 |   264.91 |    276.27 |   0+  5+  5 |        0 |          0 |  1000 |        62 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/analog_esr_meter_esr_meter_rev_a/unrouted.dsn)

Size: 117.7 kB · Layers: 2 · Nets: 23 · Components: 71 · Dimensions: 645.94 x 78.18 mm (505.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       1.82 |    35.90 |     37.72 |   0+  1+  2 |        0 |          4 |  1000 |       110 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/analog_esr_meter_esr_meter_rev_b/unrouted.dsn)

Size: 118.1 kB · Layers: 2 · Nets: 23 · Components: 88 · Dimensions: 645.94 x 78.18 mm (505.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       1.69 |    39.35 |     41.04 |   0+  1+  2 |        0 |         16 |  1000 |        53 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/analogevse_AnalogEVSE/unrouted.dsn)

Size: 27.8 kB · Layers: 2 · Nets: 42 · Components: 80 · Dimensions: 101.71 x 84.69 mm (86.14 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       2.14 |    10.63 |     12.77 |   0+  1+  2 |        0 |         12 |  1000 |       157 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/AnalogThermometer_AnalogThermometer/unrouted.dsn)

Size: 15.1 kB · Layers: 2 · Nets: 13 · Components: 19 · Dimensions: 25.4 x 25.4 mm (6.45 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       0.97 |     3.67 |      4.64 |   0+  3+  2 |        0 |          0 |  1000 |       115 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/android_debug_cable_android_debug_cable/unrouted.dsn)

Size: 12.9 kB · Layers: 2 · Nets: 17 · Components: 11 · Dimensions: 25.46 x 10.46 mm (2.66 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       3.87 |    24.67 |     28.54 |   0+ 13+  8 |        0 |         10 |  1000 |       165 |     8172.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/anima_MotorDrive/unrouted.dsn)

Size: 25 kB · Layers: 2 · Nets: 78 · Components: 62 · Dimensions: 54.61 x 63.5 mm (34.68 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      12.26 |     4.60 |     16.86 |   0+  7+  2 |        1 |          0 |   992 |        45 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/antdroid-board_antdroid-board/unrouted.dsn)

Size: 58.3 kB · Layers: 2 · Nets: 62 · Components: 60 · Dimensions: 99.45 x 53.72 mm (53.42 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes           |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :-------------- |
| 1.9.0   | N/A  |                N/A |        N/A |        N/A |      N/A |       N/A |   0+  0+  0 |      N/A |        N/A |   N/A |         0 |        0.0 |    2 / 0 | FAILED, TIMEOUT |


### Fixture: [unrouted.dsn](../fixtures/PCBench/apa102lantern_apa102-lantern-esp8266/unrouted.dsn)

Size: 39.7 kB · Layers: 2 · Nets: 22 · Components: 27 · Dimensions: 63.5 x 63.5 mm (40.32 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       1.15 |     3.57 |      4.72 |   0+  1+  2 |        0 |          0 |  1000 |        92 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/apa102lantern_apa102-lantern-side/unrouted.dsn)

Size: 28.4 kB · Layers: 2 · Nets: 14 · Components: 12 · Dimensions: 25.4 x 109.22 mm (27.74 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       1.28 |    15.02 |     16.30 |   0+  2+ 11 |        0 |          0 |  1000 |        72 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/APC_AtariPunkConsole/unrouted.dsn)

Size: 19.5 kB · Layers: 2 · Nets: 16 · Components: 30 · Dimensions: 90.0 x 43.0 mm (38.7 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       0.85 |     2.19 |      3.04 |   0+  1+  2 |        0 |          0 |  1000 |       144 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/APM-RPi-Shield_APM-RPi-Shield/unrouted.dsn)

Size: 28.7 kB · Layers: 2 · Nets: 36 · Components: 29 · Dimensions: 37.25 x 56.2 mm (20.93 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      29.90 |     3.11 |     33.01 |   0+ 20+  2 |        7 |          0 |   947 |        77 |     8172.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Apple-M0110-BT_Apple M0110/unrouted.dsn)

Size: 40.6 kB · Layers: 2 · Nets: 97 · Components: 132 · Dimensions: 275.28 x 97.92 mm (269.55 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       6.15 |    26.45 |     32.60 |   0+  2+  2 |        0 |          8 |  1000 |       115 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/arduino_arduino leds/unrouted.dsn)

Size: 34.3 kB · Layers: 2 · Nets: 0 · Components: 29 · Dimensions: 99.06 x 49.53 mm (49.06 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       1.60 |     7.31 |      8.91 |   0+  3+  4 |        0 |          0 |  1000 |       133 |     8172.0 |  446 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Arduino_Lipo_Storage_Discharger_Lipo_Storage_Discharger/unrouted.dsn)

Size: 42.1 kB · Layers: 2 · Nets: 31 · Components: 42 · Dimensions: 99.0 x 49.0 mm (48.51 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       1.70 |    12.17 |     13.87 |   0+  2+  5 |        0 |          0 |  1000 |        92 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ArduinoDueClone_ATSAM3X8EA/unrouted.dsn)

Size: 63 kB · Layers: 2 · Nets: 18 · Components: 75 · Dimensions: 160.0 x 100.0 mm (160.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      94.62 |     7.29 |    101.91 |   0+ 20+  2 |      104 |        137 |   571 |       209 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/arduino-led-driver_arduino-led-driver/unrouted.dsn)

Size: 37.5 kB · Layers: 2 · Nets: 57 · Components: 118 · Dimensions: 0.0 x 0.0 mm (0.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes   |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :------ |
| 1.9.0   | N/A  |                N/A |        N/A |      43.76 |   271.02 |    314.78 |   0+  9+  3 |        0 |          0 |  1000 |       173 |     8172.0 |    1 / 0 | TIMEOUT |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Arduino-Theremin_arduino-theremin-v1/unrouted.dsn)

Size: 24.8 kB · Layers: 2 · Nets: 3 · Components: 15 · Dimensions: 71.12 x 53.34 mm (37.94 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       0.85 |     2.73 |      3.58 |   0+  1+  4 |        0 |          0 |  1000 |        99 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/arf154_arf154/unrouted.dsn)

Size: 23.8 kB · Layers: 4 · Nets: 22 · Components: 30 · Dimensions: 18.29 x 46.23 mm (8.46 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      20.70 |     3.23 |     23.93 |   0+ 20+  2 |        1 |          2 |   990 |       182 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Aria_Aria/unrouted.dsn)

Size: 67.2 kB · Layers: 4 · Nets: 162 · Components: 91 · Dimensions: 86.06 x 27.0 mm (23.24 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      64.86 |    16.89 |     81.75 |   0+ 20+  2 |        2 |          0 |   991 |       254 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/AS5043-Encoder_sensor-board/unrouted.dsn)

Size: 12.5 kB · Layers: 2 · Nets: 8 · Components: 17 · Dimensions: 35.56 x 35.56 mm (12.65 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       0.72 |     3.76 |      4.48 |   0+  1+  7 |        0 |          0 |  1000 |        67 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ATmega32_ExploreUltraAvrDevKit__autosave-MCU_BaseBoard/unrouted.dsn)

Size: 118.5 kB · Layers: 2 · Nets: 139 · Components: 149 · Dimensions: 180.0 x 125.0 mm (225.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      39.52 |    44.01 |     83.53 |   0+ 20+  2 |        4 |          2 |   990 |       141 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ATmega32_ExploreUltraAvrDevKit_40pin_AVRMCU/unrouted.dsn)

Size: 18.3 kB · Layers: 2 · Nets: 0 · Components: 21 · Dimensions: 40.0 x 75.0 mm (30.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       3.08 |     1.57 |      4.65 |   0+ 20+  2 |        1 |          0 |   988 |       104 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ATMEGA328-Motor-Board_ATMEGA328_Motor_Board/unrouted.dsn)

Size: 74.8 kB · Layers: 2 · Nets: 46 · Components: 259 · Dimensions: 77.0 x 100.0 mm (77.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |     369.20 |   242.53 |    611.73 |   0+ 20+  3 |       16 |        128 |   967 |       376 |     8172.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/atmegax8-protoboard_atmegax8-protoboard/unrouted.dsn)

Size: 12.7 kB · Layers: 2 · Nets: 0 · Components: 7 · Dimensions: 21.34 x 54.1 mm (11.54 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       1.10 |     0.38 |      1.48 |   0+  2+  2 |        2 |          0 |   953 |        52 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Atmel-ICE-Header-Adapter_ice header adapter pcb/unrouted.dsn)

Size: 30 kB · Layers: 2 · Nets: 1 · Components: 13 · Dimensions: 48.26 x 48.26 mm (23.29 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       4.20 |    37.34 |     41.54 |   0+  3+  8 |        0 |          0 |  1000 |        66 |     8172.0 |    2 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/atmel-programmer_atmel_programmer/unrouted.dsn)

Size: 28 kB · Layers: 2 · Nets: 71 · Components: 12 · Dimensions: 76.2 x 58.42 mm (44.52 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       1.68 |     3.87 |      5.55 |   0+  2+  2 |        0 |          0 |  1000 |       152 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Atreus_54percent_rev/unrouted.dsn)

Size: 59.4 kB · Layers: 2 · Nets: 151 · Components: 191 · Dimensions: 277.0 x 110.75 mm (306.78 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      53.68 |   101.38 |    155.06 |   0+ 20+  2 |        2 |          0 |   997 |       187 |     8172.0 | 9784 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ATtiny461Breakout_ATTiny461DevBoard/unrouted.dsn)

Size: 15.7 kB · Layers: 2 · Nets: 18 · Components: 12 · Dimensions: 24.13 x 33.02 mm (7.97 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       1.01 |     1.57 |      2.58 |   0+  2+  2 |        0 |          0 |  1000 |        40 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/audio_relay_input_switch_relay_switch/unrouted.dsn)

Size: 28.8 kB · Layers: 2 · Nets: 5 · Components: 20 · Dimensions: 25.65 x 64.62 mm (16.58 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       1.15 |     4.56 |      5.71 |   0+  2+  6 |        0 |          0 |  1000 |       164 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/audprog_audprog_v2/unrouted.dsn)

Size: 25.7 kB · Layers: 2 · Nets: 19 · Components: 33 · Dimensions: 45.0 x 30.0 mm (13.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       3.44 |    42.14 |     45.58 |   0+  4+ 11 |        0 |          0 |  1000 |        82 |     8172.0 |   48 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/autohat-board_autohat-rig/unrouted.dsn)

Size: 80.2 kB · Layers: 2 · Nets: 75 · Components: 96 · Dimensions: 75.4 x 114.0 mm (85.96 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes           |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :-------------- |
| 1.9.0   | N/A  |                N/A |        N/A |      20.09 |      N/A |     20.09 |   0+  7+  7 |      N/A |        N/A |   N/A |         0 |        0.0 |    3 / 0 | FAILED, TIMEOUT |


### Fixture: [unrouted.dsn](../fixtures/PCBench/autohat-board_inverted-usd-adapter/unrouted.dsn)

Size: 3.9 kB · Layers: 2 · Nets: 0 · Components: 2 · Dimensions: 17.01 x 34.97 mm (5.95 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       0.35 |     0.25 |      0.60 |   0+  1+  2 |        0 |          0 |  1000 |        43 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/autohat-board_usd-adapter/unrouted.dsn)

Size: 3.9 kB · Layers: 2 · Nets: 0 · Components: 2 · Dimensions: 17.01 x 34.97 mm (5.95 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       0.75 |     2.53 |      3.28 |   0+  1+  3 |        0 |          0 |  1000 |        36 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Avem_Hardware_Avem_demo/unrouted.dsn)

Size: 60.1 kB · Layers: 2 · Nets: 41 · Components: 36 · Dimensions: 73.46 x 40.77 mm (29.95 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       4.41 |     4.03 |      8.44 |   0+  5+  2 |        1 |          0 |   988 |       181 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/avr_frs_shield_avr_frs/unrouted.dsn)

Size: 32.6 kB · Layers: 2 · Nets: 35 · Components: 34 · Dimensions: 68.58 x 53.34 mm (36.58 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       1.22 |     2.23 |      3.45 |   0+  1+  2 |        0 |          0 |  1000 |       175 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/avr_ledprojector_avr_ledprojection/unrouted.dsn)

Size: 22 kB · Layers: 2 · Nets: 0 · Components: 111 · Dimensions: 50.0 x 50.0 mm (25.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      13.40 |     8.09 |     21.49 |   0+ 20+  2 |        2 |          0 |   989 |       210 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/avr_ledprojector_avr_ledprojection-0402/unrouted.dsn)

Size: 21.4 kB · Layers: 2 · Nets: 0 · Components: 91 · Dimensions: 25.4 x 34.54 mm (8.77 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      22.86 |     9.29 |     32.15 |   0+ 20+  2 |        6 |          0 |   969 |       166 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/avr-divecomputer_dc/unrouted.dsn)

Size: 38 kB · Layers: 2 · Nets: 0 · Components: 60 · Dimensions: 101.6 x 96.52 mm (98.06 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes           |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :-------------- |
| 1.9.0   | N/A  |                N/A |        N/A |        N/A |      N/A |       N/A |   0+  0+  0 |      N/A |        N/A |   N/A |         0 |        0.0 |    6 / 0 | FAILED, TIMEOUT |


### Fixture: [unrouted.dsn](../fixtures/PCBench/avr-fuser-32_adapter/unrouted.dsn)

Size: 21.2 kB · Layers: 2 · Nets: 0 · Components: 19 · Dimensions: 159.38 x 79.38 mm (126.52 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      28.81 |     8.05 |     36.86 |   0+ 20+  2 |        1 |          0 |   995 |       131 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/avr-fuser-32_hvpp/unrouted.dsn)

Size: 54.8 kB · Layers: 2 · Nets: 0 · Components: 70 · Dimensions: 71.75 x 99.69 mm (71.53 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       6.63 |    26.43 |     33.06 |   0+  7+  5 |        0 |          0 |  1000 |       147 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/AVR-ISP_level-shifter_AVR-ISP_level-shifter/unrouted.dsn)

Size: 11.8 kB · Layers: 2 · Nets: 4 · Components: 27 · Dimensions: 44.0 x 20.0 mm (8.8 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       3.84 |     0.80 |      4.64 |   0+ 20+  2 |        1 |          0 |   985 |        47 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/AVR-ISP_pogo-plug_1.27mm_AVR-ISP_pogo-plug_1.27mm/unrouted.dsn)

Size: 7.3 kB · Layers: 2 · Nets: 0 · Components: 4 · Dimensions: 12.0 x 19.0 mm (2.28 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       0.49 |     1.50 |      1.99 |   0+  1+  2 |        0 |         18 |   999 |        39 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/AVR-Playground_hello_world/unrouted.dsn)

Size: 26.5 kB · Layers: 2 · Nets: 31 · Components: 4 · Dimensions: 50.8 x 52.07 mm (26.45 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       0.48 |     0.03 |      0.51 |   0+  2+  2 |        1 |          0 |   800 |        72 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/AVR-ZIF-Programmer_AVR-ZIF-Prog/unrouted.dsn)

Size: 50.6 kB · Layers: 2 · Nets: 36 · Components: 35 · Dimensions: 96.52 x 76.2 mm (73.55 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       3.57 |    22.43 |     26.00 |   0+  3+  4 |        0 |          0 |  1000 |       117 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/AzizLight_AzizLight/unrouted.dsn)

Size: 18.7 kB · Layers: 2 · Nets: 27 · Components: 55 · Dimensions: 70.1 x 29.21 mm (20.48 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       5.11 |   142.81 |    147.92 |   0+  4+ 11 |        0 |          0 |  1000 |       192 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/badge2015_badge2015/unrouted.dsn)

Size: 35.1 kB · Layers: 2 · Nets: 47 · Components: 57 · Dimensions: 38.1 x 0.0 mm (0.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      32.73 |    10.53 |     43.26 |   0+ 20+  2 |        7 |          8 |   941 |       248 |     8172.0 |  545 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/badge2016_Badge_init/unrouted.dsn)

Size: 59.8 kB · Layers: 2 · Nets: 7 · Components: 650 · Dimensions: 100.0 x 57.4 mm (57.4 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       1.59 |    18.71 |     20.30 |   0+  1+  5 |        0 |          8 |  1000 |       162 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/balena-rover-wide-hat_resin-rover/unrouted.dsn)

Size: 117.5 kB · Layers: 2 · Nets: 75 · Components: 116 · Dimensions: 85.0 x 58.0 mm (49.3 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes           |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :-------------- |
| 1.9.0   | N/A  |                N/A |        N/A |      19.30 |      N/A |     19.30 |   0+  8+  7 |      N/A |        N/A |   N/A |         0 |        0.0 |    2 / 0 | FAILED, TIMEOUT |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Baofeng-Interface_BaofengInterface/unrouted.dsn)

Size: 18.9 kB · Layers: 2 · Nets: 2 · Components: 69 · Dimensions: 25.4 x 25.4 mm (6.45 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       1.81 |     8.38 |     10.19 |   0+  1+  2 |        0 |          0 |  1000 |        72 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Baofeng-Interface_BaofengInterfaceIsolated/unrouted.dsn)

Size: 32.2 kB · Layers: 2 · Nets: 9 · Components: 144 · Dimensions: 39.12 x 44.7 mm (17.49 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       3.33 |    25.49 |     28.82 |   0+  1+  2 |        0 |          0 |  1000 |       137 |     8172.0 |  150 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/basic_esp_board_basic_esp_board/unrouted.dsn)

Size: 28.4 kB · Layers: 2 · Nets: 5 · Components: 27 · Dimensions: 66.94 x 44.45 mm (29.75 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       4.14 |     1.80 |      5.94 |   0+  5+  2 |        1 |          0 |   989 |        86 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/battmanpi_battmanpi/unrouted.dsn)

Size: 35.7 kB · Layers: 2 · Nets: 0 · Components: 80 · Dimensions: 65.0 x 56.0 mm (36.4 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       3.21 |    24.10 |     27.31 |   0+  2+  3 |        0 |          0 |  1000 |       155 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/BB-PWR-3608_BB-PWR-3608_revA/unrouted.dsn)

Size: 13.9 kB · Layers: 2 · Nets: 7 · Components: 18 · Dimensions: 15.19 x 11.75 mm (1.78 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       0.82 |     0.33 |      1.15 |   0+  4+  2 |        1 |          4 |   961 |        39 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/BB-PWR-8009_BB-PWR-8009_revA/unrouted.dsn)

Size: 18.9 kB · Layers: 2 · Nets: 5 · Components: 17 · Dimensions: 11.43 x 11.43 mm (1.31 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       1.63 |     0.13 |      1.76 |   0+ 20+  2 |        3 |          1 |   869 |        56 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/BB-PWR-8113_BB-PWR-8113_revA/unrouted.dsn)

Size: 24.4 kB · Layers: 2 · Nets: 7 · Components: 28 · Dimensions: 19.69 x 13.34 mm (2.63 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       0.77 |     0.39 |      1.16 |   0+  2+  2 |        3 |          2 |   917 |        43 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/BeaconBuddy_BeaconBuddy/unrouted.dsn)

Size: 33 kB · Layers: 2 · Nets: 37 · Components: 112 · Dimensions: 25.4 x 27.94 mm (7.1 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      24.51 |    46.73 |     71.24 |   0+ 20+  2 |        5 |        111 |   972 |       122 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/beast-phat_beast-phat/unrouted.dsn)

Size: 30.7 kB · Layers: 2 · Nets: 40 · Components: 19 · Dimensions: 65.0 x 69.5 mm (45.17 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       1.65 |     7.43 |      9.08 |   0+  4+  4 |        0 |          0 |  1000 |       180 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bee-light-measurement-matrix_bee-light-measurement-matrix/unrouted.dsn)

Size: 39.8 kB · Layers: 2 · Nets: 32 · Components: 54 · Dimensions: 59.69 x 65.02 mm (38.81 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       9.83 |     6.71 |     16.54 |   0+  6+  2 |        1 |          0 |   994 |       110 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/beer-gauge_beer-gauge/unrouted.dsn)

Size: 48.4 kB · Layers: 4 · Nets: 30 · Components: 74 · Dimensions: 69.85 x 95.25 mm (66.53 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes           |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :-------------- |
| 1.9.0   | N/A  |                N/A |        N/A |      17.11 |      N/A |     17.11 |   0+  5+  5 |      N/A |        N/A |   N/A |         0 |        0.0 |    0 / 0 | FAILED, TIMEOUT |


### Fixture: [unrouted.dsn](../fixtures/PCBench/beer-gauge_sensorboard/unrouted.dsn)

Size: 13.1 kB · Layers: 2 · Nets: 2 · Components: 16 · Dimensions: 31.75 x 38.1 mm (12.1 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       1.77 |    15.19 |     16.96 |   0+  6+  5 |        0 |         12 |  1000 |        37 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/beryl_rain_beryl_rain/unrouted.dsn)

Size: 14.6 kB · Layers: 2 · Nets: 5 · Components: 18 · Dimensions: 26.67 x 49.53 mm (13.21 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       2.05 |     1.05 |      3.10 |   0+  5+  2 |        1 |          0 |   981 |        84 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/BGM111-External-Programmer_BGM111_Programmer/unrouted.dsn)

Size: 11.1 kB · Layers: 2 · Nets: 25 · Components: 4 · Dimensions: 31.98 x 21.77 mm (6.96 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       1.47 |     5.79 |      7.26 |   0+  5+  5 |        0 |          0 |  1000 |       104 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bikedar_bikedar/unrouted.dsn)

Size: 53.1 kB · Layers: 4 · Nets: 17 · Components: 52 · Dimensions: 50.0 x 50.0 mm (25.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       7.39 |    76.01 |     83.40 |   0+  6+  6 |        0 |          7 |  1000 |        37 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/BirdAttractor_BirdAttractor_RevA/unrouted.dsn)

Size: 22.5 kB · Layers: 2 · Nets: 17 · Components: 15 · Dimensions: 48.26 x 48.26 mm (23.29 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       0.83 |     3.27 |      4.10 |   0+  2+  6 |        0 |          2 |  1000 |        80 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/BirdAttractor_BirdAttractor_RevC/unrouted.dsn)

Size: 56.6 kB · Layers: 2 · Nets: 11 · Components: 45 · Dimensions: 76.2 x 53.34 mm (40.65 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       3.64 |    41.98 |     45.62 |   0+  4+  7 |        0 |          0 |  1000 |       109 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/BirthdayCakeKeyboard_10Key/unrouted.dsn)

Size: 34.6 kB · Layers: 2 · Nets: 50 · Components: 65 · Dimensions: 73.0 x 102.5 mm (74.83 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes   |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :------ |
| 1.9.0   | N/A  |                N/A |        N/A |      36.06 |   281.54 |    317.60 |   0+  7+ 10 |        0 |          0 |  1000 |       162 |     8172.0 |    0 / 0 | TIMEOUT |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Biscay_Blueeye_mcu/unrouted.dsn)

Size: 22.4 kB · Layers: 2 · Nets: 14 · Components: 19 · Dimensions: 50.0 x 50.0 mm (25.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       6.23 |    47.50 |     53.73 |   0+  4+  9 |        0 |          0 |  1000 |        79 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Biscay_Blueeye_sipm/unrouted.dsn)

Size: 34.3 kB · Layers: 4 · Nets: 84 · Components: 145 · Dimensions: 50.0 x 50.0 mm (25.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes           |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :-------------- |
| 1.9.0   | N/A  |                N/A |        N/A |      36.05 |      N/A |     36.05 |   0+  9+ 12 |      N/A |        N/A |   N/A |         0 |        0.0 |    0 / 0 | FAILED, TIMEOUT |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Biscay_Blueeye_sipm-comp/unrouted.dsn)

Size: 37.7 kB · Layers: 2 · Nets: 12 · Components: 57 · Dimensions: 50.0 x 50.0 mm (25.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      16.47 |   293.23 |    309.70 |   0+  9+ 10 |        0 |          0 |  1000 |       220 |     8172.0 |   73 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Biscay_Blueeye_sipm-fpga/unrouted.dsn)

Size: 31.9 kB · Layers: 2 · Nets: 6 · Components: 24 · Dimensions: 50.0 x 50.0 mm (25.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      10.10 |     6.09 |     16.19 |   0+  6+  2 |        1 |          0 |   993 |        86 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bishboria_ErgoDox/unrouted.dsn)

Size: 38.2 kB · Layers: 2 · Nets: 0 · Components: 65 · Dimensions: 182.84 x 158.87 mm (290.48 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes           |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :-------------- |
| 1.9.0   | N/A  |                N/A |        N/A |        N/A |      N/A |       N/A |   0+  0+  0 |      N/A |        N/A |   N/A |         0 |        0.0 |    2 / 0 | FAILED, TIMEOUT |


### Fixture: [unrouted.dsn](../fixtures/PCBench/BITxo_BITxo/unrouted.dsn)

Size: 20.7 kB · Layers: 2 · Nets: 7 · Components: 30 · Dimensions: 73.39 x 62.61 mm (45.95 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       1.06 |     7.40 |      8.46 |   0+  2+  7 |        0 |          2 |  1000 |       197 |     8172.0 |  133 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/blackmagic-isolated_mmp/unrouted.dsn)

Size: 27.2 kB · Layers: 2 · Nets: 39 · Components: 39 · Dimensions: 15.25 x 50.0 mm (7.62 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      15.42 |    14.60 |     30.02 |   0+ 20+  2 |        2 |          1 |   980 |       165 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/BLDC-controller_BLDC_controller/unrouted.dsn)

Size: 51.6 kB · Layers: 4 · Nets: 20 · Components: 262 · Dimensions: 49.0 x 32.0 mm (15.68 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |     164.44 |   457.76 |    622.20 |   0+ 20+  2 |       19 |        967 |   955 |       165 |     8172.0 |    4 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bldc-gimbal-1d_gimbal-board/unrouted.dsn)

Size: 23.1 kB · Layers: 2 · Nets: 12 · Components: 34 · Dimensions: 81.0 x 25.0 mm (20.25 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       5.46 |    47.19 |     52.65 |   0+  6+  7 |        0 |          0 |  1000 |       108 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bldc-gimbal-1d_r1_gimbal-board/unrouted.dsn)

Size: 25.8 kB · Layers: 2 · Nets: 12 · Components: 34 · Dimensions: 79.0 x 45.0 mm (35.55 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       4.47 |    38.72 |     43.19 |   0+  4+  8 |        0 |          0 |  1000 |       199 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Blink-Eras_AVR_ISP_Pogo/unrouted.dsn)

Size: 4.7 kB · Layers: 2 · Nets: 0 · Components: 4 · Dimensions: 17.78 x 22.86 mm (4.06 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       0.36 |     0.22 |      0.58 |   0+  2+  2 |        0 |          0 |  1000 |        36 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Blink-Eras_Blink Eras/unrouted.dsn)

Size: 13.6 kB · Layers: 2 · Nets: 8 · Components: 16 · Dimensions: 27.94 x 20.32 mm (5.68 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       1.87 |     4.83 |      6.70 |   0+  5+  2 |        0 |          6 |  1000 |       129 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/blink-errr_blink-errr/unrouted.dsn)

Size: 21.3 kB · Layers: 2 · Nets: 5 · Components: 10 · Dimensions: 17.0 x 0.0 mm (0.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       0.47 |     2.09 |      2.56 |   0+  1+  3 |        0 |          0 |  1000 |        60 |     8172.0 |   22 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/blinktronicator_.kicad_pcb/unrouted.dsn)

Size: 15.8 kB · Layers: 2 · Nets: 0 · Components: 37 · Dimensions: 23.57 x 23.55 mm (5.55 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      29.43 |     3.17 |     32.60 |   0+ 20+  2 |        4 |         14 |   950 |        66 |     8172.0 |  126 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/blinky-badge_blinky/unrouted.dsn)

Size: 21.3 kB · Layers: 2 · Nets: 10 · Components: 18 · Dimensions: 40.0 x 39.88 mm (15.95 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       3.64 |    19.53 |     23.17 |   0+  4+  2 |        1 |        180 |   988 |        68 |     8172.0 |   62 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/BlueBerry-Zero_blueberry/unrouted.dsn)

Size: 34.5 kB · Layers: 2 · Nets: 63 · Components: 50 · Dimensions: 65.0 x 30.0 mm (19.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       5.59 |   152.46 |    158.05 |   0+  5+  9 |        0 |          5 |  1000 |       145 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/BML-Badges_BML_01/unrouted.dsn)

Size: 21.2 kB · Layers: 2 · Nets: 0 · Components: 21 · Dimensions: 43.18 x 45.72 mm (19.74 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       0.52 |     1.47 |      1.99 |   0+  1+  2 |        0 |          0 |  1000 |        99 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/BML-Badges_BML-Badges/unrouted.dsn)

Size: 22.8 kB · Layers: 2 · Nets: 9 · Components: 21 · Dimensions: 91.44 x 38.1 mm (34.84 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       0.64 |     2.31 |      2.95 |   0+  2+  2 |        0 |          0 |  1000 |        89 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bms-8s50-ic_bms-8s50-ic/unrouted.dsn)

Size: 118.8 kB · Layers: 2 · Nets: 66 · Components: 168 · Dimensions: 110.0 x 60.0 mm (66.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes           |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :-------------- |
| 1.9.0   | N/A  |                N/A |        N/A |        N/A |      N/A |       N/A |   0+ 18+  0 |      N/A |        N/A |   N/A |         0 |        0.0 |    1 / 0 | FAILED, TIMEOUT |


### Fixture: [unrouted.dsn](../fixtures/PCBench/BMS-bq76940_EvaluationsBoard/unrouted.dsn)

Size: 33.8 kB · Layers: 2 · Nets: 0 · Components: 134 · Dimensions: 67.94 x 75.57 mm (51.34 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      20.36 |    17.95 |     38.31 |   0+ 20+  2 |        4 |          3 |   985 |       156 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bmw-ibus-bluetooth_bmw_bt_cdcemu_analog/unrouted.dsn)

Size: 70.7 kB · Layers: 2 · Nets: 18 · Components: 51 · Dimensions: 49.0 x 30.0 mm (14.7 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       1.02 |     3.19 |      4.21 |   0+  1+  2 |        0 |          4 |  1000 |       160 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bmw-ibus-bluetooth_bmw_bt_cdcemu_digital/unrouted.dsn)

Size: 48 kB · Layers: 2 · Nets: 34 · Components: 58 · Dimensions: 60.0 x 40.0 mm (24.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       5.57 |   139.74 |    145.31 |   0+  4+ 16 |        0 |          4 |  1000 |       139 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/board_armjtag_pmod_compatible_armjtag-pmod/unrouted.dsn)

Size: 12.5 kB · Layers: 2 · Nets: 3 · Components: 4 · Dimensions: 39.88 x 20.32 mm (8.1 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       1.49 |     0.21 |      1.70 |   0+ 20+  2 |        1 |          0 |   965 |        51 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Board-RZA1L_BoardRZA1/unrouted.dsn)

Size: 108 kB · Layers: 4 · Nets: 18 · Components: 154 · Dimensions: 85.47 x 79.88 mm (68.27 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |     598.73 |   139.68 |    738.41 |   0+ 20+  2 |        4 |         46 |   992 |       392 |     8172.0 |    4 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/boards_shift-register-demo-v2/unrouted.dsn)

Size: 51.5 kB · Layers: 2 · Nets: 19 · Components: 36 · Dimensions: 104.14 x 35.56 mm (37.03 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       1.90 |     3.55 |      5.45 |   0+  2+  2 |        0 |          0 |  1000 |       152 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/boatcontrol_CommonCathode60A/unrouted.dsn)

Size: 29.7 kB · Layers: 4 · Nets: 0 · Components: 18 · Dimensions: 153.0 x 114.0 mm (174.42 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       5.00 |    39.63 |     44.63 |   0+  3+  2 |       99 |        256 |   218 |        69 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/boatcontrol_NonLatchingNO30A/unrouted.dsn)

Size: 41 kB · Layers: 4 · Nets: 32 · Components: 27 · Dimensions: 153.0 x 114.0 mm (174.42 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      65.61 |     8.42 |     74.03 |   0+ 20+  2 |       24 |          0 |   724 |       180 |     8172.0 |    4 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bobc_control_panel/unrouted.dsn)

Size: 24.3 kB · Layers: 2 · Nets: 0 · Components: 35 · Dimensions: 91.0 x 73.0 mm (66.43 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       4.93 |    16.06 |     20.99 |   0+  6+  3 |        0 |          0 |  1000 |        38 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bobc_LCD-panel-adapter-lvc/unrouted.dsn)

Size: 12.1 kB · Layers: 2 · Nets: 0 · Components: 13 · Dimensions: 40.64 x 48.26 mm (19.61 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       6.72 |     0.49 |      7.21 |   0+ 20+  2 |        1 |          0 |   981 |       132 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bobc_led_clock/unrouted.dsn)

Size: 60.7 kB · Layers: 2 · Nets: 0 · Components: 65 · Dimensions: 100.0 x 50.0 mm (50.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      20.01 |     6.94 |     26.95 |   0+ 20+  2 |        1 |          2 |   994 |        43 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bobc_matrix_clock/unrouted.dsn)

Size: 20.7 kB · Layers: 2 · Nets: 93 · Components: 36 · Dimensions: 100.0 x 98.0 mm (98.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      29.82 |    12.48 |     42.30 |   0+ 10+  2 |        2 |          0 |   989 |       151 |     8172.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bobc_mbeduinopresso/unrouted.dsn)

Size: 49.4 kB · Layers: 2 · Nets: 0 · Components: 37 · Dimensions: 100.0 x 87.0 mm (87.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      27.96 |     7.32 |     35.28 |   0+ 20+  2 |        2 |          0 |   989 |       147 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bobc_MS-F100/unrouted.dsn)

Size: 32.8 kB · Layers: 2 · Nets: 0 · Components: 33 · Dimensions: 50.8 x 17.78 mm (9.03 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      23.29 |     2.85 |     26.14 |   0+ 20+  2 |        5 |          0 |   956 |        49 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Box0-hv-analog-breakoutboard_breakout/unrouted.dsn)

Size: 24.2 kB · Layers: 2 · Nets: 28 · Components: 57 · Dimensions: 70.0 x 50.0 mm (35.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      14.55 |     0.93 |     15.48 |   0+  3+  2 |      134 |         12 |   251 |       154 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bpnode-bb_BPnode-BB/unrouted.dsn)

Size: 18 kB · Layers: 2 · Nets: 1 · Components: 10 · Dimensions: 19.81 x 49.28 mm (9.76 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       5.64 |     1.15 |      6.79 |   0+ 20+  2 |        2 |          2 |   964 |        73 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/breakout-boards_50-to-100/unrouted.dsn)

Size: 4.6 kB · Layers: 2 · Nets: 0 · Components: 2 · Dimensions: 15.24 x 17.78 mm (2.71 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       0.78 |     1.63 |      2.41 |   0+  3+  5 |        0 |          0 |  1000 |       191 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/breakout-boards_avr-isp-x2/unrouted.dsn)

Size: 3.3 kB · Layers: 2 · Nets: 6 · Components: 2 · Dimensions: 8.89 x 11.43 mm (1.02 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       0.21 |     0.23 |      0.44 |   0+  1+  2 |        0 |          0 |  1000 |        74 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/breakout-boards_esp8266-jtag/unrouted.dsn)

Size: 11.8 kB · Layers: 2 · Nets: 2 · Components: 14 · Dimensions: 30.48 x 28.45 mm (8.67 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       1.52 |     4.33 |      5.85 |   0+  2+  2 |        0 |          0 |  1000 |        64 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/breakout-boards_swd-and-uart/unrouted.dsn)

Size: 4.8 kB · Layers: 2 · Nets: 4 · Components: 3 · Dimensions: 12.7 x 25.4 mm (3.23 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       1.19 |     0.90 |      2.09 |   0+  5+  2 |        0 |          0 |  1000 |        76 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/breakout-boards_swd-to-wires/unrouted.dsn)

Size: 5.2 kB · Layers: 2 · Nets: 1 · Components: 2 · Dimensions: 12.7 x 13.97 mm (1.77 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       0.70 |     0.80 |      1.50 |   0+  3+  2 |        0 |          0 |  1000 |        39 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/breakout-boards_usb-5v-3v3/unrouted.dsn)

Size: 8.6 kB · Layers: 2 · Nets: 5 · Components: 10 · Dimensions: 25.0 x 18.0 mm (4.5 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       0.44 |     0.86 |      1.30 |   0+  1+  2 |        0 |          1 |  1000 |        75 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bristle_bot_light_follow_bristle_bot/unrouted.dsn)

Size: 24.5 kB · Layers: 2 · Nets: 0 · Components: 17 · Dimensions: 50.04 x 35.05 mm (17.54 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       0.60 |     0.55 |      1.15 |   0+  2+  2 |        1 |          0 |   961 |        68 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Brushless_ESC_Brushless_ESC/unrouted.dsn)

Size: 40.2 kB · Layers: 2 · Nets: 26 · Components: 77 · Dimensions: 63.0 x 36.0 mm (22.68 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       9.13 |     6.87 |     16.00 |   0+  6+  2 |        1 |          0 |   994 |       134 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/BrushlessESC_esc/unrouted.dsn)

Size: 41 kB · Layers: 2 · Nets: 29 · Components: 79 · Dimensions: 50.04 x 60.45 mm (30.25 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes           |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :-------------- |
| 1.9.0   | N/A  |                N/A |        N/A |      25.51 |      N/A |     25.51 |   0+  7+  3 |      N/A |        N/A |   N/A |         0 |        0.0 |    0 / 0 | FAILED, TIMEOUT |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bt-tnc_bttnc2/unrouted.dsn)

Size: 63.5 kB · Layers: 2 · Nets: 95 · Components: 105 · Dimensions: 70.0 x 40.01 mm (28.01 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      65.14 |    21.53 |     86.67 |   0+ 20+  2 |        7 |         55 |   971 |       119 |     8172.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bt-tnc_tnc/unrouted.dsn)

Size: 38.7 kB · Layers: 2 · Nets: 0 · Components: 55 · Dimensions: 56.57 x 31.0 mm (17.54 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       5.75 |    47.47 |     53.22 |   0+ 10+  7 |        0 |          2 |  1000 |       188 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bullion_bullion/unrouted.dsn)

Size: 36.9 kB · Layers: 2 · Nets: 38 · Components: 21 · Dimensions: 45.14 x 33.54 mm (15.14 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      28.34 |     1.47 |     29.81 |   0+ 20+  2 |       20 |         32 |   655 |       125 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bumps_bumps/unrouted.dsn)

Size: 86.3 kB · Layers: 2 · Nets: 71 · Components: 138 · Dimensions: 111.12 x 65.41 mm (72.68 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      71.40 |   111.65 |    183.05 |   0+ 20+  3 |        1 |        520 |   997 |       146 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/busblaster-to-swd_busblaster-to-swd/unrouted.dsn)

Size: 14.5 kB · Layers: 2 · Nets: 2 · Components: 7 · Dimensions: 22.0 x 35.0 mm (7.7 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       1.11 |     3.87 |      4.98 |   0+  1+  4 |        0 |          0 |  1000 |       103 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/bypass_crossmix_bypass_crossmix/unrouted.dsn)

Size: 26.5 kB · Layers: 2 · Nets: 33 · Components: 61 · Dimensions: 70.0 x 50.0 mm (35.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       8.54 |     2.63 |     11.17 |   0+ 20+  2 |       12 |          0 |   902 |        53 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/CAL430FR_CAL430F/unrouted.dsn)

Size: 32.1 kB · Layers: 2 · Nets: 11 · Components: 30 · Dimensions: 36.0 x 36.0 mm (12.96 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      63.52 |     2.70 |     66.22 |   0+ 20+  2 |        9 |          0 |   902 |       159 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/CAL430FR_CAL430F_watch/unrouted.dsn)

Size: 9.5 kB · Layers: 2 · Nets: 18 · Components: 8 · Dimensions: 36.0 x 45.5 mm (16.38 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       1.56 |     2.24 |      3.80 |   0+  7+  2 |        0 |          0 |  1000 |        75 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Camera-Modules_LG-G2-Camera-Shim/unrouted.dsn)

Size: 10.3 kB · Layers: 2 · Nets: 0 · Components: 4 · Dimensions: 18.54 x 9.65 mm (1.79 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       6.79 |     0.45 |      7.24 |   0+ 20+  2 |        2 |          0 |   953 |        59 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Camera-Modules_LG-G2-G3-13M-Breakout/unrouted.dsn)

Size: 12.2 kB · Layers: 2 · Nets: 1 · Components: 8 · Dimensions: 45.72 x 68.58 mm (31.35 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       2.41 |    18.88 |     21.29 |   0+  2+  7 |        0 |          0 |  1000 |        68 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/can_firewall_hardware_CAN_Firewall/unrouted.dsn)

Size: 68.4 kB · Layers: 2 · Nets: 78 · Components: 80 · Dimensions: 68.0 x 58.0 mm (39.44 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      47.67 |    42.85 |     90.52 |   0+ 20+  2 |        1 |         18 |   995 |        60 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/CANadapter_CANadapter/unrouted.dsn)

Size: 23.5 kB · Layers: 2 · Nets: 25 · Components: 28 · Dimensions: 93.98 x 22.86 mm (21.48 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       2.69 |     1.14 |      3.83 |   0+ 20+  2 |        4 |          4 |   941 |       168 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/CapPCB_CapPcb/unrouted.dsn)

Size: 14.5 kB · Layers: 2 · Nets: 2 · Components: 7 · Dimensions: 26.42 x 12.7 mm (3.36 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       2.01 |    32.47 |     34.48 |   0+  1+  2 |        0 |          8 |   999 |        52 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/cap-soil-moisture-v2_soil-moisture2x4/unrouted.dsn)

Size: 41.7 kB · Layers: 2 · Nets: 15 · Components: 21 · Dimensions: 44.85 x 44.83 mm (20.11 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       0.81 |     2.06 |      2.87 |   0+  2+  4 |        0 |          0 |  1000 |       115 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/card_card/unrouted.dsn)

Size: 11.3 kB · Layers: 2 · Nets: 7 · Components: 10 · Dimensions: 55.0 x 85.0 mm (46.75 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       3.19 |     0.60 |      3.79 |   0+  4+  2 |        4 |         25 |   874 |        75 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/CATS_PiHat_v2/unrouted.dsn)

Size: 39.4 kB · Layers: 2 · Nets: 34 · Components: 36 · Dimensions: 64.0 x 55.0 mm (35.2 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       5.44 |    56.66 |     62.10 |   0+  4+  8 |        0 |          0 |  1000 |        89 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/cat-trainer_feather32u4_mma8452_pcb/unrouted.dsn)

Size: 18.9 kB · Layers: 2 · Nets: 28 · Components: 11 · Dimensions: 56.0 x 57.0 mm (31.92 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       1.24 |     1.94 |      3.18 |   0+  3+  2 |        0 |          4 |  1000 |        33 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/cat-trainer_teensy_base_pcb/unrouted.dsn)

Size: 41.7 kB · Layers: 2 · Nets: 20 · Components: 28 · Dimensions: 69.85 x 52.07 mm (36.37 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       4.57 |     2.04 |      6.61 |   0+ 20+  2 |        1 |          0 |   985 |        84 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/C-BISCUIT_buck-reg-5v/unrouted.dsn)

Size: 21.6 kB · Layers: 2 · Nets: 2 · Components: 32 · Dimensions: 77.6 x 31.8 mm (24.68 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       2.42 |    20.93 |     23.35 |   0+  3+  7 |        0 |          0 |  1000 |        40 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/C-BISCUIT_crowbar/unrouted.dsn)

Size: 14.7 kB · Layers: 2 · Nets: 3 · Components: 13 · Dimensions: 13.0 x 37.2 mm (4.84 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       0.50 |     2.65 |      3.15 |   0+  2+  5 |        0 |          0 |  1000 |       204 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/cdm324_backpack_cdm324/unrouted.dsn)

Size: 14.8 kB · Layers: 2 · Nets: 13 · Components: 29 · Dimensions: 25.0 x 25.0 mm (6.25 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       1.91 |     7.61 |      9.52 |   0+  4+  5 |        0 |          0 |  1000 |       152 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Cherry-Mx-Bitboard_Cherry Mx Bitboard/unrouted.dsn)

Size: 4.6 kB · Layers: 2 · Nets: 1 · Components: 12 · Dimensions: 19.05 x 19.05 mm (3.63 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       0.29 |     0.25 |      0.54 |   0+  1+  2 |        0 |          0 |  1000 |        51 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ChirpHardware_chirp/unrouted.dsn)

Size: 24.4 kB · Layers: 4 · Nets: 38 · Components: 40 · Dimensions: 53.4 x 49.0 mm (26.17 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       4.18 |    94.69 |     98.87 |   0+  4+ 10 |        0 |          6 |  1000 |       218 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/ciurlys_ciurlys/unrouted.dsn)

Size: 14.9 kB · Layers: 2 · Nets: 9 · Components: 17 · Dimensions: 41.0 x 12.0 mm (4.92 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      10.71 |     0.27 |     10.98 |   0+ 20+  2 |       11 |          2 |   686 |       177 |     8172.0 |    1 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/Class_D_Amp_class_D_ampl/unrouted.dsn)

Size: 83.5 kB · Layers: 2 · Nets: 59 · Components: 89 · Dimensions: 140.5 x 79.0 mm (111.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      39.31 |    10.83 |     50.14 |   0+ 20+  2 |       14 |          8 |   926 |       153 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/clock_cb2/unrouted.dsn)

Size: 34.1 kB · Layers: 2 · Nets: 34 · Components: 41 · Dimensions: 110.49 x 38.1 mm (42.1 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |      12.46 |     2.93 |     15.39 |   0+ 20+  2 |        5 |          0 |   956 |       125 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/clock_lcdb4/unrouted.dsn)

Size: 12.9 kB · Layers: 2 · Nets: 2 · Components: 11 · Dimensions: 105.41 x 33.02 mm (34.81 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       3.96 |    23.18 |     27.14 |   0+  3+  5 |        0 |          0 |  1000 |        79 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/clunet-switch1_switch/unrouted.dsn)

Size: 47.5 kB · Layers: 2 · Nets: 28 · Components: 36 · Dimensions: 0.0 x 0.0 mm (0.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       2.70 |    38.83 |     41.53 |   0+  5+  4 |        0 |          3 |  1000 |       193 |     8172.0 |    0 / 0 |       |


### Fixture: [unrouted.dsn](../fixtures/PCBench/cobwebb-junction-box_CobwebbJunctionBox/unrouted.dsn)

Size: 10.8 kB · Layers: 2 · Nets: 9 · Components: 8 · Dimensions: 150.0 x 50.0 mm (75.0 cm²) · CAD: KiCad's Pcbnew (v)

| Version | Mode | Fanout             | Fanout (s) | Router (s) | Opt. (s) | Total (s) | Passes      | Unrouted | Violations | Score | Heap (MB) | Alloc (GB) | Warn/Err | Notes |
| :------ | :--- | -----------------: | ---------: | ---------: | -------: | --------: | ----------: | -------: | ---------: | ----: | --------: | ---------: | -------: | :---- |
| 1.9.0   | N/A  |                N/A |        N/A |       0.43 |     1.51 |      1.94 |   0+  1+  2 |        0 |          0 |  1000 |        51 |     8172.0 |    0 / 0 |       |


