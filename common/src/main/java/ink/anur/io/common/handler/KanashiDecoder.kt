package ink.anur.io.common.handler

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import java.nio.ByteBuffer

/**
 * Created by Anur IjuoKaruKas on 2020/2/22
 *
 * 通用的解码器 格式为 32 位的length + body
 */
class KanashiDecoder : ByteToMessageDecoder() {

    override fun decode(ctx: ChannelHandlerContext?, buffer: ByteBuf?, list: MutableList<Any>) {
        if (ctx != null && buffer != null) {
            decode(ctx, buffer)
                    ?.let { list.add(it) }
        }
    }

    private fun decode(ctx: ChannelHandlerContext?, buffer: ByteBuf): ByteBuffer? {
        buffer.markReaderIndex()
        val maybeLength = buffer.readInt()
        val remain = buffer.readableBytes()

        return if (remain < maybeLength) {
            buffer.resetReaderIndex()
            null
        } else {
            val resultOne = ByteBuffer.allocate(maybeLength)
            buffer.readBytes(resultOne)
            resultOne.rewind()
            resultOne
        }
    }
}