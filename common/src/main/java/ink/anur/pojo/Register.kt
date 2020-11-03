package ink.anur.pojo

import ink.anur.pojo.common.AbstractStruct
import ink.anur.pojo.common.RequestTypeEnum
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import java.nio.ByteBuffer
import java.nio.charset.Charset

/**
 * Created by Anur IjuoKaruKas on 2020/2/24
 *
 * 用于协调节点向 Leader 注册自己
 */
open class Register : AbstractStruct {

    companion object {
        val RegistrySignOffset = OriginMessageOverhead
        val RegistrySignLength = 8
        val SizeOffset = RegistrySignOffset + RegistrySignLength
        val SizeLength = 4
        val ContentOffset = SizeOffset + SizeLength
    }

    private var serverName: String

    constructor(serverName: String, registrySign: Long) {
        this.serverName = serverName

        val bytes = serverName.toByteArray(Charset.defaultCharset())
        val size = bytes.size

        init(ContentOffset + size, RequestTypeEnum.SYN) {
            it.putLong(registrySign)
            it.putInt(size)
            it.put(bytes)
        }
    }

    constructor(byteBuffer: ByteBuffer) {
        buffer = byteBuffer
        val size = byteBuffer.getInt(SizeOffset)

        byteBuffer.position(ContentOffset)
        val bytes = ByteArray(size)
        byteBuffer.get(bytes)
        this.serverName = String(bytes)

        byteBuffer.rewind()
    }

    fun getServerName(): String {
        return serverName
    }

    fun getRegistrySign(): Long {
        return buffer!!.getLong(RegistrySignOffset)
    }

    override fun writeIntoChannel(channel: Channel) {
        val wrappedBuffer = Unpooled.wrappedBuffer(buffer)
        channel.write(wrappedBuffer)
    }

    override fun totalSize(): Int {
        return size()
    }
}