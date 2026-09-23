"""
ipc_board_reader.py — KiCad Protobuf IPC Board Reader for Freerouting
----------------------------------------------------------------------
Connects to a running KiCad instance or headless `kicad-cli api-server`
via KiCad's official Protocol Buffers IPC API (kipy / NNG transport)
and serializes the PCB into the KiCadBoardJson format used by Freerouting.
"""

from __future__ import annotations

import json
import logging
import math
import os
from pathlib import Path
from typing import Any, Dict, List, Optional

logger = logging.getLogger("freerouting.ipc")


class KiCadIpcBoardReader:
    """Reads PCB data from KiCad via Protocol Buffers IPC (kipy)."""

    def __init__(self, socket_path: Optional[str] = None, timeout_ms: int = 5000):
        """Initializes connection to KiCad IPC API.

        Args:
            socket_path: Optional NNG socket path (e.g. ipc://<path>). If None,
                reads from KICAD_API_SOCKET env var or platform default.
            timeout_ms: Socket timeout in milliseconds.
        """
        import kipy

        self.kicad = kipy.KiCad(socket_path=socket_path, timeout_ms=timeout_ms)
        self.board = self.kicad.get_board()
        self.project = self.board.get_project()
        self.kicad_version = self.kicad.get_version()
        self.api_version = self.kicad.get_api_version()

    def read_board_data(self) -> Dict[str, Any]:
        """Serializes the board into KiCadBoardJson dictionary format."""
        # 1. Fast path for live KiCad GUI session:
        # In KiCad 10+, KiCad returns AS_BUSY for granular item queries (get_tracks, get_footprints)
        # while an ActionPlugin is running or modal dialog is active, but SaveDocumentToString
        # succeeds immediately. If pcbnew is available in-process, load the live string.
        doc_json = self._try_read_via_save_document()
        if doc_json is not None:
            return doc_json

        # 2. Granular IPC element extraction (for headless kicad-cli api-server or tests):
        design_name = Path(self.board.name).stem if self.board.name else "Untitled"

        data: Dict[str, Any] = {
            "designName": design_name,
            "hostCad": "KiCad",
            "hostVersion": str(self.kicad_version),
            "unit": "MM",
            "resolution": 1.0,
            "layers": [],
            "netClasses": [],
            "nets": [],
            "clearanceRules": [],
            "components": [],
            "outline": {"corners": [], "clearance": 0.5},
            "traces": [],
            "vias": [],
            "conductionAreas": [],
        }

        layer_id_to_index: Dict[Any, int] = {}
        self._collect_layers(data, layer_id_to_index)
        self._collect_net_classes(data)
        self._collect_nets(data)
        self._collect_outline(data)
        self._collect_components(data, layer_id_to_index)
        self._collect_traces(data, layer_id_to_index)
        self._collect_vias(data, layer_id_to_index)
        self._collect_zones(data, layer_id_to_index)

        return data

    def _try_read_via_save_document(self) -> Optional[Dict[str, Any]]:
        """Attempts to read the board via SaveDocumentToString + pcbnew loader.

        Bypasses KiCad's AS_BUSY state on granular item queries during ActionPlugin execution.
        """
        try:
            import tempfile
            from kipy.proto.common.commands.editor_commands_pb2 import (
                SaveDocumentToString,
                SavedDocumentResponse,
            )
            from kipy.proto.common.types.base_types_pb2 import DOCTYPE_PCB

            docs = self.kicad.get_open_documents(DOCTYPE_PCB)
            if not docs:
                return None

            resp = self.kicad._client.send(
                SaveDocumentToString(document=docs[0]),
                SavedDocumentResponse,
            )
            if not resp or not resp.contents:
                return None

            try:
                import pcbnew
            except ImportError:
                return None

            # Load the live board string into pcbnew via a temp file
            with tempfile.NamedTemporaryFile(suffix=".kicad_pcb", delete=False, mode="w", encoding="utf-8") as tf:
                tf.write(resp.contents)
                temp_pcb_path = tf.name

            try:
                pcb = pcbnew.LoadBoard(temp_pcb_path)
                if not pcb:
                    return None

                # Find board_json_helpers (parent plugin module)
                try:
                    from plugins.board_json_helpers import _build_board_json_manually
                except ImportError:
                    from board_json_helpers import _build_board_json_manually

                board_json_str = _build_board_json_manually(pcb)
                board_data = json.loads(board_json_str)

                # Ensure outline clearance reflects .kicad_pro min_copper_edge_clearance
                edge_clearance_mm = self._resolve_edge_clearance()
                if "outline" in board_data:
                    board_data["outline"]["clearance"] = edge_clearance_mm

                logger.info("Successfully serialized board via SaveDocumentToString fast path.")
                return board_data
            finally:
                try:
                    os.unlink(temp_pcb_path)
                except Exception:
                    pass
        except Exception as e:
            logger.debug(f"SaveDocumentToString fast-path unavailable or failed: {e}")
            return None

    def _collect_layers(self, data: Dict[str, Any], layer_id_to_index: Dict[Any, int]) -> None:
        """Enumerates copper layers and builds layer mapping."""
        from kipy.board_types import iter_copper_layers

        enabled = set(self.board.get_enabled_layers())
        idx = 0
        for layer_enum in iter_copper_layers():
            if layer_enum in enabled:
                layer_name = self.board.get_layer_name(layer_enum)
                layer_id_to_index[layer_enum] = idx
                data["layers"].append({
                    "index": idx,
                    "name": layer_name,
                    "type": "signal"
                })
                idx += 1

        if not data["layers"]:
            # Fallback 2-layer default
            data["layers"] = [
                {"index": 0, "name": "F.Cu", "type": "signal"},
                {"index": 1, "name": "B.Cu", "type": "signal"},
            ]

    def _collect_net_classes(self, data: Dict[str, Any]) -> None:
        """Extracts net classes from project."""
        try:
            net_classes = self.project.get_net_classes()
            for nc in net_classes:
                clearance_mm = nc.clearance / 1e6 if nc.clearance else 0.2
                track_width_mm = nc.track_width / 1e6 if nc.track_width else 0.2
                via_dia_mm = nc.via_diameter / 1e6 if nc.via_diameter else 0.6
                via_drill_mm = nc.via_drill / 1e6 if nc.via_drill else 0.3
                constituents = list(nc.constituents) if nc.constituents else []

                data["netClasses"].append({
                    "name": nc.name,
                    "clearance": round(clearance_mm, 6),
                    "traceWidth": round(track_width_mm, 6),
                    "viaDiameter": round(via_dia_mm, 6),
                    "viaDrill": round(via_drill_mm, 6),
                    "netNames": constituents
                })
        except Exception as e:
            logger.warning(f"Could not read net classes via IPC: {e}")

    def _collect_nets(self, data: Dict[str, Any]) -> None:
        """Extracts board nets and associates them with net classes."""
        net_to_class: Dict[str, str] = {}
        for nc in data["netClasses"]:
            for n_name in nc.get("netNames", []):
                net_to_class[n_name] = nc["name"]

        try:
            nets = self.board.get_nets()
            net_idx = 1
            for n in nets:
                if not n.name:
                    continue
                class_name = net_to_class.get(n.name, "Default")
                data["nets"].append({
                    "id": net_idx,
                    "name": n.name,
                    "className": class_name,
                    "containsPlane": False
                })
                net_idx += 1
        except Exception as e:
            logger.warning(f"Could not read nets via IPC: {e}")

    def _collect_outline(self, data: Dict[str, Any]) -> None:
        """Extracts board outline shapes and resolves copper-to-edge clearance."""
        # 1. Resolve copper-to-edge clearance from .kicad_pro project rules
        edge_clearance_mm = self._resolve_edge_clearance()
        data["outline"]["clearance"] = edge_clearance_mm

        # 2. Extract Edge.Cuts geometry
        from kipy.proto.board.board_types_pb2 import BL_Edge_Cuts

        corners: List[Dict[str, float]] = []
        try:
            shapes = self.board.get_shapes()
            for s in shapes:
                # Check if on Edge.Cuts layer (layer enum or name)
                is_edge_cut = False
                if hasattr(s, "layer"):
                    if s.layer == BL_Edge_Cuts or getattr(s.layer, "name", "") == "BL_Edge_Cuts":
                        is_edge_cut = True
                    else:
                        try:
                            layer_name = self.board.get_layer_name(s.layer)
                            if layer_name == "Edge.Cuts":
                                is_edge_cut = True
                        except Exception:
                            pass

                if not is_edge_cut:
                    continue

                # Handle different shape types
                type_name = type(s).__name__
                if type_name == "BoardRectangle":
                    tl = s.top_left
                    br = s.bottom_right
                    corners.extend([
                        {"x": round(tl.x / 1e6, 6), "y": round(tl.y / 1e6, 6)},
                        {"x": round(br.x / 1e6, 6), "y": round(tl.y / 1e6, 6)},
                        {"x": round(br.x / 1e6, 6), "y": round(br.y / 1e6, 6)},
                        {"x": round(tl.x / 1e6, 6), "y": round(br.y / 1e6, 6)},
                    ])
                elif type_name == "BoardSegment":
                    corners.append({"x": round(s.start.x / 1e6, 6), "y": round(s.start.y / 1e6, 6)})
                    corners.append({"x": round(s.end.x / 1e6, 6), "y": round(s.end.y / 1e6, 6)})
                elif hasattr(s, "polygon"):
                    # Polygon nodes
                    for node in s.polygon:
                        corners.append({"x": round(node.x / 1e6, 6), "y": round(node.y / 1e6, 6)})

            data["outline"]["corners"] = corners
        except Exception as e:
            logger.warning(f"Could not read outline shapes via IPC: {e}")

    def _resolve_edge_clearance(self) -> float:
        """Reads min_copper_edge_clearance from .kicad_pro design settings."""
        default_clearance = 0.5
        try:
            pro_path = Path(self.project.path) / f"{self.project.name}.kicad_pro"
            if pro_path.is_file():
                with open(pro_path, "r", encoding="utf-8") as f:
                    pro_data = json.load(f)
                rules = (
                    pro_data.get("board", {})
                    .get("design_settings", {})
                    .get("rules", {})
                )
                val = rules.get("min_copper_edge_clearance")
                if val is not None and isinstance(val, (int, float)) and val > 0:
                    logger.info(f"Loaded copper-to-edge clearance from {pro_path.name}: {val} mm")
                    return float(val)
        except Exception as e:
            logger.debug(f"Could not inspect .kicad_pro for edge clearance: {e}")

        return default_clearance

    def _collect_components(
        self, data: Dict[str, Any], layer_id_to_index: Dict[Any, int]
    ) -> None:
        """Extracts footprints and pads."""
        # 1. Index pads by parent footprint KIID
        pads_by_footprint: Dict[str, List[Any]] = {}
        try:
            all_pads = self.board.get_pads()
            for p in all_pads:
                parent_id = str(p.parent.value) if hasattr(p.parent, "value") else str(p.parent)
                pads_by_footprint.setdefault(parent_id, []).append(p)
        except Exception as e:
            logger.warning(f"Could not read pads via IPC: {e}")

        # 2. Extract footprints
        try:
            footprints = self.board.get_footprints()
            for fp in footprints:
                fp_id = str(fp.id.value) if hasattr(fp.id, "value") else str(fp.id)
                ref = fp.reference_field.text.value if fp.reference_field and fp.reference_field.text else ""
                val = fp.value_field.text.value if fp.value_field and fp.value_field.text else ""
                footprint_id = str(fp.definition.id) if fp.definition and hasattr(fp.definition, "id") else "Package"

                layer_name = "F.Cu"
                try:
                    layer_name = self.board.get_layer_name(fp.layer)
                except Exception:
                    pass

                rotation_deg = fp.orientation.value_degrees if hasattr(fp.orientation, "value_degrees") else 0.0

                comp_dict: Dict[str, Any] = {
                    "reference": ref,
                    "value": val,
                    "footprint": footprint_id,
                    "position": {
                        "x": round(fp.position.x / 1e6, 6),
                        "y": round(fp.position.y / 1e6, 6),
                    },
                    "rotation": round(rotation_deg, 4),
                    "layer": layer_name,
                    "pads": [],
                }

                # Attach pads
                fp_pads = pads_by_footprint.get(fp_id, [])
                for pad in fp_pads:
                    pad_dict = self._serialize_pad(pad, fp.position, rotation_deg)
                    comp_dict["pads"].append(pad_dict)

                data["components"].append(comp_dict)
        except Exception as e:
            logger.warning(f"Could not read footprints via IPC: {e}")

    def _serialize_pad(self, pad: Any, fp_pos: Any, fp_rot_deg: float) -> Dict[str, Any]:
        """Serializes a single pad object."""
        net_name = pad.net.name if pad.net and pad.net.name else ""
        pad_num = str(pad.number) if pad.number is not None else ""

        # Padstack properties
        shape_name = "rect"
        size_x_mm = 1.0
        size_y_mm = 1.0
        drill_mm = 0.0
        layers = ["F.Cu"]

        ps = pad.padstack
        if ps:
            if ps.drill and ps.drill.diameter:
                drill_x = getattr(ps.drill.diameter, "x", getattr(ps.drill.diameter, "x_nm", 0))
                drill_mm = drill_x / 1e6

            # Determine shape and size from copper layers
            if ps.copper_layers:
                first_cl = ps.copper_layers[0]
                shape_enum = str(first_cl.shape)
                if "CIRCLE" in shape_enum:
                    shape_name = "circle"
                elif "OVAL" in shape_enum:
                    shape_name = "oval"
                else:
                    shape_name = "rect"

                if first_cl.size:
                    size_x = getattr(first_cl.size, "x", getattr(first_cl.size, "x_nm", 1000000))
                    size_y = getattr(first_cl.size, "y", getattr(first_cl.size, "y_nm", 1000000))
                    size_x_mm = size_x / 1e6
                    size_y_mm = size_y / 1e6

            # Layers
            layers = []
            for l_enum in ps.layers:
                try:
                    layers.append(self.board.get_layer_name(l_enum))
                except Exception:
                    pass
            if not layers:
                layers = ["F.Cu"]

        # Calculate relative offset from component origin
        dx_nm = pad.position.x - fp_pos.x
        dy_nm = pad.position.y - fp_pos.y

        # Un-rotate offset by component rotation
        angle_rad = -math.radians(fp_rot_deg)
        unrot_x = (dx_nm * math.cos(angle_rad) - dy_nm * math.sin(angle_rad)) / 1e6
        unrot_y = (dx_nm * math.sin(angle_rad) + dy_nm * math.cos(angle_rad)) / 1e6

        return {
            "name": pad_num,
            "netName": net_name,
            "shape": shape_name,
            "size": {"x": round(size_x_mm, 6), "y": round(size_y_mm, 6)},
            "offset": {"x": round(unrot_x, 6), "y": round(unrot_y, 6)},
            "position": {
                "x": round(pad.position.x / 1e6, 6),
                "y": round(pad.position.y / 1e6, 6),
            },
            "drill": round(drill_mm, 6),
            "layers": layers,
        }

    def _collect_traces(self, data: Dict[str, Any], layer_id_to_index: Dict[Any, int]) -> None:
        """Extracts existing tracks."""
        try:
            tracks = self.board.get_tracks()
            tr_id = 1
            for tr in tracks:
                net_name = tr.net.name if tr.net and tr.net.name else ""
                layer_idx = layer_id_to_index.get(tr.layer, 0)
                width_mm = tr.width / 1e6 if tr.width else 0.2
                data["traces"].append({
                    "id": tr_id,
                    "netName": net_name,
                    "width": round(width_mm, 6),
                    "layerIndex": layer_idx,
                    "points": [
                        {"x": round(tr.start.x / 1e6, 6), "y": round(tr.start.y / 1e6, 6)},
                        {"x": round(tr.end.x / 1e6, 6), "y": round(tr.end.y / 1e6, 6)},
                    ]
                })
                tr_id += 1
        except Exception as e:
            logger.warning(f"Could not read tracks via IPC: {e}")

    def _collect_vias(self, data: Dict[str, Any], layer_id_to_index: Dict[Any, int]) -> None:
        """Extracts existing vias."""
        try:
            vias = self.board.get_vias()
            via_id = 1
            total_layers = len(data["layers"])
            for v in vias:
                net_name = v.net.name if v.net and v.net.name else ""
                dia_mm = v.diameter / 1e6 if v.diameter else 0.6
                drill_mm = v.drill_diameter / 1e6 if v.drill_diameter else 0.3
                data["vias"].append({
                    "id": via_id,
                    "netName": net_name,
                    "position": {
                        "x": round(v.position.x / 1e6, 6),
                        "y": round(v.position.y / 1e6, 6),
                    },
                    "diameter": round(dia_mm, 6),
                    "drill": round(drill_mm, 6),
                    "startLayerIndex": 0,
                    "endLayerIndex": max(1, total_layers - 1),
                })
                via_id += 1
        except Exception as e:
            logger.warning(f"Could not read vias via IPC: {e}")

    def _collect_zones(self, data: Dict[str, Any], layer_id_to_index: Dict[Any, int]) -> None:
        """Extracts zones and keepouts."""
        try:
            zones = self.board.get_zones()
            zone_id = 1
            for z in zones:
                net_name = z.net.name if z.net and z.net.name else ""
                first_layer = z.layers[0] if z.layers else None
                layer_idx = layer_id_to_index.get(first_layer, 0)

                points = []
                if hasattr(z, "outline") and z.outline:
                    for pt in z.outline:
                        points.append({"x": round(pt.x / 1e6, 6), "y": round(pt.y / 1e6, 6)})

                data["conductionAreas"].append({
                    "id": zone_id,
                    "netName": net_name,
                    "layerIndex": layer_idx,
                    "isObstacle": bool(z.is_rule_area) if hasattr(z, "is_rule_area") else False,
                    "polygon": points
                })
                zone_id += 1
        except Exception as e:
            logger.warning(f"Could not read zones via IPC: {e}")
