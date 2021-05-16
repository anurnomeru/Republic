package ink.anur.pojo.metastruct

import ink.anur.exception.KanashiException
import ink.anur.exception.codeabel_exception.CodeableException
import ink.anur.pojo.common.AbstractStruct
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.util.HessianUtil
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import java.nio.ByteBuffer

abstract class MetaStruct<T : SerializableMeta> : AbstractStruct {

    companion object {
        private const val ErrorCodeLength = 4
    }

    private val serializableMeta: T?
    private val errorCode: Int

    abstract fun requestTypeEnum(): RequestTypeEnum

    abstract fun metaClazz(): Class<T>

    fun GetMeta(): T {
        if (errorCode == 0) {
            return serializableMeta!!
        }
        throw CodeableException.Error(errorCode)
    }

    constructor(exception: Exception) {
        this.serializableMeta = null

        if (exception is CodeableException) {
            this.errorCode = exception.errorCode()
        } else {
            this.errorCode = 0
        }

        init(OriginMessageOverhead + ErrorCodeLength, requestTypeEnum()) {
            it.putInt(errorCode)
        }
    }

    constructor(serializableMeta: T) {
        this.serializableMeta = serializableMeta
        this.errorCode = 0

        val ser = HessianUtil.ser(serializableMeta)
        init(OriginMessageOverhead + ErrorCodeLength + ser.size, requestTypeEnum()) {
            it.putInt(errorCode)
            it.put(ser)
        }
    }

    constructor(byteBuffer: ByteBuffer) {
        byteBuffer.mark()

        val limit = byteBuffer.limit()
        this.buffer = byteBuffer
        this.errorCode = buffer.int

        if (errorCode == 0) {
            val position = OriginMessageOverhead + ErrorCodeLength
            val ba = ByteArray(limit - position)

            byteBuffer.position(position)
            byteBuffer.get(ba)

            this.serializableMeta = HessianUtil.des(ba, metaClazz())
        } else {
            this.serializableMeta = null
        }

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