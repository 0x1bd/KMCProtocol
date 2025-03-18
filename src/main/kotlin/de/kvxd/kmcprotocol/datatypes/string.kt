package de.kvxd.kmcprotocol.datatypes

import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking

internal fun ByteWriteChannel.writeString(string: String) = runBlocking {
    writeVarInt(string.length)
    writeByteArray(string.toByteArray(Charsets.UTF_8))
}

internal fun ByteReadChannel.readString(): String = runBlocking {
    val length = readVarInt()
    val bytes = ByteArray(length)
    readFully(bytes)
    return@runBlocking bytes.toString(Charsets.UTF_8)
}