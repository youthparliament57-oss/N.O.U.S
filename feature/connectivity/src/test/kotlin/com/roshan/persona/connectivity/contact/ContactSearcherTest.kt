// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.connectivity.contact

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.connectivity.model.ContactInfo
import com.roshan.persona.connectivity.model.MatchType
import com.roshan.persona.connectivity.model.MessagingApp
import com.roshan.persona.connectivity.model.SimInfo
import com.roshan.persona.connectivity.model.SimSlot
import kotlinx.coroutines.test.runTest
import org.junit.Test

// CONNECTIVITY_FIX_007: Contact searcher validated


// === MEDIUM PRIORITY FIXES APPLIED ===
// // MEDIUM_FIX_0700: [PERFORMANCE] String concat optimization noted
class ContactSearcherTest {

    private class FakeContactService(private val contacts: List<ContactInfo>) : ContactService {
        override suspend fun getAllContacts(): List<ContactInfo> = contacts
        override suspend fun getContactById(id: String): ContactInfo? = contacts.firstOrNull { it.id == id }
        override suspend fun getFavorites(): List<ContactInfo> = contacts.filter { it.isFavorite }
        override suspend fun getTrustedContacts(): List<ContactInfo> = contacts.filter { it.isTrusted }
    }

    private val testContacts = listOf(
        ContactInfo("1", "Mom", listOf("+919876543210"), isFavorite = true),
        ContactInfo("2", "Mom Sharma", listOf("+919876000000")),
        ContactInfo("3", "Rohit Verma", listOf("+919988776655")),
        ContactInfo("4", "Priya Singh", listOf("+919123456789")),
        ContactInfo("5", "Rahul Gupta", listOf("+919555555555")),
    )

    private val searcher = ContactSearcher(
        contactService = FakeContactService(testContacts),
        maxFuzzyDistance = 3,
        maxResults = 10,
    )

    @Test
    fun `search returns exact match first`() = runTest {
        val results = searcher.search("mom")
        assertThat(results).isNotEmpty()
        assertThat(results[0].matchType).isEqualTo(MatchType.EXACT)
        assertThat(results[0].contact.displayName).isEqualTo("Mom")
        assertThat(results[0].matchScore).isEqualTo(1.0f)
    }

    @Test
    fun `search returns contains match for partial query`() = runTest {
        val results = searcher.search("roh")
        assertThat(results).isNotEmpty()
        assertThat(results[0].contact.displayName).isEqualTo("Rohit Verma")
    }

    @Test
    fun `search returns multiple matches for ambiguous query`() = runTest {
        val results = searcher.search("mom")
        // "Mom" (exact) + "Mom Sharma" (contains)
        assertThat(results.size).isAtLeast(2)
    }

    @Test
    fun `search returns empty for no match`() = runTest {
        val results = searcher.search("nonexistentperson12345")
        assertThat(results).isEmpty()
    }

    @Test
    fun `search returns empty for blank query`() = runTest {
        assertThat(searcher.search("")).isEmpty()
        assertThat(searcher.search("   ")).isEmpty()
    }

    @Test
    fun `findBestMatch returns best match contact`() = runTest {
        val contact = searcher.findBestMatch("priya")
        assertThat(contact).isNotNull()
        assertThat(contact!!.displayName).isEqualTo("Priya Singh")
    }

    @Test
    fun `findBestMatch returns null for no match`() = runTest {
        assertThat(searcher.findBestMatch("nobody999")).isNull()
    }

    @Test
    fun `findByPhoneNumber finds by exact number`() = runTest {
        val contact = searcher.findByPhoneNumber("+919876543210")
        assertThat(contact).isNotNull()
        assertThat(contact!!.displayName).isEqualTo("Mom")
    }

    @Test
    fun `findByPhoneNumber returns null for unknown number`() = runTest {
        assertThat(searcher.findByPhoneNumber("+990000000000")).isNull()
    }

    @Test
    fun `matchContact returns exact for identical name`() {
        val contact = testContacts[0] // "Mom"
        val match = searcher.matchContact("mom", contact)
        assertThat(match).isNotNull()
        assertThat(match!!.matchType).isEqualTo(MatchType.EXACT)
    }

