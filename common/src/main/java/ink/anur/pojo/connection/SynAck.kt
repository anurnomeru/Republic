package ink.anur.pojo.connection

import java.nio.ByteBuffer

/**
 * Created by Anur on 2020/9/21
 *
 * 表示收到 SynAck 此时可向对端端发送消息，也可接收对端消息
 *
 * CHANNEL：SYN -> ESTABLISHED
 */
class SynAck : Syn {

    companion object {
        const val Reject = 0L
    }

    constructor(ts: Long) : super(ts)
    constructor(byteBuffer: ByteBuffer) : super(byteBuffer)

    fun reject(): Boolean {
        return getTs() == Reject
    }
}