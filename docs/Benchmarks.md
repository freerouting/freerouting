# Freerouting Benchmarks

## Test Environment

Tests were conducted on a desktop PC with the following specifications:

* **CPU:** AMD Ryzen 5 3600 (6-core, 12-thread)
* **RAM:** 32 GB
* **OS:** Windows 11 (64-bit)

## Methodology

**Freerouting v1.9**

* **Interface:** GUI
* **Settings:** Default (Route optimizer: **Off**)

**Freerouting v2.2**

* **Interface:** CLI
* **Settings:** Default (Route optimizer: **Off**, API server: **Off**)

### Command Line Execution

The following command line arguments were used for the v2.2 tests:

```bash
-de .\tests\Issue508-DAC2020_bm01.dsn \
-do .\tests\Issue508-DAC2020_bm01.ses \
--router.optimizer.enabled=false \
--gui.enabled=false \
--api_server.enabled=false \
--router.max_passes=500 \
--router.job_timeout="00:05:00"

```

## Results
| Filename                             	| File size 	| Nets to route 	| Freerouting version 	| Unrouted nets 	| Clearance violations 	| Passes to complete 	| Time to complete 	| Total allocation 	| Quality score 	| Run at              	|
|--------------------------------------	|-----------	|---------------	|---------------------	|---------------	|----------------------	|--------------------	|------------------	|------------------	|---------------	|---------------------	|
| Issue508-DAC2020_bm01.dsn            	| 31 kB     	| 195           	| v1.9 (GUI)          	| 0             	| ?                    	| 214                	| 45 seconds       	| N/A              	| N/A           	|                     	|
| Issue508-DAC2020_bm01.dsn            	| 31 kB     	| 195           	| v2.2 (CLI)          	| 45            	| 0                    	| 36                 	| 5+ minutes       	| 228585 MB        	| 922,89        	| 2026-01-13          	|
| Issue508-DAC2020_bm01.dsn            	| 31 kB     	| 195           	| v2.2 (CLI)          	| 42            	| 0                    	| 111                	| 5+ minutes       	| 322231 MB        	| 937.72        	| 2026-01-16          	|
| Issue508-DAC2020_bm01.dsn            	| 31 kB     	| 195           	| v2.2 (GUI)          	| 42            	| 0                    	| 97                 	| 5+ minutes       	| ?                	| 937.72        	| 2026-01-16          	|
| Issue508-DAC2020_bm02.dsn            	| ?         	| ?             	| v1.9                	| 0             	| ?                    	| ?                  	| ?                	| N/A              	| N/A           	|                     	|
| Issue508-DAC2020_bm04.dsn            	| ?         	| ?             	| v1.9                	| 0             	| ?                    	| ?                  	| ?                	| N/A              	| N/A           	|                     	|
| Issue508-DAC2020_bm05.dsn            	| ?         	| ?             	| v1.9                	| 46            	| ?                    	| ?                  	| ?                	| N/A              	| N/A           	|                     	|
| Issue508-DAC2020_bm05.dsn            	| 17 kB     	| 107           	| v2.2                	| 51            	| 0                    	| 244                	| 5+ minutes       	| 242909 MB        	| 806,92        	| 2026-01-13 13:41:49 	|
| Issue508-DAC2020_bm06.dsn            	| ?         	| ?             	| v1.9                	| 9             	| ?                    	| ?                  	| ?                	| N/A              	| N/A           	|                     	|
| Issue508-DAC2020_bm06.dsn            	| 23 kB     	| 98            	| v2.2                	| 8             	| 0                    	| 500+               	| 3m 24s           	| 172840 MB        	| 973,07        	| 2026-01-13 13:46:53 	|
| Issue508-DAC2020_bm07.dsn            	| 15 kB     	| 86            	| v1.9                	| 0             	| ?                    	| 4                  	| 2 seconds        	| N/A              	| N/A           	|                     	|
| Issue508-DAC2020_bm07.dsn            	| 15 kB     	| 86            	| v2.2                	| 0             	| 0                    	| 24                 	| 10 seconds       	| 5557 MB          	| 990,75        	| 2026-01-13 13:26:00 	|
| Issue508-DAC2020_bm08.dsn            	| ?         	| ?             	| v1.9                	| 0             	| ?                    	| ?                  	| ?                	| N/A              	| N/A           	|                     	|
| Issue508-DAC2020_bm08.dsn            	| 6 kB      	| 25            	| v2.2                	| 0             	| 0                    	| 4                  	| 1 second         	| 0 MB             	| 996,40        	| 2026-01-13 13:33:05 	|
| Issue508-DAC2020_bm09.dsn            	| ?         	| ?             	| v1.9                	| 1             	| ?                    	| ?                  	| ?                	| N/A              	| N/A           	|                     	|
| Issue508-DAC2020_bm10.dsn            	| ?         	| ?             	| v1.9                	| 0             	| ?                    	| ?                  	| ?                	| N/A              	| N/A           	|                     	|
| Issue508-DAC2020_bm11.dsn            	| ?         	| ?             	| v1.9                	| 14            	| ?                    	| ?                  	| ?                	| N/A              	| N/A           	|                     	|
| Issue555-BBD_Mars-64.dsn             	| 54 kB     	| 106           	| v2.2                	| 46            	| 0                    	| 28                 	| 5+ minutes       	| 269709 MB        	| 875,60        	| 2026-01-13 14:04:45 	|
| Issue555-CNH_Functional_Tester_1.dsn 	| 164 kB    	| 145           	| v2.2                	| 6             	| 0                    	| 251                	| 5+ minutes       	| 127436 MB        	| 958,57        	| 2026-01-13 14:09:49 	|
| Issue558-dev-board.dsn               	| 27 kB     	| 94            	| v2.2                	| 0             	| 0                    	| 11                 	| 8s               	| 4582 MB          	| 989,17        	| 2026-01-13 14:14:53 	|

### Metric Definitions

* **Total allocation (Cumulative Memory Allocation):** This value represents the sum total of all memory allocation instructions executed during the process. It indicates memory "churn" or throughput rather than the application's actual RAM footprint at any specific moment. Because the Garbage Collector (GC) continuously frees up short-lived objects, the actual physical memory usage is significantly lower than this figure.
