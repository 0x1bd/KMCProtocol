package de.kvxd.kmcprotocol.network.server

import com.kvxd.eventbus.Event

data object ServerBound : Event
data class ServerError(val cause: Throwable) : Event

data class SessionConnected(val session: Server.ServerSession) : Event