# ADR 0006 — Catalogue : source de données, cache hors ligne et images

- Statut : accepté
- Date : 2026-10-21

## Contexte

Le prototype a besoin de l'écran type de beaucoup d'apps : une liste paginée avec images, une fiche détail, et un contenu encore consultable hors ligne. Sur Android et iOS, la logique reste en Kotlin partagé. Comme pour la météo, il faut une API publique sans clé.

## Options

- Source : **DummyJSON** (`/products`, pagination `limit` / `skip`, total fourni, vignette + plusieurs images par produit, HTTPS, sans clé), PokéAPI (images sur GitHub, une requête de plus par élément pour le détail), Art Institute of Chicago (images IIIF, beaucoup de champs vides).
- Cache de la liste : **SQLDelight** (déjà utilisé, ADR 0002) ou cache HTTP.
- Images Android : **Coil 3** (Compose, cache disque, module réseau Ktor) ou Glide (OkHttp, pas de Compose natif).
- Images iOS : `AsyncImage` (aucun cache disque garanti hors ligne) ou **petit chargeur maison** sur un `URLCache` dédié.

## Décision

- **DummyJSON** : une seule requête par page avec tout ce qu'affichent la liste et la fiche (paramètre `select` pour ne pas recevoir les avis et autres champs lourds). Prix en dollars US.
- Module **`feature:catalog`** : `ObserveCatalogUseCase` (le cache, en Flow), `RefreshCatalogUseCase` (première page, remplace le cache), `LoadMoreProductsUseCase` (page suivante après les produits en cache, aucun appel quand tout est chargé), `GetProductUseCase` (le cache d'abord, le serveur seulement si le produit n'y est pas : prêt pour les liens profonds).
- **Cache SQLDelight** (`catalog.db`) : produits, images de chaque produit, total et date du dernier chargement. Un échec réseau laisse le cache intact ; l'écran affiche la liste enregistrée et sa date. Une page vide arrête la pagination même si le total annoncé est plus grand.
- Seules les URL d'images en **HTTPS** sont gardées (filtrage dans la couche data, puis contrôle à nouveau côté iOS).
- **Android : Coil 3.4.0** (`coil-compose` + `coil-network-ktor3`). Coil 3.5 exige compileSdk 36 avec Compose 1.11, Coil 3.6 compileSdk 37 : hors de portée d'AGP 8.13. Le module Ktor évite OkHttp (voir le problème de PR #5) et réutilise le moteur Android. Cache disque par défaut de Coil, relu sans tenir compte de l'âge.
- **iOS : `ImageLoader`** (package `FeatureCatalog`) : `NSCache` en mémoire, `URLCache` de 200 Mo dans `Caches/CatalogImages`, relu en priorité, enregistrement explicite de chaque image.
- Le catalogue est **public**, sans lien avec le compte : pas de `UserSessionObserver`, le cache survit à la déconnexion.
- Nouveau token `SizeTokens` (vignette, image de détail) dans `core:designsystem`, exposé par `Sizes` / `Radius` (Android) et `DS.Size` (iOS).

## Conséquences

- Les pages et images déjà vues restent consultables hors ligne, même après un redémarrage ; ce qui n'a jamais été chargé ne l'est pas.
- À l'actualisation, le cache repart de la première page : les pages suivantes sont rechargées en faisant défiler.
- Les caches d'images vivent dans les dossiers de cache système (Android `cacheDir`, iOS `Caches`) : le système peut les vider si l'espace manque, et ils ne partent pas dans les sauvegardes.
- DummyJSON est un service de démonstration : brancher une vraie API ne touche que `DummyJsonRemoteDataSource` et ses DTO.
