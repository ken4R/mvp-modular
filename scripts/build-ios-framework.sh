#!/usr/bin/env bash
# Compile le framework KMP "Shared" en XCFramework et le place dans le package SPM
# iosApp/Packages/SharedKMP, d'où toutes les features iOS l'importent.
#
# À lancer une fois avant d'ouvrir le projet Xcode, puis à chaque changement de code Kotlin
# (la cible Xcode "KotlinShared" le relance aussi à chaque build).
#
# Usage : scripts/build-ios-framework.sh [debug|release] [cible...]   (debug par défaut)
#   cibles possibles : iosSimulatorArm64, iosX64, iosArm64.
#
# Sans cible, deux cas :
#   - lancé par Xcode (variables PLATFORM_NAME et ARCHS) : seules les tranches du build en cours sont
#     liées, par exemple simulateur arm64 en Debug, iPhone arm64 pour une archive. La liaison Kotlin/Native
#     (Compose et Skia compris) est l'étape la plus longue : une tranche au lieu de trois.
#   - lancé à la main : XCFramework complet, les trois tranches.
#
# Variable KMP_MODULE : module Gradle du framework, « shared » (app complète) par défaut, ou le parapluie d'une
# app de démo, par exemple « umbrella:todo-demo » (ADR 0027). Xcode la reçoit du réglage de la cible Kotlin de
# chaque démo (iosApp/project.yml). Tous s'appellent « Shared » et vont au même endroit : le dernier compilé gagne.
set -euo pipefail

module="${KMP_MODULE:-shared}"
# Lettres minuscules, chiffres et tirets seulement : le chemin sert aux rm -rf ci-dessous.
if [ "$module" != shared ] && ! [[ "$module" =~ ^umbrella:[a-z0-9-]+-demo$ ]]; then
  echo "error: KMP_MODULE inconnu '$module' (shared ou umbrella:<nom>-demo)" >&2
  exit 1
fi
build="${module//://}/build"

config="${1:-debug}"
case "$config" in
  debug) Config="Debug" ;;
  release) Config="Release" ;;
  *) echo "error: configuration inconnue '$config' (debug ou release)" >&2; exit 1 ;;
esac
shift || true
targets=("$@")

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

# Tranches demandées par Xcode : plateforme (iphoneos / iphonesimulator) et architectures du build en cours.
if [ ${#targets[@]} -eq 0 ] && [ -n "${PLATFORM_NAME:-}" ] && [ -n "${ARCHS:-}" ]; then
  for arch in $ARCHS; do
    case "$PLATFORM_NAME/$arch" in
      iphonesimulator/arm64) targets+=(iosSimulatorArm64) ;;
      iphonesimulator/x86_64) targets+=(iosX64) ;;
      iphoneos/arm64) targets+=(iosArm64) ;;
      *) echo "warning: tranche $PLATFORM_NAME/$arch inconnue (ARCHS=$ARCHS), XCFramework complet, plus lent" >&2; targets=(); break ;;
    esac
  done
fi

dest="iosApp/Packages/SharedKMP/Shared.xcframework"
cd "$root"

if [ ${#targets[@]} -eq 0 ]; then
  ./gradlew ":$module:assembleShared${Config}XCFramework"
  rm -rf "$dest"
  cp -R "$build/XCFrameworks/$config/Shared.xcframework" "$dest"
  echo "Shared.xcframework ($module, $config, toutes les tranches) copié dans $dest"
  exit 0
fi

echo "Tranches liées ($config) : ${targets[*]}"
tasks=()
for target in "${targets[@]}"; do
  case "$target" in
    iosSimulatorArm64|iosX64|iosArm64) ;;
    *) echo "error: cible inconnue '$target' (iosSimulatorArm64, iosX64 ou iosArm64)" >&2; exit 1 ;;
  esac
  # Bash 3.2 de macOS : pas de ${target^} pour la majuscule.
  tasks+=(":$module:link${Config}Framework$(echo "${target:0:1}" | tr '[:lower:]' '[:upper:]')${target:1}")
done
./gradlew "${tasks[@]}"

framework() { echo "$build/bin/$1/${config}Framework/Shared.framework"; }

# Une tranche par plateforme dans un XCFramework : les deux architectures simulateur sont fusionnées par lipo.
staging="$build/XCFrameworks/$config-slices"
rm -rf "$staging"
mkdir -p "$staging"
args=()
sim=()
for target in "${targets[@]}"; do
  if [ "$target" = iosArm64 ]; then
    args+=(-framework "$root/$(framework "$target")")
    [ -d "$root/$(framework "$target").dSYM" ] && args+=(-debug-symbols "$root/$(framework "$target").dSYM")
  else
    sim+=("$target")
  fi
done
if [ ${#sim[@]} -eq 1 ]; then
  args+=(-framework "$root/$(framework "${sim[0]}")")
  [ -d "$root/$(framework "${sim[0]}").dSYM" ] && args+=(-debug-symbols "$root/$(framework "${sim[0]}").dSYM")
elif [ ${#sim[@]} -gt 1 ]; then
  mkdir -p "$staging/simulator"
  cp -R "$(framework "${sim[0]}")" "$staging/simulator/"
  binaries=()
  for target in "${sim[@]}"; do binaries+=("$(framework "$target")/Shared"); done
  lipo -create "${binaries[@]}" -output "$staging/simulator/Shared.framework/Shared"
  args+=(-framework "$root/$staging/simulator/Shared.framework")
fi
xcodebuild -create-xcframework "${args[@]}" -output "$staging/Shared.xcframework" >/dev/null

# Framework inchangé (Gradle à jour) : on garde l'ancien, Xcode n'a rien à retraiter ni à relier.
if [ -d "$dest" ] && diff -rq "$staging/Shared.xcframework" "$dest" >/dev/null 2>&1; then
  echo "Shared.xcframework ($module, $config, ${targets[*]}) inchangé"
  exit 0
fi
rm -rf "$dest"
cp -R "$staging/Shared.xcframework" "$dest"
echo "Shared.xcframework ($module, $config, ${targets[*]}) copié dans $dest"
