package de.kvxd.kmcprotocol

import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking

fun ByteWriteChannel.flushBlocking() = runBlocking {
    flush()
}