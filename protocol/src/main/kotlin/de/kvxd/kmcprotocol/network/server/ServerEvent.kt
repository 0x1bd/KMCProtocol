package de.kvxd.kmcprotocol.network.server

sealed class ServerEvent {
    data object ServerBound : ServerEvent()
    data class ServerError(val cause: Throwable) : ServerEvent()

    data class SessionConnected(val session: Server.ServerSession) : ServerEvent()
}
