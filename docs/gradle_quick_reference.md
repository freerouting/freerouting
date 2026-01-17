# Quick Reference: Gradle Commands

## Daily Development Commands

### Fast Compilation (No JARs, No Tests)
```powershell
.\gradlew compileJava
```
**Use when:** Just checking if code compiles

### Build Everything (No Tests)
```powershell
.\gradlew build -x test
```
**Use when:** Creating JARs for testing, but skipping tests
**Creates:** jar, executableJar (skips javadocJar, sourcesJar)

### Full Build (With Tests)
```powershell
.\gradlew build
```
**Use when:** Before committing code
**Creates:** jar, executableJar (skips javadocJar, sourcesJar)
**Runs:** All tests

### Create Only Executable JAR
```powershell
.\gradlew executableJar
```
**Use when:** You just want the runnable JAR
**Creates:** freerouting-X.X.X-executable.jar

## Testing Commands

### Run Unit Tests Only
```powershell
.\gradlew test
```

### Run All Tests (Unit + Integration)
```powershell
.\gradlew check
```

### Run Specific Test
```powershell
.\gradlew test --tests "YourTestClass"
```

## Publishing Commands

### Publish to Local Maven Repository
```powershell
.\gradlew publishToMavenLocal
```
**Use when:** Testing Maven publication locally
**Creates:** ALL JARs (jar, javadocJar, sourcesJar, executableJar)
**Publishes to:** ~/.m2/repository

### Publish to Maven Central
```powershell
.\gradlew publish
```
**Use when:** Making an official release
**Creates:** ALL JARs (jar, javadocJar, sourcesJar, executableJar)
**Publishes to:** Maven Central (requires credentials)

## Cleanup Commands

### Clean Build Directory
```powershell
.\gradlew clean
```

### Stop Gradle Daemon
```powershell
.\gradlew --stop
```
**Use when:** Gradle seems stuck or you want to free memory

## Verification Commands

### Check Gradle Version
```powershell
.\gradlew --version
```

### List All Tasks
```powershell
.\gradlew tasks
```

### See What Would Run (Dry Run)
```powershell
.\gradlew build --dry-run
```

### Check for Dependency Updates
```powershell
.\gradlew dependencyUpdates
```

## Docker Commands

### Build Docker Image
```powershell
.\gradlew buildDockerImage
```

## Performance Tips

### Use Build Cache
```powershell
.\gradlew build --build-cache
```

### Parallel Execution
```powershell
.\gradlew build --parallel
```

### Skip Specific Tasks
```powershell
# Skip tests
.\gradlew build -x test

# Skip multiple tasks
.\gradlew build -x test -x integrationTest
```

## Troubleshooting

### Build Failing? Try Clean Build
```powershell
.\gradlew clean build
```

### Gradle Daemon Issues?
```powershell
.\gradlew --stop
.\gradlew build
```

### See Full Error Stack Trace
```powershell
.\gradlew build --stacktrace
```

### See Debug Output
```powershell
.\gradlew build --debug
```

## What Gets Created Where

### Build Output Directory
```
build/
├── classes/          # Compiled .class files
├── libs/             # All JAR files
│   ├── freerouting-2.0.1.jar              # Standard JAR
│   ├── freerouting-2.0.1-executable.jar   # Fat JAR (runnable)
│   ├── freerouting-2.0.1-javadoc.jar      # API docs (only when publishing)
│   └── freerouting-2.0.1-sources.jar      # Source code (only when publishing)
├── reports/          # Test and build reports
└── tmp/              # Temporary files
```

### Running the Executable JAR
```powershell
java -jar build/libs/freerouting-2.0.1-executable.jar
```

## Summary of Optimizations

✅ **Faster Builds:** javadoc and sources JARs only created when publishing
✅ **No Deprecation Warnings:** All Gradle warnings fixed
✅ **Modern Gradle:** Using latest best practices
✅ **Consistent Builds:** Always use `.\gradlew` not `gradle`

**Typical Build Times:**
- `.\gradlew compileJava`: ~15 seconds
- `.\gradlew build -x test`: ~30 seconds
- `.\gradlew build`: ~3-4 minutes (with tests)
- `.\gradlew publish`: ~4-5 minutes (includes javadoc generation)
