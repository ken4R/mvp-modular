# Plan — Météo à ma position

Objectif : afficher la météo actuelle à la position de l'appareil, sur Android et iOS, à côté de la recherche de ville, avec un message clair si la localisation est refusée. Ajouter au passage l'écran Météo qui manquait sur Android. Choix techniques : [ADR 0005](../adr/0005-localisation-meteo.md).

1. **`core:common`** : `GeoPosition`, `LocationProvider`, `AppError.PermissionDenied` et `AppError.Unavailable`.
2. **`core:location`** (nouveau) : `AndroidLocationProvider` (`LocationManager`), `CoreLocationProvider` (CoreLocation), `locationModule`.
3. **`feature:weather`** : `LocalWeather`, `GetLocalWeatherUseCase` (position arrondie à 2 décimales, puis météo). Tests : arrondi, refus, position indisponible, erreur du service météo.
4. **`shared`** : `locationModule` dans `sharedModules`, `UseCases.getLocalWeather()` pour iOS.
5. **Android** : onglet Météo (recherche, liste des villes, carte météo, « Météo à ma position »), permission `ACCESS_COARSE_LOCATION` demandée au tap, bouton « Ouvrir les paramètres » après un refus ; permissions `INTERNET` et `ACCESS_COARSE_LOCATION` dans le manifeste.
6. **iOS** : bouton « Météo à ma position » dans `FeatureWeather`, bouton « Ouvrir Réglages » après un refus, `NSLocationWhenInUseUsageDescription` pour l'app et la démo `WeatherDemo`.
7. **Vérification** : CI, revues, recette manuelle ci-dessous.

## Recette manuelle (Android et iOS, appareil réel de préférence)

- Météo → « Météo à ma position » : la demande de permission système apparaît (Android : choisir « Position approximative » suffit). Accepter : la carte « Ma position » s'affiche avec la météo du lieu.
- « Actualiser » sur cette carte : la position est relue, sans nouvelle demande.
- Refuser la permission : message « La localisation est refusée… » et bouton qui ouvre la page de l'app dans les paramètres. Autoriser là, revenir, retaper : la météo s'affiche.
- Couper la localisation de l'appareil : message « Position indisponible… ».
- Mode avion : « Problème de connexion ».
- Recherche de ville (Android, nouvel écran) : « Lyon » → liste → tap → carte météo ; « L » → « 2 caractères minimum ».
- Simulateur iOS : Features → Location → Custom Location pour simuler une position. Émulateur Android : réglage de position dans les contrôles étendus.
