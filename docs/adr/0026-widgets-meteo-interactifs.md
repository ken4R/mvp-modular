# ADR 0026 — Widget Météo et case à cocher du widget Tâches

- Statut : accepté
- Date : 2026-10-28

## Contexte

Youssef veut un widget Météo et pouvoir cocher une tâche depuis l'écran d'accueil, sur Android (Glance) et iOS (WidgetKit). L'[ADR 0011](0011-widgets-ecran-accueil.md) a posé le schéma du widget Tâches : l'état est calculé dans KMP ; Android l'affiche dans le processus de l'app ; iOS le recopie dans un fichier de l'App Group que lit une extension sans le framework Kotlin (~30 Mo de mémoire au plus, pas d'accès à la base ni au Keychain). Il avait laissé deux points ouverts : la météo demanderait la position et le réseau depuis le widget ; une case à cocher demanderait d'écrire dans la base depuis l'extension iOS, et le receiver Android est exporté.

Contraintes : toute modification d'une tâche passe par un use case partagé et la file d'envoi (ADR 0012) ; rien ne doit être visible ni modifiable sans session valide ou avec le verrou Face ID / empreinte activé (ADR 0007, 0011) ; une tâche à la corbeille (ADR 0025) n'existe plus pour le widget ; l'ordre et la priorité (ADR 0024) sont ceux de la liste.

## Options

Case à cocher sur iOS (le bouton d'un widget, iOS 17+, exécute un `AppIntent` dans l'extension) :

- a. **L'extension charge le framework Kotlin et la base** : framework de 39 Mo pour une limite de ~30 Mo, base et Keychain à déplacer dans l'App Group (écarté en ADR 0011).
- b. **Intent exécuté dans le processus de l'app** (`LiveActivityIntent` détourné, ou `ForegroundContinuableIntent`) : réveille l'app en arrière-plan (Koin, base, possiblement inaccessible avant le premier déverrouillage) ou l'ouvre, ce qui retire l'intérêt du widget. Usage non prévu par Apple pour un widget.
- c. **L'extension note la demande dans l'App Group, l'app l'applique** à sa prochaine ouverture par le use case partagé ; le widget montre tout de suite la tâche cochée.

Données du widget Météo :

- 1. **Le widget demande la position** à chaque rafraîchissement : localisation « Toujours » (iOS) ou `ACCESS_BACKGROUND_LOCATION` (Android), refusées par la plupart des gens et lourdes à justifier.
- 2. **La dernière météo « Ma position » obtenue par l'app**, gardée avec sa position déjà arrondie (~1 km), actualisée sans redemander la position.
- 3. **Une ville choisie** : demande un écran de réglage du widget, et la recherche de ville en Swift dans l'extension (logique hors KMP).

## Décision

**Option c pour la case, option 2 pour la météo.**

### Case à cocher

