package com.offlinemesh.app

import com.google.common.truth.Truth.assertThat
import com.offlinemesh.app.core.crypto.IdentityGenerator
import org.junit.Test

class IdentityGeneratorTest {

    @Test
    fun generateRandom_producesValidOfcId() {
        val id = IdentityGenerator.generateRandom()

        assertThat(id).startsWith("OFC-")
        assertThat(id.length).isEqualTo(12) // OFC- + 8 characters
        assertThat(IdentityGenerator.isValidOfcId(id)).isTrue()
    }

    @Test
    fun generateFromPublicKey_isDeterministic() {
        val samplePublicKeyBytes = "SAMPLE_PUBLIC_KEY_PAYLOAD_123456789".toByteArray()

        val id1 = IdentityGenerator.generateFromPublicKey(samplePublicKeyBytes)
        val id2 = IdentityGenerator.generateFromPublicKey(sampleSampleBytes = samplePublicKeyBytes)

        assertThat(id1).isEqualTo(id2)
        assertThat(id1).startsWith("OFC-")
        assertThat(id1.length).isEqualTo(12)
        assertThat(IdentityGenerator.isValidOfcId(id1)).isTrue()
    }

    private fun IdentityGenerator.generateFromPublicKey(sampleSampleBytes: ByteArray): String {
        return IdentityGenerator.generateFromPublicKey(sampleSampleBytes)
    }

    @Test
    fun isValidOfcId_rejectsMalformedIds() {
        assertThat(IdentityGenerator.isValidOfcId("INVALID-ID")).isFalse()
        assertThat(IdentityGenerator.isValidOfcId("OFC-123")).isFalse() // Too short
        assertThat(IdentityGenerator.isValidOfcId("OFC-123456789")).isFalse() // Too long
        assertThat(IdentityGenerator.isValidOfcId("OFC-1234ILOU")).isFalse() // Contains invalid Crockford chars (I, L, O, U)
    }
}
