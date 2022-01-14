package ink.anur.service.rpc

import ink.anur.common.struct.RepublicNode
import ink.anur.core.common.AbstractRequestMapping
import ink.anur.debug.Debugger
import ink.anur.inject.bean.NigateBean
import ink.anur.io.common.transport.Connection.Companion.registerDestroyHandler
import ink.anur.io.common.transport.Connection.Companion.send
import ink.anur.io.common.transport.Connection.Companion.sendAndWaitingResponse
import ink.anur.io.common.transport.Connection.Companion.sendAsync
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.pojo.rpc.Ok
import ink.anur.pojo.rpc.RpcRegistration
import ink.anur.pojo.rpc.RpcRegistrationReport
import ink.anur.pojo.rpc.RpcRouteInfo
import ink.anur.pojo.rpc.meta.RpcRegistrationMeta
import ink.anur.pojo.rpc.meta.RpcRouteInfoMeta
import kotlinx.coroutines.ObsoleteCoroutinesApi
import java.nio.ByteBuffer
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

    fun NotifyMeWhenChanged(republicNode: RepublicNode) {
        if (deck.add(republicNode)) {
            republicNode.registerDestroyHandler {
                DontMissMe(republicNode)
            }
        }
    }

    private fun DontMissMe(republicNode: RepublicNode) {
        logger.debug("remote node $republicNode is out of route info syncer deck.")
        deck.remove(republicNode)
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

                if (entry.value.isEmpty()) {
                    outerIter.remove()
                }
            }

            logger.info("$republicNode is offline")
            logger.info(meta.StringInfo())
        }

        notifyAllNode()
    }

    fun UpdateRouteInfo(rpcRegistrationMeta: RpcRegistrationMeta, listenBy: RepublicNode) {
        synchronized(this) {
            val meta = latestRoute.GetMeta()
            val registerFun: (Map<String/* bean */, List<HashSet<String /* method */>>>) -> Unit = { map ->
                for (entry in map) {
                    val bean = entry.key
                    val methodSignss = entry.value
                    if (meta.providerMapping[bean] == null) {
                        meta.providerMapping[bean] = mutableMapOf()
                    }

                    val m: MutableMap<String /* methodSign */, MutableSet<String/* localNodeAddr */>> =
                        meta.providerMapping[bean]!!

                    for (methodSigns in methodSignss) {
                        for (methodSign in methodSigns) {
                            if (m[methodSign] == null) {
                                m[methodSign] = mutableSetOf(rpcRegistrationMeta.provider.addr)
                            } else {
                                m[methodSign]!!.add(rpcRegistrationMeta.provider.addr)
                            }
                        }
                    }
                }
            }

            registerFun(rpcRegistrationMeta.RPC_INTERFACE_BEAN)

            logger.info("${rpcRegistrationMeta.provider} start to provide services")
            logger.info(meta.StringInfo())
        }

        listenBy.registerDestroyHandler {
            RemoveFromRouteInfo(rpcRegistrationMeta.provider)
        }

        notifyAllNode()
    }

    fun UpdateRouteInfo(rpcRouteInfo: RpcRouteInfo) {
        synchronized(this) {
            latestRoute = rpcRouteInfo
            logger.info(rpcRouteInfo.GetMeta().StringInfo())
        }

        notifyAllNode()
    }

    private fun doNotify(republicNode: RepublicNode, rpcRouteInfo: RpcRouteInfo) {
        republicNode.send(rpcRouteInfo)
    }

    private fun notifyAllNode() {
        val routeForSend: RpcRouteInfo
        synchronized(this) {
            routeForSend = RpcRouteInfo(RpcRouteInfo(latestRoute.GetMeta()).buffer)
        }

        for (republicNode in deck) {
            doNotify(republicNode, routeForSend)
        }
    }
}