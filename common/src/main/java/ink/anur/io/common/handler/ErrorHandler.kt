package ink.anur.io.common.handler

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import org.slf4j.LoggerFactory

/**
 * Created by Anur IjuoKaruKas on 2020/2/22
 *
 * 将 channel 中发生的错误暴露出来
 */
class ErrorHandler : ChannelInboundHandlerAdapter() {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable?) {
        cause?.let {   logger.error("管道中出现了异常",it) }
    }
}