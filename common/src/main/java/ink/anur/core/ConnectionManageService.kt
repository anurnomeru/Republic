package ink.anur.core

import ink.anur.common.KanashiRunnable
import ink.anur.common.struct.KanashiNode
import ink.anur.config.InetConfig
import ink.anur.core.client.ClientOperateHandler
import ink.anur.cuncurrent.AsyncEventRegister
import ink.anur.debug.Debugger
import ink.anur.exception.NetWorkException
import ink.anur.inject.NigateBean
import ink.anur.inject.NigateInject
import ink.anur.mutex.ReentrantLocker
import ink.anur.pojo.Register
import ink.anur.pojo.RegisterResponse
import ink.anur.pojo.common.AbstractStruct
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.timewheel.TimedTask
import ink.anur.timewheel.Timer
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.system.exitProcess

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
@NigateBean
class ConnectionManageService : KanashiRunnable() {

    @NigateInject(useLocalFirst = true)
    private lateinit var inetConfig: InetConfig

    @NigateInject
    private lateinit var asyncEventRegister: AsyncEventRegister

    /**
     * 服务锁暂存
     */
    private val lockerMapping = HashMap<String, ReentrantLocker>()

    /**
     * 服务映射
     */
    private val channelHolderMapping = ConcurrentHashMap<String, ChannelHolder>()

    /**
     * 对某个服务进行的操作需要加锁
     */
    private fun getLock(serverName: String): ReentrantLocker {
        var lock: ReentrantLocker? = lockerMapping[serverName]
        if (lock == null) {
            synchronized(this) {
                lock = lockerMapping[serverName]
                if (lock == null) {
                    lock = ReentrantLocker()
                    lockerMapping[serverName] = lock!!
                }
            }
        }
        return lock!!
    }

    override fun run() {
        TODO("Not yet implemented")
    }

    fun send(serverName: String, abstractStruct: AbstractStruct) {

    }

    class ChannelHolder(kanashiNode: KanashiNode) {

        val clientOperateHandler = ClientOperateHandler(kanashiNode)
    }

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            println("")
        }

        /**
         * 实际发送的代码，不建议直接调用，因为没有锁
         */
        fun doSend(channel: Channel?, abstractStruct: AbstractStruct): Boolean {
            return channel?.let {
                channel.write(Unpooled.copyInt(abstractStruct.totalSize()))
                abstractStruct.writeIntoChannel(channel)
                channel.flush()
                true
            } == true
        }
    }
}