- **`CompleteTodoFromWidgetUseCase`** (`feature:todo`) : termine la tâche, **sans jamais la rouvrir** (une demande en retard ou répétée ne défait rien ; un verrou commun à toutes les instances sépare la lecture de l'état et l'inversion, pour deux taps rapides). Refuse sans session valide (`Unauthorized`), avec le verrou activé (`PermissionDenied`), pour une tâche absente ou à la corbeille (`NotFound`). Tâche déjà terminée : succès sans écriture. Mesure `TaskCompleted` comme dans l'app ; la modification part au serveur par la file d'envoi.
- **Android** : `CheckBox` Glance sur chaque ligne, `actionRunCallback<CompleteTodoAction>` avec l'id. Le callback passe par le récepteur propre à Glance, **non exporté**, avec un PendingIntent explicite : une autre app ne peut pas le déclencher (le `TodoWidgetReceiver` exporté ne fait toujours qu'ouvrir l'app). Le use case revérifie quand même session et verrou, car un widget pas encore redessiné peut montrer une tâche qu'il ne devrait plus. La tâche quitte le widget dès que la base change ; si la demande est refusée, le widget est redessiné pour décocher la case. Règle R8 pour garder `CompleteTodoAction` (recréé par son nom).
- **iOS 17+** : `Button(intent: CompleteTodoIntent)` sur chaque ligne. L'intent (dans l'extension) vérifie que le widget peut montrer la tâche maintenant (`displayed(at:)`, fichier de l'ADR 0011), puis note **seulement l'id** dans `todo-widget-completions.json` (même dossier, même protection que le fichier du widget) ; re-toucher la case annule la note. Le widget affiche la ligne cochée et barrée, le compteur en tient compte. `authenticationPolicy = .requiresAuthentication` : iPhone verrouillé (mode En veille), iOS demande de le déverrouiller. `isDiscoverable = false` : absent de Raccourcis. iOS 16 : pas de case, comme avant.
- **Application côté iOS** : `TodoWidgetPublisher` passe les ids à `UseCases.completeTodosFromWidget` au lancement et à chaque retour au premier plan, puis les oublie (réussies ou refusées). Quand le widget n'a plus de tâches à montrer (déconnexion, verrou activé), les demandes en attente sont effacées.

### Widget Météo

- **`LocalWeatherCache`** (`feature:weather`) : `GetLocalWeatherUseCase` garde la dernière météo obtenue et sa position arrondie, dans le **stockage chiffré** (`SecureLocalWeatherCache`, Keystore / Keychain). Effacée à la déconnexion (`WeatherUserSessionObserver`) et dès que la localisation est refusée ou retirée (`LocationProvider.isAuthorized`, qui lit l'autorisation sans la demander). Une écriture impossible ne prive pas l'écran Météo de sa réponse.
- **`ObserveWeatherWidgetUseCase`** : `SignedOut` sans session valide, `NoData` sans météo ou si elle a plus de **3 h**, sinon `Weather` (température, condition, jour/nuit, heure à laquelle l'app l'a obtenue, `visibleUntil` = 3 h après la mesure ou la fin de session si elle vient avant, lien `mvpmodular://weather`). Le use case réémet lui-même à `visibleUntil`. Le verrou Face ID ne masque pas la météo : elle ne dit rien du compte et la position n'est jamais affichée (ni écrite dans le fichier iOS).
- **`RefreshWeatherWidgetUseCase`** : nouvelle météo pour la position **déjà gardée**, sans demander la localisation, au plus tous les quarts d'heure, seulement avec une session valide et la localisation toujours autorisée (sinon la météo gardée est effacée, rien n'est envoyé). Rien n'est écrit si la météo a changé pendant l'appel (déconnexion, « Ma position » plus récente).
- **Android** : `WeatherWidget` (Glance), petit format ; actualisation à chaque mise à jour du widget (30 min, le minimum d'Android) après le premier affichage. `WidgetUpdater` (ex-`TodoWidgetUpdater`, rendu générique) redessine les deux widgets à chaque changement. Libellés et emoji de l'écran Météo.
- **iOS** : `WeatherWidgetPublisher` (package `FeatureWeather`) recopie l'état dans `weather-widget.json` ; produit `WeatherWidgetData` (sans dépendance) lié par l'extension ; vue dans `Packages/FeatureWeather/Widget`. Actualisation au lancement et au retour au premier plan seulement (pas de tâche de fond), et seulement si un widget Météo est posé (`WidgetCenter.getCurrentConfigurations`). L'extension `TodoWidgetExtension` porte maintenant les deux widgets (`iosApp/WidgetExtension/WidgetsBundle.swift`).
- Textes mis à jour : demande de localisation iOS et présentation du premier lancement (la dernière position est gardée pour le widget, repart pour l'actualiser s'il est posé, effacée à la déconnexion ou au retrait de l'accès à la position).

### Ordre du widget Tâches

Rappel le plus proche d'abord, puis **priorité décroissante**, puis l'ordre manuel de la liste (ADR 0024). Les tâches à la corbeille n'y sont ni montrées ni comptées (elles ne sortent pas de `observeTodos`).

## Conséquences

- Android : cocher dans le widget termine la tâche tout de suite, hors ligne compris (elle part au retour du réseau).
- iOS : la tâche cochée n'est terminée **qu'à la prochaine ouverture de l'app** : d'ici là elle reste à faire sur les autres appareils et dans les rappels (une notification peut encore arriver). Le widget la montre cochée en attendant. Une demande faite avant une déconnexion est perdue, c'est voulu.
- iOS : le fichier de demandes ne contient que des ids de tâches, jamais de titre. L'app et l'extension le lisent puis le réécrivent sans coordination (`NSFileCoordinator`) : une case cochée pendant les quelques millisecondes où l'app applique les demandes peut être perdue. Accepté pour le prototype : rien n'est terminé à tort, il suffit de recocher.
- iOS : la météo du widget ne vieillit pas en arrière-plan ; au-delà de 3 h sans ouvrir l'app, le widget invite à l'ouvrir. Une actualisation en tâche de fond (`BGAppRefreshTask`) reste possible plus tard sans changer KMP.
- La dernière position arrondie (~1 km) est maintenant **gardée** sur l'appareil, chiffrée, jusqu'à la déconnexion ou au retrait de la localisation, alors qu'elle n'était jamais enregistrée (ADR 0005). Elle n'est envoyée qu'à Open-Meteo, comme avant, mais aussi sans action de l'utilisateur tant qu'un widget Météo est posé (texte de consentement mis à jour). Elle reste gardée même sans widget posé : ne l'enregistrer qu'avec un widget posé est une amélioration possible.
- Android : le widget Météo fait un appel réseau toutes les 30 min tant qu'il est posé et la session valide (rien sans météo gardée).
- Le widget Tâches iOS a besoin d'iOS 17 pour la case ; l'app reste en iOS 16 minimum.
