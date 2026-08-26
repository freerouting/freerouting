# Security Audit Pass F — CI and Supply Chain

**Status:** Confirmed (Grok 4.6 Medium, 2026-08-21)
**Hunter model:** GPT-5.6 Luna High
**Confirmation model:** Grok 4.6 Medium, only for concrete flagged issues
**Date:** 2026-08-21
**Scope:** GitHub Actions, Gradle dependencies/plugins, Docker publishing, published artifacts

This is a read-only hunt report. Findings remain in the candidate table for traceability; the
Confirmation section records Grok 4.6 Medium verdicts. No workflow, build, or dependency files were
changed and no risk-register rows were added (Phase 3).

## Method

1. Reviewed every workflow under `.github/workflows` for trigger trust, job permissions,
   timeouts, action pinning, secret exposure, release-source selection, and artifact publishing.
2. Reviewed Gradle plugins, repositories, direct dependency versions, wrapper distribution
   verification, signing configuration, and dependency-lock/verification controls.
3. Reviewed Docker build/publish workflows and the Dockerfile for mutable build inputs, image
   provenance, registry permissions, and release tag behavior.
4. Reviewed pre-commit bootstrap and hook revisions as an additional CI supply-chain boundary.
5. Distinguished release-integrity hardening from exploitable workflow injection; no PR workflow
   with write permissions or `pull_request_target` trigger was found.

## Confirmed findings

See Confirmation below. All five hunter candidates were accepted. None was raised above Medium.

## Candidates (unconfirmed)

| Severity | Location | Finding | Exploit scenario | Fix sketch |
| --- | --- | --- | --- | --- |
| Medium | `.github/workflows/create-release.yml:47-59`, `:81-93`, `:111-123`, `:145-157`; `.github/workflows/create-snapshot.yml:37-47`, `:67-71`, `:95-99`, `:119-123`, `:147-151` | **Privileged release workflows execute mutable third-party GitHub Actions by tag.** `olegtarasov/get-tag@v2.1`, `AButler/upload-release-assets@v3.0`, and `mknejp/delete-release-assets@v1` are not pinned to immutable commit SHAs and run in jobs with `contents: write`; upload/delete actions receive the repository token directly. | A compromised upstream action, retagged release, or malicious maintainer update can execute code in a release job and upload tampered assets, delete valid snapshot assets, or use the write-scoped token against the repository. The release jobs are tag/master triggered rather than fork-triggered, reducing exposure but not removing the supply-chain boundary. | Pin third-party actions to reviewed commit SHAs, review and update them deliberately, minimize token scope where possible, and add an independent release-artifact verification step. |
| Medium | `Dockerfile:2`, `:26`; `.github/workflows/docker-release.yml:65-75`; `.github/workflows/docker-nightly.yml:58-68` | **Published Docker images are built from mutable base-image tags rather than immutable digests.** Both build stages use `eclipse-temurin:25-jdk-jammy` / `25-jre-jammy`; the workflows publish the result directly to GHCR. | A later rebuild can silently incorporate a different base image, including a compromised or vulnerable image, while the source revision and published tag look unchanged. This weakens reproducibility and makes incident comparison difficult. | Pin base images by digest, update digests through review, and record the resolved base-image digests in build metadata/SBOM output. |
| Low | `.github/workflows/create-release.yml:55-59`, `:89-93`, `:119-123`, `:153-157`; `.github/workflows/docker-release.yml:65-75`; `.github/workflows/docker-nightly.yml:58-68` | **Published release assets and container tags lack an independent artifact-integrity verification path.** Release workflows upload JAR/ZIP/MSI/DMG assets without checksums or detached signatures; Docker publishing explicitly sets `provenance: false` and supplies no SBOM or signature step. | A consumer who obtains an asset or mutable `latest`/`nightly` tag through an altered mirror or registry path has no project-published cryptographic value to compare. This overlaps Pass E's unsigned-installer candidate and is primarily a distribution assurance gap. | Publish checksums and detached signatures for release assets, sign container images, publish SBOMs/attestations, and document digest-pinned image consumption. Preserve the project's multi-platform manifest compatibility while adding provenance in a compatible form. |
| Low | `.github/workflows/pre-commit.yml:23-30`; `.pre-commit-config.yaml:19-22`, `:70-72` | **The CI pre-commit bootstrap installs an unpinned PyPI package and several unpinned hook dependencies.** `python -m pip install pre-commit` resolves the current package at run time, and some hook `additional_dependencies` have no versions. | A compromised or unexpectedly changed package/dependency can execute arbitrary code on every CI runner before quality checks. This job has only `contents: read`, so the likely impact is CI integrity or credential exposure rather than direct release publication. | Pin `pre-commit` and every additional dependency, use hashes or a lockfile where practical, and separate untrusted lint execution from any job that has release credentials. |
| Low | `build.gradle:103-125`, `:134-147`, `:149-235`; no `gradle/verification-metadata.xml` or dependency lock file found | **Gradle dependency resolution has exact direct versions but no repository-content verification or dependency locking.** Direct dependencies are version-pinned, yet transitive artifacts are resolved from Maven repositories without Gradle verification metadata or lockfiles, and no dependency-review/SCA workflow was found. | A changed transitive graph or compromised repository artifact can enter a build without a repository-content checksum policy or review-visible lockfile change. HTTPS and Maven Central reduce the likelihood, so this is a defense-in-depth candidate rather than evidence of a currently vulnerable library. | Enable Gradle dependency verification and lockfiles, review generated verification metadata, add dependency/SCA checks to CI, and keep direct version updates reviewable. |

