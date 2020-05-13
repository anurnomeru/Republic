package ink.anur.core

import ink.anur.pojo.common.AbstractStruct
import io.netty.channel.Channel
import java.util.HashMap
import java.util.concurrent.locks.ReentrantLock

/**
 * Created by Anur on 2020/5/13
 *
 * 发现连接管理过于混乱，这里统一进行连接管理
 *
 * 包括
 *  - 连接握手
 *  - 握手回调注册
 *  - 自动重连
 *  - 断开连接
 *
 *  适用于客户端与服务端共用，不再将客户端服务端的连接代码分离
 */
class ConnectionManageService {

    /**
     * 记录了服务名和 channel 的映射
     */
    private val serverChannelMap: MutableMap<String/* serverName */, ChannelHolder> = mutableMapOf()

    /**
     * 服务锁暂存
     */
    private val lockerMapping = HashMap<String, ReentrantLock>()

    /**
     * 对某个服务进行的操作需要加锁
     */
    private fun getLock(serverName: String): ReentrantLock {
        var lock: ReentrantLock? = lockerMapping[serverName]
        if (lock == null) {
            synchronized(this) {
                lock = lockerMapping[serverName]
                if (lock == null) {
                    lock = ReentrantLock()
                    lockerMapping[serverName] = lock!!
                }
            }
        }
        return lock!!
    }

    fun send(serverName: String, body: AbstractStruct) {
        val channelHolder = serverChannelMap[serverName]
        when (channelHolder?.channelStatus) {
            null -> {

            }
            ChannelStatus.CONNECTING -> {

            }
            ChannelStatus.ESTABLISH -> {

            }
        }
    }



    /**
     * 对 netty channel 的进一层封装
     */
    class ChannelHolder(val channel: Channel, @Volatile var channelStatus: ChannelStatus = ChannelStatus.CONNECTING)

    enum class ChannelStatus {
        CONNECTING,
        ESTABLISH
    }
}