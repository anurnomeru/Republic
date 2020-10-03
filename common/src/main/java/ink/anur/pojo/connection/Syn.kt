package ink.anur.pojo.connection

import ink.anur.pojo.common.AbstractStruct
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.util.TimeUtil
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import java.nio.ByteBuffer
import java.nio.charset.Charset

/**
 * Created by Anur on 2020/9/21
 *
 * Syn 代表连接发起，此时不可向对端发送消息，也不可接收对端消息
 */
open class Syn : AbstractStruct {

    companion object {
        val TsOffset = OriginMessageOverhead
        val TsLength = 8
        val ContentOffset = TsOffset + TsLength
    }

    private var ts: Long

    constructor(ts: Long) {
        this.ts = ts
        init(ContentOffset, RequestTypeEnum.REGISTER) {
            it.putLong(ts)
        }
    }

    constructor(byteBuffer: ByteBuffer) {
        buffer = byteBuffer

        byteBuffer.position(OriginMessageOverhead)
        this.ts = byteBuffer.long
        byteBuffer.rewind()
    }

    fun getTs(): Long {
        return ts
    }

    override fun writeIntoChannel(channel: Channel) {
        val wrappedBuffer = Unpooled.wrappedBuffer(buffer)
        channel.write(wrappedBuffer)
    }

    override fun totalSize(): Int {
        return size()
    }
}