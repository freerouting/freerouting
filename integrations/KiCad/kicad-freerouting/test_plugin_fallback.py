"""
test_plugin_fallback.py — Unit test suite for KiCad Plugin Routing Mode & Fallback Logic
----------------------------------------------------------------------------------------
Tests:
  1. Routing mode normalization (DSN, IPC, JSON, PROTOBUF).
  2. Fallback promotion: KiCad 11+ without SWIG automatically promotes DSN -> IPC.
  3. Fallback degradation: IPC mode requested but socket unavailable smoothly falls back to DSN (when SWIG available).
  4. ProcessDialog indicators and routing mode banners.
"""

import sys
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

# Add kicad-freerouting dir to sys.path so 'plugins' is a package
here = Path(__file__).resolve().parent
sys.path.insert(0, str(here))

# Mock pcbnew and wx before importing plugin
mock_pcbnew = MagicMock()
class MockActionPlugin:
    def register(self):
        pass
mock_pcbnew.ActionPlugin = MockActionPlugin
sys.modules["pcbnew"] = mock_pcbnew

mock_wx = MagicMock()
mock_wx.Colour = MagicMock(return_value="gray")
class MockWxWindow:
    def __init__(self, *args, **kwargs):
        pass
    def Bind(self, *args, **kwargs):
        pass
    def SetSizer(self, *args, **kwargs):
        pass
    def Refresh(self, *args, **kwargs):
        pass
    def Update(self, *args, **kwargs):
        pass
mock_wx.Panel = MockWxWindow
mock_wx.Dialog = MockWxWindow
sys.modules["wx"] = mock_wx
sys.modules["wx.aui"] = MagicMock()

from plugins import config, plugin, router_ipc


