package org.kvxd.kmcprotocol.core

import org.kvxd.kmcprotocol.network.Direction
import kotlin.reflect.KClass

open class KmcProtocolException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class KmcPacketEncodeException(
    packetClass: KClass<out MinecraftPacket>,
    state: ProtocolState,
    details: String,
    cause: Throwable? = null,
) : KmcProtocolException(
    """
    Failed to encode packet ${packetClass.qualifiedName}.
    State: $state
    Details: $details
    """.trimIndent(),
    cause,
)

class KmcPacketDecodeException(
    state: ProtocolState,
    direction: Direction,
    packetId: Int?,
    frameLength: Int?,
    details: String,
    cause: Throwable? = null,
) : KmcProtocolException(
    """
    Failed to decode Minecraft packet.
    State: $state
    Direction: $direction
    Packet id: ${packetId?.let { "0x${it.toString(16)}" } ?: "unknown"}
    Frame length: ${frameLength?.toString() ?: "unknown"}
    Details: $details
    """.trimIndent(),
    cause,
)

class KmcConnectionException(
    operation: String,
    endpoint: String,
    state: ProtocolState,
    direction: Direction,
    details: String,
    cause: Throwable? = null,
) : KmcProtocolException(
    """
    Connection failure while $operation.
    Endpoint: $endpoint
    State: $state
    Direction: $direction
    Details: $details
    """.trimIndent(),
    cause,
)
