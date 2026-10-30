# MVP Modular — consignes pour Claude Code

Prototype mobile iOS + Android, **toute la logique en Kotlin Multiplatform**, les apps natives ne font que l'UI. Architecture et conventions détaillées dans `README.md`. `feature/todo` est le module feature de référence : toute nouvelle feature en reprend la structure (domain / data / di, câblage dans `shared`, façade Swift `<Nom>UseCases` avec ses helpers Flow dans l'`iosMain` du module).

## Agents (`.claude/agents/`)
| Agent | Rôle |
|---|---|
| `mobile-planner` | Découpe la demande en plan ordonné (`docs/plans/`) |
| `mobile-architect` | Décisions structurantes, ADR (`docs/adr/`) |
| `kmp-dev` | Code partagé : domain, data, Koin, câblage `shared`, tests |
| `android-dev` | Compose + ViewModel dans `androidApp` |
| `ios-dev` | SwiftUI + ObservableObject, un package SPM par feature dans `iosApp/Packages` |
| `code-reviewer` | Revue architecture, correction, tests (lecture seule) |
| `security-reviewer` | Revue OWASP MASVS (lecture seule) |
| `qa-tester` | Complète et lance les tests, checklist de recette manuelle |
| `ci-engineer` | Workflow GitHub Actions, échecs de CI, distribution |

Commandes : `/feature <description>` enchaîne tout le flux, `/review` lance les deux revues en parallèle.

Hook `PostToolUse` (`scripts/hooks/check-architecture.sh`) : après chaque écriture de fichier, signale à Claude un import interdit dans `domain` ou une couleur en dur dans `androidApp` / `iosApp`.

CI (`.github/workflows/ci.yml`) : tests KMP + build Android sur Ubuntu, tests KMP iOS + build Xcode sur macOS, à chaque PR et push sur `main`.

## Règles non négociables
- `domain` ne dépend de rien ; les apps n'appellent que des use cases exposés par `shared`, qui renvoient `AppResult`.
- Aucune valeur visuelle en dur : `core:designsystem` → `MvpTheme` (Android) / `DS` (iOS).
- iOS : chaque feature est un package SPM `iosApp/Packages/Feature<Nom>` qui ne dépend que de `SharedKMP` et `DesignSystemUI`, avec une démo optionnelle (`Demo/`, cible `<Nom>Demo` dans `project.yml`) qui lie son parapluie KMP `umbrella/<nom>-demo` (cible `Kotlin<Nom>Demo`, ADR 0027).
- Versions uniquement dans `gradle/libs.versions.toml` ; modules KMP via le plugin `mvp.kmp.library`.
- Tout nouveau use case a un test `commonTest`.
- Code et commentaires en français, identifiants en anglais.
- Les builds Android/iOS ne tournent pas dans le conteneur cloud : ne jamais annoncer « ça compile » sans l'avoir exécuté.
