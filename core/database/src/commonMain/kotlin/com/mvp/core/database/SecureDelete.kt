package com.mvp.core.database

/** Les pages libérées (tâche ou compte supprimé) sont remises à zéro au lieu de rester lisibles dans le fichier. */
internal const val SECURE_DELETE_PRAGMA = "PRAGMA secure_delete = ON"
