package ink.anur.io.common.handler

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
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Throws(Exception::class)
    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
    }

    @Throws(Exception::class)
    override fun channelInactive(ctx: ChannelHandlerContext) {
        super.channelInactive(ctx)

        ctx.close()
        reconnectLatch.countDown()
    }

    @Throws(Exception::class)
    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        super.channelInactive(ctx)
        if (reconnectLatch.count == 1L) {
            logger.debug("与节点 [{}] 的连接断开，准备进行重连 ...", ctx.channel()
                .remoteAddress())
        }
        ctx.close()
        logger.debug("与节点 [{}] 的连接断开，原因：{}", ctx.channel()
            .remoteAddress(), cause.message)
    }
}