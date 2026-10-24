package com.mvp.core.common.domain.search

/**
 * Recherche plein texte côté appareil, partagée par les features (tâches, catalogue) : sans casse ni accents,
 * pour que « tache » trouve « Tâche » et « creme » trouve « Crème ».
 */
object SearchText {

    /** Au-delà, la recherche est tronquée : une saisie collée très longue ne ralentit pas le filtrage. */
    const val MAX_QUERY_LENGTH = 100

    /** Mots de [query], repliés par [fold] ; vide si la recherche est vide (tout correspond). */
    fun terms(query: String): List<String> =
        fold(query.take(MAX_QUERY_LENGTH)).split(' ').filter { it.isNotEmpty() }

    /** `true` si chaque mot de [terms] apparaît dans au moins un des [fields] (déjà passés ou non par [fold]). */
    fun matches(terms: List<String>, vararg fields: String?): Boolean {
        if (terms.isEmpty()) return true
        val folded = fields.filterNotNull().map(::fold)
        return terms.all { term -> folded.any { it.contains(term) } }
    }

    /** Minuscules, sans accents ni ligatures, espaces (tabulations, retours à la ligne) réduits à un seul. */
    fun fold(text: String): String {
        val out = StringBuilder(text.length)
        var lastWasSpace = true
        for (char in text.lowercase()) {
            if (char.isWhitespace()) {
                if (!lastWasSpace) out.append(' ')
                lastWasSpace = true
                continue
            }
            lastWasSpace = false
            out.append(ACCENTS[char] ?: char.toString())
        }
        return out.toString().trimEnd()
    }

    /** Lettres latines accentuées courantes (français, espagnol, allemand, portugais...), déjà en minuscules. */
    private val ACCENTS: Map<Char, String> = buildMap {
        fun alias(base: String, accented: String) = accented.forEach { put(it, base) }
        alias("a", "àáâãäåā")
        alias("c", "çćč")
        alias("e", "èéêëēė")
        alias("i", "ìíîïī")
        alias("n", "ñń")
        alias("o", "òóôõöøō")
        alias("u", "ùúûüū")
        alias("y", "ýÿ")
        alias("s", "śš")
        alias("z", "źżž")
        alias("ae", "æ")
        alias("oe", "œ")
        alias("ss", "ß")
    }
}
