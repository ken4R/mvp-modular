package com.mvp.core.location

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FirstNonNullTest {

    @Test
    fun renvoieLaPremiereReponseNonNulle() = runTest {
        val result = firstNonNull(
            listOf(
                suspend { delay(300); "fused" },
                suspend { delay(100); null },
                suspend { delay(200); "gps" },
            ),
        )
        assertEquals("gps", result)
    }

    @Test
    fun ignoreUnFournisseurQuiNeRepondJamais() = runTest {
        var cancelled = false
        val result = firstNonNull(
            listOf(
                suspend {
                    try {
                        awaitCancellation()
                    } finally {
                        cancelled = true
                    }
                },
                suspend { delay(50); "gps" },
            ),
        )
        assertEquals("gps", result)
        assertTrue(cancelled, "la demande restée en attente doit être annulée")
    }

    @Test
    fun renvoieNullSiToutesLesReponsesSontNulles() = runTest {
        val result = firstNonNull(listOf(suspend { null }, suspend { delay(10); null as String? }))
        assertNull(result)
    }

    @Test
    fun renvoieNullSansFournisseur() = runTest {
        assertNull(firstNonNull(emptyList<suspend () -> String?>()))
    }
}
