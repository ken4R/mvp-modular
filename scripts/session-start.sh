#!/usr/bin/env bash
# Hook SessionStart de Claude Code : prépare une session cloud pour compiler KMP + Android.
# Ne fait rien en local (Android Studio gère déjà le SDK).
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-$(dirname "$0")/..}"

ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
if [ ! -d "$ANDROID_HOME/platforms" ]; then
  echo "SDK Android absent de $ANDROID_HOME : lancer scripts/install-android-sdk.sh (voir README, section Environnement cloud)." >&2
  exit 0
fi

# Gradle lit le chemin du SDK dans local.properties (ignoré par git).
echo "sdk.dir=$ANDROID_HOME" > local.properties

if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  echo "export ANDROID_HOME=$ANDROID_HOME" >> "$CLAUDE_ENV_FILE"
fi

# Télécharge les dépendances Gradle une fois, pour que les builds suivants soient rapides.
./gradlew --quiet help > /dev/null 2>&1 || echo "Préchargement Gradle échoué : lancer ./gradlew help pour le détail." >&2
