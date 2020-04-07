package ink.anur.io.common.handler

import ink.anur.inject.Nigate
import ink.anur.io.common.channel.ChannelService
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import org.slf4j.LoggerFactory

/**
 * Created by Anur IjuoKaruKas on 2020/3/8
 */
class AutoUnRegistryHandler : ChannelInboundHandlerAdapter() {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @Throws(Exception::class)
    override fun channelInactive(ctx: ChannelHandlerContext) {
        super.channelInactive(ctx)
        val unRegister = Nigate.getBeanByClass(ChannelService::class.java).unRegister(ctx.channel())
        ctx.close()
        logger.error("节点 $unRegister 与本节点的连接已断开")
    }
}