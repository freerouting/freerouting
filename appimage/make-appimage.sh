#!/bin/sh
set -eu

ARCH=""
VERSION=""

if [ $# -eq 0 ]; then
	ARCH="$(uname -m)"
elif [ $# -eq 1 ]; then
	case "$1" in
		x86_64|aarch64|arm64|x64)
			ARCH="$1"
			;;
		*)
			ARCH="$(uname -m)"
			VERSION="$1"
			;;
	esac
else
	ARCH="$1"
	VERSION="$2"
fi

# Normalize architecture name
case "$ARCH" in
	x64) ARCH="x86_64" ;;
	arm64) ARCH="aarch64" ;;
esac

# If VERSION was not explicitly specified, auto-detect from existing zip in current directory
if [ -z "$VERSION" ]; then
	for f in freerouting-*-linux-x64.zip; do
		if [ -f "$f" ]; then
			v="${f#freerouting-}"
			VERSION="${v%-linux-x64.zip}"
			break
		fi
	done
fi

if [ -z "$VERSION" ]; then
	echo "Error: No version specified and no freerouting-*-linux-x64.zip found." >&2
	echo "Usage: $0 [arch] <version>" >&2
	exit 1
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

if [ "${VERSION#SNAPSHOT}" != "$VERSION" ]; then
	# SNAPSHOT / nightly release
	UPINFO_TAG="SNAPSHOT"
	UPINFO_PATTERN="freerouting-SNAPSHOT-*-$ARCH.AppImage.zsync"
else
	# Standard release tag
	UPINFO_TAG="latest"
	UPINFO_PATTERN="freerouting-*-$ARCH.AppImage.zsync"
fi

export UPINFO="gh-releases-zsync|$GH_OWNER|$GH_REPO|$UPINFO_TAG|$UPINFO_PATTERN"
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

