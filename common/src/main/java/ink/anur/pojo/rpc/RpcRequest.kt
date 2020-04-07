package ink.anur.pojo.rpc

import ink.anur.pojo.common.AbstractStruct
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.util.HessianUtil
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import java.nio.ByteBuffer

/**
 * Created by Anur IjuoKaruKas on 2020/4/7
 */
class RpcRequest : AbstractStruct {

    val requestMeta: RpcRequestMeta

    constructor(requestMeta: RpcRequestMeta) {
        this.requestMeta = requestMeta
        val ser = HessianUtil.ser(requestMeta)
        init(OriginMessageOverhead + ser.size, RequestTypeEnum.CANVASS) {
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

        requestMeta = HessianUtil.des(ba, RpcRequestMeta::class.java)
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