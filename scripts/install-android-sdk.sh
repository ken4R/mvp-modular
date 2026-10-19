#!/usr/bin/env bash
# Installe le SDK Android (sans Android Studio) pour compiler le projet en ligne de commande.
# Utilisé comme "Setup script" de l'environnement cloud Claude Code, ou en local sur une machine CI.
# Prérequis réseau : dl.google.com et maven.google.com accessibles.
set -euo pipefail

ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
CMDLINE_TOOLS_VERSION="${CMDLINE_TOOLS_VERSION:-13114758}"
PLATFORM="platforms;android-36"
BUILD_TOOLS="build-tools;36.0.0"

if [ -d "$ANDROID_HOME/platforms/android-36" ] && [ -d "$ANDROID_HOME/build-tools/36.0.0" ]; then
  echo "SDK Android déjà installé dans $ANDROID_HOME"
  exit 0
fi

mkdir -p "$ANDROID_HOME/cmdline-tools"
tmp="$(mktemp -d)"
curl -fsSL "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip" -o "$tmp/cmdline-tools.zip"
unzip -q "$tmp/cmdline-tools.zip" -d "$tmp"
rm -rf "$ANDROID_HOME/cmdline-tools/latest"
mv "$tmp/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
rm -rf "$tmp"

sdkmanager="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
yes | "$sdkmanager" --sdk_root="$ANDROID_HOME" --licenses > /dev/null
"$sdkmanager" --sdk_root="$ANDROID_HOME" "$PLATFORM" "$BUILD_TOOLS" "platform-tools"

echo "SDK Android installé dans $ANDROID_HOME"