## Controls verified during the hunt

- All reviewed jobs have explicit permissions or a workflow-level least-privilege permissions block
  and a timeout. Release build jobs use `contents: write` only because they upload/delete release
  assets; build/test jobs use `contents: read`.
- PR workflows use `pull_request`, not `pull_request_target`, and the reviewed PR jobs do not
  receive write permissions or release secrets.
- Snapshot artifact jobs correctly depend on both `build-and-test` and
  `delete-old-snapshot-assets`, so failed tests do not publish over the snapshot.
- The Gradle wrapper distribution is downloaded over HTTPS and has a pinned
  `distributionSha256Sum` in `gradle/wrapper/gradle-wrapper.properties:3-10`.
- Direct Gradle plugin and dependency versions are explicit; no active `+`, `latest.release`, or
  `latest.integration` dependency declaration was found.
- Maven Central, Google, and an HTTPS Maven Central mirror are the configured repositories; no
  insecure HTTP repository was found.
- Maven Central publications are configured to use in-memory PGP signing when release signing
  properties are provided (`gradle/publishing.gradle:39-44` and `build.gradle:313-325`). This
  does not sign GitHub release assets or Docker images.

## Candidates not promoted

- Official `actions/*` and `docker/*` actions use major-version tags rather than commit SHAs.
  This remains a general mutability concern, but the concrete privileged third-party action
  candidate above is the higher-value finding; the repository rule explicitly requires
  `actions/stale@v9` and prohibits replacing it with a SHA.
- The Docker `provenance: false` setting is an intentional project compatibility choice documented
  in `AGENTS.md`; it is included only in the broader artifact-assurance candidate, not reported as
  a separate workflow-execution vulnerability.
- The Docker release workflow's `workflow_dispatch.git_ref` behavior on release-triggered runs
  deserves a runtime test, but static review alone does not establish that the empty input causes
  checkout of the wrong revision. It is not promoted as a confirmed candidate here.
- No current CVE claim is made for the pinned Jetty/Jersey/Gson versions. Version-vulnerability
  determination belongs to the dependency scanner/confirmation phase, not this static hunt.

## Confirmation

**Confirmer:** Grok 4.6 Medium (2026-08-21). No new domains hunted. No workflow or build files
changed. No risk-register rows added (Phase 3). Hunter “controls verified” and “candidates not
promoted” blocks are accepted, including: do not treat official `actions/*` major tags as the
primary finding; keep `actions/stale@v9` per repository policy; do not invent a Docker
`git_ref` checkout bug from static review; do not claim a current Jetty/Jersey/Gson CVE from
version strings alone.

| Candidate | Verdict | Severity after confirm | Reason |
| --- | --- | --- | --- |
| Privileged release workflows execute mutable third-party GitHub Actions by tag | **Confirm** | Medium | `olegtarasov/get-tag@v2.1`, `AButler/upload-release-assets@v3.0`, and `mknejp/delete-release-assets@v1` are tag-pinned only. They run in jobs with `contents: write` and receive `secrets.GITHUB_TOKEN`. Triggers are `push` of `v*` / `master` and `workflow_dispatch`, not `pull_request_target`, so a random fork PR cannot run them. A compromised or retagged upstream action can still replace release/snapshot assets. That is a real privileged supply-chain boundary, not CI noise. |
| Published Docker images are built from mutable base-image tags | **Confirm** | Medium | `Dockerfile` uses `eclipse-temurin:25-jdk-jammy` and `eclipse-temurin:25-jre-jammy` with no digest. Nightly and release workflows push the result to GHCR (`docker-nightly.yml`, `docker-release.yml`). Rebuilds of the same source can pull a different base image. This is reproducibility and incident-response risk, not proof that the current Temurin tags are malicious. |
| Published release assets and container tags lack an independent integrity path | **Confirm** | Low | Release uploads are globbed files with no checksum artifact, GPG, or cosign step. Docker sets `provenance: false` by documented multi-arch compatibility choice and publishes mutable `latest`/`nightly` tags. Overlaps Pass E unsigned installers. Consumers have GitHub/GHCR TLS but no project-published digest/signature to compare. |
| CI pre-commit bootstrap installs unpinned PyPI packages | **Confirm** | Low | `.github/workflows/pre-commit.yml` runs `python -m pip install pre-commit` with no version or hash. Hook `additional_dependencies` for mdformat extras and codespell's `tomli` are unpinned, while hook **revs** themselves are pinned. Job permissions are `contents: read` only, so impact is CI integrity rather than release publication. |
| Gradle has exact direct versions but no verification metadata or lockfile | **Confirm** | Low | Direct plugins/dependencies are version-pinned; wrapper zip has `distributionSha256Sum`. No `gradle/verification-metadata.xml` and no Gradle lockfile exist, and no SCA/dependency-review workflow was found. Transitive resolution can change without a reviewable checksum policy. HTTPS + Maven Central lower likelihood; this is defense-in-depth, not a named vulnerable library. |

Do not add Pass F findings to `security-audit-risk-register.md` until Phase 3.

