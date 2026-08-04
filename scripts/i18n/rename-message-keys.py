#!/usr/bin/env python3
"""Rename generic error_* / message_* i18n keys to semantic names in all locale bundles."""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from properties_io import load_properties, write_properties

RESOURCE_ROOT = Path("src/main/resources/app/freerouting")

# Per-bundle renames: old_key -> new_key
BUNDLE_RENAMES: dict[str, dict[str, str]] = {
    "gui/BoardFrame": {
        "error_1": "error_gui_defaults_read_failed",
        "error_2": "error_board_save_failed",
        "error_3": "error_no_write_permission",
        "error_4": "error_board_frame_write_failed",
        "error_5": "error_output_file_close_failed",
        "error_6": "error_dsn_read_failed",
        "error_7": "error_dsn_outline_missing",
        "message_8": "error_design_file_read_failed",
    },
    "gui/GuiManager": {
        "message_3": "error_board_design_open_failed",
        "message_4": "status_design_file_label",
        "message_5": "status_design_file_opened",
        "message_6": "file_not_found_prefix",
        "message_7": "file_not_found_suffix",
        "message_8": "error_design_file_read_failed",
    },
    "gui/WindowNets": {
        "message_1": "assign_net_class_prompt",
        "message_2": "assign_net_class_dialog_title",
    },
    "gui/WindowObjectList": {
        "message_1": "assign_net_class_prompt",
        "message_2": "assign_net_class_dialog_title",
    },
    "gui/WindowNetClasses": {
        "message_1": "default_net_class_not_removed",
        "message_2": "net_class_not_removed_in_use",
    },
    "gui/WindowEditVias": {
        "message_1": "last_via_not_removed",
        "message_2": "via_not_removed_in_rule",
    },
    "gui/WindowVia": {
        "message_1": "prompt_new_padstack_name",
        "message_2": "padstack_name_exists",
        "message_3": "prompt_default_radius",
        "message_4": "padstack_not_removed_in_use",
        "message_5": "prompt_new_via_rule_name",
    },
    "gui/BoardMenuFile": {
        "message_1": "error_design_save_failed",
        "message_2": "status_design_save_as_prefix",
        "message_3": "info_legal_file_extensions",
        "message_4": "status_textfile_label",
        "message_5": "status_specctra_text_written",
        "message_6": "status_writing_textfile",
        "message_7": "status_failed",
        "message_8": "error_logfile_create_failed",
        "message_9": "status_writing_logfile",
        "message_10": "error_logfile_read_failed",
        "message_11": "status_session_file_label",
        "message_12": "status_specctra_session_written",
        "message_13": "status_writing_session_file",
        "message_14": "status_eagle_script_file_label",
        "message_15": "status_written",
        "message_16": "status_creating_eagle_script",
        "message_17": "status_gui_defaults_saved",
        "message_18": "error_gui_defaults_save_failed",
        "message_19": "error_dsn_extension_required",
        "message_20": "info_host_cad_session_filename",
        "message_21": "info_rename_file_if_needed",
        "message_22": "info_eagle_scr_extension",
    },
}


def rename_bundle_file(path: Path, renames: dict[str, str]) -> int:
    props = load_properties(path)
    changed = 0
    new_props: dict[str, str] = {}
    for key, value in props.items():
        new_key = renames.get(key, key)
        if new_key != key:
            changed += 1
        new_props[new_key] = value
    if changed:
        write_properties(path, new_props)
    return changed


def main() -> int:
    total_files = 0
    total_keys = 0
    for bundle_rel, renames in BUNDLE_RENAMES.items():
        parts = bundle_rel.split("/")
        bundle_dir = RESOURCE_ROOT.joinpath(*parts[:-1])
        base_name = parts[-1]
        for props_file in sorted(bundle_dir.glob(f"{base_name}_*.properties")):
            count = rename_bundle_file(props_file, renames)
            if count:
                total_files += 1
                total_keys += count
                print(f"  {props_file}: {count} keys")
    print(f"Renamed {total_keys} keys in {total_files} files.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