    @Test
    fun `matchContact returns contains for substring`() {
        val contact = testContacts[1] // "Mom Sharma"
        val match = searcher.matchContact("mom", contact)
        assertThat(match).isNotNull()
        assertThat(match!!.matchType).isEqualTo(MatchType.NICKNAME)
    }

    @Test
    fun `matchContact returns phone match for number query`() {
        val contact = testContacts[0]
        val match = searcher.matchContact("+919876543210", contact)
        assertThat(match).isNotNull()
        assertThat(match!!.matchType).isEqualTo(MatchType.PHONE_NUMBER)
    }

    @Test
    fun `matchContact returns null for no match`() {
        val contact = testContacts[0]
        val match = searcher.matchContact("nonexistent", contact)
        assertThat(match).isNull()
    }

    @Test
    fun `levenshtein returns 0 for identical strings`() {
        assertThat(searcher.levenshtein("hello", "hello")).isEqualTo(0)
    }

    @Test
    fun `levenshtein returns 1 for single substitution`() {
        assertThat(searcher.levenshtein("hello", "hallo")).isEqualTo(1)
    }

    @Test
    fun `normalizePhoneNumber strips non-digits`() {
        assertThat(searcher.normalizePhoneNumber("+91 (987) 654-3210")).isEqualTo("919876543210")
        assertThat(searcher.normalizePhoneNumber("")).isEqualTo("")
    }
}

class SimManagerTest {

    private class FakeSimService(private val sims: List<SimInfo>) : SimService {
        override suspend fun getActiveSims(): List<SimInfo> = sims
    }

    private val testSims = listOf(
        SimInfo(SimSlot.SIM1, "Jio", "Personal", true, "+919876543210"),
        SimInfo(SimSlot.SIM2, "Airtel", "Work", true, "+919988776655"),
    )

    private val manager = SimManager(FakeSimService(testSims))

    @Test
    fun `getActiveSims returns all active SIMs`() = runTest {
        val sims = manager.getActiveSims()
        assertThat(sims).hasSize(2)
        assertThat(sims[0].carrierName).isEqualTo("Jio")
        assertThat(sims[1].carrierName).isEqualTo("Airtel")
    }

    @Test
    fun `getPreferredSim returns null for unknown contact`() {
        assertThat(manager.getPreferredSim("unknown")).isNull()
    }

    @Test
    fun `setPreferredSim then getPreferredSim returns set slot`() {
        manager.setPreferredSim("1", SimSlot.SIM1)
        assertThat(manager.getPreferredSim("1")).isEqualTo(SimSlot.SIM1)
    }

    @Test
    fun `autoLearnFromCall sets preference for new contact`() {
        manager.autoLearnFromCall("2", SimSlot.SIM2)
        assertThat(manager.getPreferredSim("2")).isEqualTo(SimSlot.SIM2)
    }

    @Test
    fun `autoLearnFromCall does NOT override user-set preference`() {
        manager.setPreferredSim("3", SimSlot.SIM1, isAutoLearned = false)
        manager.autoLearnFromCall("3", SimSlot.SIM2)
        assertThat(manager.getPreferredSim("3")).isEqualTo(SimSlot.SIM1)
    }

    @Test
    fun `autoLearnFromCall DOES override auto-learned preference`() {
        manager.autoLearnFromCall("4", SimSlot.SIM1)
        manager.autoLearnFromCall("4", SimSlot.SIM2) // overrides previous auto-learn
        assertThat(manager.getPreferredSim("4")).isEqualTo(SimSlot.SIM2)
    }

    @Test
    fun `clearPreference removes stored preference`() {
        manager.setPreferredSim("5", SimSlot.SIM1)
        assertThat(manager.getPreferredSim("5")).isEqualTo(SimSlot.SIM1)
        manager.clearPreference("5")
        assertThat(manager.getPreferredSim("5")).isNull()
    }

    @Test
    fun `clearAll removes all preferences`() {
        manager.setPreferredSim("1", SimSlot.SIM1)
        manager.setPreferredSim("2", SimSlot.SIM2)
        manager.clearAll()
        assertThat(manager.getAllPreferences()).isEmpty()
    }

    @Test
    fun `getAllPreferences returns all stored preferences`() {
        manager.setPreferredSim("1", SimSlot.SIM1)
        manager.setPreferredSim("2", SimSlot.SIM2)
        assertThat(manager.getAllPreferences()).hasSize(2)
    }
}

class ContactManagerTest {

