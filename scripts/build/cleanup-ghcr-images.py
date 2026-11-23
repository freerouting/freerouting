#!/usr/bin/env python3
"""Delete unused untagged container image versions from GHCR.

Multi-architecture container images pushed to GitHub Container Registry (GHCR)
consist of a tagged OCI image index / Docker manifest list plus untagged
per-platform child manifests (for example, linux/amd64 and linux/arm64).
Deleting all untagged package versions blindly would break active multi-arch
tags such as ``nightly``, ``latest``, and versioned releases.

This script:
1. Lists all container package versions via the GitHub Packages REST API.
2. Inspects every tagged manifest in GHCR (recursively) to collect all child
   manifest digests still referenced by any tag.
3. Deletes only untagged package versions that are not referenced by any tag
   and are older than ``--min-age-minutes`` (protecting concurrent pushes).
"""

from __future__ import annotations

import argparse
import base64
from collections import deque
from datetime import datetime, timedelta, timezone
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

GITHUB_API_BASE = "https://api.github.com"
GHCR_BASE = "https://ghcr.io"
GITHUB_API_VERSION = "2022-11-28"
MANIFEST_ACCEPT = ", ".join(
    [
        "application/vnd.oci.image.index.v1+json",
        "application/vnd.docker.distribution.manifest.list.v2+json",
        "application/vnd.oci.image.manifest.v1+json",
        "application/vnd.docker.distribution.manifest.v2+json",
    ]
)
NEXT_LINK_RE = re.compile(r'<([^>]+)>;\s*rel="next"')


def parse_iso8601(timestamp: str) -> datetime:
    """Parse a GitHub API ISO-8601 UTC timestamp."""
    return datetime.fromisoformat(timestamp.replace("Z", "+00:00"))


def github_request(
    url: str,
    token: str,
    method: str = "GET",
) -> tuple[int, dict[str, str], bytes]:
    """Perform an authenticated request against the GitHub REST API."""
    req = urllib.request.Request(
        url,
        method=method,
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": GITHUB_API_VERSION,
            "User-Agent": "freerouting-ghcr-cleanup",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            headers = {k.lower(): v for k, v in resp.headers.items()}
            return resp.status, headers, resp.read()
    except urllib.error.HTTPError as exc:
        body = exc.read()
        headers = {k.lower(): v for k, v in exc.headers.items()} if exc.headers else {}
        return exc.code, headers, body


def fetch_all_package_versions(
    owner: str,
    package: str,
    token: str,
) -> tuple[str, list[dict]]:
    """Fetch all container package versions, detecting org vs user ownership."""
    encoded_pkg = urllib.parse.quote(package, safe="")
    scope = "orgs"
    url: str | None = (
        f"{GITHUB_API_BASE}/orgs/{owner}/packages/container/{encoded_pkg}"
        "/versions?per_page=100"
    )

    status, headers, body = github_request(url, token)
    if status == 404:
        scope = "users"
        url = (
            f"{GITHUB_API_BASE}/users/{owner}/packages/container/{encoded_pkg}"
            "/versions?per_page=100"
        )
        status, headers, body = github_request(url, token)

    if status != 200:
        raise RuntimeError(
            f"Failed to list package versions ({scope}/{owner}/{package}): "
            f"HTTP {status}: {body.decode('utf-8', errors='replace')}"
        )

    versions: list[dict] = json.loads(body.decode("utf-8"))
    while True:
        link_header = headers.get("link", "")
        match = NEXT_LINK_RE.search(link_header)
        if not match:
            break
        next_url = match.group(1)
        status, headers, body = github_request(next_url, token)
        if status != 200:
            raise RuntimeError(
                f"Failed to fetch next page of package versions: "
                f"HTTP {status}: {body.decode('utf-8', errors='replace')}"
            )
        versions.extend(json.loads(body.decode("utf-8")))

    return scope, versions


def get_ghcr_pull_token(
    image: str,
    actor: str | None = None,
    github_token: str | None = None,
) -> str:
    """Obtain a short-lived GHCR bearer token for pulling manifests."""
    scope = urllib.parse.quote(f"repository:{image}:pull", safe=":")
    token_url = f"{GHCR_BASE}/token?service=ghcr.io&scope={scope}"

    attempts: list[dict[str, str]] = []
    if github_token:
        user = actor or "x-access-token"
        basic = base64.b64encode(f"{user}:{github_token}".encode("utf-8")).decode(
            "ascii"
        )
        attempts.append({"Authorization": f"Basic {basic}"})
    attempts.append({})

    last_error: Exception | None = None
    for auth_headers in attempts:
        req = urllib.request.Request(
            token_url,
            headers={"User-Agent": "freerouting-ghcr-cleanup", **auth_headers},
        )
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                data = json.loads(resp.read().decode("utf-8"))
                return data["token"]
        except (urllib.error.HTTPError, urllib.error.URLError, KeyError) as exc:
            last_error = exc

    raise RuntimeError(f"Failed to obtain GHCR pull token for {image}: {last_error}")


def fetch_ghcr_manifest(image: str, reference: str, registry_token: str) -> dict:
    """Fetch an OCI/Docker manifest or manifest list from GHCR."""
    url = f"{GHCR_BASE}/v2/{image}/manifests/{reference}"
    req = urllib.request.Request(
        url,
        headers={
            "Authorization": f"Bearer {registry_token}",
            "Accept": MANIFEST_ACCEPT,
            "User-Agent": "freerouting-ghcr-cleanup",
        },
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))


