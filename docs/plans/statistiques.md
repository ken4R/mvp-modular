# Plan — Statistiques des tâches terminées

Objectif : un écran avec un graphique des tâches terminées par semaine. Choix techniques : [ADR 0019](../adr/0019-statistiques-taches.md).

1. **`feature:todo` (data)** : migration `4.sqm` (`completed_at`), écrite par `SqlDelightTodoLocalDataSource` (terminée, rouverte, reçue d'un autre appareil) ; requête `selectCompletionTimes` ; `TodoStatsRepositoryImpl` ; `SystemWeekCalendar` (kotlinx-datetime, ajouté à `libs.versions.toml`).
2. **`feature:todo` (domain)** : `WeekCalendar`, `TodoStatsRepository`, `CompletionStats` / `WeekCompletion`, `ObserveCompletionStatsUseCase` (8 semaines), câblés dans `todoModule`. Tests : `CompletionStatsTest` (semaines vides, comptage par semaine et bornes lundi 00:00 / dimanche 23:59, heures hors fenêtre, recalcul, calendrier de Paris aux changements d'heure de mars et d'octobre 2026) ; `SqlDelightTodoLocalDataSourceTest` (heure de fin posée, gardée, retirée, suppression, synchronisation, migration 4 → 5).
3. **`core:designsystem`** : token `sizes.chart` (`Sizes.chart` Android, `DS.Size.chart` iOS).
4. **`shared`** : `UseCases.observeCompletionStats` pour Swift.
5. **Android** : `TodoStatsViewModel`, `TodoStatsScreen` (barres Compose, TalkBack), bouton « Statistiques » dans `TodoScreen`.
6. **iOS** : `TodoStatsViewModel`, `TodoStatsView` (Swift Charts, VoiceOver) dans `FeatureTodo`, bouton « Statistiques » dans `TodoListView` (aussi dans la démo `TodoDemo`).

## Recette manuelle (simulateur / émulateur)

- Sans tâche terminée : « Statistiques » affiche 8 semaines à 0, « Aucune tâche terminée sur les 8 dernières semaines » et l'invite à cocher une tâche.
- Cocher 3 tâches, ouvrir « Statistiques » : la dernière barre (couleur d'accent) vaut 3, le résumé dit « 3 tâches terminées … · meilleure semaine : 3 ». Décocher une tâche : 2. Supprimer une tâche terminée : elle ne compte plus.
- Renommer ou ajouter une note à une tâche terminée : la barre ne change pas.
- Changer la date de l'appareil d'une semaine en avant (réglages système), rouvrir l'écran : les 3 tâches sont dans l'avant-dernière barre ; revenir à la date réelle.
- TalkBack / VoiceOver : chaque barre est lue « Semaine du 31 août : 2 tâches terminées », la dernière « Cette semaine ». iOS : la vue d'ensemble audio du graphique (rotor) est proposée.
- Thème sombre et chaque couleur d'accent : barres, libellés et graduations lisibles.
- Déconnexion puis reconnexion : les statistiques repartent de zéro.
- Mise à jour depuis la version précédente avec des tâches déjà terminées : elles apparaissent à la semaine de leur dernière modification.
