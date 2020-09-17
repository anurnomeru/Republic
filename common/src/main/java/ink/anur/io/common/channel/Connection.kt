package ink.anur.io.common.channel

import ink.anur.pojo.Register
import ink.anur.util.TimeUtil
import io.netty.channel.Channel

/**
 * Created by Anur on 2020/7/13
 *
 * manage a connection's status by channel
 */
class Connection(private val remoteNodeName: String, var connectionStatus: ConnectionStatus, var channel: Channel) {
    private val createTime = TimeUtil.getTime()
    val register = Register(remoteNodeName, createTime)
}