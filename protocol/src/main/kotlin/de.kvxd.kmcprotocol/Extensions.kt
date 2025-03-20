package de.kvxd.kmcprotocol

import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.runBlocking
import java.io.DataInput
import java.io.DataInputStream
import java.io.DataOutput
import java.io.DataOutputStream

fun ByteWriteChannel.flushBlocking() = runBlocking {
    flush()
}

fun ByteWriteChannel.asDataOutput(): DataOutput = DataOutputStream(this.toOutputStream())

fun ByteReadChannel.asDataInput(): DataInput = DataInputStream(this.toInputStream())