package ink.anur.io

import ink.anur.common.Shutdownable
import ink.anur.config.InetConfig
import ink.anur.inject.NigateBean
import ink.anur.inject.NigateInject
import ink.anur.inject.NigatePostConstruct
import ink.anur.io.common.ShutDownHooker
import ink.anur.io.server.CoordinateServer
import java.util.concurrent.CountDownLatch

/**
 * Created by Anur IjuoKaruKas on 2020/2/22
 *
 * 集群内通讯、协调服务器操作类服务端，负责协调相关的业务
 */
@NigateBean
class ServerService : Shutdownable {

    /**
     * rpc 服务端
     */
    private lateinit var rpcServer: RpcServer

    @NigatePostConstruct
    private fun init() {
        val startLatch = CountDownLatch(1)
        this.rpcServer = RpcServer(ShutDownHooker(), startLatch)
        this.rpcServer.start()
        startLatch.await()
    }

    override fun shutDown() {
        rpcServer.shutDown()
    }
}