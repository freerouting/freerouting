# Introduction

First off, thank you for considering contributing to Freerouting. It's people like you that make Freerouting such a great tool.

Following these guidelines helps to communicate that you respect the time of the developers managing and developing this open source project. In return, they should reciprocate that respect in addressing your issue, assessing changes, and helping you finalize your pull requests.

Freerouting is an open source project and we love to receive contributions from our community — you! There are many ways to contribute, from writing tutorials or blog posts, improving the documentation, submitting bug reports and feature requests or writing code which can be incorporated into Freerouting itself.

**UI translations:** Freerouting uses an LLM translation pipeline. Please read [`docs/translations.md`](translations.md) before changing non-English UI strings — edit glossary files and request a full-locale re-translation; do not patch `*_{locale}.properties` directly.

# Ground Rules

Responsibilities
* Ensure cross-platform compatibility for every change that's accepted. Windows, Mac, Debian & Ubuntu Linux.
* Create issues for any major changes and enhancements that you wish to make. Discuss things transparently and get community feedback.
* Be welcoming to newcomers and encourage diverse new contributors from all backgrounds.

# Your First Contribution

Unsure where to begin contributing to Freerouting? You can start by looking through these beginner and help-wanted issues:
Beginner issues - issues which should only require a few lines of code, and a test or two.
Help wanted issues - issues which should be a bit more involved than beginner issues.
Both issue lists are sorted by total number of comments. While not perfect, number of comments is a reasonable proxy for impact a given change will have.

### Bonus points: Add a link to a resource for people who have never contributed to open source before.

Working on your first Pull Request? You can learn how from this *free* series, [How to Contribute to an Open Source Project on GitHub](https://egghead.io/series/how-to-contribute-to-an-open-source-project-on-github) and here are a couple of friendly tutorials you can check out: http://makeapullrequest.com/ and http://www.firsttimersonly.com/.

At this point, you're ready to make your changes! Feel free to ask for help; everyone is a beginner at first :smile_cat:

If a maintainer asks you to "rebase" your PR, they're saying that a lot of code has changed, and that you need to update your branch so it's easier to merge.

# Getting started

For something that is bigger than a one or two line fix:

1. Create your own fork of the code
2. Do the changes in your fork
3. If you like the change and think the project could use it:
    * Be sure you have followed the code style for the project.
    * Note the Freerouting Code of Conduct.
    * Send a pull request.

## Code quality and formatting

Freerouting uses Google Java Format through Spotless, Checkstyle, pre-commit hooks, and
GitHub Actions. Formatting and validation are part of the contribution contract: a change
that fails these checks is not ready to commit.

Install the local checks once:

```bash
python -m pip install pre-commit
pre-commit install
```

Run the same checks locally before committing:

```bash
pre-commit run --all-files
./gradlew spotlessCheck checkstyleMain checkstyleTest checkstyleRewriteRecipes
python scripts/i18n/extract-context.py --check
```

On Windows, use `.\gradlew.bat` instead of `./gradlew`. The Gradle quality hook and
`scripts/pre-commit` are check-only: they report failures rather than applying formatting
to unrelated files or staging changes automatically. The generic hygiene hooks
automatically repair trailing whitespace, final newlines, and LF line endings only in
files selected for the current commit. Review and stage those changes, then rerun the
hook when it reports that files were modified.

`spotlessApply` is an intentional formatting operation, not a normal pre-commit step.
It formats every configured Java source. Run it only when you explicitly intend to make
a formatting change, then inspect the complete diff and keep that normalization separate
from functional changes:

```bash
./gradlew spotlessApply
git diff --ignore-space-at-eol --check
git diff --stat
```

The repository uses LF for source, build, metadata, and documentation files on every
platform through `.gitattributes`. Do not change `core.autocrlf` back and forth to fix a
single working tree. If the repository needs one-time normalization, do it in a dedicated
commit with no unrelated changes.

GitHub Actions runs the same quality gates on pull requests. A contributor should resolve
local Checkstyle, Spotless, line-ending, or i18n-context failures before opening or
updating a pull request.

As a rule of thumb, changes are obvious fixes if they do not introduce any new functionality or creative thinking. As long as the change does not affect functionality, some likely examples include the following:
* Spelling / grammar fixes
* Typo correction, white space and formatting changes
* Comment clean up
* Bug fixes that change default return values or error codes stored in constants
* Adding logging messages or debugging output
* Changes to ‘metadata’ files like .gitignore, build scripts, etc.
* Moving source files from one directory or package to another

# How to report a bug

 When filing an issue, make sure to answer these five questions:

 1. What version of Freerouting are you using?
 2. What operating system and processor architecture are you using?
 3. What did you do?
 4. What did you expect to see?
 5. What did you see instead?

If you find yourself wishing for a feature that doesn't exist in Freerouting, you are probably not alone. There are bound to be others out there with similar needs. Many of the features that Freerouting has today have been added because our users saw the need. Open an issue on our issues list on GitHub which describes the feature you would like to see, why you need it, and how it should work.
