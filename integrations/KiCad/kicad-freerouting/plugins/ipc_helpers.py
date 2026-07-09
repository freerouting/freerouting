# ---------------------------------------------------------------------------
# ipc_helpers.py — KiCad IPC API detection and board serialization
# ---------------------------------------------------------------------------
# This module handles communication with KiCad's IPC API (available in
# KiCad 9+).  It provides:
#   * ``is_ipc_available()`` — probes for IPC support at runtime.
#   * ``get_board_json_via_ipc()`` — serializes the current board to JSON.
#   * ``_build_board_json_manually()`` — fallback that walks pcbnew objects.
# ---------------------------------------------------------------------------

import json
import os
import re
import logging
from pathlib import Path

import pcbnew

logger = logging.getLogger("freerouting")

from .config import (
    IPC_JSON_EXPORT_METHODS,
    IPC_MIN_KICAD_MAJOR,
    IPC_PROBE_ATTRIBUTES,
)


_debug_logs = []


def debug_log(msg):
    global _debug_logs
    _debug_logs.append(str(msg))
    logger.info(msg)


def save_debug_logs(board):
    try:
        filename = board.GetFileName()
        if filename:
            log_file = os.path.splitext(filename)[0] + "_export_debug.log"
            with open(log_file, "w", encoding="utf-8") as f:
                f.write("\n".join(_debug_logs))
    except Exception as e:
        logger.warning(f"Could not save debug log: {e}")


def _to_str(value):
    """Convert a KiCad SWIG ``UTF8`` object to a Python ``str``.

    KiCad's SWIG bindings return ``UTF8`` objects (not Python ``str``)
    from methods like ``net.GetNetname()``, ``board.GetLayerName()``,
    etc.  These cannot be serialized by ``json.dumps()``.  This helper
    safely converts any value to a plain Python ``str``, passing
    already-native strings through unchanged.
    """
    if value is None:
        return ""
    return str(value)


def is_ipc_available():
    """Check whether we can serialize the board for IPC/API mode.

    For a SWIG ActionPlugin running inside KiCad, the protobuf-based
    IPC API methods (e.g. ``GetBoardAsJson``) are **not** exposed on the
    ``pcbnew`` module — they are available only to external IPC clients
    using the ``kicad-python`` package.  However, this plugin has direct
    SWIG access to the board and can always fall back to manual
    serialisation via ``_build_board_json_manually()``.  Therefore,
    IPC/API mode is effectively always available when KiCad 9+ is
    detected (the SWIG bindings are still present but deprecated).

    Returns:
        ``True`` if we can serialise the board (always True for a
        SWIG ActionPlugin running inside KiCad 9+).
    """
    # 1. Check for native IPC-API methods (external-client use case)
    for attr in IPC_PROBE_ATTRIBUTES:
        if hasattr(pcbnew, attr):
            logger.info(f"KiCad IPC API detected (pcbnew.{attr} is available).")
            return True

    # 2. Check version — KiCad 9+ still has SWIG bindings, and our
    #    manual fallback serialisation works on any version.
    try:
        version_str = str(pcbnew.GetBuildVersion()).strip()
        major = int(version_str.split(".")[0])
        if major >= IPC_MIN_KICAD_MAJOR:
            logger.info(
                f"KiCad {version_str} detected — "
                "SWIG ActionPlugin mode, manual serialisation available."
            )
            return True
    except Exception:
        pass

    logger.info("KiCad IPC API is not available.")
    return False


def _probe_ipc_via_json_export():
    """Try calling a JSON-export method on the current board as a probe.

    Returns:
        ``True`` if any export method succeeds, ``False`` otherwise.
    """
    try:
        board = pcbnew.GetBoard()
        if board is None:
            return False
        for method_name in IPC_JSON_EXPORT_METHODS:
            if hasattr(pcbnew, method_name):
                result = getattr(pcbnew, method_name)(board)
                if result is not None:
                    logger.info(f"IPC probe succeeded via pcbnew.{method_name}().")
                    return True
    except Exception as e:
        logger.error(f"IPC probe failed: {e}", exc_info=True)
    return False


def get_board_json_via_ipc():
    """Serialize the current board to KiCad JSON using the IPC API.

    Returns:
        JSON string conforming to the KiCadBoardJson schema.

    Raises:
        RuntimeError: if no board is loaded in KiCad.
    """
    board = pcbnew.GetBoard()
    if board is None:
        raise RuntimeError("No board loaded in KiCad.")

    return _build_board_json_manually(board)


