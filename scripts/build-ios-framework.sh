#!/usr/bin/env bash
# Compile le framework KMP "Shared" en XCFramework et le place dans le package SPM
# iosApp/Packages/SharedKMP, d'où toutes les features iOS l'importent.
#
# À lancer une fois avant d'ouvrir le projet Xcode, puis à chaque changement de code Kotlin
# (la cible Xcode "KotlinShared" le relance aussi à chaque build).
#
# Usage : scripts/build-ios-framework.sh [debug|release]   (debug par défaut)
set -euo pipefail

config="${1:-debug}"
case "$config" in
  debug) task="assembleSharedDebugXCFramework" ;;
  release) task="assembleSharedReleaseXCFramework" ;;
  *) echo "error: configuration inconnue '$config' (debug ou release)" >&2; exit 1 ;;
esac

root="$(cd "$(dirname "$0")/.." && pwd)"

# Xcode lancé depuis le Dock ne lit pas ~/.zshrc : on cherche le JDK nous-mêmes.
if [ -z "${JAVA_HOME:-}" ]; then
  for candidate in \
    "$(/usr/libexec/java_home 2>/dev/null || true)" \
    /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
    /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
    /usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home; do
    if [ -n "$candidate" ] && [ -x "$candidate/bin/java" ]; then
      export JAVA_HOME="$candidate"
      break
    fi
  done
fi
if [ -z "${JAVA_HOME:-}" ]; then
  echo "error: JDK 17+ introuvable. Installer avec 'brew install openjdk@21'." >&2
  exit 1
fi

cd "$root"
./gradlew ":shared:$task"

src="shared/build/XCFrameworks/$config/Shared.xcframework"
dest="iosApp/Packages/SharedKMP/Shared.xcframework"
rm -rf "$dest"
cp -R "$src" "$dest"
echo "Shared.xcframework ($config) copié dans $dest"