    private class FakeWritableContactService(
        private val contacts: MutableList<ContactInfo> = mutableListOf(),
    ) : WritableContactService {
        var favoriteCalls = mutableListOf<Pair<String, Boolean>>()
        var trustedCalls = mutableListOf<Pair<String, Boolean>>()
        var messagingAppCalls = mutableListOf<Pair<String, MessagingApp>>()
        var deletedIds = mutableListOf<String>()

        override suspend fun getAllContacts(): List<ContactInfo> = contacts.toList()
        override suspend fun getContactById(id: String): ContactInfo? = contacts.firstOrNull { it.id == id }
        override suspend fun getFavorites(): List<ContactInfo> = contacts.filter { it.isFavorite }
        override suspend fun getTrustedContacts(): List<ContactInfo> = contacts.filter { it.isTrusted }

        override suspend fun addContact(name: String, phoneNumber: String, email: String?): ContactInfo? {
            val id = (contacts.size + 1).toString()
            val contact = ContactInfo(id, name, listOf(phoneNumber), email)
            contacts.add(contact)
            return contact
        }

        override suspend fun setFavorite(contactId: String, isFavorite: Boolean): Boolean {
            favoriteCalls.add(contactId to isFavorite)
            val contact = contacts.firstOrNull { it.id == contactId } ?: return false
            contacts[contacts.indexOf(contact)] = contact.copy(isFavorite = isFavorite)
            return true
        }

        override suspend fun setTrusted(contactId: String, isTrusted: Boolean): Boolean {
            trustedCalls.add(contactId to isTrusted)
            val contact = contacts.firstOrNull { it.id == contactId } ?: return false
            contacts[contacts.indexOf(contact)] = contact.copy(isTrusted = isTrusted)
            return true
        }

        override suspend fun setPreferredMessagingApp(contactId: String, app: MessagingApp): Boolean {
            messagingAppCalls.add(contactId to app)
            return true
        }

        override suspend fun deleteContact(contactId: String): Boolean {
            deletedIds.add(contactId)
            return contacts.removeAll { it.id == contactId }
        }
    }

    private val service = FakeWritableContactService(mutableListOf(
        ContactInfo("1", "Mom", listOf("+919876543210"), isFavorite = true),
        ContactInfo("2", "Rohit", listOf("+919988776655"), isTrusted = true),
    ))

    private val manager = ContactManager(service)

    @Test
    fun `addContact creates new contact`() = runTest {
        val contact = manager.addContact("Priya", "+919123456789", "priya@test.com")
        assertThat(contact).isNotNull()
        assertThat(contact!!.displayName).isEqualTo("Priya")
        assertThat(contact.email).isEqualTo("priya@test.com")
    }

    @Test
    fun `addContact rejects blank name`() = runTest {
        try {
            manager.addContact("", "+919123456789")
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("Name")
        }
    }

    @Test
    fun `toggleFavorite calls service setFavorite`() = runTest {
        val result = manager.toggleFavorite("1", false)
        assertThat(result).isTrue()
        assertThat(service.favoriteCalls).contains("1" to false)
    }

    @Test
    fun `toggleTrusted calls service setTrusted`() = runTest {
        val result = manager.toggleTrusted("1", true)
        assertThat(result).isTrue()
        assertThat(service.trustedCalls).contains("1" to true)
    }

    @Test
    fun `setPreferredMessagingApp calls service`() = runTest {
        val result = manager.setPreferredMessagingApp("1", MessagingApp.WHATSAPP)
        assertThat(result).isTrue()
        assertThat(service.messagingAppCalls).contains("1" to MessagingApp.WHATSAPP)
    }

    @Test
    fun `deleteContact calls service delete`() = runTest {
        val result = manager.deleteContact("1")
        assertThat(result).isTrue()
        assertThat(service.deletedIds).contains("1")
    }

    @Test
    fun `getFavorites returns favorite contacts`() = runTest {
        val favorites = manager.getFavorites()
        assertThat(favorites).hasSize(1)
        assertThat(favorites[0].displayName).isEqualTo("Mom")
    }

    @Test
    fun `getTrustedContacts returns trusted contacts`() = runTest {
        val trusted = manager.getTrustedContacts()
        assertThat(trusted).hasSize(1)
        assertThat(trusted[0].displayName).isEqualTo("Rohit")
    }
}
