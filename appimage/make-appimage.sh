#!/bin/sh
set -eu

# Usage check and parameter parsing
if [ $# -lt 1 ]; then
	echo "Usage: $0 [arch] <version>"
	echo "Example: $0 x86_64 2.4.1"
	echo "         $0 2.4.1 (defaults arch to $(uname -m))"
	exit 1
fi

if [ $# -eq 1 ]; then
	ARCH="$(uname -m)"
	VERSION="$1"
else
	ARCH="$1"
	VERSION="$2"
fi

# Strip leading 'v' if provided
VERSION="${VERSION#v}"

SCRIPT_DIR="$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)"

export ARCH VERSION
export OUTPATH="dist"
export OUTNAME="freerouting-$VERSION-$ARCH.AppImage"
export APPDIR="AppDir"
export ADD_HOOKS="self-updater.hook"
export GITHUB_REPOSITORY="${GITHUB_REPOSITORY:-freerouting/freerouting}"
GH_OWNER="${GITHUB_REPOSITORY%/*}"
GH_REPO="${GITHUB_REPOSITORY#*/}"
export UPINFO="gh-releases-zsync|$GH_OWNER|$GH_REPO|latest|freerouting-*-$ARCH.AppImage.zsync"
export DESKTOP="$SCRIPT_DIR/freerouting.desktop"
export MAIN_BIN="freerouting"

ZIP_NAME="freerouting-$VERSION-linux-x64.zip"
EXTRACT_DIR="freerouting-$VERSION-linux-x64"

# Fetch distribution zip if not already present
if [ ! -f "$ZIP_NAME" ] && [ ! -d "$EXTRACT_DIR" ]; then
	echo "Downloading $ZIP_NAME from GitHub Releases..."
	wget "https://github.com/$GH_OWNER/$GH_REPO/releases/download/v$VERSION/$ZIP_NAME"
fi

if [ -f "$ZIP_NAME" ] && [ ! -d "$EXTRACT_DIR" ]; then
	unzip -q "$ZIP_NAME"
fi

# Prepare AppDir
rm -rf "$APPDIR"
mkdir -p "$APPDIR/bin" "$APPDIR/lib" "$APPDIR/usr/share/metainfo"
cp -rf "$EXTRACT_DIR"/* "$APPDIR/"
chmod a+x "$APPDIR"/bin/*

# Copy AppStream metainfo if available
if [ -f "$SCRIPT_DIR/app.freerouting.Freerouting.metainfo.xml" ]; then
	cp -f "$SCRIPT_DIR/app.freerouting.Freerouting.metainfo.xml" "$APPDIR/usr/share/metainfo/"
fi

# Icon resolution
if [ -f "$APPDIR/lib/freerouting.png" ]; then
	export ICON="$APPDIR/lib/freerouting.png"
elif [ -f "$SCRIPT_DIR/../assets/icon/freerouting_icon_256x256_v3.png" ]; then
	export ICON="$SCRIPT_DIR/../assets/icon/freerouting_icon_256x256_v3.png"
fi

# Deploy dependencies
quick-sharun "$APPDIR"

# Turn AppDir into AppImage
quick-sharun --make-appimage

# Smoke test the produced AppImage
quick-sharun --simple-test "$OUTPATH/$OUTNAME" -h

