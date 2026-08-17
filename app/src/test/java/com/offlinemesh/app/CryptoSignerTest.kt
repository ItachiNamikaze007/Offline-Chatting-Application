package com.offlinemesh.app

import com.google.common.truth.Truth.assertThat
import com.offlinemesh.app.core.crypto.AndroidKeystoreManager
import org.junit.Test
import java.nio.charset.StandardCharsets

class CryptoSignerTest {

    @Test
    fun ecSigningAndVerification_succeedsForMatchingKeyAndData() {
        val keystoreManager = AndroidKeystoreManager()
        val publicKeyBase64 = keystoreManager.getPublicKeyBase64()

        assertThat(publicKeyBase64).isNotEmpty()

        val data = "MESSAGE_TO_BE_SIGNED_12345".toByteArray(StandardCharsets.UTF_8)
        val signature = keystoreManager.sign(data)

        assertThat(signature).isNotEmpty()

        val isValid = keystoreManager.verify(data, signature, publicKeyBase64)
        assertThat(isValid).isTrue()
    }

    @Test
    fun ecVerification_failsForTamperedData() {
        val keystoreManager = AndroidKeystoreManager()
        val publicKeyBase64 = keystoreManager.getPublicKeyBase64()

        val originalData = "GENUINE_MESSAGE".toByteArray(StandardCharsets.UTF_8)
        val tamperedData = "TAMPERED_MESSAGE".toByteArray(StandardCharsets.UTF_8)

        val signature = keystoreManager.sign(originalData)

        val isValid = keystoreManager.verify(tamperedData, signature, publicKeyBase64)
        assertThat(isValid).isFalse()
    }
}
