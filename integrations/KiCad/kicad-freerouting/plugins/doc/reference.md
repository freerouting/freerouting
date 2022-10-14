
### help

```
java -jar FreeRouting.jar -h
```

```
-de  provide design file
-di  design folder used in file dialog
-l   provide locale
-s   spectra session file is automatic saved on exit
-t   debug option
-white   white background
-h   this help
```

### pcbnew api

http://docs.kicad-pcb.org/doxygen-python/namespacepcbnew.html
https://code.launchpad.net/~andrei-pozolotin/kicad/+git/kicad/+merge/366082

### export test

```
#
# manual test session for "Tools -> Scripting Console"
# verify that "board.dsn" file is created after this session
# result should be identical to "File -> Export -> Specctra DSN"
#
import os
import pcbnew
board = pcbnew.GetBoard()
board_path = board.GetFileName()
path_tuple = os.path.splitext(board_path)
board_prefix = path_tuple[0]
export_path = board_prefix + ".dsn"
pcbnew.ExportSpecctraDSN(export_path)
```

### import test

```
#
# manual test session for "Tools -> Scripting Console"
# verify that "board.ses" file is applied after this session
# result should be identical to "File -> Import -> Specctra Session"
#
import os
import pcbnew
board = pcbnew.GetBoard()
board_path = board.GetFileName()
path_tuple = os.path.splitext(board_path)
board_prefix = path_tuple[0]
import_path = board_prefix + ".ses"
pcbnew.ImportSpecctraSES(import_path)
```
