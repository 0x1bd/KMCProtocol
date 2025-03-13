package de.kvxd.kmcprotocol

import de.kvxd.kmcprotocol.datatypes.VarInt
import io.ktor.utils.io.core.*
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.readByteArray
import kotlin.text.toByteArray


object MinecraftTypes {

    fun writeString(sink: Sink, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        sink.writeFully(VarInt.encode(bytes.size))
        sink.writeFully(bytes)
    }

    fun readString(source: Source): String {
        val length = VarInt.decode(source)
        val bytes = source.readByteArray(length)
        return bytes.toString(Charsets.UTF_8)
    }
}