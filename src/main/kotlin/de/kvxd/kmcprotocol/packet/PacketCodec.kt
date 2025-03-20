package de.kvxd.kmcprotocol.packet

import de.kvxd.kmcprotocol.flushBlocking
import io.ktor.utils.io.*
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

class PacketCodec<T : MinecraftPacket>(
    val encode: (T, ByteWriteChannel) -> Unit,
    val decode: (ByteReadChannel) -> T
) {

    fun encode(packet: MinecraftPacket, channel: ByteWriteChannel) {
        @Suppress("UNCHECKED_CAST")
        encode.invoke(packet as T, channel)
    }

    fun decode(channel: ByteReadChannel): MinecraftPacket =
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
        private val encoders = mutableListOf<(T, ByteWriteChannel) -> Unit>()
        private val decoders = mutableListOf<(ByteReadChannel) -> Any?>()

        internal fun addCodec(
            encoder: (T, ByteWriteChannel) -> Unit,
            decoder: (ByteReadChannel) -> Any?
        ) {
            encoders.add(encoder)
            decoders.add(decoder)
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
                        channel.flushBlocking()
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