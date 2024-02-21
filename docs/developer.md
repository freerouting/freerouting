<p align="center">
<img src="https://raw.githubusercontent.com/freerouting/freerouting/master/design/social_preview/freerouting_social_preview_1280x960_v2.png" alt="Freerouting" title="Freerouting" align="center">
</p>
<h1 align="center">Freerouting</h1>
<h5 align="center">Freerouting is an advanced autorouter for all PCB programs that support the standard Specctra or Electra DSN interface.</h5>

<br/>
<br/>

# Information for developers

## How to build it from source

### Requirements

- Java >= 21 ([Adoptium Temurin 21 JRE](https://adoptium.net/temurin/releases/))
- [Gradle 6.x](https://gradle.org/releases/)
- Internet connection (dependencies are downloaded automatically)
- For IDE integration: Gradle extension (not necessary for command line usage)

### IDE

Open the `freerouting` [Gradle](http://www.gradle.org/) project in your favourite IDE (NB, IntelliJ, Eclipse etc. with Gradle Plugin) and build it
by calling the `assemble` task.

### Command Line

Navigate to the [Gradle](http://www.gradle.org/) project (e.g., `path/to/freerouting`) and enter the following command

#### Bash (Linux/OS X/Cygwin/other Unix-like shell)

``` bash
./gradlew assemble
```

#### Windows (CMD)

```powershell
gradlew assemble
```

![image](https://user-images.githubusercontent.com/910321/143483981-5f1f8473-098e-4cf2-997b-a34d14346853.png)

#### Generated Executables

All four .jar files will be generated in the `build\libs` subfolder. You would typically run the `freerouting-executable.jar` file.

## How to create a new release

Creating a release takes about half an hour if everything goes according to the plan. Usually it doesn't, so free up ~3 hours for this.

Let's suppose that the new version is `2.3.4`. You need to complete these steps:

* Check if there are [updated translations on Crowdin](https://freerouting.crowdin.com/u/projects/1/activity) and merge them if needed
* Check if there are any [outstanding pull requests](https://github.com/freerouting/freerouting/pulls) and merge them as well
* Change `ext.publishing.versionId` in `\gradle\project-info.gradle` to `2.3.4`
* Push it to GitHub
* Check if it was built successfully on GitHub Actions
* Create a new draft release
* Run `gradlew.bat assemble` -> this will generate the files in `\build\libs\freerouting*.jar`
* Rename to `freerouting-executable.jar` to `freerouting-2.3.4.jar` and add it to the release draft
* Update the `integrations\KiCad`
	* Copy `freerouting-2.3.4.jar` into `\integrations\KiCad\kicad-freerouting\plugins\jar\`
	* Update `\integrations\KiCad\kicad-freerouting\plugins\plugin.ini` with the new filename
	* Update `\integrations\KiCad\kicad-freerouting\metadata.json`
	* Create a ZIP file from the `kicad-freerouting` folder
	* Copy this `kicad-freerouting.zip` file to `kicad-freerouting-2.3.4.zip`
	* Use KiCad Packager from [https://gitlab.com/kicad/addons/metadata/tools](https://gitlab.com/kicad/addons/metadata/-/tree/main/tools) to get hash and file sizes
	* Update `\integrations\KiCad\metadata.json` with these values
 	* Push these changes to GitHub
  	* Run the "Run Kicad repository validation" command in KiCad Packager
	* Delete previous fork at https://gitlab.com/freeroutingapp/metadata
(Settings / General / Delete this project)
	* Fork https://gitlab.com/kicad/addons/metadata again
	* Create a new branch, named `freerouting-2.3.4`
	* Replace https://gitlab.com/freeroutingapp/metadata/-/blob/main/packages/app.freerouting.kicad-plugin/metadata.json with the new one
	* Create a megre request at https://gitlab.com/kicad/addons/metadata / Merge request / ...
* Update README
* Publish the release
* Check if Windows and Linux installers were added to the release [in GitHub Actions](https://github.com/freerouting/freerouting/actions)
* Set the `SONATYPE_USERNAME` and `SONATYPE_PASSWORD` environment variables, and run the `gradle publish` command in the root folder to publish it to Maven Central

![image](https://github.com/freerouting/freerouting/assets/910321/37881dba-747a-4ae3-811c-480782d8994d)

* Change `ext.publishing.versionId` in `\gradle\project-info.gradle` again to `2.3.5-SNAPSHOT`