def fetch_ghcr_blob_json(image: str, digest: str, registry_token: str) -> dict:
    """Fetch a JSON config blob from GHCR."""
    url = f"{GHCR_BASE}/v2/{image}/blobs/{digest}"
    req = urllib.request.Request(
        url,
        headers={
            "Authorization": f"Bearer {registry_token}",
            "User-Agent": "freerouting-ghcr-cleanup",
        },
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))


def fetch_tag_revision(image: str, tag: str, registry_token: str) -> str | None:
    """Return the org.opencontainers.image.revision label for a published tag."""
    try:
        manifest = fetch_ghcr_manifest(image, tag, registry_token)
    except urllib.error.HTTPError as exc:
        if exc.code == 404:
            return None
        raise

    if "manifests" in manifest and manifest["manifests"]:
        child_digest = manifest["manifests"][0].get("digest")
        if not child_digest:
            return None
        manifest = fetch_ghcr_manifest(image, child_digest, registry_token)

    config_digest = manifest.get("config", {}).get("digest")
    if not config_digest:
        return None

    config_blob = fetch_ghcr_blob_json(image, config_digest, registry_token)
    labels = config_blob.get("config", {}).get("Labels") or {}
    return labels.get("org.opencontainers.image.revision")


def write_github_output(values: dict[str, str]) -> None:
    """Write key=value outputs to GITHUB_OUTPUT when running in GitHub Actions."""
    output_path = os.environ.get("GITHUB_OUTPUT")
    if not output_path:
        return
    with open(output_path, "a", encoding="utf-8") as handle:
        for key, value in values.items():
            handle.write(f"{key}={value}\n")


