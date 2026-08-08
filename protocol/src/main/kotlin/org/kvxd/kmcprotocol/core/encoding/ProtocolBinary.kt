package org.kvxd.kmcprotocol.core.encoding

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialInfo
import net.lenni0451.mcstructs.nbt.NbtTag
import org.kvxd.kmcprotocol.core.KmcProtocolException

enum class BinaryKind {
    AnonymousNbt,
    AnonOptionalNbt,
    Nbt,
    OptionalNbt,
    CompressedNbt,
    RestBuffer,
    Buffer,
    Uuid,
}

@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
annotation class ProtocolBinary(
    val kind: BinaryKind,
    val countType: NumberEncoding = NumberEncoding.VarInt,
    val fixedCount: Int = -1,
)

internal fun PacketWriter.writeBinary(spec: ProtocolBinary, value: Any?) {
    when (spec.kind) {
        BinaryKind.AnonymousNbt -> writeNbtTag((value as? NbtTag) ?: emptyNbtCompound())
        BinaryKind.AnonOptionalNbt -> {
            val tag = value as? NbtTag
            if (tag == null) writeByte(NbtEndTypeId) else writeNbtTag(tag)
        }
        BinaryKind.Nbt -> writeNamedNbtTag((value as? NbtTag) ?: emptyNbtCompound())
        BinaryKind.OptionalNbt -> {
            val tag = value as? NbtTag
            if (tag == null) writeByte(NbtEndTypeId) else writeNamedNbtTag(tag)
        }
        BinaryKind.CompressedNbt -> writeCompressedNbtTag(value as? NbtTag)
        BinaryKind.RestBuffer -> writeBytes((value as? ByteArray) ?: ByteArray(0))
        BinaryKind.Buffer -> {
            val bytes = (value as? ByteArray) ?: ByteArray(0)
            if (spec.fixedCount < 0) writeNumber(spec.countType, bytes.size)
            writeBytes(bytes)
        }
        BinaryKind.Uuid -> writeUuid((value as? java.util.UUID) ?: java.util.UUID(0L, 0L))
    }
}

internal fun PacketReader.readBinary(spec: ProtocolBinary): Any? =
    when (spec.kind) {
        BinaryKind.AnonymousNbt -> readNbtTag() ?: emptyNbtCompound()
        BinaryKind.AnonOptionalNbt -> readNbtTag()
        BinaryKind.Nbt -> readNamedNbtTag() ?: emptyNbtCompound()
        BinaryKind.OptionalNbt -> readNamedNbtTag()
        BinaryKind.CompressedNbt -> readCompressedNbtTag()
        BinaryKind.RestBuffer -> readRemaining()
        BinaryKind.Buffer ->
            readBytes(if (spec.fixedCount >= 0) spec.fixedCount else readNumber(spec.countType).toInt())
        BinaryKind.Uuid -> readUuid()
    }

internal fun binaryMismatch(spec: ProtocolBinary, value: Any?): Nothing =
    throw KmcProtocolException("Value ${value?.let { it::class.simpleName }} is not valid for ${spec.kind}.")

@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
annotation class ProtocolOption

@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
annotation class ProtocolFlags(val encoding: NumberEncoding = NumberEncoding.VarInt)

@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
annotation class ProtocolSwitch(
    val compareTo: String,
    val presentKeys: Array<String>,
    val voidKeys: Array<String>,
    val defaultPresent: Boolean,
)

internal fun switchBranchPresent(spec: ProtocolSwitch, discriminator: Any?, owner: String): Boolean {
    if (discriminator === UnresolvedProtocolPath) {
        throw KmcProtocolException(
            "Cannot resolve the switch discriminator '${spec.compareTo}' for $owner. The path did not " +
                "match any recorded field, so the branch cannot be selected."
        )
    }

    val key = switchKey(discriminator)
    return when {
        spec.presentKeys.contains(key) -> true
        spec.voidKeys.contains(key) -> false
        else -> spec.defaultPresent
    }
}
