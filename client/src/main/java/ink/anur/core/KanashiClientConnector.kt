package ink.anur.core

import ink.anur.common.struct.RepublicNode
import ink.anur.config.InetConfiguration
import ink.anur.debug.Debugger
import ink.anur.inject.bean.Nigate
import ink.anur.inject.bean.NigateBean
import ink.anur.inject.bean.NigateInject
import ink.anur.io.common.transport.Connection.Companion.getOrCreateConnection
import ink.anur.pojo.rpc.RpcRouteInfo
import ink.anur.pojo.rpc.RpcRegistration
import ink.anur.pojo.rpc.RpcRegistrationResponse
import ink.anur.pojo.rpc.meta.RpcRegistrationMeta
import ink.anur.rpc.RpcRouteInfoHandlerService
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Created by Anur IjuoKaruKas on 2020/4/8
 */
@NigateBean
class KanashiClientConnector {

    @NigateInject
    private lateinit var inetConfiguration: InetConfiguration

    @NigateInject
    private lateinit var rpcRouteInfoHandlerService: RpcRouteInfoHandlerService

    private val logger = Debugger(this::class.java)

    private var nowConnectCounting = Random(1).nextInt()

    @Volatile
    private var nowActiveNode: RepublicNode? = null

    fun ReportAndAcquireRpcRouteInfo() {
        if (nowActiveNode != null) {
            return
        }

        val cluster = inetConfiguration.cluster
        val size = cluster.size

        // when cluster valid, we should connect to one of server
        // after connect register to the server, when routeInfo change
        // the server will notify the changes
        //
        // we should send heart beat to server, if server get wrong,
        // there should be re connect a new server
        //
        while (true) {
            val nowIndex = nowConnectCounting % size
            nowConnectCounting++
            val nowConnectNode = cluster[nowIndex]
            try {

                /**
                 * 1. get or create a connection from server
                 * 2. send local RpcRegistration to server
                 * 3. start to handle route info from server
                 */

                val connection = nowConnectNode.getOrCreateConnection(true)
                if (connection.waitForSendLicense(inetConfiguration.timeoutMs, TimeUnit.SECONDS)) {
                    logger.info("successful connect to server node $nowConnectNode, sending RPC registration...")

                    runBlocking {
                        connection.sendAndWaitForResponse(
                            RpcRegistration(
                                RpcRegistrationMeta(
                                    inetConfiguration.localNodeAddr,
                                    Nigate.getRpcBeanPath(),
                                    Nigate.getRpcInterfacePath()
                                )
                            ), RpcRegistrationResponse::class.java
                        )
                            .await()
                            .ExceptionHandler { connection.destroy() }
                            .Resp()
                    }

                    logger.info("rpc successful registry to server node $nowConnectNode")
                    nowActiveNode = nowConnectNode

                    connection.registerDestroyHandler {
                        logger.info("the connection from $nowConnectNode is destroy")
                        nowActiveNode = null
                    }
                    return
                } else {
                    logger.error("fail to connect with server node $nowConnectNode")
                }

                logger.error("disconnected with server node $nowConnectNode:")
            } catch (e: Exception) {
                logger.error("error while contact with server node $nowConnectNode", e)
            }
        }
    }
}