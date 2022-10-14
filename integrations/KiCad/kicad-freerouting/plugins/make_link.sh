#!/usr/bin/env bash

#
# make symlink for plugin repository
#

# this script location
this_dir=$(cd $(dirname "$0") && pwd)

# default plugin location
plugins_dir="$HOME/.kicad_plugins"

source="$this_dir"

target="$plugins_dir/kicad-freerouting-plugin"

echo "### ensure plugins_dir=$plugins_dir"
mkdir -v -p "$plugins_dir"

echo "### ensure plugin symbolic link"
ln -vsfn "$source" "$target"
