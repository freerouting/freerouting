# Benchmarks for KiCad designs from the 2020 Design Automation Conference

Freerouting v1.9 used GUI with default settings and route optimizer turned off.

Freerouting v2.2 used CLI with default settings, both the route optimizer and API server turned off.
The command line used for testing is:
'-de .\tests\Issue508-DAC2020_bm??.dsn -do .\tests\Issue508-DAC2020_bm??.ses --router.optimizer.enabled=false --gui.enabled=false --api_server.enabled=false'

## Results
| Filename                  	| File size 	| Nets to route 	| Freerouting version 	| Unrouted nets 	| Clearance violations 	| Passes to complete 	| Time to complete 	| Memory allocated 	| Quality score 	|
|---------------------------	|----------:	|--------------:	|--------------------:	|--------------:	|---------------------:	|-------------------:	|-----------------:	|-----------------:	|--------------:	|
| Issue508-DAC2020_bm01.dsn 	|     31 kB 	|             ? 	|                v1.9 	|             0 	|                    ? 	|                214 	|       45 seconds 	|              N/A 	|           N/A 	|
| Issue508-DAC2020_bm07.dsn 	|     15 kB 	|             ? 	|                v1.9 	|             0 	|                    ? 	|                  4 	|        2 seconds 	|              N/A 	|           N/A 	|