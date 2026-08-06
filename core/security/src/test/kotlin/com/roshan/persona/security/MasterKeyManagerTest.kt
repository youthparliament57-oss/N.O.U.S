package com.roshan.persona.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class MasterKeyManagerTest {
    private lateinit var manager: MasterKeyManager
    private lateinit var mockKeyStore: KeyStore
    private lateinit var mockKeyGenerator: KeyGenerator
    private lateinit var mockSecretKey: SecretKey

    @Before
    fun setup() {
        mockKeyStore = mockk(relaxed = true)
        mockKeyGenerator = mockk(relaxed = true)
        mockSecretKey = mockk(relaxed = true)

        mockkStatic(KeyStore::class)
        every { KeyStore.getInstance("AndroidKeyStore") } returns mockKeyStore

        every { mockKeyStore.load(null) } returns Unit

        mockkStatic(KeyGenerator::class)
        every { KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore") } returns mockKeyGenerator

        every { mockKeyGenerator.generateKey() } returns mockSecretKey

        io.mockk.mockkConstructor(KeyGenParameterSpec.Builder::class)
        val mockBuilder = mockk<KeyGenParameterSpec.Builder>(relaxed = true)
        val mockSpec = mockk<KeyGenParameterSpec>(relaxed = true)

        // Make the constructor return our mockBuilder by returning `this` internally via the mock
        every { anyConstructed<KeyGenParameterSpec.Builder>().setKeySize(any()) } answers { mockBuilder }
        every { anyConstructed<KeyGenParameterSpec.Builder>().setBlockModes(*anyVararg()) } answers { mockBuilder }
        every { anyConstructed<KeyGenParameterSpec.Builder>().setEncryptionPaddings(*anyVararg()) } answers { mockBuilder }
        every { anyConstructed<KeyGenParameterSpec.Builder>().setRandomizedEncryptionRequired(any()) } answers { mockBuilder }
        every { anyConstructed<KeyGenParameterSpec.Builder>().setUserAuthenticationRequired(any()) } answers { mockBuilder }
        every { anyConstructed<KeyGenParameterSpec.Builder>().setInvalidatedByBiometricEnrollment(any()) } answers { mockBuilder }
        every { anyConstructed<KeyGenParameterSpec.Builder>().setIsStrongBoxBacked(any()) } answers { mockBuilder }
        every { anyConstructed<KeyGenParameterSpec.Builder>().build() } returns mockSpec

        every { mockBuilder.setKeySize(any()) } returns mockBuilder
        every { mockBuilder.setBlockModes(*anyVararg()) } returns mockBuilder
        every { mockBuilder.setEncryptionPaddings(*anyVararg()) } returns mockBuilder
        every { mockBuilder.setRandomizedEncryptionRequired(any()) } returns mockBuilder
        every { mockBuilder.setUserAuthenticationRequired(any()) } returns mockBuilder
        every { mockBuilder.setInvalidatedByBiometricEnrollment(any()) } returns mockBuilder
        every { mockBuilder.setIsStrongBoxBacked(any()) } returns mockBuilder
        every { mockBuilder.build() } returns mockSpec

        manager = MasterKeyManager()
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `getOrCreateMasterKey generates new key when none exists`() {
        every { mockKeyStore.getKey("nous.master.v1", null) } returns null

        val key = manager.getOrCreateMasterKey()

        assertNotNull(key)
        assertEquals(mockSecretKey, key)
        verify { mockKeyGenerator.init(any<KeyGenParameterSpec>()) }
        verify { mockKeyGenerator.generateKey() }
    }

    @Test
    fun `getOrCreateMasterKey returns existing key`() {
        every { mockKeyStore.getKey("nous.master.v1", null) } returns mockSecretKey

        val key = manager.getOrCreateMasterKey()

        assertEquals(mockSecretKey, key)
        verify(exactly = 0) { mockKeyGenerator.generateKey() }
    }

    @Test
    fun `wipe deletes the master key`() {
        manager.wipe()

        verify { mockKeyStore.deleteEntry("nous.master.v1") }
    }
}