def list_ghcr_tags(image: str, registry_token: str) -> list[str]:
    """List tags directly from the GHCR v2 registry API."""
    url = f"{GHCR_BASE}/v2/{image}/tags/list"
    req = urllib.request.Request(
        url,
        headers={
            "Authorization": f"Bearer {registry_token}",
            "User-Agent": "freerouting-ghcr-cleanup",
        },
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        data = json.loads(resp.read().decode("utf-8"))
        return data.get("tags") or []


def collect_referenced_digests(
    image: str,
    root_references: list[str],
    registry_token: str,
) -> set[str]:
    """Recursively collect all manifest digests referenced by the given roots."""
    protected: set[str] = set()
    visited: set[str] = set()
    queue: deque[str] = deque(root_references)

    while queue:
        ref = queue.popleft()
        if ref in visited:
            continue
        visited.add(ref)
        if ref.startswith("sha256:"):
            protected.add(ref)

        manifest = fetch_ghcr_manifest(image, ref, registry_token)
        for child in manifest.get("manifests", []):
            child_digest = child.get("digest")
            if not child_digest:
                continue
            protected.add(child_digest)
            if child_digest not in visited:
                queue.append(child_digest)

    return protected


def select_unused_versions(
    versions: list[dict],
    protected_digests: set[str],
    cutoff: datetime,
) -> tuple[list[dict], list[dict]]:
    """Return (candidates_to_delete, skipped_recent_untagged)."""
    candidates: list[dict] = []
    skipped_recent: list[dict] = []

    for version in versions:
        tags = version.get("metadata", {}).get("container", {}).get("tags", [])
        if tags:
            continue

        digest = version.get("name", "")
        if digest in protected_digests:
            continue

        created_at_raw = version.get("created_at")
        if created_at_raw:
            created_at = parse_iso8601(created_at_raw)
            if created_at > cutoff:
                skipped_recent.append(version)
                continue

        candidates.append(version)

    return candidates, skipped_recent


def delete_package_version(
    scope: str,
    owner: str,
    package: str,
    version_id: int,
    token: str,
) -> tuple[bool, str]:
    """Delete a single package version via the GitHub Packages REST API."""
    encoded_pkg = urllib.parse.quote(package, safe="")
    url = (
        f"{GITHUB_API_BASE}/{scope}/{owner}/packages/container/{encoded_pkg}"
        f"/versions/{version_id}"
    )
    status, _, body = github_request(url, token, method="DELETE")
    if status == 204:
        return True, "deleted"
    if status == 404:
        return True, "already deleted (404)"
    detail = body.decode("utf-8", errors="replace").strip()
    if status == 403:
        detail += (
            " (Hint: ensure the repository has Admin access to this package under "
            "Package Settings -> Manage Actions access.)"
        )
    return False, f"HTTP {status}: {detail}"


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Delete unused untagged GHCR container package versions."
    )
    parser.add_argument(
        "--owner",
        required=True,
        help="GitHub organization or user owning the package (e.g. freerouting).",
    )
    parser.add_argument(
        "--package",
        required=True,
        help="Container package name (e.g. freerouting).",
    )
    parser.add_argument(
        "--image",
        default=None,
        help="GHCR image path (default: <owner>/<package> lowercased).",
    )
    parser.add_argument(
        "--min-age-minutes",
        type=int,
        default=10,
        help=(
            "Skip untagged versions created within this many minutes to avoid "
            "racing concurrent pushes (default: 10)."
        ),
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="List versions that would be deleted without deleting anything.",
    )
    parser.add_argument(
        "--check-manifests-only",
        action="store_true",
        help="Only inspect public GHCR tags and their referenced child digests.",
    )
    parser.add_argument(
        "--check-revision",
        default=None,
        help=(
            "Compare this git SHA against the published tag's "
            "org.opencontainers.image.revision label and emit should_build."
        ),
    )
    parser.add_argument(
        "--tag",
        default="nightly",
        help="Tag to inspect when using --check-revision (default: nightly).",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    image = (args.image or f"{args.owner}/{args.package}").lower()
    token = os.environ.get("GITHUB_TOKEN", "").strip()
    actor = os.environ.get("GITHUB_ACTOR", "").strip() or args.owner

    if args.check_revision is not None:
        published_revision: str | None = None
        try:
            registry_token = get_ghcr_pull_token(
                image, actor=actor, github_token=token or None
            )
            published_revision = fetch_tag_revision(image, args.tag, registry_token)
        except Exception as exc:  # noqa: BLE001
            print(
                f"Warning: could not inspect ghcr.io/{image}:{args.tag} ({exc}); "
                "defaulting to should_build=true.",
                file=sys.stderr,
            )

        should_build = published_revision != args.check_revision
        print(f"Image tag: ghcr.io/{image}:{args.tag}")
        print(f"Published revision: {published_revision or '<none>'}")
        print(f"Target revision:    {args.check_revision}")
        print(f"should_build={'true' if should_build else 'false'}")
        write_github_output(
            {
                "published_revision": published_revision or "",
                "should_build": "true" if should_build else "false",
            }
        )
        return 0

    if args.check_manifests_only:
        registry_token = get_ghcr_pull_token(image, actor=actor, github_token=token or None)
        tags = sorted(list_ghcr_tags(image, registry_token))
        protected = collect_referenced_digests(image, tags, registry_token)
        print(f"Image: ghcr.io/{image}")
        print(f"Tags found ({len(tags)}): {', '.join(tags)}")
        print(f"Referenced child digests protected: {len(protected)}")
        return 0

    if not token:
        print("Error: GITHUB_TOKEN environment variable is required.", file=sys.stderr)
        return 1

    scope, versions = fetch_all_package_versions(args.owner, args.package, token)
    tagged_versions = [
        v
        for v in versions
        if v.get("metadata", {}).get("container", {}).get("tags")
    ]

    if not tagged_versions:
        print(
            "Safety abort: no tagged package versions found; refusing to delete "
            "untagged versions.",
            file=sys.stderr,
        )
        return 1

    all_tags = sorted(
        tag
        for v in tagged_versions
        for tag in v.get("metadata", {}).get("container", {}).get("tags", [])
    )
    tagged_digests = [v["name"] for v in tagged_versions if v.get("name")]

    registry_token = get_ghcr_pull_token(image, actor=actor, github_token=token)
    protected_digests = collect_referenced_digests(image, tagged_digests, registry_token)

    cutoff = datetime.now(timezone.utc) - timedelta(minutes=args.min_age_minutes)
    candidates, skipped_recent = select_unused_versions(
        versions, protected_digests, cutoff
    )

    protected_untagged_count = sum(
        1
        for v in versions
        if not v.get("metadata", {}).get("container", {}).get("tags")
        and v.get("name", "") in protected_digests
    )

    print(f"Package: {scope}/{args.owner}/packages/container/{args.package}")
    print(f"Total versions: {len(versions)}")
    print(f"Tagged versions: {len(tagged_versions)} (tags: {', '.join(all_tags)})")
    print(f"Protected untagged child manifests: {protected_untagged_count}")
    print(
        f"Skipped recently created untagged versions (< {args.min_age_minutes}m): "
        f"{len(skipped_recent)}"
    )
    print(f"Unused untagged versions to delete: {len(candidates)}")

    if not candidates:
        print("Nothing to delete.")
        return 0

    if args.dry_run:
        for v in candidates:
            print(
                f"[dry-run] Would delete id={v['id']} digest={v.get('name')} "
                f"created_at={v.get('created_at')}"
            )
        return 0

    deleted = 0
    failures = 0
    for v in candidates:
        version_id = v["id"]
        digest = v.get("name", "")
        created_at = v.get("created_at", "")
        ok, message = delete_package_version(
            scope, args.owner, args.package, version_id, token
        )
        if ok:
            deleted += 1
            print(f"Deleted id={version_id} digest={digest} created_at={created_at} ({message})")
        else:
            failures += 1
            print(
                f"Failed to delete id={version_id} digest={digest}: {message}",
                file=sys.stderr,
            )

    print(f"Cleanup complete: {deleted} deleted, {failures} failed.")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
