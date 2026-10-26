package com.mvp.core.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LocationChangesTest {

    private val office = LocationNotification("t1", "Rappel", "Appeler Paul", 48.8566, 2.3522, 200, "mvpmodular://tasks/t1", 1_000L)
    private val home = LocationNotification("t2", "Rappel", "Arroser", 45.764, 4.8357, 100)

    @Test
    fun toutEstPoseLaPremiereFois() {
        val changes = planLocationChanges("g", emptySet(), listOf(office, home))

        assertEquals(emptySet(), changes.toRemove)
        assertEquals(listOf(office.identifier("g"), home.identifier("g")), changes.toAdd.map { it.first })
    }

    @Test
    fun uneNotificationInchangeeNestPasReposee() {
        val changes = planLocationChanges("g", setOf(office.identifier("g")), listOf(office))

        assertEquals(emptySet(), changes.toRemove)
        assertEquals(emptyList(), changes.toAdd)
    }

    @Test
    fun uneNotificationModifieeEstRemplacee() {
        val moved = office.copy(radiusMeters = 500)

        val changes = planLocationChanges("g", setOf(office.identifier("g")), listOf(moved))

        assertNotEquals(office.identifier("g"), moved.identifier("g"))
        assertEquals(setOf(office.identifier("g")), changes.toRemove)
        assertEquals(listOf(moved.identifier("g")), changes.toAdd.map { it.first })
    }

    @Test
    fun leTexteLeLienEtLExpirationComptentDansLEmpreinte() {
        val base = office.identifier("g")
        assertNotEquals(base, office.copy(body = "Autre").identifier("g"))
        assertNotEquals(base, office.copy(deepLink = null).identifier("g"))
        assertNotEquals(base, office.copy(expiresAtEpochMillis = 2_000L).identifier("g"))
        assertNotEquals(base, office.copy(latitude = 48.8567).identifier("g"))
    }

    @Test
    fun uneNotificationQuiNestPlusVoulueEstRetiree() {
        val changes = planLocationChanges("g", setOf(office.identifier("g"), home.identifier("g")), listOf(home))

        assertEquals(setOf(office.identifier("g")), changes.toRemove)
        assertEquals(emptyList(), changes.toAdd)
    }

    @Test
    fun lesNotificationsDUnAutreGroupeNeSontPasTouchees() {
        val changes = planLocationChanges("g", setOf(office.identifier("autre")), emptyList())

        assertEquals(emptySet(), changes.toRemove)
    }

    @Test
    fun auPlusVingtNotificationsLesPremieres() {
        val many = (1..25).map { home.copy(id = "t$it") }

        val changes = planLocationChanges("g", emptySet(), many)

        assertEquals(LocationNotificationScheduler.MAX_NOTIFICATIONS, changes.toAdd.size)
        assertEquals(many.take(20).map { it.id }, changes.toAdd.map { it.second.id })
    }

    @Test
    fun lIdentifiantCommenceParLeGroupeEtLId() {
        assertTrue(office.identifier("todo-location-reminders").startsWith("todo-location-reminders:t1:"))
        assertEquals("todo-location-reminders:t1", office.displayTag("todo-location-reminders"))
    }
}
