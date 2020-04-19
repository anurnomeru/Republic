package ink.anur.pojo.log

import ink.anur.pojo.common.AbstractStruct
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.pojo.log.meta.RecoveryCompleteMeta
import ink.anur.pojo.rpc.meta.RpcRegistrationMeta
import ink.anur.util.HessianUtil
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import java.nio.ByteBuffer

/**
 * Created by Anur IjuoKaruKas on 2020/4/19
 */
class RecoveryComplete : AbstractStruct {

    val recoveryCompleteMeta: RecoveryCompleteMeta

    constructor(recoveryCompleteMeta: RecoveryCompleteMeta) {
        this.recoveryCompleteMeta = recoveryCompleteMeta
        val ser = HessianUtil.ser(recoveryCompleteMeta)
        init(AbstractStruct.OriginMessageOverhead + ser.size, RequestTypeEnum.RPC_REGISTRATION) {
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

        recoveryCompleteMeta = HessianUtil.des(ba, RecoveryCompleteMeta::class.java)
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