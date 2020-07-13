package ink.anur.io.common.channel

import ink.anur.util.TimeUtil
import io.netty.channel.Channel

/**
 * Created by Anur on 2020/7/13
 *
 * manage a connection's status by channel
 */
class Connection(val remoteNodeName: String, var connectionStatus: ConnectionStatus, var channel: Channel) {
    private val createTime = TimeUtil.getTime()
}