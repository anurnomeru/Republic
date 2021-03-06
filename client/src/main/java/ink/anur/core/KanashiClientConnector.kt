package ink.anur.core

import ink.anur.common.KanashiExecutors
import ink.anur.config.InetConfiguration
import ink.anur.debug.Debugger
import ink.anur.inject.bean.Nigate
import ink.anur.inject.bean.NigateBean
import ink.anur.inject.bean.NigateInject
import ink.anur.inject.bean.NigatePostConstruct
import ink.anur.io.common.transport.Connection.Companion.getOrCreateConnection
import ink.anur.pojo.rpc.RpcRouteInfo
import ink.anur.pojo.rpc.RpcRegistration
import ink.anur.pojo.rpc.meta.RpcRegistrationMeta
import ink.anur.rpc.RpcPouteInfoHandlerService
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Created by Anur IjuoKaruKas on 2020/4/8
 *
 * 客户端连接获取者
 */
@NigateBean
class KanashiClientConnector {

    @NigateInject
    private lateinit var inetConfiguration: InetConfiguration

    @NigateInject
    private lateinit var rpcRouteInfoHandlerService: RpcPouteInfoHandlerService

    private val logger = Debugger(this::class.java)

    private var nowConnectCounting = Random(1).nextInt()

    @NigatePostConstruct
    fun connectTask() {
        val t = Thread {
            while (true) {
                val cluster = inetConfiguration.cluster
                val size = cluster.size

                while (true) {
                    val nowIndex = nowConnectCounting % size
                    nowConnectCounting++
                    val nowConnectNode = cluster[nowIndex]
                    try {
                        val connection = nowConnectNode.getOrCreateConnection(true)
                        if (connection.waitForSendLicense(5, TimeUnit.SECONDS)) {
                            logger.info("successful connect to server node $nowConnectNode, sending RPC registration...")

                            val rpcRouteInfo = connection.sendAndWaitForResponse(RpcRegistration(
                                    RpcRegistrationMeta(
                                            inetConfiguration.localNodeAddr,
                                            Nigate.getRpcBeanPath(),
                                            Nigate.getRpcInterfacePath()
                                    )
                            ), RpcRouteInfo::class.java)

                            if (rpcRouteInfo == null) {
                                connection.destroy()
                            } else {
                                logger.info("rpc successful registry to server node $nowConnectNode and getting latest route info")
                                rpcRouteInfoHandlerService.handlerRouteInfo(rpcRouteInfo)
                            }

                            connection.destroyLicense.license()

                        } else {
                            logger.error("fail to connect with server node $nowConnectNode")
                        }

                        logger.error("disconnected with server node $nowConnectNode:")
                    } catch (e: Exception) {
                        logger.error("error while contact with server node $nowConnectNode:", e)
                    }
                }

            }
        }
        t.name = "Server Connector"
        KanashiExecutors.execute(t)
    }
}