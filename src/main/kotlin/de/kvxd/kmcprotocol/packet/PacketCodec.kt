package de.kvxd.kmcprotocol.packet

import de.kvxd.kmcprotocol.datatypes.readString
import de.kvxd.kmcprotocol.datatypes.readVarInt
import de.kvxd.kmcprotocol.datatypes.writeString
import de.kvxd.kmcprotocol.datatypes.writeVarInt
import de.kvxd.kmcprotocol.flushBlocking
import io.ktor.utils.io.*
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.primaryConstructor

class PacketCodec<T: MinecraftPacket<T>>(
    private val clazz: KClass<T>
) {

    private val fields = mutableListOf<Field<*>>()

    internal fun varInt(property: KMutableProperty0<Int>) {
        fields.add(Field(
            property.get(),
            encoder = { channel, value ->
                channel.writeVarInt(value)
                channel.flushBlocking()
            },

            decoder = { channel ->
                channel.readVarInt()
            }
        ))
    }

    internal fun string(property: KMutableProperty0<String>) {
        fields.add(Field(
            property.get(),
            encoder = { channel, value ->
                channel.writeString(value)
                channel.flushBlocking()
            },

            decoder = { channel ->
                channel.readString()
            }
        ))
    }

    fun encode(channel: ByteWriteChannel) {
        fields.forEach { field: Field<*> ->
            @Suppress("UNCHECKED_CAST")
            (field.encoder as (ByteWriteChannel, Any) -> Unit)(channel, field.value!!)
        }
    }

    fun decode(channel: ByteReadChannel): T {
        val decodedValues = mutableListOf<Any?>()

        fields.forEach { field ->
            val decodedValue = field.decoder(channel)
            decodedValues.add(decodedValue)
        }

        // Create an instance of T using the primary constructor and passing the decoded values
        val instance = if (clazz.primaryConstructor != null) {
            // If the class has a primary constructor, use it with the decoded values
            val constructor = clazz.primaryConstructor!!
            val parameters = constructor.parameters
            val args = List(parameters.size) { index -> decodedValues[index] }

            constructor.call(*args.toTypedArray())
        } else {
            // If no constructor exists, create the class using reflection and set the fields later
            clazz.createInstance().apply {
                fields.forEachIndexed { index, field ->
                    val decodedValue = decodedValues[index]

                    @Suppress("UNCHECKED_CAST")
                    (field.value as KMutableProperty0<Any>).set(decodedValue!!)
                }
            }
        }

        return instance
    }

    companion object {

        operator fun <T : MinecraftPacket<T>> invoke(
            clazz: KClass<T>,
            init: PacketCodec<T>.() -> Unit
        ): PacketCodec<T> = PacketCodec(clazz).apply(init)
    }

}

class Field<T>(internal var value: T, internal val encoder: (ByteWriteChannel, T) -> Unit = { _, _ -> throw IllegalStateException("Encoder for field was not defined") }, internal val decoder: (ByteReadChannel) -> T = { throw IllegalStateException("Decoder for field was not defined") })