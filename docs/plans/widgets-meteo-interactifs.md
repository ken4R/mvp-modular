# Plan — Widget Météo et case à cocher du widget Tâches

Objectif : cocher une tâche depuis le widget Tâches de l'écran d'accueil, et un widget Météo, sur Android (Glance) et iOS (WidgetKit), sans contourner la connexion, le verrou Face ID / empreinte, la corbeille ni la synchronisation. Choix techniques : [ADR 0026](../adr/0026-widgets-meteo-interactifs.md), à partir de l'[ADR 0011](../adr/0011-widgets-ecran-accueil.md).

1. **`feature:todo`** : `CompleteTodoFromWidgetUseCase` (termine, ne rouvre jamais ; session, verrou, corbeille vérifiés) ; ordre du widget avec la priorité. Tests `CompleteTodoFromWidgetUseCaseTest`, `TodoWidgetUseCaseTest` (priorité, corbeille), `TodoSyncTest` (la case cochée hors ligne arrive sur l'autre appareil).
2. **`feature:weather`** : `LocalWeatherCache` / `SecureLocalWeatherCache` (chiffré), `WeatherUserSessionObserver`, `GetLocalWeatherUseCase` garde la dernière météo, `ObserveWeatherWidgetUseCase`, `RefreshWeatherWidgetUseCase`. Tests `WeatherWidgetUseCaseTest`, `LocalWeatherUseCaseTest`.
3. **`shared`** : `UseCases.completeTodosFromWidget`, `observeWeatherWidget`, `refreshWeatherWidget`.
4. **Android** : `CheckBox` + `CompleteTodoAction` dans `widget/TodoWidget.kt`, `widget/WeatherWidget.kt` (+ receiver, `res/xml/weather_widget_info.xml`), `widget/WidgetUpdater.kt` générique, règle R8.
5. **iOS** : `CompleteTodoIntent` et cases dans `FeatureTodoWidget/Widget/TodoWidget.swift`, `TodoWidgetCompletions` dans `TodoWidgetData`, application dans `TodoWidgetPublisher` ; produit `WeatherWidgetData` et `WeatherWidgetPublisher` dans `FeatureWeather`, vue `FeatureWeather/Widget/WeatherWidget.swift` ; `iosApp/WidgetExtension/WidgetsBundle.swift` ; `project.yml` (sources et dépendance de l'extension, textes de localisation).

## Recette manuelle (simulateur / émulateur)

Case à cocher (Android ; iOS 17+) :

- Connecté, verrou coupé, widget Tâches posé : chaque ligne a une case. Android : la cocher fait disparaître la tâche du widget en quelques secondes, et elle est terminée dans l'app. iOS : la ligne passe cochée et barrée, « À faire · N » baisse ; rouvrir l'app : la tâche est terminée, puis disparaît du widget.
- iOS : re-toucher une case cochée avant de rouvrir l'app la décoche ; rien n'est terminé.
- Hors ligne (mode avion) : cocher fonctionne ; le bandeau « modifications en attente » de l'app la montre, elle part au retour du réseau.
- Cocher, puis se déconnecter avant de rouvrir l'app (iOS) : rien n'est terminé pour le compte suivant.
- Verrou Face ID / empreinte activé : aucune case (le widget est masqué).
- iOS, vrai iPhone en mode En veille verrouillé : toucher une case demande le déverrouillage.
- Tâche à la corbeille : elle n'est plus dans le widget ; une case cochée juste avant (iOS) est ignorée à l'ouverture.
- Priorité : sans rappel, une tâche « Haute » passe devant les autres dans le widget.

Widget Météo (Android : Widgets → « MVP Modular » → « Météo » ; iOS : « + » → « MVP Modular » → « Météo », petit format) :

- Avant toute météo : « Ouvrez Météo et touchez « Ma position » pour l'afficher ici. »
- Onglet Météo → « Ma position » : le widget affiche « Ma position », l'icône, la température, la condition et l'heure de la mesure. Tap : l'app s'ouvre sur Météo.
- Android : 30 min plus tard (ou `adb shell am broadcast -a android.appwidget.action.APPWIDGET_UPDATE -n com.mvp.android/.widget.WeatherWidgetReceiver`), l'heure de mise à jour avance sans demande de position. iOS : idem en revenant dans l'app après 15 min.
- Plus de 3 h sans nouvelle météo (avancer l'horloge du simulateur / de l'émulateur) : le widget invite à ouvrir Météo.
- Se déconnecter : « Connectez-vous pour voir la météo ici. » ; après reconnexion, plus de météo tant que « Ma position » n'a pas été refait.
- iOS, galerie : l'aperçu montre un exemple (18 °C, ciel dégagé), pas la vraie météo.
