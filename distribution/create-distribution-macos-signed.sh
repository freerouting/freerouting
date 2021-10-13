#!/bin/bash

export APP_VERSION=$1
export APP_TYPE="app-image"
#export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-13.0.1.jdk/Contents/Home/"
export JPACKAGE_JVM="https://download.java.net/java/GA/jdk14/076bab302c7b4508975440c56f6cc26a/36/GPL/openjdk-14_osx-x64_bin.tar.gz"
export APPLE_DEVELOPER_ID=$2


SOURCE="${BASH_SOURCE[0]}"
while [ -h "$SOURCE" ]; do # resolve $SOURCE until the file is no longer a symlink
  DIR="$( cd -P "$( dirname "$SOURCE" )" >/dev/null 2>&1 && pwd )"
  SOURCE="$(readlink "$SOURCE")"
  [[ $SOURCE != /* ]] && SOURCE="$DIR/$SOURCE" # if $SOURCE was a relative symlink, we need to resolve it relative to the path where the symlink file was located
done
DIR="$( cd -P "$( dirname "$SOURCE" )" >/dev/null 2>&1 && pwd )"

cd $DIR


if [ -d ".jdk14/jdk-14.jdk/" ]; then
    echo "> jdk 14 for package generation already downloaded"
else
    mkdir -p .jdk14/ ; cd .jdk14
    echo "> downloading jdk 14"
    curl -o jdk14.tar.gz $JPACKAGE_JVM
    echo "> unpacking jdk 14"
    tar xvzf jdk14.tar.gz
    echo "> creating runtime image"
    $JAVA_HOME/bin/jlink -p "$JAVA_HOME/jmods" \
        --add-modules java.desktop \
        --strip-debug \
        --no-header-files \
        --no-man-pages \
        --strip-native-commands \
        --vm=server \
        --compress=2 \
        --output runtime
fi

cd $DIR

export JPKG_HOME=$(pwd)"/.jdk14/jdk-14.jdk/Contents/Home"
export JPKG_EXECUTABLE=$JPKG_HOME/bin/jpackage

echo "> creating application image"
$JPKG_EXECUTABLE --input ../build/dist/ \
 --name Freerouting \
 --main-jar freerouting-executable.jar \
 --type $APP_TYPE --runtime-image .jdk14/runtime --app-version $APP_VERSION

echo "> signing the application image"
/usr/bin/codesign --force --sign $APPLE_DEVELOPER_ID --deep Freerouting-$APP_VERSION.app

echo "> creating macOS DMG"
/usr/bin/hdiutil create -srcfolder Freerouting-$APP_VERSION Freerouting-$APP_VERSION.dmg -fs HFS+

mv Freerouting-$APP_VERSION.dmg freerouting-$APP_VERSION-macos-x64.dmg

