package ink.anur.pojo

import ink.anur.pojo.common.AbstractStruct
import ink.anur.pojo.common.RequestTypeEnum
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import java.nio.ByteBuffer
import java.nio.charset.Charset

/**
 * Created by Anur on 2020/10/15
 */
class SynResponse : AbstractStruct {

    constructor() {
        init(OriginMessageOverhead, RequestTypeEnum.SYN_RESPONSE) {
        }
    }

    constructor(byteBuffer: ByteBuffer) {
        buffer = byteBuffer
    }

    override fun writeIntoChannel(channel: Channel) {
        val wrappedBuffer = Unpooled.wrappedBuffer(buffer)
        channel.write(wrappedBuffer)
    }

    override fun totalSize(): Int {
        return size()
    }
}