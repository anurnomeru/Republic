package ink.anur.io.client

import ink.anur.common.KanashiIOExecutors
import ink.anur.debug.Debugger
import ink.anur.debug.DebuggerLevel
import ink.anur.io.common.handler.*
import ink.anur.io.common.transport.ShutDownHooker
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.util.AttributeKey
import kotlinx.coroutines.ObsoleteCoroutinesApi
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import javax.annotation.concurrent.ThreadSafe
import kotlin.math.min

/**
 * Created by Anur IjuoKaruKas on 2020/2/23
 *
 * 可重连的客户端
 */
@ObsoleteCoroutinesApi
class ReConnectableClient(
    private val host: String, private val port: Int,
    private val shutDownHooker: ShutDownHooker,
    private val channelActiveHook: ((ChannelHandlerContext?) -> Unit)? = null,
    private val channelInactiveHook: ((ChannelHandlerContext?) -> Unit)? = null
) : Runnable {

    companion object {
        var sleepBackOff: Long = 200
    }

    private val logger = Debugger(this::class.java)

    private val reconnectLatch = CountDownLatch(1)

    override fun run() {
        val restartMission = Thread {
            try {
                reconnectLatch.await()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }

            if (shutDownHooker.isShutDown()) {
                logger.debug("Connection to the node $this is shutting down!")
            } else {

                Thread.sleep(sleepBackOff)
                KanashiIOExecutors.execute(
                    ReConnectableClient(
                        host,
                        port,
                        shutDownHooker,
                        channelActiveHook,
                        channelInactiveHook
                    )
                )
            }
        }

        KanashiIOExecutors.execute(restartMission)
        restartMission.name = "Client Restart... node $this"

        val group = NioEventLoopGroup()

        try {
            val bootstrap = Bootstrap()
            bootstrap.group(group)
                .channel(NioSocketChannel::class.java)
                .handler(object : ChannelInitializer<SocketChannel>() {

                    @Throws(Exception::class)
                    override fun initChannel(socketChannel: SocketChannel) {

                        socketChannel.pipeline()
                            .addLast(KanashiDecoder())
                            .addLast(ChannelActiveHandler(channelActiveHook, channelInactiveHook))
                            .addLast(ReconnectHandler(reconnectLatch))
                            .addLast(RequestMappingHandler())
                            .addLast(ErrorHandler())
                    }
                })

            val channelFuture = bootstrap.connect(host, port)
            channelFuture.addListener { future ->
                if (future.isSuccess) {
                    sleepBackOff = 200
                } else {
                    sleepBackOff = min(sleepBackOff + 200, 2000)
                    if (reconnectLatch.count == 1L) {
                        logger.trace("try connect to node $this but failed, try to re connect...")
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
                group.shutdownGracefully()
                    .sync()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }

    override fun toString(): String {
        return "ReConnectableClient(host='$host', port=$port)"
    }
}