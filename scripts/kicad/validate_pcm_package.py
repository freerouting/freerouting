#!/usr/bin/env python3
"""
validate_pcm_package.py — Pre-verification for KiCad PCM (Plugin and Content Manager) packages.

Validates that:
1. KiCad plugin zip archives strictly adhere to KiCad PCM whitelist rules:
   - Only 'metadata.json', 'resources/icon.png', and files inside 'plugins/' are allowed.
   - Test files ('test_*.py', '*_test.py', 'tests/') and stray root/resource files are rejected.
2. In-archive and repo-level 'metadata.json' strictly conform to the KiCad PCM schema:
   - All required top-level fields ('$schema', 'name', 'description', 'identifier', 'type', 'author', 'license', 'versions') are present and valid.
   - In-archive 'metadata.json' contains exactly 1 version and no keys starting with 'download_'.
   - Repo-level 'metadata.json' has valid checksums, download URLs, and sizes.
3. The repo-level 'metadata.json' contains the target version with matching SHA-256,
   download_size, and install_size.
4. Source directory does not contain stray files or tests that would be packaged into the zip.
5. Missing package archives are treated as hard errors, not silent passes.

Usage:
    python scripts/kicad/validate_pcm_package.py [--version <version>] [--zip-file <zip_file>]
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import sys
import zipfile

ALLOWED_ROOT_FILES = {"metadata.json"}
ALLOWED_RESOURCE_FILES = {"resources/icon.png"}
VALID_PCM_SCHEMAS = {
    "https://go.kicad.org/pcm/schemas/v1",
    "https://go.kicad.org/pcm/schemas/v2",
}


def check_png_header(data: bytes) -> bool:
    """Verify standard PNG signature and IHDR block."""
    if len(data) < 24:
        return False
    png_signature = b"\x89PNG\r\n\x1a\n"
    if not data.startswith(png_signature):
        return False
    if data[12:16] != b"IHDR":
        return False
    width, height = struct.unpack(">II", data[16:24])
    return width > 0 and height > 0


def is_test_file(norm_path: str) -> bool:
    """Check if path represents a test file or directory."""
    parts = norm_path.split("/")
    filename = parts[-1]
    if "tests" in parts or "__tests__" in parts:
        return True
    if filename.startswith("test_") or filename.endswith("_test.py"):
        return True
    return False


def is_allowed_archive_path(archive_path: str) -> bool:
    norm_path = archive_path.replace("\\", "/").lstrip("/")
    if is_test_file(norm_path):
        return False
    if norm_path in ALLOWED_ROOT_FILES:
        return True
    if norm_path in ALLOWED_RESOURCE_FILES:
        return True
    if norm_path.startswith("plugins/"):
        return True
    return False


def validate_pcm_metadata_schema(meta: dict, is_package: bool) -> list[str]:
    """Validate metadata structure against KiCad PCM schema requirements."""
    errors = []
    scope = "In-archive" if is_package else "Repository"

    # 1. Required top-level fields
    schema_url = meta.get("$schema")
    if not schema_url or not isinstance(schema_url, str):
        errors.append(f"{scope} metadata.json must contain a valid '$schema' URI.")
    elif not any(valid_prefix in schema_url for valid_prefix in VALID_PCM_SCHEMAS):
        errors.append(
            f"{scope} metadata.json has unexpected '$schema': '{schema_url}'. Expected KiCad PCM v1 or v2 schema."
        )

    for field in ("name", "description", "identifier", "type", "license"):
        val = meta.get(field)
        if not val or not isinstance(val, str) or not val.strip():
            errors.append(f"{scope} metadata.json missing or invalid required string field '{field}'.")

    if meta.get("type") != "plugin":
        errors.append(f"{scope} metadata.json 'type' must be 'plugin', found '{meta.get('type')}'.")

    author = meta.get("author")
    if not isinstance(author, dict) or not author.get("name"):
        errors.append(f"{scope} metadata.json 'author' must be an object with a 'name' field.")

    versions = meta.get("versions")
    if not isinstance(versions, list) or len(versions) == 0:
        errors.append(f"{scope} metadata.json 'versions' must be a non-empty array.")
        return errors

    if is_package and len(versions) != 1:
        errors.append(
            f"In-archive metadata.json must contain exactly 1 version entry, found {len(versions)}."
        )

    for idx, v_entry in enumerate(versions):
        prefix = f"{scope} metadata.json versions[{idx}]"
        if not isinstance(v_entry, dict):
            errors.append(f"{prefix} must be a JSON object.")
            continue

        ver_str = v_entry.get("version")
        if not ver_str or not isinstance(ver_str, str) or not ver_str.strip():
            errors.append(f"{prefix} missing or empty required string field 'version'.")

        status = v_entry.get("status")
        if not status or not isinstance(status, str):
            errors.append(f"{prefix} missing required string field 'status'.")

        kicad_version = v_entry.get("kicad_version")
        if not kicad_version or not isinstance(kicad_version, str):
            errors.append(f"{prefix} missing required string field 'kicad_version'.")

        if is_package:
            # In package archive: absolutely no download_* keys allowed
            for key in v_entry:
                if key.startswith("download_"):
                    errors.append(
                        f"{prefix} must not contain '{key}' (no 'download_*' keys allowed in package archive)."
                    )
        else:
            # In repository index: download fields are required
            for key in ("download_sha256", "download_size", "download_url", "install_size"):
                if key not in v_entry:
                    errors.append(f"{prefix} missing required distribution field '{key}'.")
            sha = v_entry.get("download_sha256")
            if sha and (not isinstance(sha, str) or not re.match(r"^[0-9a-fA-F]{64}$", sha)):
                errors.append(f"{prefix} 'download_sha256' must be a 64-character hex string.")

    return errors


def validate_source_dir(source_dir: Path) -> list[str]:
    errors = []
    if not source_dir.is_dir():
        errors.append(f"Source directory does not exist: {source_dir}")
        return errors

    for item in source_dir.rglob("*"):
        if item.is_dir():
            continue
        rel_path = item.relative_to(source_dir).as_posix()
        # Ignore local cache / temporary files
        if "__pycache__" in rel_path or rel_path.endswith(".pyc") or rel_path.startswith("."):
            continue
        if is_test_file(rel_path):
            errors.append(
                f"Source directory contains disallowed test file: '{rel_path}'. "
                "Tests must live outside the plugin package directory (in integrations/KiCad/tests/)."
            )
        elif not is_allowed_archive_path(rel_path):
            errors.append(
                f"Source directory contains disallowed file: '{rel_path}'. "
                "Only 'metadata.json', 'resources/icon.png', and files inside 'plugins/' are allowed in KiCad PCM."
            )
    return errors


def validate_archive(
    zip_path: Path,
    expected_version: str | None,
    repo_meta_path: Path | None,
) -> tuple[list[str], dict[str, any]]:
    errors = []
    stats = {}

    if not zip_path.is_file():
        errors.append(f"Zip archive does not exist: {zip_path}")
        return errors, stats

    dl_size = zip_path.stat().st_size
    with open(zip_path, "rb") as f:
        dl_sha = hashlib.sha256(f.read()).hexdigest()

    stats["download_size"] = dl_size
    stats["download_sha256"] = dl_sha

    try:
        with zipfile.ZipFile(zip_path, "r") as zf:
            corrupt = zf.testzip()
            if corrupt:
                errors.append(f"Zip archive has corrupted file: {corrupt}")

            total_install_size = 0
            has_metadata = False
            has_icon = False
            pkg_version = None

            for entry in zf.infolist():
                if entry.is_dir():
                    continue

                norm_name = entry.filename.replace("\\", "/").lstrip("/")
                total_install_size += entry.file_size

                if is_test_file(norm_name):
                    errors.append(
                        f"Zip archive contains disallowed test file: '{norm_name}'."
                    )
                elif not is_allowed_archive_path(norm_name):
                    errors.append(
                        f"Zip archive contains disallowed extra file: '{norm_name}'."
                    )

                if norm_name == "resources/icon.png":
                    has_icon = True
                    icon_data = zf.read(entry)
                    if not check_png_header(icon_data):
                        errors.append("resources/icon.png is not a valid PNG image.")

                if norm_name == "metadata.json":
                    has_metadata = True
                    try:
                        meta_json = json.loads(zf.read(entry).decode("utf-8"))
                        schema_errors = validate_pcm_metadata_schema(meta_json, is_package=True)
                        errors.extend(schema_errors)

                        versions = meta_json.get("versions", [])
                        if versions and isinstance(versions[0], dict):
                            pkg_version = versions[0].get("version")
                    except Exception as ex:
                        errors.append(f"Failed to parse in-archive metadata.json: {ex}")

            if not has_metadata:
                errors.append("Zip archive is missing metadata.json.")
            if not has_icon:
                errors.append("Zip archive is missing resources/icon.png.")

            stats["install_size"] = total_install_size
            stats["version"] = pkg_version

    except Exception as ex:
        errors.append(f"Failed to read zip archive: {ex}")
        return errors, stats

    if not pkg_version:
        errors.append("Package archive metadata.json has no valid 'version' defined.")
    elif expected_version and pkg_version != expected_version:
        errors.append(
            f"Package version '{pkg_version}' does not match expected version '{expected_version}'."
        )

    # Validate against repository metadata if available
    target_ver = expected_version or pkg_version
    if repo_meta_path and repo_meta_path.is_file() and target_ver:
        try:
            with open(repo_meta_path, "r", encoding="utf-8") as f:
                repo_meta = json.load(f)

            repo_schema_errors = validate_pcm_metadata_schema(repo_meta, is_package=False)
            errors.extend(repo_schema_errors)

            matching = [v for v in repo_meta.get("versions", []) if v.get("version") == target_ver]
            if not matching:
                errors.append(
                    f"Repository metadata {repo_meta_path} does not have an entry for version '{target_ver}'."
                )
            else:
                entry = matching[0]
                expected_sha = entry.get("download_sha256")
                expected_dl_size = entry.get("download_size")
                expected_inst_size = entry.get("install_size")

                if expected_sha and expected_sha != dl_sha:
                    errors.append(
                        f"SHA-256 mismatch in repo metadata: expected '{expected_sha}', actual '{dl_sha}'."
                    )
                if expected_dl_size is not None and abs(expected_dl_size - dl_size) > 1024:
                    errors.append(
                        f"download_size mismatch: metadata has {expected_dl_size}, actual is {dl_size}."
                    )
                if expected_inst_size is not None and abs(expected_inst_size - stats["install_size"]) > 1024:
                    errors.append(
                        f"install_size mismatch: metadata has {expected_inst_size}, actual is {stats['install_size']}."
                    )
        except Exception as ex:
            errors.append(f"Failed to validate against repo metadata: {ex}")

    return errors, stats


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Verify KiCad PCM package integrity and metadata conformance."
    )
    parser.add_argument(
        "--source-dir",
        type=Path,
        default=Path("integrations/KiCad/kicad-freerouting"),
        help="Path to kicad-freerouting plugin source directory.",
    )
    parser.add_argument(
        "--zip-file",
        type=Path,
        default=None,
        help="Path to kicad-freerouting package zip archive (default: check standard zip locations).",
    )
    parser.add_argument(
        "--repo-metadata",
        type=Path,
        default=Path("integrations/KiCad/metadata.json"),
        help="Path to repository metadata.json file.",
    )
    parser.add_argument(
        "--version",
        type=str,
        default=None,
        help="Expected plugin version (e.g. 2.5.0).",
    )
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parents[2]
    source_dir = args.source_dir if args.source_dir.is_absolute() else repo_root / args.source_dir
    repo_meta = args.repo_metadata if args.repo_metadata.is_absolute() else repo_root / args.repo_metadata

    all_errors = []

    print(f"=== Checking KiCad source directory: {source_dir} ===")
    src_errors = validate_source_dir(source_dir)
    if src_errors:
        for err in src_errors:
            print(f"  [ERROR] {err}")
        all_errors.extend(src_errors)
    else:
        print("  [OK] Source directory clean (only valid PCM files found).")

    # Determine which zip archives to validate
    zips_to_check: list[Path] = []
    if args.zip_file:
        zips_to_check.append(args.zip_file if args.zip_file.is_absolute() else repo_root / args.zip_file)
    else:
        # Check standard zip locations
        kicad_dir = repo_root / "integrations" / "KiCad"
        generic_zip = kicad_dir / "kicad-freerouting.zip"
        if generic_zip.is_file():
            zips_to_check.append(generic_zip)
        if args.version:
            v_zip = kicad_dir / f"kicad-freerouting-{args.version}.zip"
            if v_zip.is_file() and v_zip not in zips_to_check:
                zips_to_check.append(v_zip)
        else:
            # Check latest version zip if present
            matching_zips = sorted(kicad_dir.glob("kicad-freerouting-*.zip"), reverse=True)
            if matching_zips:
                zips_to_check.append(matching_zips[0])

    if not zips_to_check:
        err_msg = "No package archives found to validate in integrations/KiCad."
        print(f"  [ERROR] {err_msg}")
        all_errors.append(err_msg)
    else:
        for z in zips_to_check:
            print(f"\n=== Validating archive: {z.name} ===")
            z_errors, stats = validate_archive(z, args.version, repo_meta)
            if z_errors:
                for err in z_errors:
                    print(f"  [ERROR] {err}")
                all_errors.extend(z_errors)
            else:
                print(f"  [OK] Archive {z.name} is valid!")
                print(f"       Version: {stats.get('version')}")
                print(f"       Download size: {stats.get('download_size')} bytes")
                print(f"       Install size:  {stats.get('install_size')} bytes")
                print(f"       SHA-256:       {stats.get('download_sha256')}")

    if all_errors:
        print(f"\nFAILURE: {len(all_errors)} error(s) detected during KiCad PCM validation.")
        return 1

    print("\nSUCCESS: All KiCad PCM validation checks passed!")
    return 0


if __name__ == "__main__":
    sys.exit(main())
