package com.mvp.umbrella

import org.koin.core.annotation.KoinInternalApi
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Commun à tous les parapluies d'app de démo (ajouté à leur iosTest par `demoUmbrella`, ADR 0027) : un parapluie ne
 * charge qu'une partie des modules Koin, une dépendance oubliée (un `get()` sans définition) ferait planter la démo
 * au premier écran. Chaque définition de [umbrellaModules] est donc résolue une fois, sur le simulateur.
 *
 * Effets de bord, limités au processus de test du simulateur : les singletons réels sont créés (bases SQLite et
 * dossiers dans Application Support ; KeychainSecureStorage, qui purge à sa création les entrées d'une installation
 * précédente du trousseau de l'app de test ; scopes de coroutines jamais annulés).
 */
class UmbrellaKoinTest {
    @OptIn(KoinInternalApi::class)
    @Test
    fun everyDefinitionResolves() {
        val koin = koinApplication { modules(umbrellaModules) }.koin
        try {
            val definitions = koin.instanceRegistry.instances.values.map { it.beanDefinition }.distinct()
            assertTrue(definitions.isNotEmpty(), "Aucune définition Koin chargée")
            val failures = definitions.mapNotNull { definition ->
                runCatching { koin.get<Any>(definition.primaryType, definition.qualifier) }
                    .exceptionOrNull()
                    ?.let { "${definition.primaryType.simpleName} : ${it.message}" }
            }
            if (failures.isNotEmpty()) fail("Définitions Koin non résolues :\n" + failures.joinToString("\n"))
        } finally {
            koin.close()
        }
    }
}