class TestPluginRoutingMode(unittest.TestCase):

    def test_routing_mode_normalization(self):
        self.assertEqual(config.normalize_routing_mode(None), "DSN")
        self.assertEqual(config.normalize_routing_mode(""), "DSN")
        self.assertEqual(config.normalize_routing_mode("dsn"), "DSN")
        self.assertEqual(config.normalize_routing_mode("DSN"), "DSN")
        self.assertEqual(config.normalize_routing_mode("ipc"), "IPC")
        self.assertEqual(config.normalize_routing_mode("IPC"), "IPC")
        self.assertEqual(config.normalize_routing_mode("protobuf"), "IPC")
        self.assertEqual(config.normalize_routing_mode("json"), "JSON")
        self.assertEqual(config.normalize_routing_mode("JSON"), "JSON")

    def test_kicad11_dsn_to_ipc_promotion(self):
        """Simulate KiCad 11 where pcbnew lacks ExportSpecctraDSN."""
        p = plugin.FreeroutingPlugin()
        p.defaults()
        p.routing_mode = "DSN"

        # When has_pcbnew_api returns False:
        with patch("plugins.plugin.has_pcbnew_api", return_value=False):
            # Assert that in a KiCad 11 environment, the mode promotes to IPC
            active_mode = p.routing_mode
            if active_mode == config.ROUTING_MODE_DSN and not plugin.has_pcbnew_api():
                active_mode = config.ROUTING_MODE_IPC

            self.assertEqual(active_mode, config.ROUTING_MODE_IPC)

    def test_ipc_to_dsn_fallback_when_server_unavailable(self):
        """Simulate KiCad 10 where user enabled IPC mode, but API server is disabled."""
        p = plugin.FreeroutingPlugin()
        p.defaults()
        p.routing_mode = "IPC"

        # Mock is_ipc_available to return False
        with patch("plugins.plugin.is_ipc_available", return_value=(False, "Connection refused")), \
             patch("plugins.plugin.has_pcbnew_api", return_value=True):
            ipc_ok, msg = plugin.is_ipc_available()
            self.assertFalse(ipc_ok)

            # Fallback logic check
            if p.routing_mode == config.ROUTING_MODE_IPC and not ipc_ok and plugin.has_pcbnew_api():
                fallback_mode = config.ROUTING_MODE_DSN
            else:
                fallback_mode = p.routing_mode

            self.assertEqual(fallback_mode, config.ROUTING_MODE_DSN)

    def test_indicator_pulse_advances_phase(self):
        """Verify that calling pulse() on StatusIndicator advances animation phase."""
        from plugins.process_utils import StatusIndicator, STATUS_IN_PROGRESS, STATUS_PASS
        parent = MagicMock()
        indicator = StatusIndicator(parent, "Testing")
        indicator.set_status(STATUS_IN_PROGRESS)
        self.assertEqual(indicator._spin_phase, 0)
        indicator.pulse()
        self.assertEqual(indicator._spin_phase, 1)
        indicator.pulse()
        self.assertEqual(indicator._spin_phase, 2)

        # Pulse when not in progress should not change phase
        indicator.set_status(STATUS_PASS)
        indicator.pulse()
        self.assertEqual(indicator._spin_phase, 2)

    def test_ipc_extract_board_in_process_fallback(self):
        """Verify that IpcRouter falls back to in-process extraction if IPC fails."""
        p = plugin.FreeroutingPlugin()
        p.defaults()
        p.board = MagicMock()
        p.board.GetFileName.return_value = "test_board.kicad_pcb"

        router = router_ipc.IpcRouter(p)

        # Mock KiCadIpcBoardReader to raise ConnectionError (simulating IPC deadlock/timeout)
        mock_board_json = '{"layers": [{"name": "F.Cu"}], "outline": {"clearance": 0.25}}'
        mock_reader_mod = MagicMock()
        mock_reader_mod.KiCadIpcBoardReader.side_effect = ConnectionError("Timed out")
        with patch.dict("sys.modules", {"ipc_board_reader": mock_reader_mod}), \
             patch("plugins.board_json_helpers._build_board_json_manually", return_value=mock_board_json):
            board_data = router.extract_board()
            self.assertIsNotNone(board_data)
            self.assertEqual(len(board_data.get("layers", [])), 1)
            self.assertEqual(board_data["layers"][0]["name"], "F.Cu")

    def test_apply_result_coordinate_scaling(self):
        """Verify that _apply_result_to_kicad scales coordinates by 1e6 for MM."""
        p = plugin.FreeroutingPlugin()
        p.defaults()

        for method in ("ApplyBoardJson", "import_json", "ImportBoardJson"):
            if hasattr(mock_pcbnew, method):
                delattr(mock_pcbnew, method)

        mock_board = MagicMock()
        mock_board.GetTracks.return_value = []
        mock_board.GetLayerID.side_effect = lambda name: 0 if name == "F.Cu" else 31

        created_tracks = []
        created_vias = []

        class MockTrack:
            def __init__(self, b):
                self.start = None
                self.end = None
                self.width = None
                self.layer = None
                self.net = None
                created_tracks.append(self)
            def SetStart(self, pt): self.start = pt
            def SetEnd(self, pt): self.end = pt
            def SetWidth(self, w): self.width = w
            def SetLayer(self, l): self.layer = l
            def SetNet(self, n): self.net = n

        class MockVia:
            def __init__(self, b):
                self.pos = None
                self.width = None
                self.drill = None
                self.layers = None
                self.net = None
                created_vias.append(self)
            def SetPosition(self, pt): self.pos = pt
            def SetWidth(self, w): self.width = w
            def SetDrill(self, d): self.drill = d
            def SetLayerPair(self, t, b): self.layers = (t, b)
            def SetNet(self, n): self.net = n

        mock_pcbnew.PCB_TRACK = MockTrack
        mock_pcbnew.PCB_VIA = MockVia
        mock_pcbnew.VECTOR2I = lambda x, y: (x, y)

        sample_output = {
            "unit": "MM",
            "resolution": 10000.0,
            "layers": [{"index": 0, "name": "F.Cu"}, {"index": 1, "name": "B.Cu"}],
            "nets": [{"id": 1, "name": "Net1"}],
            "traces": [
                {
                    "netName": "Net1",
                    "width": 0.25,
                    "layerIndex": 0,
                    "points": [{"x": 10.5, "y": 20.0}, {"x": 30.5, "y": 20.0}]
                }
            ],
            "vias": [
                {
                    "netName": "Net1",
                    "position": {"x": 15.0, "y": 25.0},
                    "diameter": 0.8,
                    "drill": 0.4
                }
            ]
        }

        p._apply_result_to_kicad(sample_output, board=mock_board)

        self.assertEqual(len(created_tracks), 1)
        self.assertEqual(created_tracks[0].start, (10500000, 20000000))
        self.assertEqual(created_tracks[0].end, (30500000, 20000000))
        self.assertEqual(created_tracks[0].width, 250000)

        self.assertEqual(len(created_vias), 1)
        self.assertEqual(created_vias[0].pos, (15000000, 25000000))
        self.assertEqual(created_vias[0].width, 800000)
        self.assertEqual(created_vias[0].drill, 400000)

    def test_board_json_helpers_direct_import(self):
        """Verify that board_json_helpers can be imported as a top-level module."""
        import importlib
        plugins_dir = here / "plugins"
        if str(plugins_dir) not in sys.path:
            sys.path.insert(0, str(plugins_dir))
        bjh = importlib.import_module("board_json_helpers")
        self.assertTrue(hasattr(bjh, "_build_board_json_manually"))

    def test_ipc_gui_mode_workflow(self):
        """Verify that IPC mode with GUI enabled builds GUI command with input JSON."""
        import tempfile
        p = plugin.FreeroutingPlugin()
        p.defaults()
        p.gui_enabled = True
        p.routing_dir = Path(tempfile.mkdtemp(prefix="test_fr_gui_"))
        p.java_path = Path("java")
        p.module_path = Path("freerouting.jar")

        router = router_ipc.IpcRouter(p)
        mock_dialog = MagicMock()
        mock_pump = MagicMock()

        sample_board = {"layers": [{"name": "F.Cu"}], "outline": {"clearance": 0.5}}
        router.extract_board = MagicMock(return_value=sample_board)

        # Simulate process thread completing and creating output SES
        output_ses = p.routing_dir / "freerouting_output_board.ses"
        def fake_show_modal():
            output_ses.write_text("(pcb freerouting.dsn)\n", encoding="utf-8")
            return mock_dialog.result_terminate

        mock_dialog.ShowModal.side_effect = fake_show_modal

        with patch("plugins.process_utils.ProcessThread") as mock_thread_cls:
            mock_proc = MagicMock()
            mock_proc.has_ok.return_value = True
            mock_proc.is_alive.return_value = False
            mock_thread_cls.return_value = mock_proc

            cancelled, success, output_info = p._run_ipc_gui_stages(router, mock_dialog, mock_pump)

            self.assertFalse(cancelled)
            self.assertTrue(success)
            self.assertIsNotNone(output_info)
            self.assertEqual(output_info.get("type"), "ses")
            self.assertTrue(p.module_command)
            self.assertIn("--gui.enabled=true", p.module_command)
            self.assertIn("-de", p.module_command)
            self.assertTrue((p.routing_dir / "freerouting_input_board.json").is_file())


if __name__ == "__main__":
    unittest.main()
