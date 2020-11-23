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
        private const val AddrSizeOffset = RandomSeedOffset + RandomSeedLength
        private const val AddrSizeLength = 4
        private const val AddrOffset = AddrSizeOffset + AddrSizeLength
        private const val Capacity = AddrOffset
    }

    private val addr: String

    private val createdTs: Long

    private var randomSeed: Long

    constructor(addr: String, createdTs: Long, randomSeed: Long) {
        this.addr = addr
        this.createdTs = createdTs;
        this.randomSeed = randomSeed;

        val bytes = addr.toByteArray(Charset.defaultCharset())
        val size = bytes.size

        init(Capacity + size, RequestTypeEnum.SYN) {
            it.putLong(createdTs)
            it.putLong(randomSeed)
            it.putInt(size)
            it.put(bytes)
        }
    }

    constructor(byteBuffer: ByteBuffer) {
        buffer = byteBuffer
        this.createdTs = byteBuffer.getLong(CreatedTsOffset)
        this.randomSeed = byteBuffer.getLong(RandomSeedOffset)
        byteBuffer.getLong(AddrSizeOffset)
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

    /**
     * warning: if both of then are equal, will never establish
     * todo: fix it
     */
    fun allowConnect(createdTs: Long, randomSeed: Long): Boolean {
        val remoteCreatedTs = buffer.getLong(CreatedTsOffset)
        val remoteRandomSeed = buffer.getLong(RandomSeedOffset)
        return remoteCreatedTs > createdTs || (remoteCreatedTs == createdTs && remoteRandomSeed > randomSeed)
    }
}