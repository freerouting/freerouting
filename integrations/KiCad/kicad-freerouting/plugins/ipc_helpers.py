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
import logging
from pathlib import Path

import pcbnew

logger = logging.getLogger("freerouting")

from .config import (
    IPC_JSON_EXPORT_METHODS,
    IPC_MIN_KICAD_MAJOR,
    IPC_PROBE_ATTRIBUTES,
)


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

    First tries the native IPC JSON-export methods.  If none are
    available, falls back to ``_build_board_json_manually()``.

    Returns:
        JSON string conforming to the ``KiCadBoardJson`` schema.

    Raises:
        RuntimeError: if no board is loaded in KiCad.
    """
    board = pcbnew.GetBoard()
    if board is None:
        raise RuntimeError("No board loaded in KiCad.")

    # Try native IPC JSON export methods
    for method_name in IPC_JSON_EXPORT_METHODS:
        if hasattr(pcbnew, method_name):
            try:
                result = getattr(pcbnew, method_name)(board)
                if isinstance(result, str) and result.strip():
                    return result
                elif isinstance(result, (dict, list)):
                    return json.dumps(result)
            except Exception as e:
                logger.error(f"pcbnew.{method_name}() failed: {e}", exc_info=True)
                continue

    # Fallback: manually walk pcbnew objects
    logger.info("Falling back to manual board JSON serialization.")
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

    _collect_layers(board, data)
    _collect_nets(board, data)
    _collect_components(board, data)
    _collect_traces(board, data)
    _collect_vias(board, data)
    _collect_outline(board, data)

    return json.dumps(data, indent=2)


def _collect_layers(board, data):
    """Populate ``data["layers"]`` from the board's layer structure."""
    try:
        for i in range(board.GetCopperLayerCount()):
            data["layers"].append({
                "index": i,
                "name": _to_str(board.GetLayerName(i)),
                "type": "signal",
            })
    except Exception as e:
        logger.warning(f"Warning: could not enumerate layers: {e}", exc_info=True)


def _collect_nets(board, data):
    """Populate ``data["nets"]`` from the board's track items."""
    try:
        net_codes = {}
        net_id = 1
        for item in board.GetTracks():
            net = item.GetNet()
            if net and net.GetNetCode() not in net_codes:
                net_codes[net.GetNetCode()] = {
                    "id": net_id,
                    "name": _to_str(net.GetNetname()) or f"Net-{net.GetNetCode()}",
                    "className": "default",
                    "containsPlane": False,
                }
                net_id += 1
        data["nets"] = list(net_codes.values())
    except Exception as e:
        logger.warning(f"Warning: could not enumerate nets: {e}", exc_info=True)


def _collect_components(board, data):
    """Populate ``data["components"]`` and their pads."""
    try:
        for fp in board.GetFootprints():
            pos = fp.GetPosition()
            component = {
                "reference": _to_str(fp.GetReference()),
                "value": _to_str(fp.GetValue()),
                "footprint": _to_str(fp.GetFPID().GetLibItemName()) if fp.GetFPID() else "",
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
                    "layers": [],
                })
            data["components"].append(component)
    except Exception as e:
        logger.warning(f"Warning: could not enumerate components: {e}", exc_info=True)


def _collect_traces(board, data):
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
                data["traces"].append({
                    "id": trace_id,
                    "netName": _to_str(net.GetNetname()) if net else "",
                    "width": track.GetWidth() / 1e6,
                    "layerIndex": track.GetLayer(),
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


def _collect_outline(board, data):
    """Populate ``data["outline"]`` from Edge.Cuts drawings."""
    try:
        edge_layer = (
            board.GetLayerID("Edge.Cuts")
            if hasattr(board, "GetLayerID")
            else -1
        )
        if edge_layer >= 0:
            for drawing in board.GetDrawings():
                if drawing.GetLayer() == edge_layer:
                    try:
                        if hasattr(drawing, "GetStart"):
                            s = drawing.GetStart()
                            data["outline"]["corners"].append(
                                {"x": s.x / 1e6, "y": s.y / 1e6}
                            )
                    except Exception:
                        pass
    except Exception as e:
        logger.warning(f"Warning: could not enumerate outline: {e}", exc_info=True)