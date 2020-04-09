package ink.anur.pojo.rpc

import ink.anur.pojo.common.AbstractStruct
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.util.HessianUtil
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import java.nio.ByteBuffer

/**
 * Created by Anur IjuoKaruKas on 2020/4/9
 *
 * 所有客户端在连接上服务器后，必须发送自己的接口信息，的回复
 */
class RpcRegistrationResponse : AbstractStruct {

    constructor(SIGN: Int) {
        init(OriginMessageOverhead + 4, RequestTypeEnum.RPC_REGISTRATION) {
            it.putInt(SIGN)
        }
    }

    constructor(byteBuffer: ByteBuffer) {
        this.buffer = byteBuffer
    }

    fun getSign(): Int {
        return this.getByteBuffer()!!.getInt(OriginMessageOverhead)
    }

    override fun writeIntoChannel(channel: Channel) {
        val wrappedBuffer = Unpooled.wrappedBuffer(buffer)
        channel.write(wrappedBuffer)
    }

    override fun totalSize(): Int {
        return size()
    }
}