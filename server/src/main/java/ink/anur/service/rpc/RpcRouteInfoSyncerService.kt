package ink.anur.service.rpc

import ink.anur.common.struct.RepublicNode
import ink.anur.debug.Debugger
import ink.anur.inject.bean.NigateBean
import ink.anur.io.common.transport.Connection.Companion.sendAsync
import ink.anur.pojo.rpc.RpcRouteInfo
import ink.anur.pojo.rpc.meta.RpcRouteInfoMeta
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Created by Anur IjuoKaruKas on 2022/1/10
 */
@NigateBean
class RpcRouteInfoSyncerService {

    private val logger = Debugger(this::class.java)
    private val deck = CopyOnWriteArraySet<RepublicNode>()

    @Volatile
    private var latestRoute = RpcRouteInfo(RpcRouteInfoMeta())

    fun NotifyMe(republicNode: RepublicNode) {
        deck.add(republicNode)
        doNotify(republicNode, latestRoute)
    }

    fun OutOfDeck(republicNode: RepublicNode) {
        deck.remove(republicNode)
        logger.debug("remote node $republicNode is out of route info syncer deck.")
    }

    fun UpdateRouteInfo(rpcRouteInfo: RpcRouteInfo) {
        latestRoute = rpcRouteInfo
        notifyAllNode()
    }

    private fun doNotify(republicNode: RepublicNode, rpcRouteInfo: RpcRouteInfo) {
        republicNode.sendAsync(rpcRouteInfo)
    }

    private fun notifyAllNode() {
        val routeForSend = latestRoute
        for (republicNode in deck) {
            doNotify(republicNode, routeForSend)
        }
    }


}