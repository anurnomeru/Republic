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

    companion object {
        private const val AddrSizeOffset = OriginMessageOverhead
        private const val AddrSizeLength = 4
        private const val AddrOffset = AddrSizeOffset + AddrSizeLength
        private const val AddrLength = 0
        private const val Capacity = AddrOffset + AddrLength
    }

    private var addr: String

    constructor(addr: String) {
        this.addr = addr

        val bytes = addr.toByteArray(Charset.defaultCharset())
        val size = bytes.size

        init(Capacity + size, RequestTypeEnum.SYN_RESPONSE) {
            it.putInt(size)
            it.put(bytes)
        }
    }

    constructor(byteBuffer: ByteBuffer) {
        buffer = byteBuffer
        val size = byteBuffer.getInt(AddrSizeOffset)

        byteBuffer.position(Capacity)
        val bytes = ByteArray(size)
        byteBuffer.get(bytes)
        this.addr = String(bytes)

        byteBuffer.rewind()
    }

    fun getAddr(): String = addr

    override fun writeIntoChannel(channel: Channel) {
        val wrappedBuffer = Unpooled.wrappedBuffer(buffer)
        channel.write(wrappedBuffer)
    }

    override fun totalSize(): Int {
        return size()
    }
}