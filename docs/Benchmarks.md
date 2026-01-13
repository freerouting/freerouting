# Benchmarks for KiCad designs from the 2020 Design Automation Conference

Freerouting v1.9 used GUI with default settings and route optimizer turned off.

Freerouting v2.2 used CLI with default settings, both the route optimizer and API server turned off.

The command line used for testing is:
'-de .\tests\Issue508-DAC2020_bm01.dsn -do .\tests\Issue508-DAC2020_bm01.ses --router.optimizer.enabled=false --gui.enabled=false --api_server.enabled=false --router.max_passes=500 --router.job_timeout="00:05:00"'

## Results
| Filename                  	| File size 	| Nets to route 	| Freerouting version 	| Unrouted nets 	| Clearance violations 	| Passes to complete 	| Time to complete 	| Memory allocated 	| Quality score 	| Run at 			|
|---------------------------	|----------:	|--------------:	|--------------------:	|--------------:	|---------------------:	|-------------------:	|-----------------:	|-----------------:	|--------------:	| --------------:	|
| Issue508-DAC2020_bm01.dsn 	|     31 kB 	|             ? 	|                v1.9 	|             0 	|                    ? 	|                214 	|       45 seconds 	|              N/A 	|           N/A 	| 					|
| Issue508-DAC2020_bm01.dsn 	|     31 kB 	|           195 	|                v2.2 	|            45 	|                    0 	|                 36 	|       5+ minutes 	|        228585 MB 	|        922,89 	| 					|
| Issue508-DAC2020_bm07.dsn 	|     15 kB 	|             ? 	|                v1.9 	|             0 	|                    ? 	|                  4 	|        2 seconds 	|              N/A 	|           N/A 	| 					|
| Issue508-DAC2020_bm07.dsn 	|     15 kB 	|            86 	|                v2.2 	|             0 	|                    0 	|                 24 	|       10 seconds 	|          5557 MB 	|        990,75 	|  2026-01-13 13:26 |
| Issue508-DAC2020_bm08.dsn 	|      6 kB 	|            25 	|                v2.2 	|             0 	|                    0 	|                  4 	|               1s 	|             0 MB 	|        996,40 	|  2026-01-13 13:33:05 |