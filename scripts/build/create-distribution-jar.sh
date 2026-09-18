#!/bin/bash
set -e

export APP_VERSION=$1

SOURCE="${BASH_SOURCE[0]}"
while [ -h "$SOURCE" ]; do
  DIR="$( cd -P "$( dirname "$SOURCE" )" >/dev/null 2>&1 && pwd )"
  SOURCE="$(readlink "$SOURCE")"
  [[ $SOURCE != /* ]] && SOURCE="$DIR/$SOURCE"
done
DIR="$( cd -P "$( dirname "$SOURCE" )" >/dev/null 2>&1 && pwd )"

echo "> Distribution directory=$DIR"
cd "$DIR"

cp ../../build/dist/freerouting-executable.jar "freerouting-$APP_VERSION.jar"

echo "> JAR created at scripts/build/freerouting-$APP_VERSION.jar"
