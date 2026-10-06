#!/usr/bin/env python3
"""
package_plugin.py — Build clean KiCad PCM plugin zip packages.

Packs 'integrations/KiCad/kicad-freerouting' into:
  - 'integrations/KiCad/kicad-freerouting.zip'
  - 'integrations/KiCad/kicad-freerouting-<version>.zip'

Strictly excludes forbidden files (__pycache__, *.pyc, test files, OS metadata),
computes SHA-256 and size metrics, optionally updates 'integrations/KiCad/metadata.json',
and runs validate_pcm_package.py.

Usage:
    python scripts/kicad/package_plugin.py [--update-metadata] [--version <version>]
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import sys
import zipfile

from validate_pcm_package import validate_archive, validate_source_dir


def package_plugin(
    source_dir: Path,
    output_dir: Path,
    version: str,
    update_metadata: bool = False,
    repo_meta_path: Path | None = None,
) -> int:
    print(f"=== Packaging KiCad Plugin for version {version} ===")
    src_errors = validate_source_dir(source_dir)
    if src_errors:
        print("ERROR: Source directory contains invalid files:")
        for err in src_errors:
            print(f"  - {err}")
        return 1

    files_to_pack: list[tuple[Path, str]] = []
    for root, dirs, files in os.walk(source_dir):
        dirs[:] = [
            d for d in dirs
            if d != "__pycache__" and not d.startswith(".") and d != "tests" and d != "__tests__"
        ]
        for f in files:
            if f.endswith(".pyc") or f.startswith(".") or f == "Thumbs.db":
                continue
            if f.startswith("test_") or f.endswith("_test.py"):
                continue
            full_path = Path(root) / f
            rel_path = full_path.relative_to(source_dir).as_posix()
            files_to_pack.append((full_path, rel_path))

    files_to_pack.sort(key=lambda x: x[1])

    versioned_zip = output_dir / f"kicad-freerouting-{version}.zip"
    generic_zip = output_dir / "kicad-freerouting.zip"

    print(f"Packing {len(files_to_pack)} files into {versioned_zip.name}...")
    with zipfile.ZipFile(versioned_zip, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        for full_path, rel_path in files_to_pack:
            zf.write(full_path, arcname=rel_path)

    shutil.copyfile(versioned_zip, generic_zip)

    dl_size = versioned_zip.stat().st_size
    with open(versioned_zip, "rb") as f:
        dl_sha = hashlib.sha256(f.read()).hexdigest()

    with zipfile.ZipFile(versioned_zip, "r") as zf:
        inst_size = sum(e.file_size for e in zf.infolist() if not e.is_dir())

    print("\nPackage metrics:")
    print(f"  Download size: {dl_size} bytes")
    print(f"  Install size:  {inst_size} bytes")
    print(f"  SHA-256:       {dl_sha}")

    if update_metadata and repo_meta_path and repo_meta_path.is_file():
        print(f"\nUpdating {repo_meta_path}...")
        with open(repo_meta_path, "r", encoding="utf-8") as f:
            repo_meta = json.load(f)

        versions = repo_meta.setdefault("versions", [])
        matched = False
        for v_entry in versions:
            if v_entry.get("version") == version:
                v_entry["download_sha256"] = dl_sha
                v_entry["download_size"] = dl_size
                v_entry["install_size"] = inst_size
                v_entry["download_url"] = (
                    f"https://github.com/freerouting/freerouting/raw/master/integrations/KiCad/kicad-freerouting-{version}.zip"
                )
                matched = True
                break

        if not matched:
            versions.append(
                {
                    "version": version,
                    "status": "stable",
                    "kicad_version": "6.0",
                    "runtime": "ipc",
                    "download_sha256": dl_sha,
                    "download_size": dl_size,
                    "download_url": (
                        f"https://github.com/freerouting/freerouting/raw/master/integrations/KiCad/kicad-freerouting-{version}.zip"
                    ),
                    "install_size": inst_size,
                }
            )

        with open(repo_meta_path, "w", encoding="utf-8") as f:
            json.dump(repo_meta, f, indent=2)
            f.write("\n")
        print(f"  [OK] Updated repository metadata for version {version}.")

    print("\n=== Validating generated packages ===")
    errs, _ = validate_archive(versioned_zip, version, repo_meta_path)
    if errs:
        print("ERROR: Validation failed:")
        for e in errs:
            print(f"  - {e}")
        return 1

    print("SUCCESS: Plugin packaged and validated successfully!")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Package KiCad plugin into clean PCM zip archives.")
    parser.add_argument("--version", type=str, default=None, help="Version string (e.g. 2.5.0)")
    parser.add_argument(
        "--update-metadata",
        action="store_true",
        help="Automatically update integrations/KiCad/metadata.json with new hashes and sizes",
    )
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parents[2]
    source_dir = repo_root / "integrations" / "KiCad" / "kicad-freerouting"
    output_dir = repo_root / "integrations" / "KiCad"
    repo_meta_path = output_dir / "metadata.json"

    version = args.version
    if not version:
        pkg_meta_path = source_dir / "metadata.json"
        if pkg_meta_path.is_file():
            with open(pkg_meta_path, "r", encoding="utf-8") as f:
                d = json.load(f)
                v_list = d.get("versions", [])
                if v_list:
                    version = v_list[0].get("version")

    if not version:
        print("ERROR: Could not detect version. Specify with --version.")
        return 1

    return package_plugin(source_dir, output_dir, version, args.update_metadata, repo_meta_path)


if __name__ == "__main__":
    sys.exit(main())
