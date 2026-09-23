"""
ipc_board_writer.py — KiCad Protobuf IPC Board Writer for Freerouting
----------------------------------------------------------------------
Writes routed tracks and vias back into the live KiCad board using
KiCad's official Protocol Buffers IPC API (kipy).
All modifications are wrapped in an atomic commit transaction
(begin_commit / create_items / push_commit) ensuring single-step
undo support (Ctrl+Z) in the KiCad GUI and automatic rollback on error.
"""

from __future__ import annotations

import json
import logging
from typing import Any, Dict, List, Optional

logger = logging.getLogger("freerouting.ipc_writer")


class KiCadIpcBoardWriter:
    """Applies routed traces and vias to KiCad via Protocol Buffers IPC (kipy)."""

    def __init__(
        self,
        board: Optional[Any] = None,
        socket_path: Optional[str] = None,
        timeout_ms: int = 5000,
    ):
        """Initializes board writer.

        Args:
            board: Optional existing `kipy.board.Board` instance.
            socket_path: Optional NNG socket path (if board is None).
            timeout_ms: Socket timeout in milliseconds.
        """
        if board is not None:
            self.board = board
            self.kicad = board.client if hasattr(board, "client") else None
        else:
            import kipy

            self.kicad = kipy.KiCad(socket_path=socket_path, timeout_ms=timeout_ms)
            self.board = self.kicad.get_board()

    def write_routed_board(
        self,
        board_data: Dict[str, Any],
        replace_unfixed: bool = True,
        commit_message: str = "Freerouting Autoroute",
    ) -> Dict[str, int]:
        """Applies routed traces and vias to the board in an atomic commit.

        Args:
            board_data: Dictionary conforming to KiCadBoardJson schema,
                containing "traces", "vias", and "layers".
            replace_unfixed: If True, existing unlocked tracks and vias
                are deleted before creating the new routed items.
            commit_message: Message for KiCad's undo history.

        Returns:
            Dictionary with counts of created and removed items.
        """
        from kipy.board_types import Track, Via, ViaType
        from kipy.geometry import Vector2
        from kipy.proto.board.board_types_pb2 import BL_F_Cu, BL_B_Cu

        # 1. Map net names to Net objects
        net_map: Dict[str, Any] = {}
        try:
            for net in self.board.get_nets():
                if net.name:
                    net_map[net.name] = net
            logger.info(f"Available nets in KiCad board: {list(net_map.keys())}")
        except Exception as e:
            logger.warning(f"Could not enumerate nets via IPC: {e}")

        # 2. Build layer index -> BoardLayer enum mapping
        layer_specs = board_data.get("layers", [])
        index_to_layer_enum: Dict[int, Any] = {}
        for layer_spec in layer_specs:
            idx = layer_spec.get("index", 0)
            name = layer_spec.get("name", "")
            try:
                layer_enum = self.board.get_layer_by_name(name)
                index_to_layer_enum[idx] = layer_enum
            except Exception:
                pass

        # Fallback defaults for 2-layer boards
        if 0 not in index_to_layer_enum:
            index_to_layer_enum[0] = BL_F_Cu
        if 1 not in index_to_layer_enum:
            index_to_layer_enum[1] = BL_B_Cu

        # 3. Begin atomic transaction
        logger.info("Beginning atomic commit on KiCad board...")
        try:
            commit = self.board.begin_commit()
        except Exception as e:
            if "busy" in str(e).lower():
                logger.warning(f"KiCad IPC server is busy during begin_commit: {e}. Falling back to in-process commit if available.")
                try:
                    from plugins.plugin import FreeroutingPlugin
                    FreeroutingPlugin._apply_result_to_kicad(json.dumps(board_data))
                    return {
                        "created_tracks": len(board_data.get("traces", [])),
                        "created_vias": len(board_data.get("vias", [])),
                        "removed_tracks": 0,
                        "removed_vias": 0,
                    }
                except Exception as fb_err:
                    logger.error(f"Fallback to in-process write-back also failed: {fb_err}", exc_info=True)
            raise

        removed_tracks_count = 0
        removed_vias_count = 0
        created_tracks_count = 0
        created_vias_count = 0

        try:
            # 4. Remove existing unfixed tracks and vias if requested
            if replace_unfixed:
                to_remove: List[Any] = []
                try:
                    for t in self.board.get_tracks():
                        if not t.locked:
                            to_remove.append(t)
                            removed_tracks_count += 1
                except Exception as e:
                    logger.debug(f"No existing tracks to enumerate: {e}")

                try:
                    for v in self.board.get_vias():
                        if not v.locked:
                            to_remove.append(v)
                            removed_vias_count += 1
                except Exception as e:
                    logger.debug(f"No existing vias to enumerate: {e}")

                if to_remove:
                    logger.info(
                        f"Removing {removed_tracks_count} unlocked tracks and "
                        f"{removed_vias_count} unlocked vias..."
                    )
                    self.board.remove_items(to_remove)

            # 5. Build new Track and Via objects
            items_to_create: List[Any] = []

            # Traces
            for tr in board_data.get("traces", []):
                net_name = tr.get("netName", "")
                net_obj = net_map.get(net_name)
                layer_idx = tr.get("layerIndex", 0)
                layer_enum = index_to_layer_enum.get(layer_idx, BL_F_Cu)
                width_mm = float(tr.get("width", 0.25))
                width_nm = int(round(width_mm * 1e6))

                points = tr.get("points", [])
                for i in range(len(points) - 1):
                    p1 = points[i]
                    p2 = points[i + 1]

                    t = Track()
                    t.start = Vector2.from_xy_mm(float(p1["x"]), float(p1["y"]))
                    t.end = Vector2.from_xy_mm(float(p2["x"]), float(p2["y"]))
                    t.width = width_nm
                    t.layer = layer_enum
                    if net_obj is not None:
                        t.net = net_obj
                        logger.debug(f"Assigned net to track: {t.net.name} (input: {net_name})")
                    else:
                        logger.warning(f"Net not found in board: '{net_name}'")

                    items_to_create.append(t)
                    created_tracks_count += 1

            # Vias
            for vj in board_data.get("vias", []):
                net_name = vj.get("netName", "")
                net_obj = net_map.get(net_name)
                pos = vj.get("position", {})
                dia_mm = float(vj.get("diameter", 0.6))
                drill_mm = float(vj.get("drill", 0.3))

                v = Via()
                v.type = ViaType.VT_THROUGH
                v.position = Vector2.from_xy_mm(float(pos.get("x", 0.0)), float(pos.get("y", 0.0)))
                v.diameter = int(round(dia_mm * 1e6))
                v.drill_diameter = int(round(drill_mm * 1e6))
                if net_obj is not None:
                    v.net = net_obj

                items_to_create.append(v)
                created_vias_count += 1

            # 6. Create items and push commit
            if items_to_create:
                logger.info(
                    f"Creating {created_tracks_count} track segments and "
                    f"{created_vias_count} vias via IPC..."
                )
                for idx, itm in enumerate(items_to_create):
                    logger.info(f"Item {idx} before create: {itm.proto}")
                self.board.create_items(items_to_create)

            self.board.push_commit(commit, message=commit_message)
            logger.info(f"Successfully committed changes: '{commit_message}'")

            return {
                "created_tracks": created_tracks_count,
                "created_vias": created_vias_count,
                "removed_tracks": removed_tracks_count,
                "removed_vias": removed_vias_count,
            }

        except Exception as e:
            logger.error(f"Error during board write-back transaction: {e}", exc_info=True)
            logger.info("Dropping (rolling back) open commit transaction...")
            try:
                self.board.drop_commit(commit)
            except Exception as drop_err:
                logger.warning(f"Could not drop commit: {drop_err}")
            raise
