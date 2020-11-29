package ink.anur.pojo

import ink.anur.pojo.common.AbstractStruct
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.util.TimeUtil
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.concurrent.ThreadLocalRandom

/**
 * Created by Anur on 2020/10/15
 */
class Syn : AbstractStruct {

    companion object {
        private const val CreatedTsOffset = OriginMessageOverhead
        private const val CreatedTsLength = 8
        private const val RandomSeedOffset = CreatedTsOffset + CreatedTsLength
        private const val RandomSeedLength = 8
        private const val ClientModeOffset = RandomSeedOffset + RandomSeedLength
        private const val ClientModeLength = 1
        private const val AddrSizeOffset = ClientModeOffset + ClientModeLength
        private const val AddrSizeLength = 4
        private const val AddrOffset = AddrSizeOffset + AddrSizeLength
        private const val Capacity = AddrOffset
    }

    private val addr: String

    private val createdTs: Long

    private val randomSeed: Long

    private val clientMode: Boolean

    constructor(addr: String, createdTs: Long, randomSeed: Long, clientMode: Boolean = false) {
        this.addr = addr
        this.createdTs = createdTs
        this.randomSeed = randomSeed
        this.clientMode = clientMode

        val bytes = addr.toByteArray(Charset.defaultCharset())
        val size = bytes.size

        init(Capacity + size, RequestTypeEnum.SYN) {
            it.putLong(createdTs)
            it.putLong(randomSeed)
            it.put(translateToByte(clientMode))
            it.putInt(size)
            it.put(bytes)
        }
    }

    constructor(byteBuffer: ByteBuffer) {
        buffer = byteBuffer
        this.createdTs = byteBuffer.getLong(CreatedTsOffset)
        this.randomSeed = byteBuffer.getLong(RandomSeedOffset)
        this.clientMode = translateToBool(byteBuffer.get(ClientModeOffset))
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

    fun clientMode() = clientMode

    fun allowConnect(createdTs: Long, randomSeed: Long, addr: String): Boolean {
        return compare(this.createdTs, createdTs) {
            compare(this.randomSeed, randomSeed) {
                compare(this.addr.hashCode().toLong(), addr.hashCode().toLong()) {
                    throw UnsupportedOperationException()
                }
            }
        }
    }

    private fun compare(long1: Long, long2: Long, ifEq: () -> Boolean): Boolean {
        return when {
            long1 > long2 -> {
                true
            }
            long1 == long2 -> {
                ifEq.invoke()
            }
            else -> {
                false
            }
        }
    }
}