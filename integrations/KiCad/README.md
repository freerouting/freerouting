# KiCad Freerouting Plugin

This plugin connects [KiCad](https://www.kicad.org/) PCB Editor with the Freerouting autorouter.

## Routing Modes

The plugin supports two routing workflows:

| Mode | Status | When used |
|---|---|---|
| **DSN** | Default / Recommended | Standard Specctra export/import; supported on all KiCad versions; rock solid. |
| **JSON/API** | Experimental | Opt-in bridge via local REST API (`127.0.0.1:37864`). |

> **Note:** DSN mode is the recommended default for production designs. For the ongoing development of native Protocol Buffers IPC integration, see [`docs/research/kicad_ipc_api_research.md`](../../docs/research/kicad_ipc_api_research.md).

## Installation

1. Open KiCad 6.0 or newer.
2. Open **Plugin and Content Manager** (PCM) from the KiCad main menu or PCB Editor (**Tools > Plugin and Content Manager**).
   ![PCM](https://user-images.githubusercontent.com/910321/210979489-9856712b-f5c8-497e-9bfa-3f869dae85bc.png)
3. Search for **Freerouting**.
   ![Search](https://user-images.githubusercontent.com/910321/210980390-8bfdaeed-ea17-4e3f-b998-b5e52c04b2c0.png)
4. Click **Install**, then click **Apply Changes**.
   ![Install](https://user-images.githubusercontent.com/910321/210980590-0e006f1c-dfb9-4fd1-994c-8e6e0b4cb56a.png)

## Usage

1. Open your PCB design in KiCad **PCB Editor**.
2. *(Optional)* Remove any existing unrouted tracks or vias you want redone.
   ![Clean](https://user-images.githubusercontent.com/910321/181244962-ccf3c688-d364-470b-bfca-03dd049919b1.png)
3. Start Freerouting from **Tools > External Plugins > Freerouting**.
   ![Menu](https://user-images.githubusercontent.com/910321/181245125-cbf652bf-428a-4648-b455-5ebba78be920.png)
4. Let the autorouter run. When it finishes and exits, the plugin automatically re-imports the routed `.ses` session file into your board.
   ![Result](https://user-images.githubusercontent.com/910321/210981925-d32fb974-e3e6-4e65-832e-ed033ef3b3db.png)
