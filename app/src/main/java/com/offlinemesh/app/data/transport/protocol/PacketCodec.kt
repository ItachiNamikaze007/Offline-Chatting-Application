package com.offlinemesh.app.data.transport.protocol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets

object PacketCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(packet: ProtocolPacket): ByteArray {
        val jsonString = json.encodeToString(packet)
        return jsonString.toByteArray(StandardCharsets.UTF_8)
    }

    fun decode(bytes: ByteArray): ProtocolPacket {
        val jsonString = String(bytes, StandardCharsets.UTF_8)
        return json.decodeFromString<ProtocolPacket>(jsonString)
    }

    fun decodeOrNull(bytes: ByteArray): ProtocolPacket? {
        return try {
            decode(bytes)
        } catch (e: Exception) {
            null
        }
    }
}
