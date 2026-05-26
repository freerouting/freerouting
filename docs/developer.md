<p align="center">
<img src="https://raw.githubusercontent.com/freerouting/freerouting/master/assets/social_preview/freerouting_social_preview_1280x960_v2.png" alt="Freerouting" title="Freerouting" align="center">
</p>
<h1 align="center">Freerouting</h1>
<h5 align="center">Freerouting is an advanced autorouter for all PCB programs that support the standard Specctra or Electra DSN interface.</h5>

<br/>
<br/>

# Information for developers

## How to build it from source

### Requirements

- Java >= 25 ([Adoptium Temurin 25 JRE](https://adoptium.net/temurin/releases/))
- [Gradle 9.x](https://gradle.org/releases/)
- Internet connection (dependencies are downloaded automatically)
- For IDE integration: Gradle extension (not necessary for command line usage)

### IDE

Open the `freerouting` [Gradle](http://www.gradle.org/) project in your favourite IDE (NB, IntelliJ, Eclipse etc. with Gradle Plugin) and build it by calling the `assemble` task.

### Command Line

Navigate to the [Gradle](http://www.gradle.org/) project (e.g., `path/to/freerouting`) and enter the following command

#### Bash (Linux/OS X/Cygwin/other Unix-like shell)

``` bash
./gradlew assemble
```

#### Windows (CMD)

```powershell
gradlew executableJar
```

![image](https://user-images.githubusercontent.com/910321/143483981-5f1f8473-098e-4cf2-997b-a34d14346853.png)

#### Generated Executables

All four .jar files will be generated in the `build\libs` subfolder. You would typically run the `freerouting-current-executable.jar` file.

## How to create a new release

Creating a release takes about half an hour if everything goes according to the plan. Usually it doesn't, so free up ~3 hours for this.

Let's suppose that the new version is `2.3.4`. You need to complete these steps:

* Run the `gradlew wrapper --gradle-version latest` command to update the Gradle wrapper to the latest version.
* Run the `./gradlew dependencyUpdates useLatestVersions --no-configuration-cache --no-parallel` command to check if there are any dependencies that need to be updated. Update them manually if necessary and commit the changes.
* Check if there are any [outstanding pull requests](https://github.com/freerouting/freerouting/pulls) and merge them as well
* Change `ext.publishing.versionId` in `\gradle\project-info.gradle` to `2.3.4`
* Push it to GitHub
* Check if it was built successfully on GitHub Actions
* Create a new draft release
* Run `gradlew.bat executableJar` -> this will generate the files in `\build\libs\freerouting*.jar`
* Rename to `freerouting-current-executable.jar` to `freerouting-2.3.4.jar`
* Update the `integrations\KiCad`
    * Copy `freerouting-2.3.4.jar` into `\integrations\KiCad\kicad-freerouting\plugins\jar\`
    * Update `\integrations\KiCad\kicad-freerouting\plugins\plugin.ini` with the new filename
    * Update `\integrations\KiCad\kicad-freerouting\metadata.json`
    * Create a ZIP file from the `kicad-freerouting` folder
    * Copy this `kicad-freerouting.zip` file to `kicad-freerouting-2.3.4.zip`
    * Use KiCad Packager
      from [https://gitlab.com/kicad/addons/metadata/tools](https://gitlab.com/kicad/addons/metadata/-/tree/main/tools)
      to get hash and file sizes
    * Update `\integrations\KiCad\metadata.json` with these values
    * Push these changes to GitHub
        * Run the "Run KiCad repository validation" command in KiCad Packager
    * Delete previous fork at https://gitlab.com/freeroutingapp/metadata
      (Settings / General / Delete this project)
    * Fork https://gitlab.com/kicad/addons/metadata again
    * Create a new branch, named `freerouting-2.3.4`
    * Replace https://gitlab.com/freeroutingapp/metadata/-/blob/main/packages/app.freerouting.kicad-plugin/metadata.json
      with the new one
    * Create a merge request at https://gitlab.com/kicad/addons/metadata / Merge request / ...
* Update README.md, integrations.md, self-hosting.md and settings.md
* Publish the release
* Check if Windows, Linux and macOS installers were added to the release [in GitHub Actions](https://github.com/freerouting/freerouting/actions) and if the Docker image was updated on [GHCR.io](https://github.com/freerouting/freerouting/pkgs/container/freerouting)
* Publish the library to Maven Central
    * Use the [Gradle Maven plugin]([url](https://github.com/vanniktech/gradle-maven-publish-plugin)) and set the properties in `/.gradle/gradle.properties`
      <img width="896" height="293" alt="image" src="https://github.com/user-attachments/assets/fa85332d-91d8-4715-924d-aa8b6f86c64c" />      
    * Run the `./gradlew publishToMavenCentral --no-configuration-cache` command in the root folder to publish it to Maven Central
    * Publish the deployment [on Maven Central Repository](https://central.sonatype.com/publishing/deployments) 

* Update the Docker image on Azure
    1. build docker image locally for Linux x64 (~2 mins)
       ```
       docker build -t freerouting:latest .
         ```
    3. tag the docker image
       ```
       docker tag freerouting:latest freerouting.azurecr.io/freerouting/api:latest
         ```
    5. push image to Azure as freerouting.azurecr.io/freerouting/api:latest
       ```
         az login
       az acr login --name freerouting
       docker push freerouting.azurecr.io/freerouting/api:latest
       ```
* Change `ext.publishing.versionId` in `\gradle\project-info.gradle` again to `2.3.5-SNAPSHOT`

* Test and publish a new version of the Python Freerouting Client on PyPi.org (in the separate `freerouting-python-client` repository)
* Optionally regenerate non-official SDK scaffolds from this repository before preparing SDK PRs:
    * `./scripts/sdk/regenerate-all.ps1 -SharedVersion 2.3.4`
    * `./scripts/sdk/generate-javascript-client.ps1`
    * `./scripts/sdk/generate-csharp-client.ps1`
    * `./scripts/sdk/generate-cpp-client.ps1`

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

- Freerouting follows Google coding conventions from Google Java Style, and the configured OpenRewrite recipe
  `org.openrewrite.staticanalysis.CodeCleanup` applies those rules automatically to the codebase.
- Run the cleanup recipe locally with the Rewrite task:

```
  ./gradlew rewriteRun
```

- Run the same command before committing so formatting stays consistent with the automated checks.