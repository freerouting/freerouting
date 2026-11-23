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
import re
import shutil
import subprocess
import sys
import zipfile

from validate_pcm_package import validate_archive, validate_source_dir


def file_sha256(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def sync_plugin_version_and_jar(
    repo_root: Path, source_dir: Path, version: str, skip_build: bool = False
) -> dict[str, str] | None:
    """Compile the latest executableJar, copy it as freerouting-<version>.jar only if needed,
    and update version references in plugins/plugin.ini and kicad-freerouting/metadata.json.
    Returns a dict of change statuses per artifact, or None on error.
    """
    jar_path = repo_root / "build" / "libs" / "freerouting-current-executable.jar"
    if not skip_build:
        gradle_cmd = "gradlew.bat" if os.name == "nt" else "./gradlew"
        print(f"=== Building latest executableJar ({gradle_cmd} executableJar -q) ===")
        proc = subprocess.run([gradle_cmd, "executableJar", "-q"], cwd=repo_root)
        if proc.returncode != 0:
            print(f"ERROR: Gradle executableJar failed with exit code {proc.returncode}.")
            return None

    if not jar_path.is_file():
        print(f"ERROR: Built JAR not found at {jar_path}.")
        return None

    changes: dict[str, str] = {}

    # 1. Update bundled JAR only if filename or content changed; ensure strictly one .jar exists
    jar_dir = source_dir / "plugins" / "jar"
    jar_dir.mkdir(parents=True, exist_ok=True)
    target_jar = jar_dir / f"freerouting-{version}.jar"
    other_jars = [p for p in jar_dir.glob("*.jar") if p != target_jar]
    jar_filename_changed = bool(other_jars) or not target_jar.is_file()
    for old_jar in other_jars:
        old_jar.unlink()

    built_sha = file_sha256(jar_path)
    if target_jar.is_file() and file_sha256(target_jar) == built_sha:
        changes["jar"] = f"UNCHANGED ({target_jar.relative_to(repo_root)} already up to date)"
    else:
        shutil.copyfile(jar_path, target_jar)
        reason = "renamed/replaced" if other_jars else ("created" if jar_filename_changed else "content updated")
        changes["jar"] = f"UPDATED ({target_jar.relative_to(repo_root)}, {reason})"

    # 2. Update plugins/plugin.ini only if the .jar filename reference changed
    plugin_ini = source_dir / "plugins" / "plugin.ini"
    if plugin_ini.is_file():
        ini_text = plugin_ini.read_text(encoding="utf-8")
        new_ini_text, count = re.subn(
            r"(?m)^(location\s*=\s*jar/)freerouting-[^\s]+\.jar$",
            rf"\g<1>freerouting-{version}.jar",
            ini_text,
        )
        if count > 0 and new_ini_text != ini_text:
            plugin_ini.write_text(new_ini_text, encoding="utf-8")
            changes["ini"] = f"UPDATED ({plugin_ini.relative_to(repo_root)} -> jar/freerouting-{version}.jar)"
        else:
            changes["ini"] = f"UNCHANGED ({plugin_ini.relative_to(repo_root)} already points to jar/freerouting-{version}.jar)"
    else:
        changes["ini"] = "SKIPPED (plugins/plugin.ini not found)"

    # 3. Update kicad-freerouting/metadata.json (inside ZIP) only if version changed
    pkg_meta_path = source_dir / "metadata.json"
    if pkg_meta_path.is_file():
        with open(pkg_meta_path, "r", encoding="utf-8") as f:
            pkg_meta = json.load(f)
        versions = pkg_meta.get("versions", [])
        old_ver = versions[0].get("version") if versions else None
        if versions and old_ver != version:
            versions[0]["version"] = version
            with open(pkg_meta_path, "w", encoding="utf-8") as f:
                json.dump(pkg_meta, f, indent=2)
                f.write("\n")
            changes["pkg_metadata"] = f"UPDATED ({pkg_meta_path.relative_to(repo_root)}: {old_ver} -> {version})"
        else:
            changes["pkg_metadata"] = f"UNCHANGED ({pkg_meta_path.relative_to(repo_root)} already at {version})"
    else:
        changes["pkg_metadata"] = "SKIPPED (kicad-freerouting/metadata.json not found)"

    return changes


def package_plugin(
    source_dir: Path,
    output_dir: Path,
    version: str,
    repo_meta_path: Path | None = None,
    versioned: bool = False,
    changes: dict[str, str] | None = None,
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

    generic_zip = output_dir / "kicad-freerouting.zip"
    versioned_zip = output_dir / f"kicad-freerouting-{version}.zip"
    primary_zip = versioned_zip if versioned else generic_zip

    print(f"Packing {len(files_to_pack)} files into {primary_zip.name}...")
    with zipfile.ZipFile(primary_zip, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        for full_path, rel_path in files_to_pack:
            zf.write(full_path, arcname=rel_path)

    if primary_zip != generic_zip:
        shutil.copyfile(primary_zip, generic_zip)

    dl_size = primary_zip.stat().st_size
    with open(primary_zip, "rb") as f:
        dl_sha = hashlib.sha256(f.read()).hexdigest()

    with zipfile.ZipFile(primary_zip, "r") as zf:
        inst_size = sum(e.file_size for e in zf.infolist() if not e.is_dir())

    print("\nPackage metrics:")
    print(f"  Download size: {dl_size} bytes")
    print(f"  Install size:  {inst_size} bytes")
    print(f"  SHA-256:       {dl_sha}")

    repo_meta_status = "SKIPPED (integrations/KiCad/metadata.json not found)"
    if repo_meta_path and repo_meta_path.is_file():
        with open(repo_meta_path, "r", encoding="utf-8") as f:
            repo_meta = json.load(f)

        versions = repo_meta.setdefault("versions", [])
        download_url = (
            f"https://github.com/freerouting/freerouting/raw/master/integrations/KiCad/kicad-freerouting-{version}.zip"
        )
        matched_entry = next((v for v in versions if v.get("version") == version), None)
        if matched_entry is not None:
            entry_changed = (
                matched_entry.get("download_sha256") != dl_sha
                or matched_entry.get("download_size") != dl_size
                or matched_entry.get("install_size") != inst_size
                or matched_entry.get("download_url") != download_url
            )
            if entry_changed:
                matched_entry["download_sha256"] = dl_sha
                matched_entry["download_size"] = dl_size
                matched_entry["install_size"] = inst_size
                matched_entry["download_url"] = download_url
                with open(repo_meta_path, "w", encoding="utf-8") as f:
                    json.dump(repo_meta, f, indent=2)
                    f.write("\n")
                repo_meta_status = f"UPDATED (integrations/KiCad/metadata.json entry for {version}; older releases untouched)"
            else:
                repo_meta_status = f"UNCHANGED (integrations/KiCad/metadata.json entry for {version} already up to date)"
        else:
            versions.append(
                {
                    "version": version,
                    "status": "stable",
                    "kicad_version": "6.0",
                    "runtime": "ipc",
                    "download_sha256": dl_sha,
                    "download_size": dl_size,
                    "download_url": download_url,
                    "install_size": inst_size,
                }
            )
            with open(repo_meta_path, "w", encoding="utf-8") as f:
                json.dump(repo_meta, f, indent=2)
                f.write("\n")
            repo_meta_status = f"INSERTED (new entry for {version} in integrations/KiCad/metadata.json; older releases untouched)"

    print("\n=== Change Summary ===")
    if changes:
        print(f"  .jar          : {changes.get('jar', 'N/A')}")
        print(f"  plugin.ini    : {changes.get('ini', 'N/A')}")
        print(f"  ZIP metadata  : {changes.get('pkg_metadata', 'N/A')}")
    print(f"  Repo metadata : {repo_meta_status}")

    print("\n=== Validating generated packages ===")
    errs, _ = validate_archive(primary_zip, version, repo_meta_path)
    if errs:
        print("ERROR: Validation failed:")
        for e in errs:
            print(f"  - {e}")
        return 1

    print(f"SUCCESS: Plugin packaged into {primary_zip.name} and validated successfully!")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Package KiCad plugin into clean PCM zip archives.")
    parser.add_argument("--version", type=str, default=None, help="Version string (e.g. 2.5.0)")
    parser.add_argument(
        "--update-metadata",
        action="store_true",
        help="Automatically update integrations/KiCad/metadata.json with new hashes and sizes",
    )
    parser.add_argument(
        "--versioned",
        action="store_true",
        help="Also write kicad-freerouting-<version>.zip and validate against repo metadata.json",
    )
    parser.add_argument(
        "--skip-build",
        action="store_true",
        help="Skip running Gradle executableJar and use existing build/libs/freerouting-current-executable.jar",
    )
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parents[2]
    source_dir = repo_root / "integrations" / "KiCad" / "kicad-freerouting"
    output_dir = repo_root / "integrations" / "KiCad"
    repo_meta_path = output_dir / "metadata.json"

    version = args.version
    if not version:
        print("ERROR: A --version string is required.")
        print("Example: python scripts/kicad/package_plugin.py --version 2.6.0")
        return 1

    changes = sync_plugin_version_and_jar(repo_root, source_dir, version, skip_build=args.skip_build)
    if changes is None:
        return 1

    return package_plugin(
        source_dir,
        output_dir,
        version,
        repo_meta_path,
        versioned=args.versioned,
        changes=changes,
    )


if __name__ == "__main__":
    sys.exit(main())
