package com.offlinemesh.app

import com.google.common.truth.Truth.assertThat
import com.offlinemesh.app.core.crypto.AndroidKeystoreManager
import com.offlinemesh.app.core.crypto.PayloadCrypto
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

class PayloadCryptoTest {

    @Test
    fun payloadCrypto_encryptAndDecrypt_restoresOriginalPlaintext() {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))

        val aliceKeyPair = kpg.generateKeyPair()
        val bobKeyPair = kpg.generateKeyPair()
        val plaintext = "Ultra secret offline message from Alice to Bob"

        // Alice encrypts for Bob
        val encrypted = PayloadCrypto.encrypt(
            plaintext = plaintext,
            recipientPublicKey = bobKeyPair.public,
            senderPrivateKey = aliceKeyPair.private
        )

        assertThat(PayloadCrypto.isEncrypted(encrypted)).isTrue()
        assertThat(encrypted).doesNotContain("secret")
        assertThat(encrypted).doesNotContain(plaintext)

        // Bob decrypts from Alice
        val decrypted = PayloadCrypto.decrypt(
            encryptedPayload = encrypted,
            senderPublicKey = aliceKeyPair.public,
            recipientPrivateKey = bobKeyPair.private
        )

        assertThat(decrypted).isEqualTo(plaintext)
    }

    @Test
    fun payloadCrypto_relayNodeCannotDecryptWithoutPrivateKey() {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))

        val aliceKeyPair = kpg.generateKeyPair()
        val bobKeyPair = kpg.generateKeyPair()
        val eveRelayKeyPair = kpg.generateKeyPair()

        val plaintext = "Confidential Mesh Communication"

        val encrypted = PayloadCrypto.encrypt(
            plaintext = plaintext,
            recipientPublicKey = bobKeyPair.public,
            senderPrivateKey = aliceKeyPair.private
        )

        // Eve (Relay node) tries to decrypt using her own private key
        var eveDecrypted: String? = null
        try {
            eveDecrypted = PayloadCrypto.decrypt(
                encryptedPayload = encrypted,
                senderPublicKey = aliceKeyPair.public,
                recipientPrivateKey = eveRelayKeyPair.private
            )
        } catch (e: Exception) {
            // Expected exception due to AEAD bad tag / wrong key
        }

        assertThat(eveDecrypted).isNotEqualTo(plaintext)
    }

    @Test
    fun keystoreManager_encryptAndDecryptPayload_matchesCorrectly() {
        val kmAlice = AndroidKeystoreManager("alice_test_key")
        val kmBob = AndroidKeystoreManager("bob_test_key")

        val alicePub = kmAlice.getPublicKeyBase64()
        val bobPub = kmBob.getPublicKeyBase64()

        val originalText = "Hello Bob from Keystore Alice"

        val ciphertext = kmAlice.encryptPayload(originalText, bobPub)
        assertThat(ciphertext).doesNotContain("Hello")

        val decryptedText = kmBob.decryptPayload(ciphertext, alicePub)
        assertThat(decryptedText).isEqualTo(originalText)
    }
}