def _build_board_json_manually(board):
    """Build a KiCadBoardJson-compatible dict by walking pcbnew objects.

    This is a best-effort fallback when the IPC API doesn't provide a
    direct JSON export.  It iterates over footprints, tracks, and
    drawings to reconstruct the board data.

    Args:
        board: The ``pcbnew.BOARD`` object to serialize.

    Returns:
        Pretty-printed JSON string.
    """
    global _debug_logs
    _debug_logs = []
    debug_log("Starting manual board JSON serialization...")

    data = {
        "designName": Path(board.GetFileName()).stem if board.GetFileName() else "Untitled",
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

    layer_id_to_index = {}
    idx = 0
    layer_id_count = getattr(pcbnew, "PCB_LAYER_ID_COUNT", getattr(pcbnew, "LAYER_ID_COUNT", 128))
    enabled_layers = board.GetEnabledLayers() if hasattr(board, "GetEnabledLayers") else None
    for i in range(layer_id_count):
        if enabled_layers is None or enabled_layers.Contains(i):
            is_copper = False
            if hasattr(pcbnew, "IsCopperLayer"):
                is_copper = pcbnew.IsCopperLayer(i)
            else:
                is_copper = (i == 0 or i == 31 or (1 <= i < board.GetCopperLayerCount() - 1))
            if is_copper:
                layer_id_to_index[i] = idx
                idx += 1

    _collect_layers(board, data, layer_id_to_index)
    _collect_net_classes(board, data)
    _collect_nets(board, data)
    _collect_clearance_rules(board, data)
    _collect_components(board, data, layer_id_to_index)
    _collect_traces(board, data, layer_id_to_index)
    _collect_vias(board, data)
    _collect_conduction_areas(board, data, layer_id_to_index)
    _collect_outline(board, data)

    save_debug_logs(board)
    return json.dumps(data, indent=2)


def _collect_layers(board, data, layer_id_to_index):
    """Populate ``data["layers"]`` from the board's layer structure."""
    try:
        for layer_id, idx in sorted(layer_id_to_index.items(), key=lambda x: x[1]):
            data["layers"].append({
                "index": idx,
                "name": _to_str(board.GetLayerName(layer_id)),
                "type": "signal",
            })
    except Exception as e:
        logger.warning(f"Warning: could not enumerate layers: {e}", exc_info=True)


def _collect_net_classes(board, data):
    """Populate ``data["netClasses"]`` from the board's netclasses."""
    try:
        netclasses_dict = {}
        
        # Try getting default netclass first
        default_nc = None
        design_settings = board.GetDesignSettings() if hasattr(board, "GetDesignSettings") else None
        if design_settings:
            for attr in ["GetDefaultNetClass", "GetDefault", "GetDefaultNetclass", "m_DefaultNetClass"]:
                if hasattr(design_settings, attr):
                    val = getattr(design_settings, attr)
                    if callable(val):
                        default_nc = val()
                    else:
                        default_nc = val
                    break
        if default_nc:
            netclasses_dict[_to_str(default_nc.GetName())] = default_nc
            
        # Get other netclasses
        netclasses = None
        if hasattr(board, "GetNetClasses"):
            netclasses = board.GetNetClasses()
        elif hasattr(board, "GetAllNetClasses"):
            netclasses = board.GetAllNetClasses()
            
        if netclasses:
            if hasattr(netclasses, "NetClasses"):
                for name, netclass in netclasses.NetClasses().items():
                    netclasses_dict[_to_str(name)] = netclass
            elif hasattr(netclasses, "items"):
                for name, netclass in netclasses.items():
                    netclasses_dict[_to_str(name)] = netclass
            elif hasattr(netclasses, "values"):
                for netclass in netclasses.values():
                    netclasses_dict[_to_str(netclass.GetName())] = netclass
                    
        for name, netclass in netclasses_dict.items():
            nc_data = {
                "name": _to_str(name),
                "clearance": (netclass.GetClearance() / 1e6) if hasattr(netclass, "GetClearance") else 0.2,
                "traceWidth": (netclass.GetTrackWidth() / 1e6) if hasattr(netclass, "GetTrackWidth") else 0.2,
                "viaDiameter": (netclass.GetViaDiameter() / 1e6) if hasattr(netclass, "GetViaDiameter") else 0.6,
                "viaDrill": (netclass.GetViaDrill() / 1e6) if hasattr(netclass, "GetViaDrill") else 0.3,
                "uviaDiameter": (netclass.GetMicroViaDiameter() / 1e6) if hasattr(netclass, "GetMicroViaDiameter") else 0.3,
                "uviaDrill": (netclass.GetMicroViaDrill() / 1e6) if hasattr(netclass, "GetMicroViaDrill") else 0.1,
            }
            if not any(nc["name"] == nc_data["name"] for nc in data["netClasses"]):
                data["netClasses"].append(nc_data)
    except Exception as e:
        logger.warning(f"Warning: could not enumerate netclasses: {e}", exc_info=True)


def _collect_nets(board, data):
    """Populate ``data["nets"]`` from the board's netinfo list."""
    debug_log("Starting _collect_nets...")
    try:
        debug_log(f"board type: {type(board)}")
        for attr in ["GetNetsByNetcode", "GetNetsByName", "GetNets", "GetNetInfoList"]:
            debug_log(f"board has {attr}: {hasattr(board, attr)}")
            
        nets = None
        if hasattr(board, "GetNetsByNetcode"):
            nets = board.GetNetsByNetcode()
            debug_log("Using board.GetNetsByNetcode()")
        elif hasattr(board, "GetNetsByName"):
            nets = board.GetNetsByName()
            debug_log("Using board.GetNetsByName()")
        elif hasattr(board, "GetNets"):
            nets = board.GetNets()
            debug_log("Using board.GetNets()")
        elif hasattr(board, "GetNetInfoList"):
            nets = board.GetNetInfoList()
            debug_log("Using board.GetNetInfoList()")
            
        if not nets:
            debug_log("nets collection is None or empty!")
            return
            
        debug_log(f"nets type: {type(nets)}")
        for attr in ["items", "values", "NetsByNetcode", "NetsByName", "GetNetCount", "GetNetItem"]:
            debug_log(f"nets object has {attr}: {hasattr(nets, attr)}")
            
        net_list = []
        iterator = []
        if hasattr(nets, "items"):
            iterator = nets.items()
            debug_log("nets has items() - iterating items")
        elif hasattr(nets, "values"):
            iterator = [(n.GetNetCode() if hasattr(n, "GetNetCode") else 0, n) for n in nets.values()]
            debug_log("nets has values() - iterating values")
        elif hasattr(nets, "NetsByNetcode"):
            iterator = nets.NetsByNetcode().items()
            debug_log("nets has NetsByNetcode() - iterating items")
        elif hasattr(nets, "NetsByName"):
            iterator = [(net.GetNetCode(), net) for net in nets.NetsByName().values()]
            debug_log("nets has NetsByName() - iterating values")
        else:
            try:
                iterator = list(enumerate(nets))
                debug_log("nets is directly iterable")
            except TypeError as te:
                debug_log(f"nets is not directly iterable: {te}")
                if hasattr(nets, "GetNetCount") and hasattr(nets, "GetNetItem"):
                    debug_log("Using GetNetCount/GetNetItem")
                    iterator = [(i, nets.GetNetItem(i)) for i in range(nets.GetNetCount())]
                    
        iterator_list = list(iterator)
        debug_log(f"iterator length: {len(iterator_list)}")
        
        for key, net in iterator_list:
            net_code = net.GetNetCode() if hasattr(net, "GetNetCode") else 0
            net_name = _to_str(net.GetNetname()) if hasattr(net, "GetNetname") else "unknown"
            debug_log(f"Found net: code={net_code}, name={net_name}")
            if net_code <= 0:
                continue
            class_name = "Default"
            if hasattr(net, "GetNetClassName"):
                class_name = _to_str(net.GetNetClassName())
            elif hasattr(net, "GetClassName"):
                class_name = _to_str(net.GetClassName())
            elif hasattr(net, "GetNetClass"):
                nc = net.GetNetClass()
                if nc and hasattr(nc, "GetName"):
                    class_name = _to_str(nc.GetName())
            net_list.append({
                "id": net_code,
                "name": net_name or f"Net-{net_code}",
                "className": class_name,
                "containsPlane": False,
            })
        data["nets"] = net_list
        debug_log(f"Successfully collected {len(net_list)} nets.")
    except Exception as e:
        import traceback
        debug_log(f"Error in _collect_nets: {e}\n{traceback.format_exc()}")
        logger.warning(f"Warning: could not enumerate nets: {e}", exc_info=True)


def _collect_clearance_rules(board, data):
    """Populate ``data["clearanceRules"]`` by parsing custom design rules."""
    debug_log("Starting _collect_clearance_rules...")
    try:
        filename = board.GetFileName()
        debug_log(f"board file name: {filename}")
        rules_text = ""
        
        # 1. Try reading custom rules from the .kicad_pcb file itself
        if filename and os.path.exists(filename):
            try:
                debug_log(f"Reading from pcb file: {filename}")
                with open(filename, 'r', encoding='utf-8') as f:
                    content = f.read()
                    rules_match = re.search(r'\(custom_rules\s+"((?:[^"\\]|\\.)*)"\)', content, re.DOTALL)
                    if rules_match:
                        rules_text = rules_match.group(1)
                        rules_text = rules_text.replace('\\"', '"').replace('\\\\', '\\').replace('\\n', '\n')
                        debug_log("Successfully parsed custom rules from .kicad_pcb")
                    else:
                        debug_log("No custom_rules section found in .kicad_pcb")
            except Exception as fe:
                debug_log(f"Could not read custom rules from .kicad_pcb file: {fe}")
                
        # 2. Fallback to .kicad_dru file
        if not rules_text and filename:
            dru_file = os.path.splitext(filename)[0] + ".kicad_dru"
            debug_log(f"Checking for .kicad_dru file: {dru_file}")
            if os.path.exists(dru_file):
                try:
                    with open(dru_file, 'r', encoding='utf-8') as f:
                        rules_text = f.read()
                        debug_log("Successfully read custom rules from .kicad_dru")
                except Exception as de:
                    debug_log(f"Could not read .kicad_dru: {de}")
                    
        # 3. Fallback to GetDesignSettings() properties
        if not rules_text:
            settings = board.GetDesignSettings() if hasattr(board, "GetDesignSettings") else None
            debug_log(f"settings object: {settings}")
            if settings:
                for attr in ["m_CustomRules", "CustomRules", "GetCustomRules", "GetCustomRulesText"]:
                    debug_log(f"settings has {attr}: {hasattr(settings, attr)}")
                    if hasattr(settings, attr):
                        val = getattr(settings, attr)
                        if callable(val):
                            rules_text = val()
                        else:
                            rules_text = val
                        debug_log(f"Read rules text using settings.{attr}")
                        break
        
        if not rules_text:
            debug_log("No custom rules found in any source.")
            return
            
        debug_log(f"Found rules text (length: {len(rules_text)}). Parsing...")
        rule_pattern = re.compile(r'\(rule\s+"([^"]+)"\s*(.*?)\)', re.DOTALL)
        clearance_pattern = re.compile(r'\(constraint\s+clearance\s+\(min\s+([\d.]+)(mm|mil|in|um)\)\)')
        condition_pattern = re.compile(r'A\.NetClass\s*==\s*\'([^\']+)\'.*?B\.NetClass\s*==\s*\'([^\']+)\'')
        condition_pattern_reverse = re.compile(r'B\.NetClass\s*==\s*\'([^\']+)\'.*?A\.NetClass\s*==\s*\'([^\']+)\'')
        
        rules_found = 0
        for match in rule_pattern.finditer(rules_text):
            rule_name = match.group(1)
            rule_body = match.group(2)
            debug_log(f"Parsing rule: {rule_name}")
            
            cl_match = clearance_pattern.search(rule_body)
            if not cl_match:
                debug_log(f"No clearance constraint in rule {rule_name}")
                continue
            val_str = cl_match.group(1)
            unit_str = cl_match.group(2)
            
            val = float(val_str)
            if unit_str == "mil":
                val = val * 0.0254
            elif unit_str == "in":
                val = val * 25.4
            elif unit_str == "um":
                val = val / 1000.0
                
            class_a = None
            class_b = None
            
            cond_match = condition_pattern.search(rule_body)
            if cond_match:
                class_a = cond_match.group(1)
                class_b = cond_match.group(2)
            else:
                cond_match_rev = condition_pattern_reverse.search(rule_body)
                if cond_match_rev:
                    class_b = cond_match_rev.group(1)
                    class_a = cond_match_rev.group(2)
                    
            debug_log(f"Rule {rule_name}: classA={class_a}, classB={class_b}, clearance={val}")
            if class_a and class_b:
                data["clearanceRules"].append({
                    "classA": class_a,
                    "classB": class_b,
                    "clearance": val
                })
                rules_found += 1
        debug_log(f"Successfully collected {rules_found} custom clearance rules.")
    except Exception as e:
        import traceback
        debug_log(f"Error in _collect_clearance_rules: {e}\n{traceback.format_exc()}")
        logger.warning(f"Warning: could not parse custom design rules: {e}", exc_info=True)


def _collect_components(board, data, layer_id_to_index):
    """Populate ``data["components"]`` and their pads."""
    try:
        for fp in board.GetFootprints():
            pos = fp.GetPosition()
            component = {
                "reference": _to_str(fp.GetReference()),
                "value": _to_str(fp.GetValue()),
                "footprint": _to_str(fp.GetFPIDAsString()) if hasattr(fp, "GetFPIDAsString") else (_to_str(fp.GetFPID().GetLibItemName()) if fp.GetFPID() else ""),
                "position": {"x": pos.x / 1e6, "y": pos.y / 1e6},
                "rotation": (
                    fp.GetOrientationDegrees()
                    if hasattr(fp, "GetOrientationDegrees")
                    else 0.0
                ),
                "layer": "F.Cu" if fp.GetLayer() == 0 else "B.Cu",
                "pads": [],
            }
            import math
            fp_rot = component["rotation"]
            rot_rad = -math.radians(fp_rot)
            cos_rot = math.cos(rot_rad)
            sin_rot = math.sin(rot_rad)
            for pad in fp.Pads():
                pad_net = pad.GetNet()
                pad_pos = pad.GetPosition()
                pad_size = pad.GetSize()
                dx = (pad_pos.x - pos.x) / 1e6
                dy = (pad_pos.y - pos.y) / 1e6
                local_dx = dx * cos_rot - dy * sin_rot
                local_dy = dx * sin_rot + dy * cos_rot
                
                shape_val = pad.GetShape() if hasattr(pad, "GetShape") else -1
                shape_str = "rect"
                if hasattr(pcbnew, "PAD_SHAPE_CIRCLE") and shape_val == pcbnew.PAD_SHAPE_CIRCLE:
                    shape_str = "circle"
                elif hasattr(pcbnew, "PAD_SHAPE_OVAL") and shape_val == pcbnew.PAD_SHAPE_OVAL:
                    shape_str = "oval"
                
                drill_val = 0.0
                if hasattr(pad, "GetDrillSize"):
                    drill_size = pad.GetDrillSize()
                    if hasattr(drill_size, "x"):
                        drill_val = drill_size.x / 1e6
                
                pad_layers = []
                if hasattr(pad, "GetLayerSet"):
                    pad_layer_set = pad.GetLayerSet()
                    for layer_id in layer_id_to_index.keys():
                        if pad_layer_set.Contains(layer_id):
                            pad_layers.append(_to_str(board.GetLayerName(layer_id)))
                else:
                    pad_layers.append(_to_str(board.GetLayerName(pad.GetLayer())))
                    
                component["pads"].append({
                    "name": _to_str(pad.GetPadName()),
                    "netName": _to_str(pad_net.GetNetname()) if pad_net else "",
                    "shape": shape_str,
                    "size": {"x": pad_size.x / 1e6, "y": pad_size.y / 1e6},
                    "offset": {
                        "x": local_dx,
                        "y": local_dy,
                    },
                    "drill": drill_val,
                    "layers": pad_layers,
                })
            data["components"].append(component)
    except Exception as e:
        logger.warning(f"Warning: could not enumerate components: {e}", exc_info=True)


def _collect_traces(board, data, layer_id_to_index):
    """Populate ``data["traces"]`` from PCB_TRACK items."""
    try:
        trace_id = 1
        for track in board.GetTracks():
            if track.Type() == pcbnew.PCB_TRACE_T:
                net = track.GetNet()
                points = []
                try:
                    s, e = track.GetStart(), track.GetEnd()
                    points.append({"x": s.x / 1e6, "y": s.y / 1e6})
                    points.append({"x": e.x / 1e6, "y": e.y / 1e6})
                except Exception:
                    pass
                layer_id = track.GetLayer()
                layer_idx = layer_id_to_index.get(layer_id, 0)
                data["traces"].append({
                    "id": trace_id,
                    "netName": _to_str(net.GetNetname()) if net else "",
                    "width": track.GetWidth() / 1e6,
                    "layerIndex": layer_idx,
                    "points": points,
                })
                trace_id += 1
    except Exception as e:
        logger.warning(f"Warning: could not enumerate traces: {e}", exc_info=True)


def _collect_vias(board, data):
    """Populate ``data["vias"]`` from PCB_VIA items."""
    try:
        via_id = 1
        for track in board.GetTracks():
            if track.Type() == pcbnew.PCB_VIA_T:
                net = track.GetNet()
                pos = track.GetStart()
                drill = (
                    track.GetDrillValue()
                    if hasattr(track, "GetDrillValue")
                    else track.GetWidth() * 0.5
                )
                data["vias"].append({
                    "id": via_id,
                    "netName": _to_str(net.GetNetname()) if net else "",
                    "position": {"x": pos.x / 1e6, "y": pos.y / 1e6},
                    "diameter": track.GetWidth() / 1e6,
                    "drill": drill / 1e6,
                    "startLayerIndex": 0,
                    "endLayerIndex": (
                        board.GetCopperLayerCount() - 1
                        if hasattr(board, "GetCopperLayerCount")
                        else 1
                    ),
                })
                via_id += 1
    except Exception as e:
        logger.warning(f"Warning: could not enumerate vias: {e}", exc_info=True)


def _collect_conduction_areas(board, data, layer_id_to_index):
    """Populate ``data["conductionAreas"]`` from board.Zones()."""
    try:
        zone_id = 1
        for zone in board.Zones():
            layer_set = zone.GetLayerSet()
            for layer_id, layer_idx in layer_id_to_index.items():
                if layer_set.Contains(layer_id):
                    net = zone.GetNet()
                    outline = zone.Outline()
                    if outline and outline.OutlineCount() > 0:
                        for outline_idx in range(outline.OutlineCount()):
                            poly = outline.Outline(outline_idx)
                            points = []
                            for pt_idx in range(poly.PointCount()):
                                pt = poly.CPoint(pt_idx)
                                points.append({"x": pt.x / 1e6, "y": pt.y / 1e6})
                            
                            data["conductionAreas"].append({
                                "id": zone_id,
                                "netName": _to_str(net.GetNetname()) if net else "",
                                "layerIndex": layer_idx,
                                "isObstacle": zone.GetIsKeepout() if hasattr(zone, "GetIsKeepout") else False,
                                "polygon": points
                            })
                            zone_id += 1
    except Exception as e:
        logger.warning(f"Warning: could not enumerate conduction areas: {e}", exc_info=True)


def _collect_outline(board, data):
    """Populate ``data["outline"]`` from Edge.Cuts drawings."""
    global _debug_logs
    try:
        # 1. Try using the native GetBoardPolygonOutlines method if available (KiCad 6+)
        if hasattr(board, "GetBoardPolygonOutlines"):
            outlines = pcbnew.SHAPE_POLY_SET()
            board.GetBoardPolygonOutlines(outlines)
            if outlines.OutlineCount() > 0:
                debug_log(f"Found {outlines.OutlineCount()} outlines via GetBoardPolygonOutlines.")
                outline = outlines.Outline(0)
                for j in range(outline.PointCount()):
                    pt = outline.CPoint(j)
                    data["outline"]["corners"].append(
                        {"x": pt.x / 1e6, "y": pt.y / 1e6}
                    )
                debug_log(f"Successfully collected {len(data['outline']['corners'])} outline corners via SHAPE_POLY_SET.")
                return
    except Exception as e:
        debug_log(f"GetBoardPolygonOutlines failed, falling back to drawings scan: {e}")

    # 2. Fallback to manual drawings iteration on Edge.Cuts
    try:
        edge_layer = (
            board.GetLayerID("Edge.Cuts")
            if hasattr(board, "GetLayerID")
            else -1
        )
        if edge_layer >= 0:
            corners = []
            for drawing in board.GetDrawings():
                if drawing.GetLayer() == edge_layer:
                    # If it's a shape (rectangle, polygon, circle etc.) with GetPolyShape
                    if hasattr(drawing, "GetPolyShape"):
                        try:
                            poly = drawing.GetPolyShape()
                            if poly and poly.OutlineCount() > 0:
                                outline = poly.Outline(0)
                                for j in range(outline.PointCount()):
                                    pt = outline.CPoint(j)
                                    corners.append({"x": pt.x / 1e6, "y": pt.y / 1e6})
                                continue
                        except Exception:
                            pass

                    # Otherwise handle standard drawings (e.g. line segments)
                    try:
                        if hasattr(drawing, "GetStart"):
                            s = drawing.GetStart()
                            corners.append({"x": s.x / 1e6, "y": s.y / 1e6})
                    except Exception:
                        pass

            data["outline"]["corners"] = corners
            debug_log(f"Collected {len(corners)} outline corners via drawings fallback.")
    except Exception as e:
        logger.warning(f"Warning: could not enumerate outline: {e}", exc_info=True)