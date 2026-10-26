# ADR 0022 — Calendrier des tâches par échéance et export vers le calendrier du téléphone

- Statut : accepté
- Date : 2026-10-26

## Contexte

L'utilisateur veut voir ses tâches par date d'échéance dans un calendrier, et pouvoir en exporter une vers le calendrier du téléphone. Une tâche n'avait pas d'échéance : seulement un rappel (heure précise, propre à l'appareil, ADR 0004 et 0012). Il faut décider de la forme de l'échéance, de sa synchronisation, de l'endroit où le mois est calculé et de la façon d'écrire dans le calendrier du système sans demander plus de permissions que nécessaire.

## Options

**Échéance**

1. **Réutiliser le rappel** : rien à migrer, mais une tâche à rendre jeudi n'a pas forcément besoin d'une notification, et le rappel ne quitte pas l'appareil.
2. **Instant (epoch ms)** comme le rappel : un « jeudi » saisi à Paris devient mercredi soir à New York.
3. **Date du calendrier sans fuseau**, stockée en jours depuis le 1970-01-01 : « le 28 octobre » reste le 28 octobre partout.

**Export**

1. **Écriture directe** (`CalendarContract` avec `WRITE_CALENDAR`, `EKEventStore.save` avec l'accès en écriture) : silencieux, mais une permission de plus, et l'app pourrait écrire sans que l'utilisateur voie quoi.
2. **Écran d'ajout du système, prérempli** : Android `ACTION_INSERT` sur `CalendarContract.Events` (l'app de calendrier s'ouvre, aucune permission) ; iOS `EKEventEditViewController` (depuis iOS 17, hors du processus de l'app et sans aucun accès au calendrier).
3. **Fichier `.ics` partagé** : universel, mais le parcours (feuille de partage, choix de l'app) est moins direct et dépend de l'app qui reçoit le fichier.

## Décision

**Échéance option 3, export option 2**, calcul dans KMP.

- **Modèle** : `Todo.dueDate: Int?` (jours depuis le 1970-01-01). `CalendarDate` (domain) convertit année / mois / jour ↔ jours et donne le jour de la semaine ISO, sans bibliothèque (algorithme de Howard Hinnant) : le domaine ne dépend de rien et les deux apps comptent pareil. Échéances acceptées du 1er janvier 2000 au 31 décembre 2100 ; une date passée est permise (tâche déjà en retard).
- **Base** : migration `5.sqm` (`todo.db` version 6), colonne `due_date` (`NULL` sans échéance).
- **Synchronisation** : l'échéance fait partie de la tâche (`TodoRecord.dueDate`), comme le titre et la note : la changer date la tâche (`updated_at`) et l'envoie au serveur, qui départage par la règle habituelle (la plus récente gagne, ADR 0012). Une échéance reçue hors limites fait ignorer la version, comme un titre invalide. Le rappel reste propre à l'appareil.
- **Use cases** (`feature:todo`) : `SetTodoDueDateUseCase` (pose, retire, valide) ; `ObserveTodoCalendarUseCase` (`CalendarMonthRequest` → `CalendarMonth` : le mois en semaines complètes du lundi au dimanche, 4 à 6 semaines, tâches de chaque jour à faire d'abord, jours en retard, nombre de tâches en retard et sans échéance), recalculé à chaque changement des tâches et à minuit via l'interface `DayCalendar` (`SystemDayCalendar`, kotlinx-datetime, fuseau de l'appareil lu à chaque calcul) ; `ExportTodoToCalendarUseCase` (`TodoCalendarEvent` : titre sur une ligne, jour de l'échéance, lien profond de la tâche). Testés dans `TodoCalendarTest`, `SqlDelightTodoLocalDataSourceTest` et `TodoSyncTest`.
- **Export, règles** : une journée entière le jour de l'échéance, le titre et le lien `mvpmodular://tasks/<id>` (qui ne porte aucune donnée, ADR 0008). Jamais la note, ni les pièces jointes, ni le rappel : le calendrier du téléphone est synchronisé (iCloud, Google) et lisible par d'autres apps. L'app n'écrit rien elle-même : l'utilisateur voit l'événement, choisit le calendrier et confirme.
- **Android** : bouton « Calendrier » sur la liste → `TodoCalendarScreen` (grille Compose, case décrite pour TalkBack) à la place de la liste, dans l'onglet (sous le verrou) ; une tâche touchée ouvre sa fiche, qui revient au calendrier. Fiche : échéance (`DatePickerDialog` borné à 2000-2100), « Ajouter au calendrier » (`ACTION_INSERT`, journée de minuit à minuit dans le fuseau de l'appareil, ouvert dans sa propre tâche comme le partage) ; sans app de calendrier, un message. Nouveau token `sizes.calendarDay` (48).
- **iOS** : `TodoCalendarView` / `TodoCalendarViewModel` dans `FeatureTodo` (le calendrier est une vue des tâches, comme les statistiques, et ouvre `TodoDetailView` : un package séparé dépendrait de `FeatureTodo`, ce que la règle des packages interdit) ; démo `TodoDemo` avec deux tâches datées. Fiche : `DatePicker` compact en calendrier grégorien (même si l'iPhone affiche un autre calendrier), « Ajouter au calendrier » présente `EKEventEditViewController` par UIKit, fermé au passage en arrière-plan avec les autres fenêtres (`PresentedSheets`). Sur iOS 16 (cible minimale), cet écran exige l'accès complet au calendrier : il est demandé seulement à ce moment, avec `NSCalendarsUsageDescription` (vérifiée par la CI) ; depuis iOS 17 aucune demande.

## Conséquences

- Un événement ajouté vit dans le calendrier du téléphone, hors de l'app : il n'est ni mis à jour quand l'échéance change, ni supprimé avec la tâche ou à la déconnexion (`TodoUserSessionObserver` ne peut pas l'atteindre). La fiche le dit sous le bouton. Le suivre demanderait l'accès en écriture et de garder l'identifiant de l'événement : à revoir si l'utilisateur le demande.
- L'échéance est effacée avec les tâches à la déconnexion (même base, `TodoUserSessionObserver`) et exclue des sauvegardes comme le reste de `todo.db` (ADR 0013). Aucun événement de mesure n'est ajouté (le texte du consentement, ADR 0009, ne change pas).
- Sur iOS 16, accepter la demande donne à l'app l'accès complet au calendrier, dont elle ne se sert que pour cet écran. Quand la cible minimale passera à iOS 17, retirer la demande et la clé.
- Android : sur certains appareils, l'app de calendrier peut interpréter une journée entière dans son propre fuseau ; à vérifier sur l'émulateur (recette ci-dessous).
- Le widget et l'écran Compose Multiplatform (ADR 0010, 0011) n'affichent pas l'échéance pour l'instant.
