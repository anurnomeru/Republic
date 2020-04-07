package ink.anur.core.common

import ink.anur.pojo.common.RequestTypeEnum
import io.netty.channel.Channel
import java.nio.ByteBuffer

/**
 * Created by Anur IjuoKaruKas on 2020/2/24
 *
 * 定义一个消息如何消费的顶级接口
 */
interface RequestMapping {

    /**
     * 表明此路由可消费什么消息
     */
    fun typeSupport(): RequestTypeEnum

    /**
     * 如何去消费一个消息
     */
    fun handleRequest(fromServer: String, msg: ByteBuffer, channel: Channel)
}