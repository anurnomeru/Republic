package ink.anur.core.server

import ink.anur.common.Shutdownable
import ink.anur.config.InetConfiguration
import ink.anur.inject.bean.NigateBean
import ink.anur.inject.bean.NigateInject
import ink.anur.inject.bean.NigatePostConstruct
import ink.anur.io.common.ShutDownHooker
import ink.anur.io.server.CoordinateServer

/**
 * Created by Anur IjuoKaruKas on 2020/2/22
 *
 * 集群内通讯、协调服务器操作类服务端，负责协调相关的业务
 */
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
        val sdh = ShutDownHooker("终止协调服务器的套接字接口 ${inetConfiguration.localServerPort} 的监听！")
        this.coordinateServer = CoordinateServer(inetConfiguration.localServerPort, sdh)
        coordinateServer.start()
    }

    override fun shutDown() {
        coordinateServer.shutDown()
    }
}