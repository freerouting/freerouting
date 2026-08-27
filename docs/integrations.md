<p align="center">
<img src="https://raw.githubusercontent.com/freerouting/freerouting/master/assets/social_preview/freerouting_social_preview_1280x960_v2.png" alt="Freerouting" title="Freerouting" align="center">
</p>
<h1 align="center">Freerouting</h1>
<h5 align="center">Freerouting is an advanced autorouter for all PCB programs that support the standard Specctra or Electra DSN interface.</h5>

<br/>
<br/>

# EDA Integrations

## [KiCad](https://www.kicad.org/)

1. Open KiCad 6.0 or newer

2. Start Tools / Plugin and Content Manager (Ctrl+M)

![image](https://user-images.githubusercontent.com/910321/210979489-9856712b-f5c8-497e-9bfa-3f869dae85bc.png)

3. Search for the Freerouting plugin

![image](https://user-images.githubusercontent.com/910321/210980390-8bfdaeed-ea17-4e3f-b998-b5e52c04b2c0.png)

4. Click on the Install button

![image](https://user-images.githubusercontent.com/910321/210980590-0e006f1c-dfb9-4fd1-994c-8e6e0b4cb56a.png)

5. Open you PCB design in PCB Editor

6. (Optional) Remove routed tracks and via from the design

![image](https://user-images.githubusercontent.com/910321/181244962-ccf3c688-d364-470b-bfca-03dd049919b1.png)

7. Start Freerouting from the Tools / External Plugins menu

![image](https://user-images.githubusercontent.com/910321/181245125-cbf652bf-428a-4648-b455-5ebba78be920.png)

8. Wait until the Freerouting app exits and the plugin loads your routed design

![image](https://user-images.githubusercontent.com/910321/210981925-d32fb974-e3e6-4e65-832e-ed033ef3b3db.png)

## [Autodesk Fusion](https://www.autodesk.com/products/fusion-360/overview)

Autodesk Fusion Electronics (which replaced standalone Autodesk EAGLE) integrates with Freerouting via a two-way ULP/Script workflow.

### 1) Export DSN from Autodesk Fusion

1. In Autodesk Fusion, open your PCB layout.
2. In the top toolbar, go to the **Automate** tab and click **Run ULP** (or type `run` in the command line at the bottom).
3. Select [`freerouting_fusion_plugin.ulp`](../integrations/AutodeskFusion/freerouting_fusion_plugin.ulp).
4. Click **Export DSN** and confirm the save location for `<your_design>.dsn`.

### 2) Autoroute with Freerouting

1. Open Freerouting and open `<your_design>.dsn`.
2. Run autorouting.
3. Save the routed result as an Autodesk Fusion script:
   - **GUI:** Click **File > Save as...** and select **Autodesk Fusion Script (*.scr)** to generate `<your_design>.scr`.
   - **CLI:** `freerouting -de <your_design>.dsn -do <your_design>.scr`

### 3) Import Routed Script into Autodesk Fusion

1. Switch back to your PCB layout in Autodesk Fusion.
2. In the **Automate** tab, click **Run Script** (or type `script` in the command line at the bottom).
3. Select `<your_design>.scr`.
4. Fusion rips up old traces, places the newly routed tracks and vias, and recalculates the ratsnest.

## [Target 3001!](https://ibfriedrich.com/)

1) Freerouting is accessible directly from the GUI menu in Actions / Automatisms and assistants / Autorouter / Freerouting autorouter...
<img src="https://raw.githubusercontent.com/freerouting/freerouting/master/integrations/Target3001!/Target3001_Step1_OpenFreeroutingDialogWindow.png" alt="Open Freerouting dialog window" title="Open Freerouting dialog window" align="center">

2) There you can select the signals (=nets) to be routed
<img src="https://raw.githubusercontent.com/freerouting/freerouting/master/integrations/Target3001!/Target3001_Step2_SignalSelection.png" alt="If no special signal is selected, all signals are affected" title="If no special signal is selected, all signals are affected" align="center">

3) Next you can influence the algorithm
<img src="https://raw.githubusercontent.com/freerouting/freerouting/master/integrations/Target3001!/Target3001_Step3_InstallFreeroutingAndJava.png" alt="Most users have to install FreeRouting once, some will also have to install Java first" title="Most users have to install FreeRouting once, some will also have to install Java first" align="center">

4) They will get the Freerouting installer from https://github.com/freerouting/freerouting/releases/
<img src="https://raw.githubusercontent.com/freerouting/freerouting/master/integrations/Target3001!/Target3001_Step4_SelectLayers.png" alt="Select layers and their functions" title="Select layers and their functions" align="center">

5) Normally the user does not have to change the settings and can click directly on the [Start] button. So then it is a one-click solution. After the creation of the session file SES, Target automatically asks, if the results shall be used
<img src="https://raw.githubusercontent.com/freerouting/freerouting/master/integrations/Target3001!/Target3001_Step5_AcceptSES.png" alt="Accept results from SES file" title="Accept results from SES file" align="center">

6) The tracks and vias are imported immediately into the TARGET project file
<img src="https://raw.githubusercontent.com/freerouting/freerouting/master/integrations/Target3001!/Target3001_Step6_ResultsImported.png" alt="Results are imported" title="Results are imported" align="center">

## [pcb-rnd](http://www.repo.hu/projects/pcb-rnd)

### Using the standalone freerouting application

1) Download the latest `freerouting-<version>.jar` file from the [Releases](https://github.com/freerouting/freerouting/releases) page

2) Start pcb-rnd and load your layout.

3) Export the layout as Specctra DSN (File / Export... / Specctra DSN).

4) Start the router by running the downloaded JAR file, push the "Open Your Own Design" button and select the exported .dsn file in the file chooser.

5) Do the routing.

5) When you're finished, export the results into a Specctra session file (File / Export Specctra Session File). The router will generate a .ses file for you.

6) Go back to pcb-rnd and import the results (File / Import autorouted dsn/ses file...). Track widths and clearances during autorouting are based on the currently selected route style during DSN export.


### Using freerouting from within pcb-rnd

1) Download the latest `freerouting-<version>-linux-x64.zip` from the [Releases](https://github.com/freerouting/freerouting/releases) page

2) Unzip it and rename the top directory to `freerouting.net` (the default location is `/opt/freerouting.net`)

3) Start pcb-rnd and ensure that this directory is specified in (File / Preferences / Config Tree / Plugins / ar_extern / freerouting_net...); the location of the executable can be customised.

4) Load your layout

5) Open the external autorouter window with (Connect / Automatic Routing / External autorouter...)

6) Select the freerouting.net tab, and push the "Route" button.

7) Go back to the layout and inspect the autorouted networks. Track widths and clearances during autorouting are based on the currently selected route style when the autorouter is started.
