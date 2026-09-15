# Freerouting GitHub Label Taxonomy & Reference

This reference documents the official labels, hex colors, and application criteria across the Freerouting repository.

---

## 1. Type & Kind

| Label | Color | Description | Criteria / When to Apply |
| :--- | :--- | :--- | :--- |
| `bug` | `#fc2929` | Something isn't working as expected or intended | Crashes, routing regressions, clearance violations, deadlocks, incorrect UI behavior, build failures. |
| `enhancement` | `#84b6eb` | New feature or request for improvement | New algorithms, settings capabilities, UI controls, API endpoints, CLI flags, or integrations. |
| `documentation` | `#0075ca` | Improvements or additions to documentation and guides | Updates to `README.md`, `docs/`, inline javadoc, API specifications, tutorials, or routing guides. |
| `question` | `#cc317c` | Further information is requested | Inquiries about usage, board design advice, parameter tuning, or general discussions. |

---

## 2. Components & Core Domain

| Label | Color | Description | Criteria / When to Apply |
| :--- | :--- | :--- | :--- |
| `routing-engine` | `#D93F0B` | Core autorouting pipeline: pathfinding, fanout, DRC clearance, optimizer, and route metrics | Spatial search trees (`ShapeSearchTree`), maze routing, modified A*, Lee expansion, clearance matrix calculation, keepouts, fanout, trace optimization, unrouted net handling, or plane routing. |
| `GUI` | `#7952B3` | Graphical User Interface, Swing components, rendering, and visual tools | Desktop Swing interface, board visualizer, interactive mode, net class tables, settings dialogs, rendering, and accessibility. |
| `CLI` | `#333333` | Command Line Interface, command arguments, and batch/headless execution | Command-line arguments, headless batch execution (`Freerouting.java`), parameter parsing, or automated runner scripts. |
| `API` | `#0E8A16` | REST API server, OpenAPI specifications, and headless API endpoints | Embedded Jetty server, Jersey JAX-RS REST endpoints, session management, or OpenAPI specs. |
| `MCP` | `#6F42C1` | Model Context Protocol server and AI assistant integration bridge | `@freerouting/freerouting-mcp-server` package, AI assistant tools, stdio/SSE bridges, and MCP schema definitions. |

---

## 3. Tech Stack & Infrastructure

| Label | Color | Description | Criteria / When to Apply |
| :--- | :--- | :--- | :--- |
| `Java` | `#f89820` | Issues and pull requests related to the Java engine and core codebase | Core Java codebase, Gradle build scripts, JVM memory management, heap allocation, and Java dependencies. |
| `Python` | `#3572A5` | Issues, scripts, and clients related to Python | Python client library (`freerouting-python-client`), i18n translation scripts (`scripts/i18n/`), or test utilities. |
| `CI` | `#006B75` | Continuous integration, workflows, quality gates, and automated builds | GitHub Actions workflows (`.github/workflows/`), pre-commit hooks, Spotless/Checkstyle gates, and release packaging. |
| `Docker` | `#2496ED` | Docker container images, container builds, and container deployments | `Dockerfile`, container builds, GitHub Packages (GHCR), or Azure container registry publishing. |
| `dependencies` | `#0366d6` | Pull requests that update a dependency file | Automated or manual dependency bumps (Dependabot, Gradle plugins, third-party libraries). |

---

## 4. EDA Integrations

| Label | Color | Description | Criteria / When to Apply |
| :--- | :--- | :--- | :--- |
| `KiCad` | `#1F5B8B` | Issues and integrations related to KiCad EDA software | KiCad plugin (`integrations/KiCad`), KiCad DSN/SES compatibility, or KiCad addon packaging. |
| `Autodesk Fusion` | `#E55B2B` | Issues and integrations related to Autodesk Fusion 360 / EAGLE | Autodesk Fusion 360 / EAGLE ULP export scripts (`eagle2freerouting.ulp`) and SCR session import scripts. |

---

## 5. Platforms & Operating Systems

| Label | Color | Description | Criteria / When to Apply |
| :--- | :--- | :--- | :--- |
| `Windows` | `#fbca04` | Issues and build tasks specific to Microsoft Windows | Windows-specific paths, batch scripts (`.bat` / `.ps1`), MSI/EXE installers, or rendering. |
| `Linux` | `#FCC624` | Issues and build tasks specific to Linux platforms | Linux distributions (Debian, Ubuntu, Arch, Fedora), XDG directories, headless graphics, or shell scripts. |
| `macOS` | `#d93f0b` | Issues and build tasks specific to Apple macOS | macOS DMG installers, Apple Silicon / Intel JVM compatibility, or macOS menu integration. |

---

## 6. Triage, Community & Lifecycle

| Label | Color | Description | Criteria / When to Apply |
| :--- | :--- | :--- | :--- |
| `good first issue` | `#4F2579` | Ideal for first contributors | Scoped, beginner-friendly tasks suitable for newcomers to open-source or Freerouting. |
| `help wanted` | `#159818` | Extra attention or community help is needed | Open issues where maintainers actively seek assistance from community contributors. |
| `missing-info` | `#e6e6e6` | More information needed | Reports missing reproduction files (`.dsn` / `.zip`), logs, version info, or OS details. |
| `duplicate` | `#cccccc` | This issue or pull request already exists | Issues or PRs that describe an already tracked bug or feature request. |
| `wontfix` | `#ffffff` | This will not be worked on | Proposed changes or feature requests that fall outside project scope or goals. |
| `support/donation` | `#820438` | Donation and financial support related topics | Inquiries or tasks related to GitHub Sponsors, donations, and funding. |
| `no-issue-activity` | `#ededed` | Stale issue due to inactivity | Automatically applied to inactive issues by the stale workflow bot. |
| `no-pr-activity` | `#ededed` | Stale pull request due to inactivity | Automatically applied to inactive pull requests by the stale workflow bot. |
