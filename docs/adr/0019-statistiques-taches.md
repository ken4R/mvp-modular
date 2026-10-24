# ADR 0019 — Statistiques des tâches terminées

- Statut : accepté
- Date : 2026-10-24

## Contexte

L'utilisateur veut un écran avec un graphique des tâches terminées par semaine. Une tâche n'avait qu'un état `is_done` et une heure `updated_at` (dernière modification du titre, de l'état ou de la note, qui départage la synchronisation, ADR 0012) : aucune donnée ne dit quand elle a été terminée. Les semaines dépendent du fuseau de l'appareil et des changements d'heure ; les deux apps doivent compter pareil.

## Options

1. **Utiliser `updated_at`** : rien à migrer, mais renommer une tâche terminée la déplace dans une autre semaine.
2. **Colonne `completed_at` propre à l'appareil**, comme le rappel : posée quand la tâche passe à terminée, retirée quand elle est rouverte, jamais envoyée au serveur.
3. **`completed_at` synchronisé** : chaque appareil aurait la même heure, mais il faudrait changer le format échangé (`TodoRecord`), le faux serveur et la règle de conflit pour un champ qui n'est qu'un affichage.
4. **Journal des achèvements** (une ligne par tâche cochée) : garde l'historique d'une tâche supprimée, mais une table de plus à effacer à la déconnexion, et des données gardées sur des tâches que l'utilisateur a supprimées.

## Décision

**Option 2**, calcul dans KMP, graphique natif.

- **Base** : migration `4.sqm` (`todo.db` version 5), colonne `completed_at` (epoch ms, `NULL` si non terminée). Les tâches déjà terminées prennent leur `updated_at`, celles d'avant la synchronisation (heure 0) restent sans heure et ne comptent pas.
- **Écriture** (`SqlDelightTodoLocalDataSource`) : à l'heure de la modification qui termine la tâche (même horloge croissante que la synchronisation), gardée si on change ensuite son titre, sa note ou son rappel, retirée si elle est rouverte. Une tâche terminée sur un autre appareil prend l'heure de la modification reçue, l'approximation la plus proche.
- **Domaine** : `ObserveCompletionStatsUseCase` (`feature:todo`) rend `CompletionStats` : les 8 dernières semaines, semaine en cours comprise, de la plus ancienne à la plus récente (semaines vides comprises), le total et la meilleure semaine. Une heure hors de ces semaines (plus ancienne ou dans le futur) est ignorée. Le découpage passe par l'interface `WeekCalendar` : le domaine ne dépend de rien, les tests utilisent un calendrier fixe.
- **Semaines** : `SystemWeekCalendar` (data) avec **kotlinx-datetime 0.8.0** : semaine ISO, lundi 00:00 dans le fuseau de l'appareil, lu à chaque calcul ; les semaines se comptent en jours du calendrier, donc une semaine avec changement d'heure dure 167 ou 169 h. `WeekCompletion.weekStart` est un instant (epoch ms) : chaque app en tire la date dans le même fuseau.
- **Apps** : un bouton « Statistiques » sur la liste des tâches ouvre l'écran à la place de la liste, dans l'onglet (sous le verrou, comme la fiche d'une tâche). Android : barres en Compose (une colonne par semaine), chaque barre décrite pour TalkBack ; iOS : Swift Charts (`BarMark`), libellé et valeur VoiceOver par barre. La semaine en cours est dans la couleur d'accent, les autres dans la couleur secondaire. Hauteur du graphique : nouveau token `sizes.chart`.

## Conséquences

- Une tâche supprimée sort des statistiques : elles comptent les tâches présentes, pas un historique.
- Deux appareils peuvent afficher des semaines légèrement différentes pour une tâche terminée hors ligne puis synchronisée (heure de réception de la modification, ou heure locale déplacée par l'ordre de synchronisation). Si le backend réel doit garantir les mêmes chiffres partout, passer à l'option 3.
- `completed_at` reçu d'un autre appareil reprend l'heure envoyée par ce client : la borner côté serveur (déjà demandé par ADR 0012) bornera aussi les statistiques ; une heure dans le futur n'est pas comptée.
- Les données sont dans `todo.db` : effacées à la déconnexion avec les tâches (`TodoUserSessionObserver`), exclues de la sauvegarde comme le reste de la base (ADR 0013). Aucun événement de mesure n'est ajouté (le texte du consentement, ADR 0009, ne change pas).
- Nouvelle dépendance `kotlinx-datetime`, limitée à `feature:todo` (data) et non exportée vers Swift.
