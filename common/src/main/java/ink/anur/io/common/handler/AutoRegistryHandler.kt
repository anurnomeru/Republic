package ink.anur.io.common.handler

import ink.anur.common.struct.KanashiNode
import ink.anur.config.InetConfig
import ink.anur.core.ConnectionManageService
import ink.anur.inject.Nigate
import ink.anur.inject.NigateInject
import ink.anur.pojo.Register
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import org.slf4j.LoggerFactory

/**
 * Created by Anur IjuoKaruKas on 2020/2/25
 *
 * 自动注册的处理器
 */
class AutoRegistryHandler(private val register: Register,
                          private val injectChannel: (Channel) -> Unit
) : ChannelInboundHandlerAdapter() {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @NigateInject
    private lateinit var connectionManageService: ConnectionManageService

    init {
        Nigate.injectOnly(this)
    }

    override fun channelActive(ctx: ChannelHandlerContext?) {
        super.channelActive(ctx)
        injectChannel.invoke(ctx!!.channel())
        val channel = ctx.channel()
        connectionManageService.doSend(channel, register)
    }
}