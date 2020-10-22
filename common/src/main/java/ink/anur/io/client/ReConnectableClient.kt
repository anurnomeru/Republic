package ink.anur.io.client

import ink.anur.common.KanashiIOExecutors
import ink.anur.io.common.handler.ChannelActiveHandler
import ink.anur.io.common.handler.ErrorHandler
import ink.anur.io.common.handler.KanashiDecoder
import ink.anur.io.common.handler.ReconnectHandler
import ink.anur.io.common.transport.ShutDownHooker
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import javax.annotation.concurrent.ThreadSafe

/**
 * Created by Anur IjuoKaruKas on 2020/2/23
 *
 * 可重连的客户端
 */
class ReConnectableClient(private val host: String, private val port: Int,

                          private val connectLicense: License,

                          private val shutDownHooker: ShutDownHooker,

                          /**
                           * 当受到对方的注册回调后，触发此函数，注意 它可能会被多次调用
                           */
                          private val doAfterConnectToServer: ((ChannelHandlerContext) -> Unit)? = null) : Runnable {

    private val logger = LoggerFactory.getLogger(this::class.java)

    private val reconnectLatch = CountDownLatch(1)

    @ThreadSafe
    class License {
        private val semaphore = Semaphore(0)

        fun hasLicense() = semaphore.availablePermits() > 0

        fun license() = semaphore.acquire()

        fun disable() = semaphore.drainPermits()

        fun enable() = semaphore.release()
    }

    override fun run() {
        val restartMission = Thread {
            try {
                reconnectLatch.await()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }

            if (shutDownHooker.isShutDown()) {
                logger.debug("与节点 {$host:$port} 的连接已被终止，无需再进行重连")
            } else {
                logger.trace("正在重新连接节点 {$host:$port} ...")
                KanashiIOExecutors.execute(ReConnectableClient(host, port, connectLicense, shutDownHooker, doAfterConnectToServer))
            }
        }


        KanashiIOExecutors.execute(restartMission)
        restartMission.name = "Client Restart {$host:$port}"

        val group = NioEventLoopGroup()

        try {
            val bootstrap = Bootstrap()
            bootstrap.group(group)
                    .channel(NioSocketChannel::class.java)
                    .handler(object : ChannelInitializer<SocketChannel>() {

                        @Throws(Exception::class)
                        override fun initChannel(socketChannel: SocketChannel) {
                            socketChannel.pipeline()
                                    .addLast(KanashiDecoder())// 解码处理器
                                    .addLast(ChannelActiveHandler(doAfterConnectToServer))
                                    .addLast(ReconnectHandler(reconnectLatch))// 重连控制器
                                    .addLast(ErrorHandler())// 错误处理
                        }
                    })

            connectLicense.license()
            val channelFuture = bootstrap.connect(host, port)
            channelFuture.addListener { future ->
                if (!future.isSuccess) {
                    if (reconnectLatch.count == 1L) {
                        logger.trace("连接节点 {$host:$port} 失败，准备进行重连 ...")
                    }
                    reconnectLatch.countDown()
                }
            }

            shutDownHooker.shutDownRegister { group.shutdownGracefully() }

            channelFuture.channel()
                    .closeFuture()
                    .sync()
        } catch (e: Throwable) {
            throw e
        } finally {
            try {
                logger.error("shutdown finally")
                group.shutdownGracefully()
                        .sync()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }
}