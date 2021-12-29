package ink.anur.pojo.coordinate

import ink.anur.pojo.common.AbstractStruct
import ink.anur.pojo.common.RequestTypeEnum
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import java.nio.ByteBuffer

/**
 * Created by Anur IjuoKaruKas on 2020/2/26
 *
 * 投票 ~
 */
class Voting : AbstractStruct {

    companion object {
        val AgreedSignOffset = OriginMessageOverhead
        val AgreedSignLength = 1
        val FromLeaderSignOffset = AgreedSignOffset + AgreedSignLength
        val FromLeaderSignLength = 1
        val AskVoteGenerationOffset = FromLeaderSignOffset + FromLeaderSignLength
        val AskVoteGenerationLength = 8
        val GenerationOffset = AskVoteGenerationOffset + AskVoteGenerationLength
        val GenerationLength = 8
        val Capacity = GenerationOffset + GenerationLength
    }

    /**
     * 拉票成功/失败
     */
    var agreed: Boolean = false

    /**
     * 去拉票，结果拉到了leader节点，则无需继续拉票了，直接成为follower。
     */
    var fromLeaderNode: Boolean = false

    /**
     * 请求拉票时的世代信息
     */
    var askVoteGeneration: Long = 0

    /**
     * 该选票的世代信息
     */
    var generation: Long = 0

    constructor(byteBuffer: ByteBuffer) {
        buffer = byteBuffer
        byteBuffer.mark()
        byteBuffer.position(AgreedSignOffset)

        this.agreed = translateToBool(byteBuffer.get())
        this.fromLeaderNode = translateToBool(byteBuffer.get())
        this.askVoteGeneration = byteBuffer.getLong()
        this.generation = byteBuffer.getLong()
        buffer.reset()
    }

    constructor(agreed: Boolean, fromLeaderNode: Boolean, canvassGeneration: Long, voteGeneration: Long) {
        this.agreed = agreed
        this.fromLeaderNode = fromLeaderNode
        this.askVoteGeneration = canvassGeneration
        this.generation = voteGeneration

        init(Capacity, RequestTypeEnum.VOTING) {
            it.put(translateToByte(agreed))
            it.put(translateToByte(fromLeaderNode))
            it.putLong(canvassGeneration)
            it.putLong(voteGeneration)
        }
    }

    override fun writeIntoChannel(channel: Channel) {
        val wrappedBuffer = Unpooled.wrappedBuffer(buffer)
        channel.write(wrappedBuffer)
    }

    override fun totalSize(): Int {
        return size()
    }
}