# Plan d'action du projet

État au 31 octobre 2026. Ce document retrace ce qui a été fait depuis le début, étape par étape, et ce qui reste à faire. Les détails de chaque feature sont dans `docs/plans/`, les choix techniques dans `docs/adr/`.

## Ce qui a été fait

### Phase 0 : fondations (commit initial, PR #1 à #5)

- Architecture modulaire Kotlin Multiplatform : `core:common` (clean archi, `AppResult`, use cases, Koin), `core:designsystem`, puis `core:network` (Ktor, ADR 0001).
- `shared` exporté en framework iOS ; app Android en Compose, app iOS en SwiftUI générée par XcodeGen.
- Feature de référence `feature/todo`.
- Agents Claude (`.claude/agents/`), commandes `/feature` et `/review`, hook d'architecture.
- CI GitHub Actions : tests KMP et build Android sur Ubuntu, tests KMP iOS et build Xcode sur macOS (PR #3).
- Un package SPM par feature iOS avec son app de démo (PR #4).
- Feature météo par recherche de ville, via Open-Meteo (PR #5).

### Roadmap 1 (PR #6 à #9)

Réglages (#6), persistance locale SQLDelight des tâches et réglages (#7, ADR 0002), connexion avec authentification locale factice et stockage chiffré (#8, ADR 0003), rappels de tâches par notifications locales (#9, ADR 0004).

### Roadmap 2 (PR #10 à #15)

Météo à ma position et écran Météo Android (#10, ADR 0005), catalogue liste / détail avec pagination et cache d'images (#11, ADR 0006), déverrouillage Face ID / empreinte (#12, ADR 0007), liens profonds (#13, ADR 0008), mesure d'audience avec consentement (#14, ADR 0009), premier écran en Compose Multiplatform comparé au SwiftUI natif (#15, ADR 0010).

### Roadmap 3 (PR #16 à #18)

Widget Tâches de l'écran d'accueil (#16, ADR 0011), mode hors ligne avec synchronisation au retour du réseau (#17, ADR 0012), correctifs Android : permission biométrique et bouton « Retirer » du rappel (#18). Des vidéos de démo ont aussi été tournées sur le Mac (hors dépôt).

### Roadmap 4 (PR #19 et #20)

Position Android demandée à tous les fournisseurs actifs (#19), durcissement avant distribution : dossier de base exclu des sauvegardes iOS, `secure_delete`, R8 en release, authentification factice limitée au debug (#20, ADR 0013).

### Roadmap 5 (PR #21 à #28)

Notes et pièces jointes (#21, ADR 0014), recherche et filtres (#22), thème sombre et couleur d'accent (#23, ADR 0015), présentation du premier lancement (#24, ADR 0016), partage d'une tâche (#25, ADR 0017), ajout vocal Siri / Google Assistant (#26, ADR 0018), statistiques (#27, ADR 0019), suivi des plantages avec consentement séparé (#28, ADR 0020).

### Feature flags (PR #29 et #30)

Module `core:featureflags` (ADR 0021) : features en développement coupées en release, menu debug, flags distants lus dans `remote-config/flags.json` publié par GitHub Pages (`pages.yml`). Reste à activer GitHub Pages sur le dépôt (Settings > Pages > Source : GitHub Actions), ce qui demande un dépôt public ou un forfait payant.

### Idées 10 et 11 (PR #31 et #32)

Calendrier des tâches par échéance avec export vers le calendrier du téléphone (#31, ADR 0022), rappels de tâches par lieu (#32, ADR 0023).

### Roadmap 6 (PR #33 à #35)

Priorités et glisser-déposer (#33, ADR 0024), corbeille avec annulation et restauration pendant 30 jours (#34, ADR 0025), widget Météo et case à cocher du widget Tâches (#35, ADR 0026).

### Build iOS (PR #36 et #37)

Une seule tranche Kotlin/Native par build Xcode et cache `~/.konan` en CI (#36), un framework KMP réduit (parapluie) par app de démo (#37, ADR 0027). Une modification Kotlin passe de 261 s à 34 s de build, et le job iOS de la CI de 35 à environ 10 minutes.

### Mode focus Pomodoro (PR #38)

Minuteur sur une tâche, notification en cours sur Android et Live Activity sur iOS (ADR 0028).

## La suite

1. **Tester sur appareil** tout ce qui a été fait depuis la roadmap 5 (recettes dans `docs/plans/`), ainsi que l'APK release Android (R8) et le widget iOS sur iPhone réel.
2. **Décider** : Compose Multiplatform ou non pour les prochains écrans (ADR 0010, encore au statut « proposé ») ; activer GitHub Pages pour les flags distants.
3. **Qualité** : tests UI Maestro, accessibilité, localisation FR / EN.
4. **Préparer la prod** : vrai backend et vraie authentification (jeton lié à la biométrie, horodatages bornés côté serveur, identifiants liés au compte), vrai SDK de plantages, compte Apple Developer payant (App Group des widgets, distribution).
5. **Nouvelles features** : tâches récurrentes, sous-tâches, étiquettes, export / import.
