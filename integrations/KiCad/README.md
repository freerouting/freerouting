# KiCad Freerouting Plugin

## Synopsis

FreeRouting round trip invocation:
* export board.dsn file from pcbnew
* auto route by invoking FreeRouting.jar
* import generated board.ses file into pcbnew

## Installation

1. Open KiCad

2. Start PCB Editor

![image](https://user-images.githubusercontent.com/910321/181243949-b8a18c2a-6801-4cca-8043-148f1c693414.png)

3. Go to Tools, External Plugins, Open Plugin Directory

![image](https://user-images.githubusercontent.com/910321/181244181-2cb3461e-a820-4505-86dd-1fb7111bbb4f.png)

4. You will have `$HOME/.kicad_plugins` on Linux, `%userprofile%\Documents\KiCad\6.0\scripting\plugins` on Windows opened by default

5. Copy [the `kicad-freerouting` directory](https://github.com/freerouting/freerouting/tree/master/integrations/KiCad) into this KiCad plugins directory

## Operation

1. Open you PCB design in PCB Editor

2. (Optional) Remove routed tracks and via from the design

![image](https://user-images.githubusercontent.com/910321/181244962-ccf3c688-d364-470b-bfca-03dd049919b1.png)

3. Start Freerouting

![image](https://user-images.githubusercontent.com/910321/181245125-cbf652bf-428a-4648-b455-5ebba78be920.png)

4. Wait until the Freerouting app exits and the plugin loads your routed design
