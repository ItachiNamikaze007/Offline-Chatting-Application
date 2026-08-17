package com.offlinemesh.app.core.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec

class AndroidKeystoreManager(
    private val keyAlias: String = DEFAULT_KEY_ALIAS
) {
    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val DEFAULT_KEY_ALIAS = "offlinemesh_identity_key"
        private const val EC_CURVE = "secp256r1"
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    }

    private var fallbackKeyPair: KeyPair? = null

    /**
     * Initializes or retrieves the hardware-backed EC KeyPair.
     */
    fun getOrCreateKeyPair(): KeyPair {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(keyAlias)) {
                generateKeystoreKeyPair()
            }
            val privateKey = keyStore.getKey(keyAlias, null) as PrivateKey
            val publicKey = keyStore.getCertificate(keyAlias).publicKey
            KeyPair(publicKey, privateKey)
        } catch (e: Exception) {
            // Fallback for JVM Unit Tests where AndroidKeyStore is not present
            fallbackKeyPair ?: generateStandardKeyPair().also { fallbackKeyPair = it }
        }
    }

    private fun generateKeystoreKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec(EC_CURVE))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()
        kpg.initialize(spec)
        return kpg.generateKeyPair()
    }

    private fun generateStandardKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec(EC_CURVE))
        return kpg.generateKeyPair()
    }

    /**
     * Signs data using the local private key with ECDSA SHA256.
     */
    fun sign(data: ByteArray): String {
        val keyPair = getOrCreateKeyPair()
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM).apply {
            initSign(keyPair.private)
            update(data)
        }
        val sigBytes = signature.sign()
        return base64Encode(sigBytes)
    }

    /**
     * Verifies data against an ECDSA signature using the sender's public key.
     */
    fun verify(data: ByteArray, signatureBase64: String, publicKeyBase64: String): Boolean {
        return try {
            val publicKey = decodePublicKey(publicKeyBase64) ?: return false
            val sigBytes = base64Decode(signatureBase64)
            val signature = Signature.getInstance(SIGNATURE_ALGORITHM).apply {
                initVerify(publicKey)
                update(data)
            }
            signature.verify(sigBytes)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Returns Base64 encoded X.509 representation of public key.
     */
    fun getPublicKeyBase64(): String {
        val publicKey = getOrCreateKeyPair().public
        return base64Encode(publicKey.encoded)
    }

    fun decodePublicKey(base64PublicKey: String): PublicKey? {
        return try {
            val keyBytes = base64Decode(base64PublicKey)
            val keySpec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("EC")
            keyFactory.generatePublic(keySpec)
        } catch (e: Exception) {
            null
        }
    }

    private fun base64Encode(bytes: ByteArray): String {
        return try {
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            java.util.Base64.getEncoder().encodeToString(bytes)
        }
    }

    private fun base64Decode(base64: String): ByteArray {
        return try {
            Base64.decode(base64, Base64.NO_WRAP)
        } catch (e: Exception) {
            java.util.Base64.getDecoder().decode(base64)
        }
    }
}
