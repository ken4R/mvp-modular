#!/usr/bin/env bash
# Hook PostToolUse de Claude Code : vérifie les règles non négociables du projet
# sur chaque fichier écrit ou modifié. Un code de sortie 2 renvoie le message à Claude
# pour qu'il corrige ; il ne bloque jamais un humain.
set -uo pipefail

command -v jq > /dev/null || exit 0
file=$(jq -r '.tool_input.file_path // empty')
[ -n "$file" ] && [ -f "$file" ] || exit 0

root="${CLAUDE_PROJECT_DIR:-$(pwd)}"
rel="${file#"$root"/}"
problems=()

case "$rel" in
  # domain ne dépend de rien : ni data, ni plateforme, ni Koin.
  */src/commonMain/kotlin/*/domain/*.kt)
    hits=$(grep -nE '^import +(com\.mvp\.[a-z.]*\.data\.|org\.koin\.|android\.|platform\.)' "$file")
    [ -n "$hits" ] && problems+=("$rel : la couche domain ne doit pas dépendre de data, de Koin ou de la plateforme.
$hits")
    ;;
esac

case "$rel" in
  # Couleurs en dur dans l'UI : tout passe par core:designsystem (MvpTheme / DS).
  androidApp/src/main/*/ui/theme/*) ;;  # MvpTheme convertit les tokens : seul endroit autorisé
  androidApp/src/main/*.kt)
    hits=$(grep -nE 'Color\(0x|Color\.(Red|Blue|Green|Black|White|Gray|Yellow|Cyan|Magenta)\b' "$file")
    [ -n "$hits" ] && problems+=("$rel : couleur en dur, utiliser MaterialTheme.colorScheme (MvpTheme) ou ajouter un token dans core:designsystem.
$hits")
    ;;
  iosApp/Packages/DesignSystemUI/*) ;;  # pont DS : seul endroit autorisé
  iosApp/*.swift)
    hits=$(grep -nE 'Color\((red|hue|white):|UIColor\(|Color\.(red|blue|green|black|white|gray|yellow|orange|pink|purple)\b|\.(foregroundColor|foregroundStyle|background|tint)\(\.(red|blue|green|black|white|gray|yellow|orange|pink|purple)\)' "$file")
    [ -n "$hits" ] && problems+=("$rel : couleur en dur, utiliser DS.colors(scheme) ou ajouter un token dans core:designsystem.
$hits")
    ;;
esac

[ ${#problems[@]} -eq 0 ] && exit 0
printf '%s\n\n' "${problems[@]}" >&2
exit 2
