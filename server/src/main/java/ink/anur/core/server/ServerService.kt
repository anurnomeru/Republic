package ink.anur.core.server

import ink.anur.common.Shutdownable
import ink.anur.config.InetConfiguration
import ink.anur.inject.bean.NigateBean
import ink.anur.inject.bean.NigateInject
import ink.anur.inject.bean.NigatePostConstruct
import ink.anur.io.common.transport.ShutDownHooker
import ink.anur.io.server.CoordinateServer
import kotlinx.coroutines.ObsoleteCoroutinesApi

/**
 * Created by Anur IjuoKaruKas on 2020/2/22
 *
 * 集群内通讯、协调服务器操作类服务端，负责协调相关的业务
 */
@ObsoleteCoroutinesApi
@NigateBean
class ServerService : Shutdownable {

    @NigateInject
    private lateinit var inetConfiguration: InetConfiguration

    /**
     * 协调服务端
     */
    private lateinit var coordinateServer: CoordinateServer

    @NigatePostConstruct
    private fun init() {
        val sdh = ShutDownHooker()
        this.coordinateServer = CoordinateServer(inetConfiguration.localNode.host, inetConfiguration.localNode.port, sdh)
        coordinateServer.start()
    }

    override fun shutDown() {
        coordinateServer.shutDown()
    }
}