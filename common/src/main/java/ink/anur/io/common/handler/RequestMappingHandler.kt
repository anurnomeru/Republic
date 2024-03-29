package ink.anur.io.common.handler

import ink.anur.io.common.transport.Connection.Companion.receive
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import kotlinx.coroutines.ObsoleteCoroutinesApi
import java.nio.ByteBuffer

/**
 * Created by Anur on 2020/10/12
 */
@ObsoleteCoroutinesApi
class RequestMappingHandler : SimpleChannelInboundHandler<ByteBuffer>() {

    override fun channelRead0(ctx: ChannelHandlerContext?, msg: ByteBuffer?) {
        if (ctx != null && msg != null) {
            ctx.receive(msg)
        }
    }
}


