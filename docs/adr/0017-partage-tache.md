# ADR 0017 — Partage d'une tâche

- Statut : accepté
- Date : 2026-10-24

## Contexte

L'utilisateur veut envoyer une tâche par message, mail ou note, depuis la fiche de la tâche, avec un lien qui rouvre l'app sur cette tâche. Une tâche peut porter une note personnelle (ADR 0014) et des pièces jointes gardées sur l'appareil, et l'app a une connexion et un verrou Face ID / empreinte (ADR 0003, 0007).

## Options

1. **Texte + lien `mvpmodular://tasks/<id>`** dans la feuille de partage du système (Android `ACTION_SEND`, iOS `ShareLink`), avec le lien profond existant (ADR 0008).
2. **Partage de fichiers** (pièces jointes, export) : il faudrait confier les fichiers à une autre app, ce qu'ADR 0014 exclut.
3. **Vraie invitation** (tâche partagée entre comptes, lien `https://`) : demande un backend, des droits par tâche et des liens vérifiés. Hors de portée sans serveur.

## Décision

**Option 1.** La logique est dans KMP : `ShareTodoUseCase` (`feature:todo`) construit `TodoShare` (objet, texte, lien) à partir d'une `TodoShareRequest(todoId, includeNote)`. Les apps ne font qu'ouvrir la feuille de partage.

Règles :

- **Contenu minimal** : titre (sur une ligne), état (à faire / terminée) et lien. Ni rappel (propre à l'appareil), ni pièce jointe, ni nombre de pièces jointes, ni compte, ni identifiant d'appareil.
- **Note seulement si demandée** : sans note, la feuille s'ouvre directement. Avec une note, Android demande « Inclure la note » (case décochée par défaut) ; iOS propose « Sans la note » / « Avec la note ». C'est la note **enregistrée** qui part : Android enregistre d'abord un brouillon modifié, et seulement si la note est cochée ; iOS désactive « Avec la note » tant que le brouillon n'est pas enregistré (`ShareLink` a besoin de son contenu avant le tap) et retire les textes préparés dès que la tâche change.
- **Tâche absente** : `AppError.NotFound`, rien n'est partagé.
- **Le lien ne fait que naviguer** (ADR 0008) : il est lu strictement par `DeepLinks.parse`, mis en attente jusqu'à la connexion et au déverrouillage, et une tâche inconnue (autre compte, supprimée) affiche « Tâche introuvable ». Il ne porte que l'identifiant aléatoire de la tâche.
- **Feuille ouverte et verrou** : la feuille du système est une fenêtre à part, au-dessus du masque de l'aperçu et de l'écran de verrouillage. iOS : la fiche ferme toutes les fenêtres modales (`PresentedSheets.dismissAll`) au passage en arrière-plan, verrou activé ou non. Android : l'app choisie s'ouvre dans sa propre tâche (`FLAG_ACTIVITY_NEW_DOCUMENT | FLAG_ACTIVITY_MULTIPLE_TASK`), hors de l'aperçu de l'app ; le sélecteur lui-même reste dans la tâche de l'app et peut réapparaître au retour par-dessus le verrou, avec l'aperçu du texte : risque accepté (l'utilisateur vient de le lancer, déverrouillé), à vérifier sur appareil.
- **Pas de mesure d'audience** pour le partage : un nouvel événement changerait le texte du consentement Analytics (ADR 0009).

## Conséquences

- Sans backend, le lien n'est utile que sur un appareil connecté au même compte, et pour l'instant le faux serveur est propre à chaque app : chez un destinataire, il ouvre l'app sur « Tâche introuvable » (ou rien, s'il n'a pas l'app ; les messageries ne rendent pas toujours un schéma `mvpmodular://` cliquable). Le titre et l'état restent lisibles dans le texte.
- Ce qui est partagé sort de l'app pour de bon : le titre (et la note si cochée) est confié à l'app choisie par l'utilisateur, hors de tout effacement à la déconnexion.
- Pour un vrai partage plus tard : liens vérifiés `https://` (voir ADR 0008), tâche partagée côté serveur avec des droits, et un lien qui ne révèle pas l'identifiant interne (jeton d'invitation révocable).
