package de.kvxd.kmcprotocol.network.server

import com.kvxd.eventbus.Event

class SrvServerBound : Event

class SrvSessionConnected(val session: Server.Session) : Event
