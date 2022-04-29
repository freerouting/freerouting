#!/bin/bash

export APP_VERSION=$1
export APP_TYPE="app-image"
export JPACKAGE_JVM="https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.3+7/OpenJDK17U-jdk_x64_linux_hotspot_17.0.3_7.tar.gz"
export JPACKAGE_HOME=.jdk/jdk-17.0.3+7

SOURCE="${BASH_SOURCE[0]}"
while [ -h "$SOURCE" ]; do # resolve $SOURCE until the file is no longer a symlink
  DIR="$( cd -P "$( dirname "$SOURCE" )" >/dev/null 2>&1 && pwd )"
  SOURCE="$(readlink "$SOURCE")"
  [[ $SOURCE != /* ]] && SOURCE="$DIR/$SOURCE" # if $SOURCE was a relative symlink, we need to resolve it relative to the path where the symlink file was located
done
DIR="$( cd -P "$( dirname "$SOURCE" )" >/dev/null 2>&1 && pwd )"

cd $DIR


if [ -d ".jdk/" ]; then
    echo "> JDK for package generation already downloaded"
else
    mkdir -p .jdk/ ; cd .jdk
    echo "> downloading JDK"
    curl -o jdk.tar.gz $JPACKAGE_JVM
    echo "> unpacking JDK"
    tar xvzf jdk.tar.gz
	cd ..
    echo "> creating runtime image"
    $JPACKAGE_HOME/bin/jlink -p "$JPACKAGE_HOME/jmods" \
        --add-modules java.desktop \
        --strip-debug \
        --no-header-files \
        --no-man-pages \
        --strip-native-commands \
        --vm=server \
        --compress=2 \
        --output $JPACKAGE_HOME/runtime
fi

cd $DIR

export JPKG_HOME=$(pwd)"/"$JPACKAGE_HOME"/"

cp ../LICENSE ../build/dist/LICENSE

$JPKG_HOME/bin/jpackage --input ../build/dist/ \
 --name freerouting \
 --main-jar freerouting-executable.jar \
 --type $APP_TYPE --runtime-image $JPACKAGE_HOME/runtime --app-version $APP_VERSION
 
mv freerouting freerouting-$APP_VERSION-linux-x64
cp ../build/dist/LICENSE freerouting-$APP_VERSION-linux-x64/LICENSE
mv ../build/dist/freerouting-executable.jar freerouting-$APP_VERSION-executable.jar 
 
zip -r freerouting-$APP_VERSION-linux-x64.zip freerouting-$APP_VERSION-linux-x64



