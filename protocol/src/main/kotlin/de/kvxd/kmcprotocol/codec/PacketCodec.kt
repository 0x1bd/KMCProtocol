package de.kvxd.kmcprotocol.codec

import de.kvxd.kmcprotocol.packet.MinecraftPacket
import io.ktor.utils.io.*
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.primaryConstructor

class PacketCodec<T : MinecraftPacket>(
    val encode: suspend (T, ByteWriteChannel) -> Unit,
    val decode: suspend (ByteReadChannel) -> T
) {

    suspend fun encode(packet: MinecraftPacket, channel: ByteWriteChannel) {
        @Suppress("UNCHECKED_CAST")
        encode.invoke(packet as T, channel)
    }

    suspend fun decode(channel: ByteReadChannel): MinecraftPacket =
        decode.invoke(channel)

    companion object {
        inline operator fun <reified T : MinecraftPacket> invoke(
            block: PacketCodecBuilder<T>.() -> Unit
        ): PacketCodec<T> {
            val builder = PacketCodecBuilder<T>(T::class)
            builder.block()
            return builder.build()
        }
    }

    class PacketCodecBuilder<T : MinecraftPacket>(private val clazz: KClass<T>) {
        private val encoders = mutableListOf<suspend (T, ByteWriteChannel) -> Unit>()
        private val decoders = mutableListOf<suspend (ByteReadChannel) -> Any?>()

        private fun <E> addCodec(
            encoder: suspend (T, ByteWriteChannel) -> Unit,
            decoder: suspend (ByteReadChannel) -> E
        ) {
            encoders.add(encoder)
            decoders.add(decoder)
        }

        fun <E> element(
            property: KProperty1<T, E>,
            codec: ElementCodec<E>
        ) {
            addCodec(
                encoder = { packet, channel -> codec.encode(channel, property.get(packet)) },
                decoder = { channel -> codec.decode(channel) }
            )
        }

        fun build(): PacketCodec<T> {
            val constructor = clazz.primaryConstructor
                ?: throw IllegalArgumentException("Class ${clazz.simpleName} has no primary constructor")

            if (constructor.parameters.size != decoders.size) {
                throw IllegalArgumentException("Codec steps don't match constructor parameters")
            }

            return PacketCodec(
                encode = { packet, channel ->
                    encoders.forEach {
                        it(packet, channel)
                    }
                },
                decode = { channel ->
                    val args = decoders.map { it(channel) }.toTypedArray()
                    constructor.call(*args)
                }
            )
        }
    }
}