#!/bin/bash

export APP_VERSION=$1
export APP_TYPE="app-image"

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
		--add-modules java.desktop,java.logging,java.management,java.net.http,java.sql,java.xml,jdk.crypto.ec,jdk.management \
		--strip-debug \
		--no-header-files \
		--no-man-pages \
		--strip-native-commands \
		--vm=server \
		--output $JAVA_HOME/runtime

echo "> Creating the app image"
$JAVA_HOME/bin/jpackage --input ../../build/dist/ \
 --name freerouting \
 --main-jar freerouting-executable.jar \
 --type $APP_TYPE --runtime-image $JAVA_HOME/runtime --app-version 1.0.0 --license-file ../../LICENSE \
 --icon ../../assets/icon/freerouting_icon_256x256_v3.icns

echo "> Registering freerouting:// URL scheme in Info.plist"
PLIST="freerouting.app/Contents/Info.plist"
/usr/libexec/PlistBuddy -c "Add :CFBundleURLTypes array" "$PLIST"
/usr/libexec/PlistBuddy -c "Add :CFBundleURLTypes:0 dict" "$PLIST"
/usr/libexec/PlistBuddy -c "Add :CFBundleURLTypes:0:CFBundleURLName string 'Freerouting Protocol'" "$PLIST"
/usr/libexec/PlistBuddy -c "Add :CFBundleURLTypes:0:CFBundleURLSchemes array" "$PLIST"
/usr/libexec/PlistBuddy -c "Add :CFBundleURLTypes:0:CFBundleURLSchemes:0 string freerouting" "$PLIST"

echo "> Creating DMG"
hdiutil create -volname "Freerouting" -srcfolder freerouting.app -ov -format UDZO freerouting-$APP_VERSION-macos-x64.dmg
