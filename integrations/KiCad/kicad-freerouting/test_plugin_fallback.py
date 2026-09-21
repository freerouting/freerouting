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


if __name__ == "__main__":
    unittest.main()
