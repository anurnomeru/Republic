package ink.anur.core

import ink.anur.common.struct.KanashiNode
import ink.anur.config.InetConfig
import ink.anur.core.client.ClientOperateHandler
import ink.anur.debug.Debugger
import ink.anur.exception.NetWorkException
import ink.anur.inject.NigateInject
import ink.anur.pojo.common.AbstractStruct
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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

    @NigateInject(useLocalFirst = true)
    private lateinit var inetConfig: InetConfig

    /**
     * 记录了服务名和 channel 的映射
     */
    private val connectionMapping: ConcurrentHashMap<String/* serverName */, ChannelHolder> = ConcurrentHashMap()

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

    /**
     * 向某个节点发送请求
     *
     * 如果还未连接，则会首先发起连接
     *
     * 在连接建立后，会将堆积的消息进行发送，一个消息类型只会缓存最后一个发送类型
     */
    fun sendAsync(serverName: String, body: AbstractStruct) {
        val lock = getLock(serverName)

        lock.lock()
        try {
            val connectionHolder = connectionMapping.compute(serverName) { _, v ->
                return@compute v ?: let {
                    val nodes = inetConfig.getNode(serverName)
                    if (nodes.isEmpty()) {
                        throw NetWorkException("无法根据服务名 $serverName 获取到对应的 KanashiNode")
                    }
                    ChannelHolder(nodes)
                }
            }

            connectionHolder!!.sendAsync(body)
        } finally {
            lock.unlock()
        }
    }

    /**
     * 对 netty channel 的进一层封装
     */
    class ChannelHolder(val clusters: List<KanashiNode>, val initialMethod: List<() -> Boolean>? = null, @Volatile var channelStatus: ChannelStatus = ChannelStatus.CONNECTING) : Runnable {

        companion object {
            private val random = Random(1)
            private val logger = Debugger(this.javaClass)
            private val whatEver = Any()
        }

        @Volatile
        private var connection: ClientOperateHandler? = null

        /**
         * 避免每次连接都从 0 开始，导致连接基本都对准第一个机器
         */
        private var nowConnectCounting = random.nextInt()

        /**
         * 塞进去一个元素就会开始重联
         */
        private val needReConnect = ArrayBlockingQueue<Any>(1)

        /**
         * 连接管理代码
         */
        override fun run() {
            while (true) {
                val poll = needReConnect.poll(30, TimeUnit.SECONDS)
                channelStatus == ChannelStatus.CONNECTING
                if (poll != null) {
                    val size = clusters.size

                    while (true) {
                        val nowIndex = nowConnectCounting % size
                        nowConnectCounting++
                        val connectLatch = CountDownLatch(1)
                        val nowConnectNode = clusters[nowIndex]

                        logger.debug("正在向节点 $nowConnectNode 发起连接")
                        val connect = ClientOperateHandler(nowConnectNode,
                                {
                                    connectLatch.countDown()
                                },
                                {
                                    connection = null
                                    needReConnect.offer(whatEver) //触发一次重连
                                    false
                                })

                        connect.start()
                        if (connectLatch.await(5, TimeUnit.SECONDS)) {
                            connection = connect
                            channelStatus == ChannelStatus.INITIALING
                            logger.info("与节点 $nowConnectNode 的连接已经建立")

                            /*
                             * 只要初始化方法中又一个执行失败，则连接失败
                             */
                            if (initialMethod?.any { !it.invoke() } == true) {
                                connect.shutDown()
                            } else {
                                logger.info("与节点 $nowConnectNode 的连接已经初始化成功")
                                channelStatus == ChannelStatus.ESTABLISHED
                                break// 代表成功了
                            }
                        }
                    }
                }
            }

        }

        fun sendAsync(body: AbstractStruct) {

        }

        enum class ChannelStatus {
            /*
             * 正在连接中
             */
            CONNECTING,

            /*
             * 正在初始化
             */
            INITIALING,

            /*
             * 连接正式建立
             */
            ESTABLISHED
        }
    }
}