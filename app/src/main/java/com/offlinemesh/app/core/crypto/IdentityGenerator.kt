package com.offlinemesh.app.core.crypto

import java.security.MessageDigest
import java.security.SecureRandom

object IdentityGenerator {
    // Crockford Base32 alphabet: excludes I, L, O, U to prevent visual ambiguity
    private const val CROCKFORD_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val ID_LENGTH = 8
    private const val PREFIX = "OFC-"

    /**
     * Generates a deterministic OFC ID from the SHA-256 hash of an EC public key byte array.
     */
    fun generateFromPublicKey(publicKeyBytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(publicKeyBytes)
        return formatId(digest)
    }

    /**
     * Generates a random OFC ID using SecureRandom.
     */
    fun generateRandom(): String {
        val randomBytes = ByteArray(16)
        SecureRandom().nextBytes(randomBytes)
        return formatId(randomBytes)
    }

    private fun formatId(entropy: ByteArray): String {
        val sb = StringBuilder(PREFIX)
        var bitBuffer = 0L
        var bitsInEntropy = 0
        var byteIdx = 0

        while (sb.length < PREFIX.length + ID_LENGTH && (byteIdx < entropy.size || bitsInEntropy >= 5)) {
            while (bitsInEntropy < 5 && byteIdx < entropy.size) {
                bitBuffer = (bitBuffer shl 8) or (entropy[byteIdx].toLong() and 0xFF)
                bitsInEntropy += 8
                byteIdx++
            }
            if (bitsInEntropy >= 5) {
                val index = ((bitBuffer shr (bitsInEntropy - 5)) and 0x1F).toInt()
                sb.append(CROCKFORD_ALPHABET[index])
                bitsInEntropy -= 5
            }
        }

        // Pad if needed
        while (sb.length < PREFIX.length + ID_LENGTH) {
            sb.append('0')
        }

        return sb.toString()
    }

    /**
     * Validates whether a given string is a valid OFC ID.
     */
    fun isValidOfcId(id: String): Boolean {
        if (!id.startsWith(PREFIX) || id.length != PREFIX.length + ID_LENGTH) {
            return false
        }
        val codePart = id.substring(PREFIX.length)
        return codePart.all { it in CROCKFORD_ALPHABET }
    }
}
