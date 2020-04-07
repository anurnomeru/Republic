package ink.anur.common.struct

import ink.anur.pojo.common.RequestTypeEnum
import io.netty.channel.Channel
import java.nio.ByteBuffer

/**
 * Created by Anur IjuoKaruKas on 2019/8/7
 */
class Request(val msg: ByteBuffer, val typeEnum: RequestTypeEnum, val channel: Channel?){
}