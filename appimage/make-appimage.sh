#!/bin/sh
set -eu

# Parameters
ARCH=$1
VERSION=$2
export ARCH VERSION
export OUTPATH="dist"
export APPDIR="AppDir"
export ADD_HOOKS="self-updater.hook"
export GITHUB_REPOSITORY="https://github.com/freerouting/freerouting"
export UPINFO="gh-releases-zsync|${GITHUB_REPOSITORY%/*}|${GITHUB_REPOSITORY#*/}|latest|*$ARCH.AppImage.zsync"
export ICON="$APPDIR"/lib/freerouting.png
export DESKTOP=appimage/freerouting.desktop
export MAIN_BIN=freerouting

# Fetch latest zip
wget https://github.com/freerouting/freerouting/releases/download/v$VERSION/freerouting-$VERSION-linux-x64.zip
unzip freerouting-$VERSION-linux-x64.zip

# Prepare files
rm -rf "$APPDIR"
mkdir -p "$APPDIR"/bin "$APPDIR"/lib
cp -rf freerouting-$VERSION-linux-x64/* "$APPDIR"/.
cp -f freerouting.desktop "$APPDIR"/.
chmod a+x "$APPDIR"/bin/*

# Cleanup
rm -rf freerouting-$VERSION-linux-x64 freerouting-$VERSION-linux-x64.zip

# Deploy dependencies
quick-sharun "$APPDIR"

# Turn AppDir into AppImage
quick-sharun --make-appimage

# Rename files here if needed
#mv -f dist/*-"$ARCH".AppImage dist/freerouting-"$ARCH".AppImage
#mv -f dist/*-"$ARCH".AppImage.zsync dist/freerouting-"$ARCH".AppImage.zsync

# Test the app for 12 seconds
quick-sharun --simple-test dist/*.AppImage -h

