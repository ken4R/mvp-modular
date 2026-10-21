# ADR 0005 — Localisation de l'appareil : `core:location`

- Statut : accepté
- Date : 2026-10-21

## Contexte

La météo doit pouvoir s'afficher pour la position de l'appareil, sur Android et iOS, à côté de la recherche de ville. Une position approximative suffit. Un refus de la permission doit donner un message clair. Le Kotlin partagé garde la logique ; `feature:weather` ne doit pas dépendre d'une API de plateforme.

## Options

- Android : **Google Play Services** (`FusedLocationProviderClient` : précis et économe, mais une dépendance de plus, absente des appareils sans services Google, et dont il faut vérifier le `minCompileSdk` face à AGP 8.13) ou **`LocationManager`** du système (aucune dépendance ; fournisseur « fused » natif à partir d'Android 12).
- iOS : **CoreLocation** appelé depuis Kotlin/Native, ou depuis Swift dans le package `FeatureWeather`.
- Permission : **approximative** (`ACCESS_COARSE_LOCATION`, `kCLLocationAccuracyKilometer`) ou précise.
- Où vit le contrat : dans `core:location` (la feature en dépend) ou **dans `core:common`**, comme `NotificationsPreference` et `SessionExpiry`.

## Décision

- Contrat **`LocationProvider.currentPosition(): AppResult<GeoPosition>`** dans `core:common` ; deux nouvelles erreurs partagées : `AppError.PermissionDenied` et `AppError.Unavailable`.
- Nouveau module **`core:location`** (`expect`/`actual` `locationModule`) :
  - Android : `LocationManager`, sans nouvelle dépendance. Fournisseur fused (Android 12+), puis réseau, puis GPS (Android 12+, ou permission précise). Dernière position connue si elle a moins de 10 minutes, sinon une mesure (`getCurrentLocation` sur Android 11+, `requestSingleUpdate` avant). Aucun fournisseur actif → `Unavailable`. La permission est demandée par l'écran Météo ; le module vérifie seulement qu'elle est accordée.
  - iOS : CoreLocation depuis Kotlin/Native, sur le main thread. Demande « Lorsque l'app est active » la première fois, puis `requestLocation()` avec une précision du kilomètre. Refus, restriction ou service coupé → `PermissionDenied`.
  - Délai maximal de 15 s, puis `Unavailable`.
- **`GetLocalWeatherUseCase`** (`feature:weather`) : position → arrondie à 2 décimales (environ 1 km) → météo Open-Meteo. Seule la position arrondie quitte l'appareil.
- Rien n'est enregistré : pas de `UserSessionObserver` à ajouter.
- Android obtient enfin l'écran Météo (onglet entre Tâches et Réglages) et la permission `INTERNET`, qui manquait jusqu'ici car aucun écran Android n'appelait le réseau.

## Conséquences

- Sur Android, sans services Google, la position peut être plus lente à obtenir qu'avec Play Services ; sur Android 8 à 11, sans fournisseur réseau (certains émulateurs), la position est indisponible car le GPS demanderait la permission précise.
- La position arrondie est envoyée à Open-Meteo, tiers déjà utilisé par la recherche de ville. Le texte de permission iOS (`NSLocationWhenInUseUsageDescription`, dans `iosApp/project.yml`) le dit.
- Pas de nom de lieu pour la position : l'écran affiche « Ma position ». Un géocodage inverse (CLGeocoder / `Geocoder`) enverrait la position à Apple ou Google ; laissé de côté.
- Toute autre feature qui a besoin de la position injecte `LocationProvider`.
