# Plan — Recherche et filtres (tâches et catalogue)

Objectif : retrouver une tâche ou un produit par quelques mots, et réduire la liste avec des filtres simples. Tout se fait sur l'appareil, hors ligne compris, sans nouvelle donnée stockée ni envoyée.

1. **`core:common`** : `SearchText` (domain/search) : recherche sans casse ni accents (« tache » trouve « Tâche », « oeuf » trouve « Œuf »), tous les mots doivent apparaître, saisie tronquée à 100 caractères. Test `SearchTextTest`.
2. **`feature:todo`** : `TodoFilter` (recherche dans le titre et la note, `TodoStatusFilter` Toutes / À faire / Terminées, avec rappel, avec pièce jointe, cumulables), `FilteredTodos` (tâches filtrées + toutes les tâches), `ObserveFilteredTodosUseCase`. Test `TodoFilterUseCaseTest`.
3. **`feature:catalog`** : `CatalogFilter` (recherche dans le nom et la marque, « 4★ et plus », `CatalogSort` ordre du serveur / prix croissant / prix décroissant / mieux notés, tris stables), `FilteredCatalog` (produits filtrés + cache complet), `ObserveFilteredCatalogUseCase`. La recherche porte sur les pages déjà chargées ; en bas de la liste, la page suivante est chargée comme sans filtre, et filtrée à son tour. Test `CatalogFilterUseCaseTest`.
4. **`shared`** : `UseCases.observeFilteredTodos(filter, ...)`, `observeFilteredCatalog(filter, ...)` ; un nouveau filtre = un nouvel abonnement.
5. **Android** : `TodoScreen` (champ de recherche + puces de filtre sous l'ajout), `CatalogScreen` (champ de recherche + puces « 4★ et plus » et tris ; un second tap sur le tri choisi revient à l'ordre du serveur). Les ViewModels gardent le filtre dans leur état et passent d'une observation à l'autre (`flatMapLatest`).
6. **iOS** : `TodoListView` (champ de recherche, sélecteur segmenté de l'état, boutons bascule rappel / pièce jointe), `CatalogView` (`.searchable` + menu « Filtrer et trier »).

Sur les deux plateformes, la recherche est appliquée après 0,25 s de pause de saisie, et le clavier n'apprend pas les mots cherchés (autocorrection désactivée). « Tout afficher » retire la recherche et les filtres et garde le tri. Un lien profond vers une tâche retire les filtres, pour que la tâche soit dans la liste. L'écran Compose Multiplatform (ADR 0010) n'a pas la recherche.

## Recette manuelle (simulateur / émulateur)

- Tâches : créer « Tâche importante », « Appeler le dentiste » (avec rappel), « Payer la facture » (avec une pièce jointe), et une tâche terminée avec la note « Crème fraîche ». Chercher « tache », « TACHE », « creme » : la bonne tâche reste seule.
- Filtres « À faire », « Terminées », « Avec rappel », « Avec pièce jointe », puis deux à la fois : la liste suit. Aucune correspondance : « Aucune tâche ne correspond » et « Tout afficher ».
- Recherche active, ouvrir un lien `mvp://todo/<id>` vers une tâche qui ne correspond pas : les filtres disparaissent et la tâche est mise en évidence.
- Rappels désactivés dans Réglages, filtre « Terminées » : l'invitation à activer les notifications reste affichée s'il y a des rappels à venir.
- Catalogue : chercher « apple », « mascara », « creme » ; « 4★ et plus » ; chaque tri. Chercher un mot rare : la liste charge les pages suivantes jusqu'à trouver ou arriver au bout (« Aucun produit ne correspond. »).
- Catalogue hors ligne (mode avion) : la recherche fonctionne sur les produits enregistrés.
