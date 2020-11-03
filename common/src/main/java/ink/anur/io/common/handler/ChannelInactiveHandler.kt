package ink.anur.io.common.handler

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter

/**
 * Created by Anur IjuoKaruKas on 2020/4/6
 */
class ChannelInactiveHandler(private val whatEver: ((ChannelHandlerContext) -> Unit)? = null) : ChannelInboundHandlerAdapter() {

    override fun channelInactive(ctx: ChannelHandlerContext) {
        super.channelInactive(ctx)
        whatEver?.invoke(ctx)
    }
}