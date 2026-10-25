package com.mvp.core.featureflags

import com.mvp.core.featureflags.data.remote.RemoteFlagsParser
import com.mvp.core.featureflags.domain.model.FeatureFlag
import com.mvp.core.featureflags.domain.model.FeatureStage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Vérifie le JSON publié sur GitHub Pages (remote-config/flags.json) : lisible par l'app, et ne citant que des
 * features livrées du catalogue. Le fichier est public : une feature en développement n'y a pas sa place (ADR 0021).
 * Test JVM : les tests unitaires Android partent du dossier du module.
 */
class PublishedFlagsTest {

    private val file = File("../../remote-config/flags.json")

    @Test
    fun publishedFlagsOnlyListReleasedFlagsWithBooleanValues() {
        val bytes = file.readBytes()
        val keys = Json.parseToJsonElement(bytes.decodeToString()).jsonObject.getValue("flags").jsonObject.keys
        val released = FeatureFlag.entries.filter { it.stage == FeatureStage.Released }.map { it.key }.toSet()
        assertTrue(keys.isNotEmpty())
        assertTrue(released.containsAll(keys), "Clés hors catalogue ou en développement : ${keys - released}")
        // Chaque clé a bien une valeur booléenne (sinon le parseur l'ignorerait sans rien dire).
        assertEquals(keys, RemoteFlagsParser.parse(bytes).keys)
    }
}
