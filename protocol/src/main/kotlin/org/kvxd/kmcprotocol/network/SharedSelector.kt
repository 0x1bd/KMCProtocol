package org.kvxd.kmcprotocol.network

import io.ktor.network.selector.SelectorManager
import kotlinx.coroutines.Dispatchers

object KmcSelector {

    val shared: SelectorManager by lazy { SelectorManager(Dispatchers.IO) }
}
