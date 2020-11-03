package ink.anur.pojo

import ink.anur.pojo.common.AbstractStruct
import ink.anur.pojo.common.RequestTypeEnum
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import java.nio.ByteBuffer

/**
 * Created by Anur IjuoKaruKas on 2020/2/24
 *
 * 注册的 response 里面是空的 只是回复一下
 */
open class RegisterResponse : AbstractStruct {

    companion object {
        val RegistrySignOffset = OriginMessageOverhead
        val RegistrySignLength = 8
        val Capacity = RegistrySignOffset + RegistrySignLength
    }

    constructor(registrySign: Long) {
        init(Capacity, RequestTypeEnum.SYN_RESPONSE) {
            it.putLong(registrySign)
        }
    }

    fun getRegistrySign(): Long {
        return buffer.getLong(Register.RegistrySignOffset)
    }

    constructor(byteBuffer: ByteBuffer) {
        buffer = byteBuffer
    }

    override fun writeIntoChannel(channel: Channel) {
        val wrappedBuffer = Unpooled.wrappedBuffer(buffer)
        channel.write(wrappedBuffer)
    }

    override fun totalSize(): Int {
        return size()
    }
}