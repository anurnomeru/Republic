package ink.anur.connector

import ink.anur.common.KanashiExecutors
import ink.anur.common.KanashiRunnable
import ink.anur.config.ClientInetSocketAddressConfiguration
import ink.anur.core.client.ClientOperateHandler
import ink.anur.debug.Debugger
import ink.anur.inject.NigateAfterBootStrap
import ink.anur.inject.NigateBean
import ink.anur.inject.NigateInject
import ink.anur.inject.NigatePostConstruct
import java.util.concurrent.CountDownLatch
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
    private lateinit var inetSocketAddressConfiguration: ClientInetSocketAddressConfiguration

    private val debugger = Debugger(this::class.java)

    /**
     * 避免每次连接都从 0 开始
     */
    private var nowConnectCounting = Random(1).nextInt()

    @Volatile
    private var connection: ClientOperateHandler? = null

    @NigateAfterBootStrap
    fun connectToServer() {
        KanashiExecutors.execute(Runnable {
            val cluster = inetSocketAddressConfiguration.getCluster()
            val size = cluster.size

            while (true) {
                val nowIndex = nowConnectCounting % size
                nowConnectCounting++
                val connectLatch = CountDownLatch(1)
                val nowConnectNode = cluster[nowIndex]

                debugger.info("正在向服务器 $nowConnectNode 发起连接")
                val connect = ClientOperateHandler(nowConnectNode,
                    {
                        println("????")
                        connectLatch.countDown()
                    },
                    {
                        println("????+1")
                        debugger.info("与服务器 $nowConnectNode 的连接已经断开")
                        connectToServer()
                        false
                    })

                connect.start()
                if (connectLatch.await(10, TimeUnit.SECONDS)) {
                    connection = connect
                    debugger.info("与服务器 $nowConnectNode 建立连接")
                    break
                } else {
                    debugger.info("与服务器 $nowConnectNode 的连接超时")
                }
            }
        })
    }

}