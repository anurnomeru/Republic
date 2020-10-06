package ink.anur.pojo.common

import ink.anur.exception.ByteBufferValidationException
import ink.anur.util.ByteBufferUtil
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

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
        const val CrcOffset = 0
        private const val CrcLength = 4

        const val RequestTypeOffset = CrcOffset + CrcLength
        private const val RequestTypeLength = 4

        const val IdentifierOffset = RequestTypeOffset + RequestTypeLength
        private const val IdentifierSignLength = 4

        const val OriginMessageOverhead = IdentifierOffset + IdentifierSignLength

        private const val positive: Byte = 1
        val requestSignBoxer = AtomicInteger(Int.MIN_VALUE)

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

    }

    lateinit var buffer: ByteBuffer

    fun init(capacity: Int, requestTypeEnum: RequestTypeEnum, then: (ByteBuffer) -> Unit) {
        val bf = ByteBuffer.allocate(capacity)
        bf.mark()
        bf.position(RequestTypeOffset)
        bf.putInt(requestTypeEnum.byteSign) // type
        bf.putInt(requestSignBoxer.incrementAndGet()) // identifier
        then.invoke(bf)
        bf.reset()

        buffer = bf
    }

    fun asResponse(abstractStruct: AbstractStruct): AbstractStruct {
        buffer.putLong(IdentifierOffset, abstractStruct.buffer.getLong(IdentifierOffset))
        return this
    }

    fun size(): Int {
        return buffer.limit()
    }

    fun getIdentifier(): Int {
        return buffer.getInt(IdentifierOffset)
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

    /**
     * 如何写入 Channel
     */
    abstract fun writeIntoChannel(channel: Channel)

    /**
     * 真正的 size，并不局限于维护的 buffer
     */
    abstract fun totalSize(): Int
}