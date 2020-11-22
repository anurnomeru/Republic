package ink.anur.io.common.handler

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter

/**
 * Created by Anur IjuoKaruKas on 2020/4/6
 */
class ChannelActiveHandler(
        private val channelActiveHook: ((ChannelHandlerContext) -> Unit)? = null,
        private val channelInactiveHook: ((ChannelHandlerContext) -> Unit)? = null) : ChannelInboundHandlerAdapter() {

    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        channelActiveHook?.invoke(ctx)
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        super.channelInactive(ctx)
        channelInactiveHook?.invoke(ctx)
    }
}