# Plan — Mode hors ligne

Objectif : les tâches restent modifiables sans réseau et se synchronisent toutes seules au retour du réseau, avec une règle claire en cas de conflit ; les écrans disent quand l'appareil est hors ligne. Pas encore de backend : faux serveur derrière une interface. Choix techniques : [ADR 0012](../adr/0012-mode-hors-ligne.md).

1. **`core:common`** : contrat `ConnectivityObserver`.
2. **`core:connectivity`** (nouveau module) : `AndroidConnectivityObserver` (ConnectivityManager), `NetworkPathConnectivityObserver` (iOS, NWPathMonitor), `ObserveConnectivityUseCase`, `connectivityModule`. Test `ObserveConnectivityUseCaseTest`.
3. **`feature:todo`** :
   - base : colonne `updated_at`, table `TodoPendingChange`, migration `2.sqm` ;
   - data : `SqlDelightTodoLocalDataSource` (file d'envoi dans la même transaction, implémente aussi `TodoSyncLocalDataSource`), `TodoRemoteDataSource` + `FakeTodoRemoteDataSource`, `TodoSyncRepositoryImpl`, `TodoUserSessionObserver` (efface aussi la file, après la synchronisation en cours) ;
   - domain : `TodoSyncRepository`, `TodoSyncStatus`, `StartTodoSyncUseCase`, `SyncTodosUseCase`, `ObserveTodoSyncStatusUseCase` ;
   - tests : `TodoSyncTest` (deux appareils sur le même faux serveur : modifications hors ligne, conflit, suppression, état du bandeau, réessai espacé, sans session, déconnexion) et `SqlDelightTodoLocalDataSourceTest` (file d'envoi, rappel hors file, heures croissantes, envoi pendant une modification, versions reçues, migration 1 → 3).
4. **`shared`** : `connectivityModule`, export de `core:connectivity`, `UseCases.startTodoSync()`, `observeTodoSyncStatus(...)`, `syncTodos()`, `observeConnectivity(...)`.
5. **Android** : permission `ACCESS_NETWORK_STATE`, `StartTodoSyncUseCase` lancé par `MvpApplication`, `ui/common/StatusBanner.kt`, bandeau dans Tâches (avec « Réessayer »), Météo et Catalogue (relance au retour du réseau).
6. **iOS** : `StatusBanner` dans `DesignSystemUI`, `TodoSync.start()` (package `FeatureTodo`) dans `iOSApp` et `TodoDemo`, bandeau dans `TodoListView`, `WeatherView`, `CatalogView`.

## Recette manuelle (simulateur / émulateur)

Couper le réseau : émulateur Android → mode Avion ; simulateur iOS → couper le Wi-Fi du Mac.

- Hors ligne, onglet Tâches : bandeau « Hors ligne · vos tâches restent modifiables ». Ajouter, cocher, supprimer : tout marche, le bandeau compte « N modifications en attente » (une tâche modifiée plusieurs fois compte une fois).
- Remettre le réseau : le bandeau disparaît en une seconde environ (le faux serveur répond en 0,4 s).
- Hors ligne, fermer l'app puis la rouvrir : les tâches et le compteur sont toujours là ; remettre le réseau : le compteur se vide.
- Changer seulement le rappel d'une tâche hors ligne : le compteur ne bouge pas (le rappel reste sur l'appareil).
- Se déconnecter hors ligne avec des modifications en attente, se reconnecter : aucune tâche, aucun compteur.
- Onglet Météo hors ligne : bandeau « Hors ligne · la météo s'affichera au retour du réseau » ; une recherche affiche « Problème de connexion ».
- Onglet Catalogue hors ligne : bandeau, la liste enregistrée reste consultable ; remettre le réseau : la liste s'actualise toute seule si l'actualisation avait échoué.
- Wi-Fi sans internet (Android) : compté comme hors ligne.
