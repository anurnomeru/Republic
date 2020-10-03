package ink.anur.pojo.connection

import java.nio.ByteBuffer

/**
 * Created by Anur on 2020/9/21
 *
 * 当收到 Establish 后，即可接收对端消息，也可开始向对端发送消息
 *
 * CHANNEL：SYN -> ESTABLISHED
 */
class Establish : Syn {
    constructor(ts: Long) : super(ts)
    constructor(byteBuffer: ByteBuffer) : super(byteBuffer)
}