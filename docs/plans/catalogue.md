# Plan — Catalogue liste / détail

Objectif : une liste de produits paginée avec images, une fiche détail, et un contenu consultable hors ligne, sur Android et iOS. Choix techniques : [ADR 0006](../adr/0006-catalogue-cache-images.md).

1. **`core:designsystem`** : `SizeTokens` (vignette, image de détail).
2. **`feature:catalog`** (nouveau) : modèles `ProductSummary`, `Product`, `Catalog` ; `CatalogRepository` ; 4 use cases (observer, actualiser, page suivante, fiche) ; `DummyJsonRemoteDataSource` ; cache SQLDelight `CatalogDatabase`. Tests `commonTest` avec un MockEngine Ktor et une base en mémoire.
3. **`shared`** : `catalogModule` dans `sharedModules`, export iOS, `UseCases.observeCatalog(...)`, `refreshCatalog()`, `loadMoreProducts()`, `getProduct()`.
4. **Android** : onglet « Catalogue » entre Météo et Réglages ; liste (tirer pour actualiser, page suivante en arrivant en bas), fiche par-dessus la liste (retour système), images avec Coil.
5. **iOS** : package `FeatureCatalog` (liste dans une `NavigationStack`, fiche avec galerie, `ImageLoader`), onglet « Catalogue », démo `CatalogDemo` ; CI : schéma `CatalogDemo`.
6. **Vérification** : CI, revues, recette manuelle ci-dessous.

## Recette manuelle (Android et iOS)

- Catalogue : la liste s'affiche avec vignettes, marque, prix, remise et note.
- Faire défiler jusqu'en bas : la page suivante arrive (20 produits par page), jusqu'à la fin du catalogue (le pied de liste disparaît).
- Tirer vers le bas : la liste est rechargée depuis le début.
- Toucher un produit : fiche avec galerie d'images (balayer), prix, note, stock, description. Retour : la liste est à la même position.
- Mode avion, puis relancer l'app : la liste déjà vue s'affiche, avec « Pas de connexion internet. Liste enregistrée le … » ; les fiches et images déjà vues s'ouvrent ; en bas de la liste, « Réessayer ».
- Mode avion dès la première ouverture (cache vide) : message d'erreur et « Réessayer ».
- Se déconnecter puis se reconnecter : le catalogue est toujours là (contenu public, non effacé).
