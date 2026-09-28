#!/bin/sh
set -eu

SCRIPT_DIR="$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)"

# Execute AppImage distribution build for SNAPSHOT release
"$SCRIPT_DIR/create-distribution-appimage.sh" "$@"
