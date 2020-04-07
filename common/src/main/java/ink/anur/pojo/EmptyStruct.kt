package ink.anur.pojo

import ink.anur.pojo.common.AbstractStruct
import io.netty.channel.Channel

/**
 * Created by Anur IjuoKaruKas on 2020/2/22
 *
 * 空的实现
 */
class EmptyStruct : AbstractStruct() {

    override fun writeIntoChannel(channel: Channel) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun totalSize(): Int {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}