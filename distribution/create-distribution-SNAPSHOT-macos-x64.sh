#!/bin/bash

export APP_VERSION=$1
export APP_TYPE="dmg"

echo "> JAVA_HOME="$JAVA_HOME

SOURCE="${BASH_SOURCE[0]}"
while [ -h "$SOURCE" ]; do # resolve $SOURCE until the file is no longer a symlink
  DIR="$( cd -P "$( dirname "$SOURCE" )" >/dev/null 2>&1 && pwd )"
  SOURCE="$(readlink "$SOURCE")"
  [[ $SOURCE != /* ]] && SOURCE="$DIR/$SOURCE" # if $SOURCE was a relative symlink, we need to resolve it relative to the path where the symlink file was located
done
DIR="$( cd -P "$( dirname "$SOURCE" )" >/dev/null 2>&1 && pwd )"

echo "> Distribution directory="$DIR
cd $DIR

echo "> Building the Java runtime"
$JAVA_HOME/bin/jlink -p "$JAVA_HOME/jmods" \
		--add-modules java.desktop \
		--strip-debug \
		--no-header-files \
		--no-man-pages \
		--strip-native-commands \
		--vm=server \
		--compress=2 \
		--output $JAVA_HOME/runtime

echo "> Creating the package"
$JAVA_HOME/bin/jpackage --input ../build/dist/ \
 --name freerouting \
 --main-jar freerouting-executable.jar \
 --type $APP_TYPE --runtime-image $JAVA_HOME/runtime --app-version 1.0.0 --license-file ../LICENSE 

mv freerouting-1.0.0.dmg freerouting-$APP_VERSION-macos-x64.dmg

