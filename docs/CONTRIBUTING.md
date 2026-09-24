# Contributing to Freerouting

Hi there! Thank you for taking the time to contribute to Freerouting. Whether you're fixing a bug, improving the routing algorithms, refining the UI, translating strings, or polishing documentation, I truly appreciate your help.

---

## Getting in Touch

If you have questions, ideas, or want to discuss an approach before writing code, feel free to reach out directly:

- **GitHub Discussions**: [Start or join a discussion](https://github.com/freerouting/freerouting/discussions) — ideal for architectural ideas, feature suggestions, and open questions.
- **Freerouting on X (Twitter)**: [@freeroutingPCB](https://x.com/freeroutingPCB)
- **Personal X (Twitter)**: [@andrasfuchs](https://x.com/andrasfuchs)
- **LinkedIn**: [Andras Fuchs](https://www.linkedin.com/in/andrasfuchs/)
- **Email**: [info@freerouting.app](mailto:info@freerouting.app)

---

## Where to Start

Unsure where to begin? Check out these curated issues:
- [Good first issues](https://github.com/freerouting/freerouting/labels/good%20first%20issue) — well-scoped tasks, typically needing just a few lines of code and a test.
- [Help wanted issues](https://github.com/freerouting/freerouting/labels/help%20wanted) — areas where community input and contributions are actively sought.

For a full guide to our label system, take a look at [`docs/labels.md`](labels.md).

If you are new to open source or Git, welcome! Everyone starts somewhere. Feel free to ask questions in your PR or in Discussions if you get stuck.

---

## Ground Rules

- **Cross-platform**: Changes must work cleanly across Windows, macOS (Apple Silicon and Intel), and Linux.
- **Discuss major changes first**: For architectural refactors or new features, please open an issue or discussion first so we can align before you spend days coding.
- **Keep PRs focused**: Smaller, single-purpose PRs are much easier to review, test, and merge quickly.
- **UI translations**: Freerouting uses an automated LLM translation pipeline. Please read [`docs/translations.md`](translations.md) before changing non-English UI strings — update glossary files rather than editing `*_{locale}.properties` files directly.
- **Code of Conduct**: Please review and respect our [Code of Conduct](code_of_conduct.md).

---

## Development & Pull Request Workflow

1. **Fork & Branch**: Fork the repo and create a descriptive branch for your work (e.g. `fix/via-clearance` or `feature/kicad-ipc-import`).
2. **Make your changes**: Write clean, readable code and include unit or integration tests that verify your fix or feature.
3. **Verify quality gates**: Make sure local checks pass before committing (see below).
4. **Open a PR**: Submit your pull request against `master`. Describe what changed, why, and reference any relevant issue numbers.

---

## Code Quality & Formatting

Freerouting uses **Spotless** (Google Java Format), **Checkstyle**, explicit LF line endings, and **pre-commit** hooks to keep the codebase consistent.

### Set up local checks (one-time)

```bash
pip install pre-commit
pre-commit install
```

### Run quality checks before committing

```bash
pre-commit run --all-files
./gradlew spotlessCheck checkstyleMain checkstyleTest
python scripts/i18n/extract-context.py --check
```
*(On Windows, use `.\gradlew.bat` instead of `./gradlew`)*

- `spotlessCheck` verifies formatting without modifying files.
- If you deliberately need to reformat Java sources, run `./gradlew spotlessApply`, review the diff, and commit it separately from functional changes.
- The repository enforces LF line endings on all platforms via `.gitattributes`.

---

## Reporting Bugs & Suggesting Features

### How to report a bug
When filing an issue, please include:
1. Freerouting version (e.g. `v2.5.0`)
2. Operating system and architecture (e.g. Windows 11 x64, macOS M2, Ubuntu 24.04)
3. Step-by-step reproduction steps
4. Expected vs. actual behavior
5. Sample `.dsn` design file or log output (if possible) — this saves enormous time when investigating!

### Suggesting features
Have an idea to make Freerouting better? Open an [issue](https://github.com/freerouting/freerouting/issues) or start a [discussion](https://github.com/freerouting/freerouting/discussions) explaining the problem you're trying to solve and how you envision the feature working.

Thank you again for contributing to Freerouting!
