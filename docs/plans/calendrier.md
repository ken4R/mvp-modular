# Plan — Calendrier des tâches et export vers le calendrier du téléphone

Objectif : voir les tâches par date d'échéance dans un calendrier mensuel, et ajouter une tâche au calendrier du téléphone. Choix techniques : [ADR 0022](../adr/0022-calendrier-echeances.md).

1. **`feature:todo` (domain)** : `CalendarDate` (dates sans fuseau, jours depuis 1970), `DayCalendar`, `Todo.dueDate`, `TodoDueDate`, `CalendarMonthRequest` / `CalendarMonth` / `CalendarDay`, `TodoCalendarEvent` ; `SetTodoDueDateUseCase`, `ObserveTodoCalendarUseCase`, `ExportTodoToCalendarUseCase`, câblés dans `todoModule`.
2. **`feature:todo` (data)** : migration `5.sqm` (`due_date`), écrite et synchronisée par `SqlDelightTodoLocalDataSource` (`TodoRecord.dueDate`, file d'envoi, version reçue hors limites ignorée) ; `SystemDayCalendar` (kotlinx-datetime).
3. **Tests** : `TodoCalendarTest` (dates connues et aller-retour 2000-2100, grilles de 4, 5 et 6 semaines, tâches par jour et ordre, retards, sans échéance, passage de minuit, mois refusés, validation de l'échéance, export sans la note, calendrier de Paris au changement d'heure) ; `SqlDelightTodoLocalDataSourceTest` (échéance persistée et envoyée, reçue, hors limites, migration jusqu'à la version 6) ; `TodoSyncTest` (l'échéance passe d'un appareil à l'autre, pas le rappel).
4. **`core:designsystem`** : token `sizes.calendarDay` (`Sizes.calendarDay` Android, `DS.Size.calendarDay` iOS).
5. **`shared`** : `UseCases.setTodoDueDate`, `exportTodoToCalendar`, `observeTodoCalendar` pour Swift.
6. **Android** : `TodoCalendarViewModel`, `TodoCalendarScreen`, échéance et « Ajouter au calendrier » dans `TodoDetailScreen`, échéance sous le titre dans la liste, bouton « Calendrier ».
7. **iOS** : `TodoCalendarViewModel`, `TodoCalendarView`, `DueDates`, `CalendarExport` dans `FeatureTodo` ; échéance dans `TodoDetailView` et la liste ; `NSCalendarsUsageDescription` (iOS 16) dans `iosApp` et `TodoDemo` ; tâches datées dans la démo.

## Recette manuelle (simulateur / émulateur)

- Fiche d'une tâche : « Choisir une date » (Android) / « Ajouter une échéance » (iOS), choisir jeudi : la liste affiche « Échéance le jeu. … ». Changer la date, puis « Retirer » : la ligne disparaît.
- Échéance hier sur une tâche à faire : « en retard » en rouge dans la liste et la fiche ; la cocher : plus de retard.
- « Calendrier » : le mois en cours, aujourd'hui en surbrillance et choisi, les tâches du jour en dessous. Les jours avec des tâches montrent leur nombre à faire (rouge si en retard, ✓ si toutes terminées). « ‹ » / « › » changent de mois ; toucher un jour du mois voisin affiche ce mois ; « Aujourd'hui » revient au jour. Un mois commençant un dimanche (août 2027) a 6 lignes.
- Toucher une tâche du calendrier : sa fiche s'ouvre ; « Retour » revient au calendrier. Cocher une tâche dans la liste du jour : le compteur de la case change.
- Laisser l'écran ouvert au passage de minuit (ou changer l'heure de l'appareil) : « aujourd'hui » avance, la tâche d'hier passe en retard.
- « Ajouter au calendrier » : Android, l'app de calendrier propose un événement « journée entière » au bon jour, titre et lien, sans la note ; iOS 17+, l'écran d'ajout du système s'ouvre sans demande d'accès ; iOS 16, la demande d'accès s'affiche d'abord. Annuler : rien n'est ajouté. Passer l'app en arrière-plan pendant l'écran d'ajout (iOS) : il se ferme.
- Émulateur sans app de calendrier : message « Aucune application de calendrier ».
- Deux appareils (ou déconnexion / reconnexion du réseau) : l'échéance posée hors ligne arrive sur l'autre après la synchronisation ; le rappel, non.
- TalkBack / VoiceOver : chaque case est lue « Mercredi 28 octobre, 2 tâches dont 1 à faire, en retard, aujourd'hui » ; flèches « Mois précédent » / « Mois suivant ».
- Thème sombre et chaque couleur d'accent : case choisie, aujourd'hui, retards lisibles.
- Mise à jour depuis la version précédente : les tâches existantes n'ont pas d'échéance, le calendrier compte « N tâches sans échéance ».
- Déconnexion : le calendrier de l'app est vide à la reconnexion ; les événements déjà ajoutés au calendrier du téléphone restent (attendu, voir l'ADR).
