package ink.anur.pojo.common

import ink.anur.exception.ByteBufferValidationException
import ink.anur.util.ByteBufferUtil
import io.netty.channel.Channel
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by Anur IjuoKaruKas on 2020/2/22
 *
 * 一个 基础的数据 由以下部分组成：
 *
 * 　4　   +   4    + ...（子类自由扩展）
 * CRC32  +  type  + ...（子类自由扩展）
 *
 * 所有的指令都满足 4位CRC + 4位类型
 */
abstract class AbstractStruct {

    /**
     * 4字节 crc + 4字节类型 + 内容
     */
    companion object {
        private const val CrcOffset = 0
        private const val CrcLength = 4

        private const val RequestTypeOffset = CrcOffset + CrcLength
        private const val RequestTypeLength = 4

        private const val IdentifierOffset = RequestTypeOffset + RequestTypeLength
        private const val IdentifierSignLength = 4

        const val OriginMessageOverhead = IdentifierOffset + IdentifierSignLength

        private const val positive: Byte = 1
        private const val noneIdentifier: Int = 0
        private const val respIdentifierMask: Int = 1.shl(31)

        val identifierBoxer = AtomicInteger(Int.MIN_VALUE)

        fun translateToByte(boolean: Boolean): Byte {
            return if (boolean) {
                1
            } else {
                0
            }
        }

        fun translateToBool(byte: Byte): Boolean {
            return byte == positive
        }

        fun ByteBuffer.getRequestType(): RequestTypeEnum = RequestTypeEnum.parseByByteSign(this.getInt(RequestTypeOffset))

        fun ByteBuffer.getIdentifier(): Int = this.getInt(IdentifierOffset)
    }

    lateinit var buffer: ByteBuffer

    fun init(capacity: Int, requestTypeEnum: RequestTypeEnum, then: (ByteBuffer) -> Unit) {
        val bf = ByteBuffer.allocate(capacity)
        bf.mark()
        bf.position(RequestTypeOffset)
        bf.putInt(requestTypeEnum.byteSign) // type
        bf.putInt(noneIdentifier) // identifier
        then.invoke(bf)
        bf.reset()

        buffer = bf
    }

    fun size(): Int {
        return buffer.limit()
    }

    fun getRequestType(): RequestTypeEnum {
        return RequestTypeEnum.parseByByteSign(buffer.getInt(RequestTypeOffset))
    }

    fun ensureValid() {
        val stored = checkSum()
        val compute = computeChecksum()
        if (stored != compute) {
            throw ByteBufferValidationException()
        }
    }

    private fun checkSum(): Long {
        return ByteBufferUtil.readUnsignedInt(buffer, CrcOffset)
    }

    fun computeChecksum(): Long {
        return ByteBufferUtil.crc32(buffer.array(), buffer.arrayOffset() + RequestTypeOffset, buffer.limit() - RequestTypeOffset)
    }

    fun getIdentifier(): Int {
        return buffer.getInt(IdentifierOffset)
    }

    fun getRespIdentifier(): Int {
        return getIdentifier().or(respIdentifierMask)
    }

    fun isResp(): Boolean {
        return buffer.getInt(IdentifierOffset).and(respIdentifierMask) == respIdentifierMask
    }

    fun asResp(request: AbstractStruct): AbstractStruct {
        buffer.putInt(getRespIdentifier())
        return this
    }

    /**
     * 如何写入 Channel
     */
    abstract fun writeIntoChannel(channel: Channel)

    /**
     * 真正的 size，并不局限于维护的 buffer
     */
    abstract fun totalSize(): Int
}