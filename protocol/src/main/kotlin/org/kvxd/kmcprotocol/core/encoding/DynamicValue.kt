package org.kvxd.kmcprotocol.core.encoding

import kotlinx.serialization.SerialName
import net.benwoodworth.knbt.NbtTag
import java.util.UUID
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

internal class DynamicValue(
    val value: Any? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DynamicValue) return false
        return valuesEqual(value, other.value)
    }

    override fun hashCode(): Int = valueHash(value)

    override fun toString(): String = "DynamicValue(value=${renderValue(value)})"

    companion object {
        val Null = DynamicValue(null)

        fun obj(values: Map<String, DynamicValue>) = DynamicValue(values)

        fun list(values: List<DynamicValue>) = DynamicValue(values)

        fun bytes(values: ByteArray) = DynamicValue(values)

        private fun valuesEqual(left: Any?, right: Any?): Boolean =
            when {
                left is ByteArray && right is ByteArray -> left.contentEquals(right)
                left is Number && right is Number -> left.toDouble() == right.toDouble()
                left is List<*> && right is List<*> -> left.size == right.size && left.zip(right).all { (a, b) -> valuesEqual(a, b) }
                left is Map<*, *> && right is Map<*, *> -> left.keys == right.keys && left.keys.all { valuesEqual(left[it], right[it]) }
                else -> left == right
            }

        private fun valueHash(value: Any?): Int =
            when (value) {
                is ByteArray -> value.contentHashCode()
                is List<*> -> value.fold(1) { acc, item -> 31 * acc + valueHash(item) }
                is Map<*, *> -> value.entries.fold(1) { acc, entry ->
                    31 * acc + (entry.key?.hashCode() ?: 0) xor valueHash(entry.value)
                }
                else -> value?.hashCode() ?: 0
            }

        private fun renderValue(value: Any?): String =
            when (value) {
                is ByteArray -> value.contentToString()
                else -> value.toString()
            }
    }
}

@Suppress("UNCHECKED_CAST")
internal fun DynamicValue.asObject(): Map<String, DynamicValue> =
    value as? Map<String, DynamicValue> ?: emptyMap()

@Suppress("UNCHECKED_CAST")
internal fun DynamicValue.asList(): List<DynamicValue> =
    value as? List<DynamicValue> ?: emptyList()

internal fun DynamicValue.asBytes(): ByteArray = value as? ByteArray ?: byteArrayOf()

internal fun DynamicValue?.asNumber(): Number = this?.value as? Number ?: 0

internal fun DynamicValue.asStringList(): List<String> =
    (value as? Iterable<*>)?.mapNotNull { item ->
        when (val unwrapped = item.unwrapDynamicValue()) {
            is String -> unwrapped
            is Enum<*> -> unwrapped.serialName()
            else -> unwrapped?.toString()
        }
    } ?: emptyList()

internal fun Any?.toDynamicValue(): DynamicValue =
    when (this) {
        null -> DynamicValue.Null
        is DynamicValue -> this
        is NbtTag -> DynamicValue(this)
        is ByteArray -> DynamicValue.bytes(this)
        is Iterable<*> -> DynamicValue.list(map { it.toDynamicValue() })
        is Map<*, *> -> DynamicValue.obj(entries.associate { it.key.toString() to it.value.toDynamicValue() })
        is UUID -> DynamicValue(this)
        is Enum<*> -> DynamicValue(serialName())
        is String, is Number, is Boolean -> DynamicValue(this)
        else -> DynamicValue.obj(
            this::class.memberProperties.associate { property ->
                property.protocolName() to property.getter.call(this).toDynamicValue()
            }
        )
    }

private fun KProperty1<out Any, *>.protocolName(): String =
    findAnnotation<SerialName>()?.value ?: name
