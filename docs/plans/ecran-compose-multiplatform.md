# Plan — Un écran en Compose Multiplatform

Objectif : partager aussi l'interface d'un écran entre Android et iOS avec Compose Multiplatform, et la comparer à l'écran SwiftUI natif. Choix et comparaison : [ADR 0010](../adr/0010-ecran-compose-multiplatform.md).

1. **Versions** : Compose Multiplatform 1.9.3 (sur Jetpack Compose 1.9, comme l'app Android), Material 3 1.9.0, ViewModel `org.jetbrains.androidx.lifecycle` 2.9.6, dans `libs.versions.toml`.
2. **`feature:todo-compose`** (nouveau module KMP, plugin `compose-compiler`) : `TodoComposeViewModel` sur les use cases de `feature:todo`, `TodoComposeScreen`, `SharedMvpTheme` (tokens `core:designsystem`), date du rappel formatée par la plateforme (`expect` / `actual`), `todoComposeViewController` (iosMain). Tests `commonTest` du ViewModel avec les vrais use cases : ajout, validation, tri après cochage, suppression, tâche introuvable, réglage Notifications, consigne de rappels.
3. **`shared`** : dépendance `api` (pour Android), non exportée vers Swift ; `ComposeScreens.todo(onClose:)` en iosMain.
4. **Android** : bouton « Version Compose MP » sur l'écran Tâches, retour par « Fermer » ou le bouton retour ; un lien vers une tâche referme la version Compose.
5. **iOS** : package `FeatureTodoCompose` (`TodoComposeView`), affiché dans l'onglet Tâches à la place de `TodoListView` par son bouton (pas de feuille, qui passerait au-dessus du verrou), refermé par un lien profond ; démo `TodoComposeDemo` ; clé `CADisableMinimumFrameDurationOnPhone` dans `project.yml`.
6. **CI** : scheme `TodoComposeDemo`, contrôle de la clé Info.plist, tailles de l'APK, de l'app iOS et du binaire `Shared` release.
7. **Vérification** : CI, revues, recette manuelle ci-dessous.

## Recette manuelle (sur appareil de préférence : rendu, défilement et accessibilité ne se jugent pas en simulateur)

- Tâches → « Version Compose MP » : la même liste s'affiche, avec le titre « Écran partagé en Compose Multiplatform ». Ajouter une tâche, la cocher (elle passe en bas), la supprimer : l'écran natif montre les mêmes changements après « Fermer ».
- Titre vide → « Le titre est obligatoire » ; il disparaît dès qu'on tape. 81 caractères → « 80 caractères maximum ».
- Une tâche avec un rappel (posé depuis l'écran natif) affiche « Rappel le … » au format de l'appareil. Notifications coupées dans Réglages : la consigne « Activez les notifications… » apparaît.
- Mode sombre : couleurs du design system dans les deux versions.
- Android : le bouton retour referme la version Compose ; tourner l'écran la garde ouverte. iOS : « Fermer » revient à l'écran SwiftUI ; le clavier ne cache pas le champ et ne décale pas l'écran deux fois.
- Verrou activé (Réglages → Sécurité), version Compose ouverte : sélecteur d'apps, l'aperçu ne montre pas les tâches ; arrière-plan puis retour, l'écran verrouillé est au premier plan.
- Ouvrir `mvpmodular://tasks` pendant que la version Compose est affichée : retour à l'écran natif.
- iOS, comparer avec l'écran SwiftUI : défilement et rebond, sélection de texte et copier / coller dans le champ, taille de texte (Réglages iOS → Luminosité → Taille du texte), VoiceOver (chaque tâche, la case, « Supprimer » sont lus et activables).
- App de démo `TodoComposeDemo` : s'ouvre sur deux tâches d'exemple, à comparer à `TodoDemo`.
- Se déconnecter puis se reconnecter : la version Compose est vide, comme l'écran natif.
