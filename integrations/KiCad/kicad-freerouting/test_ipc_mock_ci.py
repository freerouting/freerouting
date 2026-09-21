"""
test_ipc_mock_ci.py — Offline Mock IPC Tests for CI Automation
--------------------------------------------------------------
Tests KiCadIpcBoardReader and KiCadIpcBoardWriter using mocked kipy
Protobuf objects so these tests can run in headless CI environments
(e.g., GitHub Actions Ubuntu runners) without KiCad or Xvfb installed.
"""

from __future__ import annotations

import sys
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

# Add ipc_bridge directory to sys.path
here = Path(__file__).resolve().parent
bridge_dir = here / "ipc_bridge"
sys.path.insert(0, str(bridge_dir))


class MockProtoVec2:
    def __init__(self, x_nm: int = 0, y_nm: int = 0):
        self.x = x_nm
        self.y = y_nm

    @classmethod
    def from_xy_mm(cls, x_mm: float, y_mm: float):
        return cls(int(round(x_mm * 1e6)), int(round(y_mm * 1e6)))


class MockNet:
    def __init__(self, name: str, net_number: int):
        self.name = name
        self.net_number = net_number
        self.proto = MagicMock()
        self.proto.name = name
        self.proto.number = net_number


class MockTrack:
    def __init__(self, start=None, end=None, width_nm: int = 0, layer_name: str = "", net: MockNet = None, locked: bool = False):
        self.start = start or MockProtoVec2()
        self.end = end or MockProtoVec2()
        self.width = width_nm
        self.layer = layer_name
        self.net = net
        self.locked = locked
        self.proto = MagicMock()


class MockVia:
    def __init__(self, pos=None, diameter_nm: int = 0, drill_nm: int = 0, net: MockNet = None, locked: bool = False):
        self.position = pos or MockProtoVec2()
        self.diameter = diameter_nm
        self.drill_diameter = drill_nm
        self.net = net
        self.locked = locked
        self.type = None
        self.proto = MagicMock()


class MockPadStackCopperLayer:
    def __init__(self, shape="RECTANGLE", size=None):
        self.shape = shape
        self.size = size or MockProtoVec2(1000000, 1000000)


class MockPadStack:
    def __init__(self, layers=None, shape="RECTANGLE", size=None):
        self.drill = MagicMock()
        self.drill.diameter = MockProtoVec2(0, 0)
        self.copper_layers = [MockPadStackCopperLayer(shape, size)]
        self.layers = layers or [0]


class MockPad:
    def __init__(self, parent_id: str, number: str, pos: tuple[int, int], net: MockNet):
        self.parent = MagicMock()
        self.parent.value = parent_id
        self.number = number
        self.position = MockProtoVec2(pos[0], pos[1])
        self.net = net
        self.padstack = MockPadStack(layers=[0], shape="RECTANGLE", size=MockProtoVec2(1000000, 1000000))


class MockField:
    def __init__(self, text: str):
        self.text = MagicMock()
        self.text.value = text


class MockFootprint:
    def __init__(self, fp_id: str, ref: str, val: str, pos: tuple[int, int], layer: int = 0):
        self.id = MagicMock()
        self.id.value = fp_id
        self.reference_field = MockField(ref)
        self.value_field = MockField(val)
        self.definition = MagicMock()
        self.definition.id = f"Package:{val}"
        self.position = MockProtoVec2(pos[0], pos[1])
        self.layer = layer
        self.orientation = MagicMock()
        self.orientation.value_degrees = 0.0


class MockProject:
    def __init__(self):
        self.path = ""
        self.name = "mock_project"
        self.copper_to_edge_clearance = 500000  # 0.5 mm in nm
        self.copper_to_copper_clearance = 200000  # 0.2 mm in nm
        self.copper_to_hole_clearance = 250000
        self.hole_to_hole_clearance = 250000

    def get_net_classes(self):
        return []


class BoardRectangle:
    def __init__(self, tl: tuple[int, int], br: tuple[int, int], layer: int = 44):
        self.layer = layer
        self.top_left = MockProtoVec2(tl[0], tl[1])
        self.bottom_right = MockProtoVec2(br[0], br[1])


class MockBoard:
    def __init__(self):
        self.name = "mock_board.kicad_pcb"
        self.project = MockProject()
        self.nets = [MockNet("", 0), MockNet("GND", 1), MockNet("+3V3", 2)]
        self.tracks: list[MockTrack] = []
        self.vias: list[MockVia] = []
        self.footprints: list[MockFootprint] = []
        self.pads: list[MockPad] = []
        self.shapes: list[Any] = []
        self.zones: list[Any] = []
        self.drawings: list[Any] = []
        self.commits_pushed = []

    def get_project(self):
        return self.project

    def get_nets(self):
        return self.nets

    def get_tracks(self):
        return list(self.tracks)

    def get_vias(self):
        return list(self.vias)

    def get_footprints(self):
        return list(self.footprints)

    def get_pads(self):
        return list(self.pads)

    def get_shapes(self):
        return list(self.shapes)

    def get_zones(self):
        return list(self.zones)

    def get_drawings(self):
        return list(self.drawings)

    def get_enabled_layers(self):
        return [0, 31]

    def get_layer_name(self, layer_num):
        if layer_num == 0:
            return "F.Cu"
        elif layer_num == 31:
            return "B.Cu"
        elif layer_num == 44:
            return "Edge.Cuts"
        return "Unknown"

    def get_layer_by_name(self, name):
        if name == "F.Cu":
            return 0
        elif name == "B.Cu":
            return 31
        elif name == "Edge.Cuts":
            return 44
        return 0

    def begin_commit(self):
        return "mock_commit_token"

    def create_items(self, items):
        for it in items:
            if isinstance(it, MockTrack):
                self.tracks.append(it)
            elif isinstance(it, MockVia):
                self.vias.append(it)

    def remove_items(self, items):
        for it in items:
            if it in self.tracks:
                self.tracks.remove(it)
            elif it in self.vias:
                self.vias.remove(it)

    def push_commit(self, commit, message=""):
        self.commits_pushed.append((commit, message))

    def drop_commit(self, commit):
        pass


