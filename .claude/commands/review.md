---
description: Lance code-reviewer et security-reviewer en parallèle sur le diff courant et fusionne leurs rapports.
argument-hint: [fichiers ou branche, optionnel]
---

Lance en parallèle les sous-agents **code-reviewer** et **security-reviewer** sur : $ARGUMENTS (si vide, le diff courant).
Fusionne leurs rapports en un seul, trié par sévérité, sans doublons, avec un verdict global.
