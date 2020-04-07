package ink.anur.io.common.handler

import ink.anur.common.pool.EventDriverPool
import ink.anur.common.struct.Request
import ink.anur.pojo.common.AbstractStruct
import ink.anur.pojo.common.RequestTypeEnum
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

/**
 * Created by Anur IjuoKaruKas on 2020/3/1
 *
 * 将收到的消息扔进 EventDriverPool，再给到 CoordinateMessageService 消费
 */
class EventDriverPoolHandler : SimpleChannelInboundHandler<ByteBuffer>() {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun channelRead0(ctx: ChannelHandlerContext?, msg: ByteBuffer?) {
        if (ctx != null && msg != null) {
            var sign = 0
            try {
                sign = msg.getInt(AbstractStruct.TypeOffset)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val typeEnum = RequestTypeEnum.parseByByteSign(sign)

            if (typeEnum != RequestTypeEnum.HEAT_BEAT) {
                logger.trace("<--- 收到了类型为 $typeEnum 的消息")
            }
            EventDriverPool.offer(Request(msg, typeEnum, ctx.channel()))
        } else {
            logger.error("Channel HandlerContext or its byte buffer in pipeline is null")
        }
    }
}