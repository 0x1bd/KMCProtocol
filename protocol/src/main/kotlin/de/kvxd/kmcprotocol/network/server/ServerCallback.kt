package de.kvxd.kmcprotocol.network.server

open class ServerCallback {

    open fun onBound() {}
    open fun onClose() {}
    open fun onSessionConnected(session: ServerSession) {}
    open fun onError(cause: Throwable) {
        cause.printStackTrace()
    }

}
