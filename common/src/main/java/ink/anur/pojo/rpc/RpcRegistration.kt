package ink.anur.pojo.rpc

import ink.anur.pojo.common.AbstractStruct
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.pojo.rpc.meta.RpcRegistrationMeta
import ink.anur.util.HessianUtil
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import java.nio.ByteBuffer

/**
 * Created by Anur IjuoKaruKas on 2020/4/9
 *
 * 所有客户端在连接上服务器后，必须发送自己的接口信息
 */
class RpcRegistration : AbstractStruct {

    val rpcRegistrationMeta: RpcRegistrationMeta

    constructor(rpcRegistrationMeta: RpcRegistrationMeta) {
        this.rpcRegistrationMeta = rpcRegistrationMeta
        val ser = HessianUtil.ser(rpcRegistrationMeta)
        init(OriginMessageOverhead + ser.size, RequestTypeEnum.RPC_REGISTRATION) {
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

        rpcRegistrationMeta = HessianUtil.des(ba, RpcRegistrationMeta::class.java)
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