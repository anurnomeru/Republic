package ink.anur.core

import ink.anur.common.KanashiExecutors
import ink.anur.config.InetConfiguration
import ink.anur.debug.Debugger
import ink.anur.inject.bean.Nigate
import ink.anur.inject.bean.NigateBean
import ink.anur.inject.bean.NigateInject
import ink.anur.inject.bean.NigatePostConstruct
import ink.anur.io.common.transport.Connection.Companion.getOrCreateConnection
import ink.anur.pojo.rpc.RpcProviderMapping
import ink.anur.pojo.rpc.RpcRegistration
import ink.anur.pojo.rpc.meta.RpcRegistrationMeta
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
                        val connection = nowConnectNode.getOrCreateConnection()
                        if (connection.waitForEstablished(5, TimeUnit.SECONDS)) {
                            logger.info("successful connect to server node $nowConnectNode, sending RPC registration...")

                            val sendAndWaitForResponse = connection.sendAndWaitForResponse(RpcRegistration(
                                    RpcRegistrationMeta(
                                            inetConfiguration.localNodeAddr,
                                            Nigate.getRpcBeanPath(),
                                            Nigate.getRpcInterfacePath()
                                    )
                            ), RpcProviderMapping::class.java)
                        } else {
                            logger.error("fail to connect with server node $nowConnectNode")
                        }

                        connection.destroyLicense.enable()
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