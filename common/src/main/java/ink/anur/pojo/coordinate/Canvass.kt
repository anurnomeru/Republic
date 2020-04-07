package ink.anur.pojo.coordinate

import ink.anur.pojo.common.AbstractStruct
import ink.anur.pojo.common.RequestTypeEnum
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import java.nio.ByteBuffer

/**
 * Created by Anur IjuoKaruKas on 2020/2/26
 *
 * 投票 ~
 */
class Canvass : AbstractStruct {

    companion object {
        val GenerationOffset = OriginMessageOverhead
        val GenerationLength = 8
        val Capacity = GenerationOffset + GenerationLength
    }

    /**
     * 该选票的世代信息
     */
    var generation: Long = 0

    constructor(generation: Long) {
        this.generation = generation

        val byteBuffer = ByteBuffer.allocate(Capacity)
        init(byteBuffer, RequestTypeEnum.CANVASS)

        byteBuffer.putLong(generation)
        byteBuffer.flip()
    }

    constructor(byteBuffer: ByteBuffer) {
        buffer = byteBuffer
        byteBuffer.mark()
        byteBuffer.position(GenerationOffset)

        this.generation = byteBuffer.getLong()
        buffer!!.reset()
    }

    override fun writeIntoChannel(channel: Channel) {
        val wrappedBuffer = Unpooled.wrappedBuffer(buffer)
        channel.write(wrappedBuffer)
    }

    override fun totalSize(): Int {
        return size()
    }
}