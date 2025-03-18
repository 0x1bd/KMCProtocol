package de.kvxd.kmcprotocol.packet

interface MinecraftPacket<T: MinecraftPacket<T>> {

    val codec: PacketCodec<T>

}