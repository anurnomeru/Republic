package ink.anur.pojo.log

import ink.anur.pojo.common.AbstractStruct
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.pojo.log.meta.Proposal
import ink.anur.util.HessianUtil
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import java.nio.ByteBuffer

/**
 * Created by Anur IjuoKaruKas on 2/25/2019
 * <p>
 * LogItem 是对应最基本的操作，表示这个操作将被写入日志
 * <p>
 * 一个 logItem 由以下部分组成：
 * <p>
 * 　4　   +   4    +    4      + key +    4        +  v
 * CRC32  +  type  + keyLength + key + valueLength +  v
 */
open class LogItem : AbstractStruct {

    val proposal: Proposal

    constructor(proposal: Proposal) {
        this.proposal = proposal
        val ser = HessianUtil.ser(proposal)
        init(AbstractStruct.OriginMessageOverhead + ser.size, RequestTypeEnum.RPC_REGISTRATION) {
            it.put(ser)
        }
        reComputeCheckSum()
    }

    constructor(byteBuffer: ByteBuffer) {
        val limit = byteBuffer.limit()
        val position = AbstractStruct.OriginMessageOverhead

        this.buffer = byteBuffer
        val ba = ByteArray(limit - position)
        byteBuffer.mark()

        byteBuffer.position(position)
        byteBuffer.get(ba)

        proposal = HessianUtil.des(ba, Proposal::class.java)
        byteBuffer.reset()
    }

    override fun writeIntoChannel(channel: Channel) {
        val wrappedBuffer = Unpooled.wrappedBuffer(buffer)
        channel.write(wrappedBuffer)
    }

    override fun totalSize(): Int {
        return size()
    }
}