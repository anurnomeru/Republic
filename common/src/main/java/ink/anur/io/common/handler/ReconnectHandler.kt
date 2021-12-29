package ink.anur.io.common.handler

import ink.anur.debug.Debugger
import ink.anur.debug.DebuggerLevel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch

/**
 * Created by Anur IjuoKaruKas on 2020/2/23
 *
 * 此处理器用于客户端重连
 */
class ReconnectHandler(private val reconnectLatch: CountDownLatch) : ChannelInboundHandlerAdapter() {
    private val logger = Debugger(this::class.java).switch(DebuggerLevel.INFO)

    @Throws(Exception::class)
    override fun channelActive(ctx: ChannelHandlerContext?) {
        super.channelActive(ctx)
    }

    @Throws(Exception::class)
    override fun channelInactive(ctx: ChannelHandlerContext?) {
        super.channelInactive(ctx)

        ctx?.close()
        reconnectLatch.countDown()
    }

    @Throws(Exception::class)
    override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable) {
        super.channelInactive(ctx)
        if (reconnectLatch.count == 1L) {
            logger.debug(
                "channel disconnect: [{}], try to reconnect...", ctx?.channel()
                    ?.remoteAddress() ?: "Unknown channel context"
            )
        }
        ctx?.close()?.let {
            logger.debug(
                "channel disconnect: [{}], cause by: {}", ctx.channel()
                    .remoteAddress(), cause.message
            )
        } ?: let {
            logger.debug(
                "Unknown channel disconnected"
            )
        }
    }
}