class TestMockIpcCi(unittest.TestCase):
    """Offline unit tests verifying IPC reader and writer logic using mocks."""

    def test_mock_ipc_board_reader(self):
        mock_board = MockBoard()
        # Add outline rectangle on Edge.Cuts (layer 44)
        mock_board.shapes.append(BoardRectangle((0, 0), (100000000, 80000000), layer=44))

        # Add footprint and pad
        fp = MockFootprint("fp_1", "R1", "10k", (10000000, 20000000), layer=0)
        pad = MockPad("fp_1", "1", (10000000, 20000000), mock_board.nets[2])
        mock_board.footprints.append(fp)
        mock_board.pads.append(pad)

        # Setup mock kipy hierarchy
        mock_kipy = MagicMock()
        mock_kipy.KiCad.return_value.get_board.return_value = mock_board
        mock_kipy.KiCad.return_value.get_version.return_value = "10.99.0"
        mock_kipy.KiCad.return_value.get_api_version.return_value = "1.0.0"

        mock_board_types = MagicMock()
        mock_board_types.iter_copper_layers = MagicMock(return_value=[0, 31])

        mock_proto_board = MagicMock()
        mock_proto_board.board_types_pb2.BL_Edge_Cuts = 44

        with patch.dict(sys.modules, {
            "kipy": mock_kipy,
            "kipy.board_types": mock_board_types,
            "kipy.geometry": MagicMock(),
            "kipy.proto": MagicMock(),
            "kipy.proto.board": mock_proto_board,
            "kipy.proto.board.board_types_pb2": mock_proto_board.board_types_pb2,
        }):
            from ipc_board_reader import KiCadIpcBoardReader
            reader = KiCadIpcBoardReader(socket_path="mock://test")
            board_data = reader.read_board_data()

            self.assertEqual(board_data["designName"], "mock_board")
            self.assertEqual(board_data["hostCad"], "KiCad")
            self.assertEqual(len(board_data["layers"]), 2)
            # Net 0 is empty string name (unconnected), nets 1 and 2 are GND and +3V3
            self.assertEqual(len(board_data["nets"]), 2)
            self.assertEqual(len(board_data["components"]), 1)
            self.assertEqual(board_data["components"][0]["reference"], "R1")
            self.assertEqual(len(board_data["components"][0]["pads"]), 1)
            self.assertEqual(board_data["outline"]["clearance"], 0.5)
            self.assertEqual(len(board_data["outline"]["corners"]), 4)

    def test_mock_ipc_board_writer_transaction(self):
        mock_board = MockBoard()

        mock_kipy = MagicMock()
        mock_board_types = MagicMock()
        mock_board_types.Track = MockTrack
        mock_board_types.Via = MockVia
        mock_board_types.ViaType = MagicMock()

        mock_geometry = MagicMock()
        mock_geometry.Vector2 = MockProtoVec2

        with patch.dict(sys.modules, {
            "kipy": mock_kipy,
            "kipy.board_types": mock_board_types,
            "kipy.geometry": mock_geometry,
            "kipy.proto": MagicMock(),
            "kipy.proto.board": MagicMock(),
            "kipy.proto.board.board_types_pb2": MagicMock(),
        }):
            from ipc_board_writer import KiCadIpcBoardWriter
            writer = KiCadIpcBoardWriter(board=mock_board)

            payload = {
                "layers": [{"index": 0, "name": "F.Cu"}, {"index": 1, "name": "B.Cu"}],
                "traces": [
                    {
                        "netName": "+3V3",
                        "width": 0.25,
                        "layerIndex": 0,
                        "points": [{"x": 10.0, "y": 20.0}, {"x": 15.0, "y": 20.0}],
                    }
                ],
                "vias": [
                    {
                        "netName": "+3V3",
                        "position": {"x": 15.0, "y": 20.0},
                        "diameter": 0.6,
                        "drill": 0.3,
                    }
                ],
            }

            result = writer.write_routed_board(payload, replace_unfixed=True)
            self.assertEqual(result["created_tracks"], 1)
            self.assertEqual(result["created_vias"], 1)
            self.assertEqual(len(mock_board.tracks), 1)
            self.assertEqual(len(mock_board.vias), 1)
            self.assertEqual(mock_board.tracks[0].width, 250000)
            self.assertEqual(mock_board.vias[0].diameter, 600000)
            self.assertEqual(len(mock_board.commits_pushed), 1)


if __name__ == "__main__":
    unittest.main()
