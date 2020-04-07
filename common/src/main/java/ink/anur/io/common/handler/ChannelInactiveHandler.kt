package ink.anur.io.common.handler

import ink.anur.io.common.ShutDownHooker
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter

/**
 * Created by Anur IjuoKaruKas on 2020/4/6
 *
 * reconnect，判断是否需要在断开后重连
 */
class ChannelInactiveHandler(private val shutDownHooker: ShutDownHooker, private val reconnectWhileChannelInactive: (() -> Boolean)?) : ChannelInboundHandlerAdapter() {

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        if (reconnectWhileChannelInactive?.invoke() != false) {

        } else {
            shutDownHooker.shutdown()
        }
        super.channelInactive(ctx)
    }
}