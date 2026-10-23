package com.mvp.core.location

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        val result = firstNonNull(listOf<suspend () -> String?>({ null }, { delay(10); null }))
        assertNull(result)
    }

    @Test
    fun renvoieNullSansFournisseur() = runTest {
        assertNull(firstNonNull(emptyList<suspend () -> String?>()))
    }

    @Test
    fun uneExceptionRemonteEtAnnuleLesAutres() = runTest {
        // Contrat : le helper ne filtre pas les erreurs, chaque demande gère les siennes (voir AndroidLocationProvider).
        var cancelled = false
        assertFailsWith<IllegalStateException> {
            firstNonNull(
                listOf(
                    suspend {
                        try {
                            awaitCancellation()
                        } finally {
                            cancelled = true
                        }
                    },
                    suspend { delay(10); error("fournisseur en panne") },
                ),
            )
        }
        assertTrue(cancelled, "la demande restée en attente doit être annulée")
    }
}
