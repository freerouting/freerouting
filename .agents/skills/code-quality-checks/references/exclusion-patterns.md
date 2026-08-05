# Exclusion Patterns Reference

This document contains the complete exclusion patterns for code quality tools in the Freerouting project.

---

## Directories to Exclude from All Analysis

```
./fixtures/
./build/
./.gradle/
./logs/
./src_v19/
./examples/
./results/
./scripts/benchmark/results/
./helpset/
./docs/reference/
.run/
```

---

## File Extensions to Exclude from All Analysis

```
*.TAB
OFFSETS
*.ulp
*.flex
*.dsn
*.ses
*.frb
*.bin
*.exe
*.dll
*.so
*.class
*.jar
*.brd
.DS_Store
*.iml
*.ipr
*.iws
.gitignore
```

---

## Language-Specific Exclusions

### Non-English Resources (Exclude)

**HelpSet Directories:**
- `./src/main/resources/app/freerouting/helpset/de/`
- `./src/main/resources/app/freerouting/helpset/es/`
- `./src/main/resources/app/freerouting/helpset/fr/`
- `./src_v19/main/resources/app/freerouting/helpset/de/`
- `./src_v19/main/resources/app/freerouting/helpset/es/`
- `./src_v19/main/resources/app/freerouting/helpset/fr/`

**Localized Properties Files:**
- `*_ar.properties`, `*_bn.properties`, `*_de.properties`
- `*_es.properties`, `*_fr.properties`, `*_hi.properties`
- `*_it.properties`, `*_ja.properties`, `*_ko.properties`
- `*_pt.properties`, `*_ru.properties`, `*_zh.properties`
- `*_zh_tw.properties`

### English Resources (Must Analyze)

- `./src/main/resources/app/freerouting/helpset/en/`
- `*_en.properties`
- All English documentation and resource files

---

## Freerouting-Specific Requirements for Codespell

1. **Exclude Non-English Resources:**
   - Exclude localized helpset directories (`helpset/de/`, `helpset/es/`, `helpset/fr/`, etc.)
   - Exclude localized properties files (`*_ar.properties`, `*_bn.properties`, `*_de.properties`, etc.)
   - **Preserve English resources** (`helpset/en/`, `*_en.properties`) for validation

2. **Exclude Binary/Non-UTF-8 Files:**
   - `*.TAB` - Binary table files
   - `OFFSETS` - Binary offset files
   - `.run/` - Run configuration directory
   - `*.ulp` - Eagle library files
   - `*.flex` - Flex lexer files
   - `*.dsn`, `*.ses`, `*.frb` - Board design files

3. **Exclude Generated/Test Directories:**
   - `./fixtures/` - Test fixtures
   - `./build/` - Build outputs
   - `./.gradle/` - Gradle cache
   - `./logs/` - Log files
   - `./examples/` - Example files
   - `./results/` - Test results
   - `./scripts/benchmark/results/` - Benchmark results
   - `./src_v19/` - Legacy reference implementation

4. **Exclude Configuration Files:**
   - `.gitignore` - Git ignore patterns (contains non-word patterns)

5. **Project Ignore Words:**
   ```
   slave,master,ue,wan,nto,abd,te,fo,cas,ro,inout,snd,ser,weerd,deweerd,
   thead,ws,stdio,ois,highlight,curNet,onText,cripts
   ```
   **Note:** `cripts` is included to handle false positives from backslash-separated paths (e.g., `\scripts\build`) where codespell isolates the truncated word.

6. **Regex Ignore Pattern:**
   ```
   ignore-regex = '\[fnrstv]'
   ```
   **Explanation:** Ignore bracketed single letters f, n, r, s, t, v
