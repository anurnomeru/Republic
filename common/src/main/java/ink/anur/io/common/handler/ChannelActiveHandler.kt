package ink.anur.io.common.handler

import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter

/**
 * Created by Anur IjuoKaruKas on 2020/2/25
 *
 * 连接启用管理器
 */
class ChannelActiveHandler(val doAfterChannelActive: ((Channel) -> Unit)?
) : ChannelInboundHandlerAdapter() {

    override fun channelActive(ctx: ChannelHandlerContext?) {
        super.channelActive(ctx)
        doAfterChannelActive?.invoke(ctx!!.channel())
    }
}