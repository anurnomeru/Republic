package ink.anur.io.common.channel

import io.netty.channel.Channel

/**
 * Created by Anur on 2020/7/13
 */
class ConnectionManageService {
    fun syn(server: String, channel: Channel) {
        Connection(server, ConnectionStatus.CONNECTING, channel)
    }
}