---
description: Enchaîne les agents du projet pour livrer une feature de bout en bout (plan → archi → KMP → Android/iOS → revues).
argument-hint: <description de la feature>
---

Livre la feature suivante dans MVP Modular : $ARGUMENTS

Enchaîne les sous-agents dans cet ordre, en passant à chacun le plan et les résultats précédents :

1. **mobile-planner** : écrit le plan dans `docs/plans/`.
2. **mobile-architect** : seulement si le plan liste des décisions à prendre ; écrit les ADR.
3. **kmp-dev** : domain, data, di, câblage `shared` et `UseCases`, tests `commonTest`.
4. **android-dev** et **ios-dev** en parallèle : écrans et ViewModels.
5. **qa-tester** : complète les tests, les lance, rédige la checklist de recette.
6. **code-reviewer**, puis **security-reviewer** si la feature touche réseau, stockage, auth, permissions ou secrets.
7. Si une revue ou un test est ❌, renvoie les points bloquants à l'agent concerné, puis relance la revue.

Termine par un résumé : fichiers créés/modifiés, ce qui a été vérifié, ce qui reste à valider dans Android Studio et Xcode.
