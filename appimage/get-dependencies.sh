#!/bin/sh
set -eu

ARCH=$(uname -m)

echo "Initializing the build container..."
echo "---------------------------------------------------------------"
pacman-key --init
pacman -Syy --noconfirm archlinux-keyring
pacman -Syu --noconfirm

echo "Installing package dependencies..."
echo "---------------------------------------------------------------"

# The required set is present on every architecture (Arch Linux ports).
pacman -S --noconfirm --needed base-devel ca-certificates curl git jq patchelf tar unzip wget xz

# Optional packages may not exist on every architecture port, please double check.
pacman -S --noconfirm --needed 7zip file xorg-server-xvfb

echo "Installing quick-sharun..."
echo "---------------------------------------------------------------"
wget -q -O /usr/local/bin/quick-sharun \
	https://raw.githubusercontent.com/pkgforge-dev/Anylinux-AppImages/refs/heads/main/useful-tools/quick-sharun.sh
	#https://raw.githubusercontent.com/pkgforge-dev/Anylinux-AppImages/f6627df7a7aa48c9c614999fa46fb5707ca9b611/useful-tools/quick-sharun.sh
chmod a+x /usr/local/bin/quick-sharun

echo "CONTAINER IS READY!"

