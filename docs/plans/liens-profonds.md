# Plan — Liens profonds

Objectif : un lien, ou le tap sur un rappel, ouvre l'app directement sur le bon écran (une tâche, Météo, la fiche d'un produit, Réglages), sur Android et iOS, après la connexion et le déverrouillage. Choix techniques : [ADR 0008](../adr/0008-liens-profonds.md).

1. **`core:deeplink`** (nouveau module KMP) : `DeepLink`, `DeepLinks` (lecture et écriture des liens), `PendingDeepLinkRepository` en mémoire, use cases `OpenDeepLinkUseCase`, `ObservePendingDeepLinkUseCase`, `ConsumeDeepLinkUseCase`, et un `UserSessionObserver` qui oublie le lien en attente à la déconnexion. Tests : chaque écran, casse et `/` final, requête ignorée, liens et identifiants malformés, lien trop long, aller-retour, attente puis retrait, repli sur Tâches, lien plus récent gardé, déconnexion.
2. **Rappels** : `LocalNotification.deepLink`, posé par `feature:todo` (`mvpmodular://tasks/<id>`) ; Android l'ouvre par un intent explicite, iOS le range dans `userInfo`.
3. **`shared`** : `core:deeplink` exporté vers Swift, `UseCases.openDeepLink()`, `consumeDeepLink()`, `observePendingDeepLink(...)`.
4. **Android** : `intent-filter` `mvpmodular`, `singleTask`, `DeepLinkViewModel` ; `MainActivity` transmet chaque lien, les onglets changent d'onglet, mettent la tâche en évidence (`TodoViewModel.focus`) ou ouvrent la fiche produit.
5. **iOS** : `CFBundleURLTypes` dans `project.yml`, `.onOpenURL` dans `iOSApp`, tap sur un rappel dans `TodoReminders`, `AppTabsView` (onglets + navigation), `TodoListView(focusRequest:)` et `CatalogView(productRequest:)`.
6. **Design system** : token `highlight` pour la tâche mise en évidence.
7. **Vérification** : CI, revues, recette manuelle ci-dessous.

## Recette manuelle (simulateur / émulateur suffisent)

Ouvrir un lien :

- iOS (simulateur) : `xcrun simctl openurl booted "mvpmodular://weather"`, ou taper le lien dans Safari ou Notes.
- Android : `adb shell am start -a android.intent.action.VIEW -d "mvpmodular://weather"`.

À vérifier :

- `mvpmodular://weather`, `mvpmodular://catalog`, `mvpmodular://settings`, `mvpmodular://tasks` : l'app s'ouvre sur l'onglet, qu'elle soit fermée, en arrière-plan ou déjà ouverte sur un autre onglet.
- `mvpmodular://catalog/products/1` : la fiche du produit 1 s'ouvre ; le retour mène à la liste. `mvpmodular://catalog/products/999999` : « Produit introuvable. ».
- `mvpmodular://tasks/<id>` : copier l'id d'une tâche est malaisé à la main, le plus simple est le rappel : poser un rappel dans 1 minute, fermer l'app, taper la notification. L'app s'ouvre sur Tâches, la tâche est mise en évidence 3 secondes et la liste défile jusqu'à elle.
- `mvpmodular://tasks/inconnue` : Tâches avec « Cette tâche n'existe plus ». `mvpmodular://nimporte` : onglet Tâches.
- Déconnecté : ouvrir `mvpmodular://settings` → écran de connexion ; se connecter → Réglages.
- Verrou activé (Réglages → Sécurité) : mettre l'app en arrière-plan, ouvrir `mvpmodular://catalog` → écran verrouillé ; déverrouiller → Catalogue. Rien de l'app n'est visible avant.
- Verrouillé, ouvrir un lien puis « Se déconnecter » et se reconnecter : l'app s'ouvre sur Tâches (le lien a été oublié).
- Android : après avoir suivi un lien, tourner l'écran ou rouvrir l'app depuis les apps récentes : pas de nouveau saut d'onglet. Un lien ouvert depuis une autre app (navigateur) ne crée pas une seconde copie de l'app dans la pile du navigateur.
