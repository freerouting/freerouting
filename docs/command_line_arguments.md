* -de [design input file]: loads up a Specctra .dsn file at startup.
* -di [design input directory]: if the GUI is used, this sets the default folder for the open design dialogs.
* -dr [design rules file]: reads the rules from a previously saved .rules file.
* -do [design output file]: saves a Specctra board (.dsn), a Specctra session file (.ses) or Eagle session script file (
  .scr) when the routing is finished.
* -mp [number of passes]: sets the upper limit of the number of auto-router passes that will be performed.
* -l [language]: "en" for English, "de" for German, "zh" for Simplified Chinese, otherwise it's the system default.
  English is set by default for unsupported languages.
* -host [host_name host_version]: sets the name of the host process, if it was run as an external library or plugin.
* -mt [number of threads]: sets thread pool size for route optimization. The default is one less than the number of
  logical processors in the system. Set it to 0 to disable route optimization.
* -oit [percentage]: stops the route optimizer if the improvement drops below a certain percentage threshold per pass.
  Default is 0.1%, and `-oit 0` means to continue improving until it is interrupted by the user or it runs out of
  options to test.
* -us [greedy | global | hybrid]: sets board updating strategy for route optimization: greedy, global optimal or hybrid.
  The default is greedy. When hybrid is selected, another option "hr" specifies hybrid ratio.
* -hr [m:n]: sets hybrid ratio in the format of #_global_optiomal_passes:#_prioritized_passes. The default is 1:1. It's
  only effective when hybrid strategy is selected.
* -is [sequential | random | prioritized]: sets item selection strategy for route optimization: sequential, random,
  prioritized. The default is prioritized. Prioritied strategy selects items based on scores calculated in previous
  round.
* -inc [net class names, separated by commas]: auto-router ignores the listed net classes, eg. `-inc GND,VCC` will not
  try to wire components that are either in the "GND" or in the "VCC" net class.
* -im: saves intermediate steps in version-specific binary format. This allows to user to resume the interrupted
  optimization from the last checkpoint. Turned off by default.
* -dct [seconds]: dialog confirmation timeout. Sets the timeout of the dialogs that start a default action in x seconds.
  20 seconds by default.
* -da: disable anonymous analytics.
* -dl: disable logging.
* -ll: set console logging level. Valid values are: OFF, FATAL, ERROR, WARN, INFO, DEBUG, TRACE, ALL. Numerical
  values between 0 (OFF) and 7 (ALL) are also accepted. Default is INFO (4).
* -help: shows help.