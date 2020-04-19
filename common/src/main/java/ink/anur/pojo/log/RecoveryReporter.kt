package ink.anur.pojo.log

import ink.anur.pojo.common.AbstractStruct
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.pojo.enumerate.RequestTypeEnum
import ink.anur.pojo.log.common.GenerationAndOffset
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import java.nio.ByteBuffer

/**
 * Created by Anur IjuoKaruKas on 2019/7/10
 *
 * 当选主成功后，从节点需要像主节点汇报自己最大的 offset
 */
class RecoveryReporter : AbstractStruct {

    private val CommitedGenerationLength = 8

    private val CommitedOffsetOffset = OriginMessageOverhead + CommitedGenerationLength

    private val CommitedOffsetLength = 8

    private val nowGenOffset = CommitedOffsetOffset + CommitedOffsetLength

    private val nowGenLength = 8

    private val BaseMessageOverhead: Int = nowGenOffset + nowGenLength

    constructor(latestGAO: GenerationAndOffset, nowGen: Long) {
        init(BaseMessageOverhead, RequestTypeEnum.RECOVERY_REPORTER) {
            it.putLong(latestGAO.generation)
            it.putLong(latestGAO.offset)
            it.putLong(nowGen)
        }
    }

    constructor(byteBuffer: ByteBuffer) {
        this.buffer = byteBuffer
    }

    fun getCommited(): GenerationAndOffset {
        return GenerationAndOffset(buffer!!.getLong(OriginMessageOverhead), buffer!!.getLong(CommitedOffsetOffset))
    }

    fun getNowGen(): Long {
        return buffer!!.getLong(nowGenOffset)
    }

    override fun writeIntoChannel(channel: Channel) {
        channel.write(Unpooled.wrappedBuffer(buffer))
    }

    override fun totalSize(): Int {
        return size()
    }

    override fun toString(): String {
        return "RecoveryReporter { GAO => ${getCommited()} }"
    }
}