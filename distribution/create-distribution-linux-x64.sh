#!/bin/bash

echo "> JAVA_HOME="$JAVA_HOME

export APP_VERSION=$1
export APP_TYPE="app-image"

cp ../LICENSE ../build/dist/LICENSE

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
 --type $APP_TYPE --runtime-image $JAVA_HOME/runtime --app-version $APP_VERSION

echo "> Composing the distribution file"
mv freerouting freerouting-$APP_VERSION-linux-x64
cp ../build/dist/LICENSE freerouting-$APP_VERSION-linux-x64/LICENSE
mv ../build/dist/freerouting-executable.jar freerouting-$APP_VERSION-executable.jar 
 
zip -r freerouting-$APP_VERSION-linux-x64.zip freerouting-$APP_VERSION-linux-x64



