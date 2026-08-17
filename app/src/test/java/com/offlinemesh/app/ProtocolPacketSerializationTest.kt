package com.offlinemesh.app

import com.google.common.truth.Truth.assertThat
import com.offlinemesh.app.data.transport.protocol.PacketCodec
import com.offlinemesh.app.data.transport.protocol.PacketType
import com.offlinemesh.app.data.transport.protocol.ProtocolPacket
import org.junit.Test
import java.util.UUID

class ProtocolPacketSerializationTest {

    @Test
    fun packetSerialization_preservesAllFields() {
        val original = ProtocolPacket(
            protocolVersion = 1,
            packetId = UUID.randomUUID().toString(),
            packetType = PacketType.DATA_DIRECT,
            senderId = "OFC-7K4P9X2M",
            senderDisplayName = "Alice",
            senderPublicKey = "MEYCIQC3/fakePublicKeyBase64==",
            recipientId = "OFC-3R9N2B7K",
            timestamp = 1720000000000L,
            ttl = 3,
            hopCount = 0,
            payload = "Hello over offline mesh!",
            signature = "MEQCIBGsignatureBase64=="
        )

        val bytes = PacketCodec.encode(original)
        assertThat(bytes).isNotEmpty()

        val decoded = PacketCodec.decode(bytes)
        assertThat(decoded).isEqualTo(original)
        assertThat(decoded.packetId).isEqualTo(original.packetId)
        assertThat(decoded.payload).isEqualTo("Hello over offline mesh!")
        assertThat(decoded.ttl).isEqualTo(3)
        assertThat(decoded.hopCount).isEqualTo(0)
    }

    @Test
    fun decodeOrNull_returnsNullOnCorruptedData() {
        val corruptedBytes = "NOT_A_VALID_JSON_PACKET".toByteArray()
        val decoded = PacketCodec.decodeOrNull(corruptedBytes)
        assertThat(decoded).isNull()
    }
}
