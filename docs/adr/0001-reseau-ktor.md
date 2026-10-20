# ADR 0001 — Réseau : Ktor dans un module `core:network`

- Statut : accepté
- Date : 2026-10-20

## Contexte

La feature `weather` est le premier appel réseau du projet. Toute la logique devant rester en KMP, le client HTTP doit tourner sur Android et iOS, et les prochaines features en auront aussi besoin.

## Décision

- **Ktor client** (3.6) avec `kotlinx.serialization` pour le JSON. Moteur Android (`HttpURLConnection`) sur Android, plutôt qu'OkHttp dont la version tirée par Ktor 3.6 exige compileSdk 37, Darwin (`NSURLSession`) sur iOS, choisis par plateforme dans `core:network`.
- Nouveau module **`core:network`** : `createHttpClient()` (JSON tolérant aux champs inconnus, délais d'attente, statuts non 2xx en exception), `safeApiCall` qui convertit les exceptions Ktor en `AppError` (`Network`, `Unauthorized`, `NotFound`, `Unknown`), et `networkModule` (un `HttpClient` unique dans Koin).
- Les features en dépendent en `implementation` : les types Ktor ne sortent pas de leur couche `data`. `shared` ne l'exporte pas vers Swift.
- Tests : `ktor-client-mock` (MockEngine) passé à `createHttpClient(engine)`, pour tester repository et use cases sans réseau.

## Conséquences

- Pas de clé d'API pour l'instant (Open-Meteo est gratuit). Une API authentifiée ajoutera l'en-tête dans `core:network` ; aucun secret ne doit être commité.
- Tout en HTTPS : pas d'exception ATS sur iOS. Android aura besoin de la permission `INTERNET` le jour où son app appellera une feature réseau.
