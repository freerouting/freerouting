#!/bin/bash

export APP_VERSION=$1
export APP_TYPE="app-image"
#export JAVA_HOME="/usr/lib/jvm/java-11-openjdk-amd64/"
export JPACKAGE_JVM="https://download.java.net/java/GA/jdk14/076bab302c7b4508975440c56f6cc26a/36/GPL/openjdk-14_linux-x64_bin.tar.gz"


SOURCE="${BASH_SOURCE[0]}"
while [ -h "$SOURCE" ]; do # resolve $SOURCE until the file is no longer a symlink
  DIR="$( cd -P "$( dirname "$SOURCE" )" >/dev/null 2>&1 && pwd )"
  SOURCE="$(readlink "$SOURCE")"
  [[ $SOURCE != /* ]] && SOURCE="$DIR/$SOURCE" # if $SOURCE was a relative symlink, we need to resolve it relative to the path where the symlink file was located
done
DIR="$( cd -P "$( dirname "$SOURCE" )" >/dev/null 2>&1 && pwd )"

cd $DIR


if [ -d ".jdk14/jdk-14/" ]; then
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

export JPKG_HOME=$(pwd)"/.jdk14/jdk-14/"
export JPKG_EXECUTABLE=$JPKG_HOME/bin/jpackage

cp ../LICENSE ../build/dist/LICENSE

$JPKG_EXECUTABLE --input ../build/dist/ \
 --name Freerouting \
 --main-jar freerouting-executable.jar \
 --type $APP_TYPE --runtime-image .jdk14/runtime --app-version $APP_VERSION
 
mv Freerouting freerouting-$APP_VERSION-linux-x64
cp ../build/dist/LICENSE freerouting-$APP_VERSION-linux-x64/LICENSE
mv ../build/dist/freerouting-executable.jar freerouting-$APP_VERSION-executable.jar 
 
zip -r freerouting-$APP_VERSION-linux-x64.zip freerouting-$APP_VERSION-linux-x64



