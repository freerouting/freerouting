# Issue and Pull Request Labels

This document outlines the label taxonomy used across the [Freerouting issue tracker](https://github.com/freerouting/freerouting/issues) and [pull requests](https://github.com/freerouting/freerouting/pulls).

Labels categorize issues and pull requests by type, subsystem, tech stack, EDA integration, platform, and workflow status.

---

## 1. Type & Kind

Labels that indicate the primary nature of the issue or pull request.

| Label | Color | Description | When to Apply |
| :--- | :--- | :--- | :--- |
| `bug` | `#fc2929` | Something isn't working as expected or intended | Bug reports, unexpected routing errors, crashes, or algorithmic regressions. |
| `enhancement` | `#84b6eb` | New feature or request for improvement | Feature requests, algorithmic improvements, usability upgrades, or new capabilities. |
| `documentation` | `#0075ca` | Improvements or additions to documentation and guides | Updates to `README.md`, `docs/`, inline javadoc, API specifications, or guides. |
| `question` | `#cc317c` | Further information is requested | User questions, discussions, or inquiries about usage and design. |

---

## 2. Components & Interfaces

Labels identifying which functional interface or system component is affected.

| Label | Color | Description | When to Apply |
| :--- | :--- | :--- | :--- |
| `GUI` | `#7952B3` | Graphical User Interface, Swing components, rendering, and visual tools | Issues or PRs related to the Swing desktop UI, board visualizer, interactive mode, or accessibility. |
| `CLI` | `#333333` | Command Line Interface, command arguments, and batch/headless execution | Command-line arguments, headless batch processing, CLI flags, or headless runner scripts. |
| `API` | `#0E8A16` | REST API server, OpenAPI specifications, and headless API endpoints | Embedded Jetty server, Jersey JAX-RS REST endpoints, session management, or OpenAPI specs. |
| `MCP` | `#6F42C1` | Model Context Protocol server and AI assistant integration bridge | The `@freerouting/freerouting-mcp-server` package, AI assistant tools, and MCP stdio/SSE bridges. |

---

## 3. Tech Stack & Environment

Labels identifying the programming language, infrastructure, or build environment.

| Label | Color | Description | When to Apply |
| :--- | :--- | :--- | :--- |
| `Java` | `#f89820` | Issues and pull requests related to the Java engine and core codebase | Core Java codebase, Gradle build scripts, JVM memory management, and Java dependencies. |
| `Python` | `#3572A5` | Issues, scripts, and clients related to Python | Python client library (`freerouting-python-client`), i18n translation scripts, or test utilities. |
| `CI` | `#006B75` | Continuous integration, workflows, quality gates, and automated builds | GitHub Actions workflows, Checkstyle/Spotless quality gates, automated test runners, and release pipelines. |
| `Docker` | `#2496ED` | Docker container images, container builds, and container deployments | `Dockerfile`, container builds, GitHub Packages (GHCR), or Azure container registry publishing. |
| `dependencies` | `#0366d6` | Pull requests that update a dependency file | Automated or manual dependency bumps (Dependabot, Gradle plugins, libraries). |

---

## 4. EDA Integrations

Labels identifying specific third-party EDA suites and CAD integrations.

| Label | Color | Description | When to Apply |
| :--- | :--- | :--- | :--- |
| `KiCad` | `#1F5B8B` | Issues and integrations related to KiCad EDA software | KiCad plugin (`integrations/KiCad`), DSN export/import compatibility, or KiCad addon packaging. |
| `Autodesk Fusion` | `#E55B2B` | Issues and integrations related to Autodesk Fusion 360 / EAGLE | Autodesk Fusion 360 / EAGLE scripts and export/import workflows. |

---

## 5. Operating Systems & Platforms

Labels for platform-specific behavior, packaging, and testing.

| Label | Color | Description | When to Apply |
| :--- | :--- | :--- | :--- |
| `Windows` | `#fbca04` | Issues and build tasks specific to Microsoft Windows | Windows-specific paths, batch scripts (`.bat` / `.ps1`), MSI/EXE installers, or rendering. |
| `Linux` | `#FCC624` | Issues and build tasks specific to Linux platforms | Linux distributions (Debian, Ubuntu, Fedora, Arch), headless graphics, or shell scripts. |
| `macOS` | `#d93f0b` | Issues and build tasks specific to Apple macOS | macOS DMG installers, Apple Silicon / Intel JVM compatibility, or macOS menu integration. |

---

## 6. Triage, Community & Status

Labels used during issue triage, community contributions, and lifecycle management.

| Label | Color | Description | When to Apply |
| :--- | :--- | :--- | :--- |
| `good first issue` | `#4F2579` | Ideal for first contributors | Scoped, beginner-friendly tasks suitable for newcomers to open-source or Freerouting. |
| `help wanted` | `#159818` | Extra attention or community help is needed | Open issues where maintainers actively seek assistance from community contributors. |
| `missing-info` | `#e6e6e6` | More information needed | Reports missing reproduction files (`.dsn` / `.zip`), logs, version info, or OS details. |
| `duplicate` | `#cccccc` | This issue or pull request already exists | Issues or PRs that describe an already tracked bug or feature request. |
| `wontfix` | `#ffffff` | This will not be worked on | Proposed changes or feature requests that fall outside project scope or goals. |
| `support/donation` | `#820438` | Donation and financial support related topics | Inquiries or tasks related to GitHub Sponsors, donations, and funding. |
| `no-issue-activity` | `#ededed` | Stale issue due to inactivity | Automatically applied to inactive issues by the stale workflow bot. |
| `no-pr-activity` | `#ededed` | Stale pull request due to inactivity | Automatically applied to inactive pull requests by the stale workflow bot. |
