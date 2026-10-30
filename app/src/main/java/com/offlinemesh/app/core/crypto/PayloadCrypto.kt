package com.offlinemesh.app.core.crypto

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Provides End-to-End payload encryption and decryption for OfflineMesh messages.
 *
 * Uses Elliptic Curve Diffie-Hellman (ECDH) on secp256r1 to derive a shared secret,
 * hashes the secret with SHA-256 to create a 256-bit AES key, and encrypts the payload
 * using AES-GCM (12-byte random IV, 128-bit authentication tag).
 *
 * Relay nodes only forward the ciphertext and cannot decrypt the message contents
 * without possessing either the sender's or recipient's private key.
 */
object PayloadCrypto {

    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
    private const val PREFIX = "ENC:V1:"

    /**
     * Encrypts plaintext message with the recipient's public key and sender's private key.
     */
    fun encrypt(
        plaintext: String,
        recipientPublicKey: PublicKey,
        senderPrivateKey: PrivateKey
    ): String {
        val sharedSecret = deriveSharedSecret(senderPrivateKey, recipientPublicKey)
        val aesKey = deriveAesKey(sharedSecret)

        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, spec)

        val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        val ivBase64 = base64Encode(iv)
        val cipherBase64 = base64Encode(ciphertext)

        return "$PREFIX$ivBase64:$cipherBase64"
    }

    /**
     * Decrypts ciphertext using the sender's public key and recipient's private key.
     * Returns the original plaintext or throws an exception if decryption fails.
     */
    fun decrypt(
        encryptedPayload: String,
        senderPublicKey: PublicKey,
        recipientPrivateKey: PrivateKey
    ): String {
        if (!isEncrypted(encryptedPayload)) {
            // Fallback for unencrypted legacy payloads
            return encryptedPayload
        }

        val raw = encryptedPayload.removePrefix(PREFIX)
        val colonIdx = raw.indexOf(':')
        if (colonIdx == -1) {
            throw IllegalArgumentException("Malformed encrypted payload")
        }

        val ivBase64 = raw.substring(0, colonIdx)
        val cipherBase64 = raw.substring(colonIdx + 1)

        val iv = base64Decode(ivBase64)
        val ciphertext = base64Decode(cipherBase64)

        val sharedSecret = deriveSharedSecret(recipientPrivateKey, senderPublicKey)
        val aesKey = deriveAesKey(sharedSecret)

        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, aesKey, spec)

        val decryptedBytes = cipher.doFinal(ciphertext)
        return String(decryptedBytes, StandardCharsets.UTF_8)
    }

    /**
     * Checks whether a payload string is encrypted with OfflineMesh ENC:V1 format.
     */
    fun isEncrypted(payload: String): Boolean {
        return payload.startsWith(PREFIX)
    }

    private fun deriveSharedSecret(privateKey: PrivateKey, publicKey: PublicKey): ByteArray {
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(publicKey, true)
        return keyAgreement.generateSecret()
    }

    private fun deriveAesKey(sharedSecret: ByteArray): SecretKeySpec {
        val sha256 = MessageDigest.getInstance("SHA-256")
        val keyBytes = sha256.digest(sharedSecret)
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun base64Encode(bytes: ByteArray): String {
        return try {
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            java.util.Base64.getEncoder().encodeToString(bytes)
        }
    }

    private fun base64Decode(str: String): ByteArray {
        return try {
            Base64.decode(str, Base64.NO_WRAP)
        } catch (e: Exception) {
            java.util.Base64.getDecoder().decode(str)
        }
    }
}
