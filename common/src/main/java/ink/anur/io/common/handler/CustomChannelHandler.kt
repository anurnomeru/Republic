package ink.anur.io.common.handler

import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter

/**
 * Created by Anur IjuoKaruKas on 2020/4/6
 *
 * 当连接上对方后，如果断开了连接，做什么处理
 *
 * 返回 true 代表继续重连
 * 返回 false 则不再重连
 */
class CustomChannelHandler(private val doAfterConnectToServer: ((Channel) -> Unit)? = null, private val reconnectWhileChannelInactive: (() -> Unit)?) : ChannelInboundHandlerAdapter() {

    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        doAfterConnectToServer?.invoke(ctx.channel())
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        reconnectWhileChannelInactive?.invoke()
        super.channelInactive(ctx)
    }
}