package ink.anur.log

import ink.anur.pojo.common.AbstractStruct
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.pojo.metastruct.SerializableMeta
import ink.anur.util.HessianUtil
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import java.nio.ByteBuffer

/**
 * Created by Anur on 2021/1/18
 */
abstract class Operation : AbstractStruct {

    companion object {
        const val MinMessageOverhead = OriginMessageOverhead + 1
    }

    private val serializableMeta: SerializableMeta

    abstract fun metaClazz(): Class<*>

    constructor(serializableMeta: SerializableMeta, requestTypeEnum: RequestTypeEnum) {
        this.serializableMeta = serializableMeta
        val ser = HessianUtil.ser(serializableMeta)
        init(OriginMessageOverhead + ser.size, requestTypeEnum) {
            it.put(ser)
        }
    }

    constructor(byteBuffer: ByteBuffer) {
        val limit = byteBuffer.limit()
        val position = OriginMessageOverhead

        this.buffer = byteBuffer
        val ba = ByteArray(limit - position)
        byteBuffer.mark()

        byteBuffer.position(position)
        byteBuffer.get(ba)

        serializableMeta = HessianUtil.des(ba, metaClazz().javaClass)
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