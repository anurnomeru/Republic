package ink.anur.io.client

import ink.anur.common.KanashiExecutors
import ink.anur.common.struct.KanashiNode
import ink.anur.inject.Nigate
import ink.anur.io.common.ShutDownHooker
import ink.anur.io.common.handler.AutoRegistryHandler
import ink.anur.io.common.handler.ChannelInactiveHandler
import ink.anur.io.common.handler.EventDriverPoolHandler
import ink.anur.io.common.handler.ErrorHandler
import ink.anur.io.common.handler.KanashiDecoder
import ink.anur.io.common.handler.ReconnectHandler
import ink.anur.service.RegisterResponseHandleService
import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch

/**
 * Created by Anur IjuoKaruKas on 2020/2/23
 *
 * 可重连的客户端
 */
class ReConnectableClient(private val node: KanashiNode, private val shutDownHooker: ShutDownHooker,

                          private val injectChannel: (Channel) -> Unit,

                          /**
                           * 当受到对方的注册回调后，触发此函数，注意 它可能会被多次调用
                           */
                          private val doAfterConnectToServer: (() -> Unit)? = null,

                          /**
                           * 当连接上对方后，如果断开了连接，做什么处理
                           *
                           * 返回 true 代表继续重连
                           * 返回 false 则不再重连
                           */
                          private val doAfterDisConnectToServer: (() -> Boolean)? = null) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    private val reconnectLatch = CountDownLatch(1)

    val registrySign: Long = Nigate.getBeanByClass(RegisterResponseHandleService::class.java).registerCallBack { doAfterConnectToServer }

    fun start() {

        val restartMission = Thread {
            try {
                reconnectLatch.await()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }

            if (shutDownHooker.isShutDown()) {
                logger.debug("与节点 $node 的连接已被终止，无需再进行重连")
            } else {
                logger.trace("正在重新连接节点 $node ...")
                ReConnectableClient(node, shutDownHooker, injectChannel, doAfterConnectToServer, doAfterDisConnectToServer).start()
            }
        }
        KanashiExecutors.execute(restartMission)
        restartMission.name = "Client Restart $node"

        val group = NioEventLoopGroup()

        try {
            val bootstrap = Bootstrap()
            bootstrap.group(group)
                .channel(NioSocketChannel::class.java)
                .handler(object : ChannelInitializer<SocketChannel>() {

                    @Throws(Exception::class)
                    override fun initChannel(socketChannel: SocketChannel) {
                        socketChannel.pipeline()
                            .addFirst(AutoRegistryHandler(node, registrySign, injectChannel)) // 自动注册到管道管理服务
                            .addLast(KanashiDecoder())// 解码处理器
                            .addLast(EventDriverPoolHandler())// 消息事件驱动
                            .addFirst(ChannelInboundHandlerAdapter())
                            .addLast(ChannelInactiveHandler(shutDownHooker, doAfterDisConnectToServer))
                            .addLast(ReconnectHandler(reconnectLatch))// 重连控制器
                            .addLast(ErrorHandler())// 错误处理
                    }
                })

            val channelFuture = bootstrap.connect(node.host, node.port)
            channelFuture.addListener { future ->
                if (!future.isSuccess) {
                    if (reconnectLatch.count == 1L) {
                        logger.trace("连接节点 $node 失败，准备进行重连 ...")
                    }
                    reconnectLatch.countDown()
                }
            }

            shutDownHooker.shutDownRegister { group.shutdownGracefully() }

            channelFuture.channel()
                .closeFuture()
                .sync()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        } finally {
            try {
                group.shutdownGracefully()
                    .sync()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }
}