package com.mvp.feature.todo.domain.order

/**
 * Clés d'ordre fractionnaires (ADR 0024) : des chaînes comparées caractère par caractère, avec toujours une clé
 * entre deux autres. Déplacer une tâche ne change que sa clé : une seule modification part au serveur, et deux
 * appareils qui déplacent des tâches différentes ne se gênent pas. Algorithme de David Greenspan
 * (« Implementing Fractional Indexing », 2020), en base 62 : une partie entière (la tête dit sa longueur) puis une
 * partie fractionnaire sans zéro final. `""` veut dire « pas de clé » (tâche d'avant cette version, ou reçue d'un
 * serveur qui ne connaît pas l'ordre) : elle se trie avant les autres.
 */
object OrderKey {

    /** Longueur maximale d'une clé gardée ou reçue ; au-delà, la section est renumérotée. */
    const val MAX_LENGTH = 64

    private const val DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    private const val ZERO = '0'
    private const val FIRST = "a0"
    private val SMALLEST_INTEGER = "A" + "0".repeat(26)

    /** Clé que l'app a pu écrire : chiffres base 62, forme valide, au plus [MAX_LENGTH] caractères. */
    fun isValid(key: String): Boolean = key.length <= MAX_LENGTH && isWellFormed(key)

    /**
     * Clé strictement entre [before] et [after] ; `null` : début ou fin de la liste. Les deux bornes doivent être
     * bien formées et dans l'ordre ([IllegalArgumentException] sinon).
     */
    fun between(before: String?, after: String?): String {
        require(before == null || isWellFormed(before)) { "Clé invalide : $before" }
        require(after == null || isWellFormed(after)) { "Clé invalide : $after" }
        require(before == null || after == null || before < after) { "Clés dans le désordre : $before, $after" }
        if (before == null) {
            if (after == null) return FIRST
            val intAfter = integerPart(after)
            if (intAfter == SMALLEST_INTEGER) return intAfter + midpoint("", after.substring(intAfter.length))
            if (intAfter < after) return intAfter
            return requireNotNull(decrementInteger(intAfter)) { "Plus de clé avant $after" }
        }
        val intBefore = integerPart(before)
        val fracBefore = before.substring(intBefore.length)
        if (after == null) return incrementInteger(intBefore) ?: (intBefore + midpoint(fracBefore, null))
        val intAfter = integerPart(after)
        if (intBefore == intAfter) return intBefore + midpoint(fracBefore, after.substring(intAfter.length))
        val next = requireNotNull(incrementInteger(intBefore)) { "Plus de clé après $before" }
        return if (next < after) next else intBefore + midpoint(fracBefore, null)
    }

    /** [count] clés croissantes strictement entre [before] et [after], réparties pour rester courtes. */
    fun between(before: String?, after: String?, count: Int): List<String> {
        require(count >= 0)
        return when {
            count == 0 -> emptyList()
            count == 1 -> listOf(between(before, after))
            after == null -> generateSequence(between(before, null)) { between(it, null) }.take(count).toList()
            before == null -> generateSequence(between(null, after)) { between(null, it) }.take(count).toList().reversed()
            else -> {
                val half = count / 2
                val middle = between(before, after)
                between(before, middle, half) + middle + between(middle, after, count - half - 1)
            }
        }
    }

    private fun isWellFormed(key: String): Boolean {
        if (key.isEmpty() || key == SMALLEST_INTEGER || key.any { DIGITS.indexOf(it) < 0 }) return false
        val length = integerLength(key[0]) ?: return false
        return key.length >= length && !key.substring(length).endsWith(ZERO)
    }

    /** Longueur de la partie entière, tête comprise : « a » à « z » de 2 à 27 caractères, « Z » à « A » aussi. */
    private fun integerLength(head: Char): Int? = when (head) {
        in 'a'..'z' -> head - 'a' + 2
        in 'A'..'Z' -> 'Z' - head + 2
        else -> null
    }

    private fun integerPart(key: String): String = key.substring(0, integerLength(key[0])!!)

    /** Partie fractionnaire strictement entre [a] et [b] (`null` : sans borne haute), sans zéro final. */
    private fun midpoint(a: String, b: String?): String {
        if (b != null) {
            var common = 0
            while ((a.getOrNull(common) ?: ZERO) == b[common]) common++
            if (common > 0) {
                return b.substring(0, common) + midpoint(a.substring(minOf(common, a.length)), b.substring(common))
            }
        }
        val digitA = if (a.isEmpty()) 0 else DIGITS.indexOf(a[0])
        val digitB = if (b == null) DIGITS.length else DIGITS.indexOf(b[0])
        return when {
            digitB - digitA > 1 -> DIGITS[(digitA + digitB + 1) / 2].toString()
            b != null && b.length > 1 -> b.substring(0, 1)
            else -> DIGITS[digitA] + midpoint(a.drop(1), null)
        }
    }

    /** Entier suivant, `null` après le plus grand. */
    private fun incrementInteger(integer: String): String? {
        val head = integer[0]
        val digits = integer.substring(1).toCharArray()
        for (i in digits.indices.reversed()) {
            val next = DIGITS.indexOf(digits[i]) + 1
            if (next < DIGITS.length) {
                digits[i] = DIGITS[next]
                return head + digits.concatToString()
            }
            digits[i] = ZERO
        }
        // Retenue jusqu'à la tête : l'entier change de longueur.
        return when (head) {
            'Z' -> "a$ZERO"
            'z' -> null
            else -> {
                val nextHead = head + 1
                val rest = digits.concatToString()
                nextHead + if (nextHead > 'a') rest + ZERO else rest.dropLast(1)
            }
        }
    }

    /** Entier précédent, `null` avant le plus petit. */
    private fun decrementInteger(integer: String): String? {
        val head = integer[0]
        val digits = integer.substring(1).toCharArray()
        val last = DIGITS.last()
        for (i in digits.indices.reversed()) {
            val previous = DIGITS.indexOf(digits[i]) - 1
            if (previous >= 0) {
                digits[i] = DIGITS[previous]
                return head + digits.concatToString()
            }
            digits[i] = last
        }
        return when (head) {
            'a' -> "Z$last"
            'A' -> null
            else -> {
                val previousHead = head - 1
                val rest = digits.concatToString()
                previousHead + if (previousHead < 'Z') rest + last else rest.dropLast(1)
            }
        }
    }
}
