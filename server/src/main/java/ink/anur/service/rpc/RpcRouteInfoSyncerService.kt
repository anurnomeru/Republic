package ink.anur.service.rpc

import ink.anur.common.struct.RepublicNode
import ink.anur.debug.Debugger
import ink.anur.inject.bean.NigateBean
import ink.anur.io.common.transport.Connection.Companion.send
import ink.anur.io.common.transport.Connection.Companion.sendAndWaitingResponse
import ink.anur.io.common.transport.Connection.Companion.sendAsync
import ink.anur.pojo.rpc.RpcRouteInfo
import ink.anur.pojo.rpc.meta.RpcRegistrationMeta
import ink.anur.pojo.rpc.meta.RpcRouteInfoMeta
import kotlinx.coroutines.ObsoleteCoroutinesApi
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Created by Anur IjuoKaruKas on 2022/1/10
 */
@ObsoleteCoroutinesApi
@NigateBean
class RpcRouteInfoSyncerService {

    private val logger = Debugger(this::class.java)
    private val deck = CopyOnWriteArraySet<RepublicNode>()

    @Volatile
    private var latestRoute = RpcRouteInfo(RpcRouteInfoMeta())

    fun NotifyMe(republicNode: RepublicNode) {
        deck.add(republicNode)
    }

    fun OutOfDeck(republicNode: RepublicNode) {
        deck.remove(republicNode)
        logger.debug("remote node $republicNode is out of route info syncer deck.")
    }

    fun RemoveFromRouteInfo(republicNode: RepublicNode) {
        synchronized(this) {
            val meta = latestRoute.GetMeta()
            val outerIter = meta.providerMapping.iterator()

            outerIter.forEachRemaining { entry ->
                val iterator = entry.value.iterator()
                iterator.forEachRemaining {
                    it.value.remove(republicNode.addr)
                    if (it.value.isEmpty()) {
                        iterator.remove()
                    }
                }

                if (entry.value.isEmpty()){
                    outerIter.remove()
                }
            }

            logger.info("$RepublicNode is offline")
            logger.info(meta.StringInfo())
        }



        notifyAllNode()
    }

    fun UpdateRouteInfo(republicNode: RepublicNode, rpcRegistrationMeta: RpcRegistrationMeta) {
        synchronized(this) {
            val meta = latestRoute.GetMeta()
            for (entry in rpcRegistrationMeta.RPC_BEAN) {
                val bean = entry.key
                val methodSigns = entry.value
                if (meta.providerMapping[bean] == null) {
                    meta.providerMapping[bean] = mutableMapOf()
                }

                val m: MutableMap<String /* methodSign */, MutableSet<String/* localNodeAddr */>> =
                    meta.providerMapping[bean]!!

                methodSigns.forEach {
                    if (m[it] == null) {
                        m[it] = mutableSetOf(republicNode.addr)
                    } else {
                        m[it]!!.add(republicNode.addr)
                    }
                }
            }

            logger.info("$RepublicNode start to provide services")
            logger.info(meta.StringInfo())
        }

        notifyAllNode()
    }

    private fun doNotify(republicNode: RepublicNode, rpcRouteInfo: RpcRouteInfo) {
        republicNode.send(rpcRouteInfo)
    }

    private fun notifyAllNode() {
        val routeForSend: RpcRouteInfo
        synchronized(this) {
            routeForSend = RpcRouteInfo(latestRoute.buffer)
        }

        for (republicNode in deck) {
            doNotify(republicNode, routeForSend)
        }
    }
}