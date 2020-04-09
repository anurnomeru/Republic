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
 * rcp 包含所有的集群内可以使用的 provider
 */
class RpcProviderMapping : AbstractStruct {

    val rpcProviderMappingMeta: RpcProviderMappingMeta

    constructor(rpcProviderMappingMeta: RpcProviderMappingMeta) {
        this.rpcProviderMappingMeta = rpcProviderMappingMeta
        val ser = HessianUtil.ser(rpcProviderMappingMeta)
        init(AbstractStruct.OriginMessageOverhead + ser.size, RequestTypeEnum.RPC_REGISTRATION) {
            it.put(ser)
        }
    }

    constructor(byteBuffer: ByteBuffer) {
        val limit = byteBuffer.limit()
        val position = AbstractStruct.OriginMessageOverhead

        this.buffer = byteBuffer
        val ba = ByteArray(limit - position)
        byteBuffer.mark()

        byteBuffer.position(position)
        byteBuffer.get(ba)

        rpcProviderMappingMeta = HessianUtil.des(ba, RpcProviderMappingMeta::class.java)
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