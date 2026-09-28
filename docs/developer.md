# Developer Guide

This document covers how to build, test, and release Freerouting from source.

## Building from source

### Requirements

- **Java JDK >= 25** ([Adoptium Temurin 25 JDK](https://adoptium.net/temurin/releases/))
- **Gradle 9.x** (or simply use the included `./gradlew` / `.\gradlew.bat` wrapper)
- Internet connection (dependencies download automatically)

### Command-line builds

Open a terminal in the repository root:

- **Build executable JAR**:
  ```bash
  ./gradlew executableJar
  ```
  *(On Windows: `.\gradlew.bat executableJar`)*
  The runnable JAR is generated at `build/libs/freerouting-current-executable.jar`.

- **Fast compilation check (no tests, no JARs)**:
  ```bash
  ./gradlew compileJava
  ```

- **Run all standard unit tests**:
  ```bash
  ./gradlew test
  ```

- **Run full verification suite (tests + Spotless + Checkstyle)**:
  ```bash
  ./gradlew check
  ```

### GUI and Accessibility Tests

GUI accessibility tests are tagged `@Tag("gui")` and run in headless mode:

```bash
./gradlew testGui
```

`testGui` runs component-level tests without opening real desktop windows. The default `test` task excludes GUI tests; `testAll` runs `test`, `testSlow`, `testSerial`, and `testGui`.

## Translations (i18n)

Freerouting ships UI strings as Java `.properties` bundles under `src/main/resources/app/freerouting/`. Maintainer tooling for LLM-assisted translation lives in [`scripts/i18n/README.md`](../scripts/i18n/README.md).

**Translators:** see [`docs/translations.md`](translations.md). Do **not** edit `*_{locale}.properties` by hand; fix [`scripts/i18n/glossary/`](../scripts/i18n/glossary/) and request a **full-locale re-translation** in your PR or issue.

Typical workflow after editing **English** strings:

```bash
pip install -r scripts/i18n/requirements.txt
python scripts/i18n/extract-context.py          # refresh scripts/i18n/context/
python scripts/i18n/translate.py --locale de --missing-only
python scripts/i18n/validate.py --locale de
./gradlew test --tests app.freerouting.i18n.EnglishPropertiesParityTest
```

After a Java refactor removes UI strings, review unused-key reports and prune stale keys from all locale bundles when you are confident they are truly unused:

```bash
./gradlew test --tests app.freerouting.i18n.EnglishPropertiesParityTest.englishBundlesDoNotContainUnusedKeys
# Review build/reports/i18n/EnglishBundlesContainUnusedKeysReport.txt before applying:
python scripts/i18n/prune-unused-keys.py --apply
python scripts/i18n/extract-context.py
```

Supported locales: **source** `en`; **targets** `ar`, `bn`, `cs`, `de`, `es`, `fr`, `hi`, `hu`, `id`, `it`, `ja`, `ko`, `nl`, `pl`, `pt`, `pt_br`, `ro`, `ru`, `sv`, `th`, `tr`, `uk`, `vi`, `zh`, `zh_tw`. PCB terminology glossaries exist for all of these under `scripts/i18n/glossary/`.

CI runs `python scripts/i18n/extract-context.py --check` on pull requests to ensure committed context metadata matches the English sources (no API key required).

## Code Quality & Pre-commit Automation

Freerouting uses **Spotless** (Google Java Style), **Checkstyle 13.9.0**, explicit LF
line-ending rules, and **pre-commit** hooks. The Gradle quality checks are check-only:
they fail when code is not ready rather than formatting unrelated files or staging
changes automatically. The pre-commit hygiene hooks automatically repair trailing
whitespace, final newlines, and LF line endings in files selected for the current
commit.

### Installing & Setting Up Pre-commit Hooks

1. Install [pre-commit](https://pre-commit.com/):
   ```bash
   pip install pre-commit
   ```

2. Install the git hooks in your local workspace:
   ```bash
   pre-commit install
   ```

3. Run pre-commit checks manually across all files:
   ```bash
   pre-commit run --all-files
   ```
   The hygiene hooks may update files and then report that they changed. Review and
   stage those small non-functional fixes, then rerun the command. CI remains
   check-only and never modifies the pull request.

### Gradle Code Quality Commands

- **Run all verification checks (Spotless + Checkstyle 13.9.0 + Unit Tests)**:
  ```bash
  ./gradlew check
  ```

- **Check Java formatting without changing files**:
  ```bash
  ./gradlew spotlessCheck
  ```

- **Format all configured Java sources intentionally**:
  ```bash
  ./gradlew spotlessApply
  ```
  `spotlessApply` can modify hundreds of files. Use it only as a deliberate,
  separately reviewed formatting operation, and inspect the resulting diff.

- **Run Checkstyle on maintained sources independently**:
  ```bash
  ./gradlew checkstyleMain checkstyleTest
  ```

- **Verify generated i18n context without rewriting it**:
  ```bash
  python scripts/i18n/extract-context.py --check
  ```

The frozen `src_v19/` compatibility source set is compiled for compatibility but is
excluded from current Checkstyle enforcement. Java text-block formatting is owned by
Spotless; the project-specific Checkstyle suppression is kept in
`config/checkstyle/checkstyle-suppressions.xml` so updates to the upstream Google
Checkstyle configuration do not overwrite it.

## How to create a new release

Creating a release takes about half an hour if everything goes according to the plan. Usually it doesn't, so free up ~3 hours for this.

Let's suppose that the new version is `<version>` (e.g. `2.5.0`). You need to complete these steps:

### 1. Pre-Release (Clean & Ready `master`)

* Check if there are any [outstanding pull requests](https://github.com/freerouting/freerouting/pulls) and merge them into `master` first.
* Fetch the latest `master` branch and ensure your local repo is up to date:
  ```bash
  git checkout master
  git pull origin master
  ```

### 2. Prepare Release Branch (`release/v<version>`)

* Create a new release branch:
  ```bash
  git checkout -b release/v<version>
  ```
* Run `gradlew wrapper --gradle-version latest` (or pass `--gradle-distribution-sha256-sum <hash>` if checksum verification is configured) to update the Gradle wrapper.
* Run `./gradlew dependencyUpdates useLatestVersions` to check and apply dependency updates. Verify changes manually if necessary.
* Change `ext.publishInfo.versionId` in `gradle/project-info.gradle` to `<version>`.
* Set the package version in `integrations/mcp-server/package.json` to `<version>` (`npm version <version> --no-git-tag-version`).
* Build the executable JAR:
  ```powershell
  gradlew executableJar
  ```
* Copy and rename `build/libs/freerouting-current-executable.jar` to `freerouting-<version>.jar`.
* Update the KiCad integration (`integrations/KiCad/`):
    * Copy `freerouting-<version>.jar` into `integrations/KiCad/kicad-freerouting/plugins/jar/` (and remove the previous version JAR).
    * Update `integrations/KiCad/kicad-freerouting/plugins/plugin.ini` with the new filename (`location = jar/freerouting-<version>.jar`).
    * Update `integrations/KiCad/kicad-freerouting/metadata.json` with the new version and download URL.
    * Create a ZIP file from the `kicad-freerouting` folder and save it as both `kicad-freerouting.zip` and `kicad-freerouting-<version>.zip`.
    * Use KiCad Packager from [https://gitlab.com/kicad/addons/metadata/tools](https://gitlab.com/kicad/addons/metadata/-/tree/main/tools) to compute SHA-256 and file sizes.
    * Update `integrations/KiCad/metadata.json` with the new version entry, SHA-256, download size, and install size.
    * Run a full routing session from KiCad after manually installing the plugin ZIP, to make sure that the router executes properly in CLI mode and the resulting SES file imports without corruption or parser errors.
* Update documentation version references (`README.md`, `integrations.md`, `self-hosting.md`, `settings.md`, and `scoring.md` if the score formulas or defaults changed).
* Run verification quality checks:
  ```bash
  ./gradlew spotlessCheck checkstyleMain checkstyleTest
  ```
* Commit the release changes and push to GitHub:
  ```bash
  git add -A
  git commit -m "Prepare release v<version>"
  git push -u origin release/v<version>
  ```

### 3. Pull Request & Verification

* Create a PR from `release/v<version>` → `master`.
* Check if it builds successfully on GitHub Actions CI.
* Merge the PR into `master`.

### 4. Publish Release & Artifacts

* Update your local `master` branch:
  ```bash
  git checkout master
  git pull origin master
  ```
* Create and publish the new release on GitHub:
  * Draft a release for tag `v<version>` targeting the latest commit on `master`.
  * You do not need to manually attach artifacts. Publishing the release automatically triggers GitHub Actions (`create-release.yml` and `docker-release.yml`) to build and attach:
    * Universal executable JAR (`freerouting-<version>.jar`).
    * Windows x64 installer (`freerouting-<version>-windows-x64.msi`).
    * Linux x64 distribution package (`freerouting-<version>-linux-x64.zip`).
    * macOS Apple Silicon ARM64 disk image (`freerouting-<version>-macos-arm64.dmg`).
    * macOS Intel x86_64 disk image (`freerouting-<version>-macos-x64.dmg`).
    * Multi-architecture Docker image automatically published to GitHub Container Registry ([`ghcr.io/freerouting/freerouting`](https://github.com/freerouting/freerouting/pkgs/container/freerouting)).
  * *Note on KiCad plugin ZIP:* We do not attach the KiCad plugin ZIP directly to the GitHub release; it is hosted under `integrations/KiCad/kicad-freerouting-<version>.zip` and distributed to users through KiCad's Plugin and Content Manager (PCM).
* Publish the library to Maven Central:
    * Use the [Gradle Maven plugin](https://github.com/vanniktech/gradle-maven-publish-plugin) and verify properties in `~/.gradle/gradle.properties`:
      <img width="896" height="293" alt="image" src="https://github.com/user-attachments/assets/fa85332d-91d8-4715-924d-aa8b6f86c64c" />
    * Run `./gradlew publishToMavenCentral --no-configuration-cache` in the root folder to publish it to Maven Central.
    * Publish the deployment on [Maven Central Repository (Sonatype)](https://central.sonatype.com/publishing).
* Publish the MCP Server NPM package:
  ```bash
  cd integrations/mcp-server
  npm pkg fix
  npm publish
  cd ../..
  ```
* Test and publish a new version of the Python Freerouting Client on PyPI (in the separate `freerouting-python-client` repository). Keep the PyPI package version in sync with the Freerouting GA release.
* Submit KiCad Addon Repository update:
    * You can perform this update directly on GitLab's website without cloning the repository locally:
    * Go to your GitLab fork of the official repository: [`https://gitlab.com/freeroutingapp/metadata`](https://gitlab.com/freeroutingapp/metadata) (or fork [`https://gitlab.com/kicad/addons/metadata`](https://gitlab.com/kicad/addons/metadata) if not already done, and click **Update fork** to bring it up to date).
    * Navigate to [`packages/app.freerouting.kicad-plugin/metadata.json`](https://gitlab.com/freeroutingapp/metadata/-/blob/main/packages/app.freerouting.kicad-plugin/metadata.json).
    * Click **Edit** -> **Edit single file**.
    * Paste the updated version entry or content from `integrations/KiCad/metadata.json` into the editor.
    * In the **Commit changes** section:
      * Set **Commit message**: `Update Freerouting Addon to <version>`
      * Set **Commit to a new branch**: enter a new branch name (e.g. `freerouting-<version>`). *Do not commit to `main`*, as GitLab CI package validation requires a dedicated branch.
      * Ensure **Create a merge request for this change** is checked.
      * Click **Commit changes**.
    * On the Merge [Request page](https://gitlab.com/kicad/addons/metadata/-/merge_requests), the GitLab CI pipeline will automatically start and run the `validate` and `build` stages:
      * Click the **Pipelines** tab on the MR to view progress.
      * Once the `build` job finishes, open its log: it outputs a link to a temporary PCM repository containing only the updated package.
      * In KiCad, open **Plugin and Content Manager (PCM)** -> **Manage** -> **Repository**, add that temporary repository URL, and test installing and running the plugin to verify everything works end-to-end.
    * *Note:* After the merge request is approved and merged, a scheduled job syncs updates to the official KiCad PCM repository within ~24 hours.
* **Docker Image Updates (GHCR):**
    * The multi-architecture Docker image is automatically built and published to GitHub Container Registry ([`ghcr.io/freerouting/freerouting`](https://github.com/freerouting/freerouting/pkgs/container/freerouting)) via `.github/workflows/docker-release.yml` whenever a release is published on GitHub. No manual build or push is required.
* Optionally regenerate non-official SDK scaffolds from this repository before preparing SDK PRs:
    * `./scripts/sdk/regenerate-all.ps1 -SharedVersion <version>`
    * `./scripts/sdk/generate-javascript-client.ps1`
    * `./scripts/sdk/generate-csharp-client.ps1`
    * `./scripts/sdk/generate-cpp-client.ps1`

### 5. Next Development Cycle

* Create a branch for the next development snapshot:
  ```bash
  git checkout -b chore/bump-to-next-snapshot
  ```
* Change `ext.publishInfo.versionId` in `gradle/project-info.gradle` to the next snapshot version (e.g. `2.5.1-SNAPSHOT`).
* Commit, push, open a PR from `chore/bump-to-next-snapshot` → `master`, and merge it into `master`.

## How to update the MCP Server NPM package

The `@freerouting/freerouting-mcp-server` package lives in `integrations/mcp-server`. Its npm semver tracks the main Freerouting release (not an independent version line).

When preparing a Freerouting GA release (or shipping MCP bridge fixes between releases):

1. **Log in to npm** (requires access to the `@freerouting` scope):
   ```bash
   npm login
   ```
2. **Navigate to the package directory**:
   ```bash
   cd integrations/mcp-server
   ```
3. **Set the package version** to match the Freerouting GA release (same value as `ext.publishing.versionId`):
   ```bash
   npm version <version> --no-git-tag-version
   ```
   Or edit `"version"` in `package.json` manually.
4. **Verify the package manifest** before publishing:
   ```bash
   npm pkg fix
   ```
   The `bin` entry must map the command name to `./src/index.js` (with the `./` prefix), and `src/index.js` must start with `#!/usr/bin/env node`. If npm warns that the bin script was removed during publish, fix these two items and publish again.
5. **Publish**:
   ```bash
   npm publish
   ```
   *(The first publish of this scoped package required `npm publish --access public`; subsequent releases inherit public access.)*

End users should run `npx -y @freerouting/freerouting-mcp-server` without a version pin so they always receive the latest bridge compatible with the public API.

## Client API SDK strategy

Freerouting keeps its official API clients in separate repositories from the core Java codebase.

- Current official support: **Python only** (repository: `freerouting-python-client`).
- We also see active demand for additional SDKs, especially **JavaScript** and **C++**.

### Why separate repositories

- SDKs and the Freerouting server have different release cadences and dependency ecosystems.
- Language-specific CI/CD and package publishing (PyPI, npm, etc.) stay isolated and easier to maintain.
- Users get cleaner issue tracking and documentation per language.

### Source of truth and generation model

- The OpenAPI definition from this repository is the canonical API contract.
- SDK repositories should generate client code from that OpenAPI contract, then keep a small handwritten layer for ergonomics.
- Keep generated code reproducible by committing generation config and templates into each SDK repository.

### JavaScript/C++ expansion plan

- Start with JavaScript first (higher ecosystem demand and lower maintenance cost than C++ bindings).
- Add C++ only with clear ownership and a maintained packaging/distribution plan.
- Keep one repository per SDK (`freerouting-js-client`, `freerouting-cpp-client`) instead of mixing multiple language toolchains into this repository.

### Release automation recommendation

It is both possible and recommended to add templates/scripts that regenerate the JavaScript client at each Freerouting release.

This repository includes starter generator scripts in `scripts/sdk/` for Python, JavaScript, C#, and C++.
Officially supported client publishing remains Python-only for now.
Use `scripts/sdk/regenerate-all.ps1` when you want one command that keeps all generated SDK scaffolds aligned to the same API and version.

Recommended guardrails:

- Trigger generation from release tag events (or manually for pre-releases).
- Open an automated PR in the JavaScript SDK repository with the regenerated client and changelog.
- Run SDK tests in CI before merge; publish to npm only after PR approval.
- Avoid direct auto-publish on generation alone to reduce risk from contract-breaking changes.
- Apply the same PR-first approach for C# and C++ scaffolds (generate, test in SDK repo, review, then publish).

## Source formatting and cleanup

- Freerouting follows Google coding conventions from Google Java Style, enforced via Spotless and Checkstyle.
- Verify formatting locally before committing:

```bash
./gradlew spotlessCheck checkstyleMain checkstyleTest
```

- When formatting changes are intentionally needed, apply them via:

```bash
./gradlew spotlessApply
```

## Questions & Support

Have questions about the architecture or need help getting started?

- **GitHub Discussions**: [Start a discussion](https://github.com/freerouting/freerouting/discussions)
- **Twitter/X**: [@freeroutingPCB](https://x.com/freeroutingPCB) or [@andrasfuchs](https://x.com/andrasfuchs)
- **LinkedIn**: [Andras Fuchs](https://www.linkedin.com/in/andrasfuchs/)
- **Email**: [info@freerouting.app](mailto:info@freerouting.app)
