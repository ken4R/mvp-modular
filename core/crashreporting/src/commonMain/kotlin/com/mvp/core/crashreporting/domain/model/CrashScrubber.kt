package com.mvp.core.crashreporting.domain.model

/**
 * Règles de nettoyage d'un rapport avant qu'il ne soit enregistré (ADR 0020). Les messages des exceptions ne
 * sont jamais lus ; les types et les lignes de pile ne contiennent normalement que des noms de code, mais on
 * masque quand même ce qui ressemble à une donnée personnelle (e-mail, chemin de fichier de l'utilisateur,
 * longue suite de chiffres) et on borne la taille, pour qu'un plantage en boucle ne remplisse pas le disque.
 */
object CrashScrubber {

    const val MAX_EXCEPTIONS = 5
    const val MAX_FRAMES = 40
    const val MAX_LINE_LENGTH = 300

    private val email = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")

    // Dossiers propres à une personne ou à l'installation (compte macOS de la machine de build, conteneur de l'app,
    // profil Android) : seul le nom du fichier est gardé (« <chemin>/TodoRepository.kt:42 »).
    private val userPath = Regex("(/Users/|/home/|/var/mobile/|/private/var/|/data/user/|/data/data/|/storage/)[^\\s()]*/")

    // Isolée : ni au milieu d'une adresse mémoire hexadécimale (0x0000000104a2…), ni collée à un nom.
    private val longNumber = Regex("(?<![0-9A-Za-z])\\d{7,}(?![0-9A-Za-z])")

    fun scrub(exceptions: List<CrashedException>): List<CrashedException> =
        exceptions.take(MAX_EXCEPTIONS).map { exception ->
            CrashedException(
                type = clean(exception.type),
                frames = exception.frames.take(MAX_FRAMES).map(::clean),
            )
        }

    private fun clean(line: String): String =
        line.take(MAX_LINE_LENGTH)
            .replace(email, "<email>")
            .replace(userPath, "<chemin>/")
            .replace(longNumber, "<nombre>")
}
