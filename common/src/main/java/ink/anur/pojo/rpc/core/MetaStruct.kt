package ink.anur.pojo.rpc.core

import ink.anur.pojo.common.AbstractStruct
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.util.HessianUtil
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import java.nio.ByteBuffer

abstract class MetaStruct<T : SerializableMeta> : AbstractStruct {

    val serializableMeta: T

    abstract fun requestTypeEnum(): RequestTypeEnum

    abstract fun metaClazz(): Class<T>

    constructor(serializableMeta: T) {
        this.serializableMeta = serializableMeta
        val ser = HessianUtil.ser(serializableMeta)
        init(OriginMessageOverhead + ser.size, requestTypeEnum()) {
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

        serializableMeta = HessianUtil.des(ba, metaClazz())
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