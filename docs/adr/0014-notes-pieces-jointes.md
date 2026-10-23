# ADR 0014 — Notes et pièces jointes des tâches

- Statut : accepté
- Date : 2026-10-23

## Contexte

Une tâche n'a qu'un titre de 80 caractères. On veut pouvoir y ajouter une note libre et des pièces jointes (une photo, un document), stockées sur l'appareil. Les tâches sont déjà synchronisées par une file d'envoi hors ligne vers un faux serveur (ADR 0012), effacées à la déconnexion (`UserSessionObserver`) et leurs bases sont hors sauvegardes, avec `secure_delete` (ADR 0013). Les pièces jointes sont des données personnelles (photos de documents, de lieux) : elles doivent suivre les mêmes règles.

## Décision

**La note est synchronisée, les pièces jointes restent sur l'appareil.**
- Note : colonne `note` de `TodoEntity` (migration `3.sqm`, schéma version 4), 2 000 caractères max (`UpdateTodoNoteUseCase`). Elle fait partie du contenu de la tâche : un changement de note est daté (`updated_at`), entre dans la file d'envoi et voyage dans `TodoRecord` avec la même règle de conflit (la version la plus récente gagne, par tâche). Une note reçue trop longue est ignorée, comme un titre invalide.
- Pièces jointes : propres à l'appareil, comme le rappel. Le faux serveur n'a pas de stockage de fichiers et un vrai backend demandera un service dédié (envoi par morceaux, quota, antivirus) : à décider avec lui. Une tâche supprimée sur un autre appareil perd ici ses pièces jointes.

**Formats limités à ce que les apps affichent elles-mêmes** : photo (JPEG), PDF, texte UTF-8 (`AttachmentType`). Aucune pièce jointe n'est confiée à une autre app (pas de `FileProvider` sur Android, pas de feuille de partage) : l'aperçu est dans la fiche (Coil / `PdfRenderer` / texte sur Android, `UIImage` / PDFKit / texte sur iOS), donc derrière le verrou Face ID / empreinte (ADR 0007) et sous le masque des apps récentes. La fiche s'affiche à la place de la liste, pas en feuille, pour la même raison sur iOS.

**Règles dans KMP** (`AddTodoAttachmentUseCase`) : type annoncé dans la liste, contenu conforme (signature `FF D8 FF` pour le JPEG, `%PDF-` pour le PDF, texte UTF-8 valide sans octet nul), 10 Mo max, fichier non vide, 10 pièces jointes max par tâche. Le nom affiché est nettoyé (dernier segment du chemin, sans caractères de contrôle ni d'inversion de sens, 100 caractères max) et n'est jamais un chemin : le fichier est nommé par l'id de la pièce jointe (UUID).

**Photos réenregistrées par les apps** avant l'ajout : décodage puis JPEG qualité 85, 2 048 px au plus, orientation appliquée (ImageDecoder / BitmapFactory + EXIF sur Android, ImageIO sur iOS). Le JPEG produit n'a aucune métadonnée : ni position GPS, ni appareil, ni date. HEIC et PNG deviennent du JPEG. C'est du code de plateforme (décodeurs d'images), donc dans les apps ; KMP refuse de toute façon une « photo » qui n'est pas un JPEG. Sélecteurs sans permission : Photo Picker et `OpenDocument` sur Android, `PhotosPicker` et `fileImporter` sur iOS. Pas d'appareil photo pour l'instant (permission caméra à ajouter si besoin).

**Nouveau module `core:files`** : `PrivateFileStore` (écrire, lire, chemin, supprimer, tout supprimer, lister), un dossier par feature (`todo-attachments`), noms limités à `[A-Za-z0-9_-]{1,64}` (pas de `../`).
- Android : `noBackupFilesDir/<dossier>` (ni sauvegarde Google, ni transfert d'appareil, que `allowBackup="false"` ne bloque plus depuis Android 12), écriture dans un fichier temporaire puis renommage. Chiffré par le chiffrement de l'appareil (FBE).
- iOS : `Application Support/<dossier>`, exclu des sauvegardes (`NSURLIsExcludedFromBackupKey`), fichiers en `NSFileProtectionComplete` (illisibles iPhone verrouillé), écriture atomique.

**Cohérence base / fichiers** (`SqlDelightTodoLocalDataSource`) : le fichier est écrit avant sa ligne (`TodoAttachmentEntity`) et supprimé après elle, hors transaction. Une ligne a donc toujours son fichier. La suppression d'une tâche (ici ou reçue du serveur) retire ses pièces jointes dans la même transaction que la tâche. La déconnexion vide les trois tables puis supprime tout le dossier. Le nombre de pièces jointes vient d'une jointure dans `selectAll` : la liste se réactualise quand on en ajoute ou en retire une.

## Conséquences

- Les pièces jointes ne se retrouvent pas sur un autre appareil ni après réinstallation : à dire à l'utilisateur (texte de la fiche « Ils restent sur cet appareil »), et à revoir avec un vrai backend.
- Un fichier peut rester orphelin si l'app est tuée entre la suppression d'une ligne et celle de son fichier : il n'est plus affiché et part à la déconnexion. Un nettoyage au démarrage (`names()` moins les ids en base) pourra être ajouté.
- Les fichiers supprimés ne sont pas écrasés sur le disque (pas d'équivalent de `secure_delete`) : la suppression passe par le système de fichiers chiffré, ce qui rend les blocs libérés illisibles sans la clé de l'appareil. Accepté.
- `PdfRenderer` (Android) et PDFKit (iOS) analysent des PDF choisis par l'utilisateur dans le processus de l'app. Accepté pour un fichier que l'utilisateur a lui-même joint ; l'aperçu Android s'arrête à 30 pages.
- Un ajout lit le fichier entier en mémoire (10 Mo max), puis le copie dans KMP (iOS : une seule copie `memcpy` par `bytesOf`).
- L'écran Compose Multiplatform (ADR 0010) n'affiche pas la fiche : la note et les pièces jointes passent par les écrans natifs.
