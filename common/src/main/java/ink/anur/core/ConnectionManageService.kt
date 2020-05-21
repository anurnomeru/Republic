package ink.anur.core

import ink.anur.common.KanashiRunnable
import ink.anur.common.struct.KanashiNode
import ink.anur.config.InetConfig
import ink.anur.core.client.ClientOperateHandler
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

    companion object {
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

    @NigateInject(useLocalFirst = true)
    private lateinit var inetConfig: InetConfig

    /**
     * 记录了服务名和 channel 的映射
     */
    private val connectionMapping: ConcurrentHashMap<String/* serverName */, ChannelHolder> = ConcurrentHashMap()

    /**
     * 服务锁暂存
     */
    private val lockerMapping = HashMap<String, ReentrantLocker>()

    /**
     * 专门用于发送的线程
     */
    override fun run() {
        for (channelHolder in connectionMapping.values) {
            if (channelHolder.channelStatus == ChannelHolder.ChannelStatus.ESTABLISHED) {
                val channel = channelHolder.getChannel()
                val waitToSendIter = channelHolder.sendBuffer.values.iterator()

                while (waitToSendIter.hasNext()) {
                    val waitToSend = waitToSendIter.next()
                    if (doSend(channel, waitToSend) == true) {
                        waitToSendIter.remove()
                    } else {
                        // 发送失败认定为断线 有可能有潜在的问题
                        break
                    }
                }
            }
        }
    }

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

    /**
     *      ｜ connector ｜                                   ｜ be connector ｜
     *  (CONNECTING) Register            -->
     *                                   <--           （ESTABLISHED）RegisterResponse
     *
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
                    InitiativeChannelHolder(nodes)
                }
            }

            connectionHolder!!.sendAsync(body)
        } finally {
            lock.unlock()
        }
    }

    /**
     * 被动连接时，将管道纳入管理
     */
    fun registryChannel(serverName: String, channel: Channel) {
        this.getLock(serverName).lockSupplier {

        }

        lock.lock()
        try {
            connectionMapping.compute(serverName) { _, v ->
                return@compute v ?: let {
                    PassiveChannelHolder(channel) {
                        lock.lock()
                        try {
                            connectionMapping.remove(serverName)
                        } finally {
                            lock.unlock()
                        }
                    }
                }
            }
        } finally {
            lock.unlock()
        } t
    }

    /**
     * 管理连接的 ChannelHolder
     */
    class ChannelHolder(val clusters: List<KanashiNode>, val initialMethod: List<() -> Boolean>? = null) : Runnable {

        companion object {
            private val random = Random(1)
            private val logger = Debugger(this.javaClass)
            private val whatEver = Any()
        }

        /**
         * 主动连接模式的锁，此锁有效时，才会触发重连，否则不会触发重连
         */
        private var activateModeLock = ReentrantLocker()

        /**
         * 状态修改锁
         */
        private var stateChangingLock = ReentrantLocker()

        /**
         * 使用双锁检查去执行一段带锁的代码
         */
        private fun doInStateChangingLockWithDoubleLock(
                /**
                 * volatile suggest
                 */
                predicate: () -> Boolean, doInLock: () -> Unit) {
            if (predicate.invoke()) {
                stateChangingLock.lockSupplier {
                    if (predicate.invoke()) {
                        doInLock.invoke()
                    }
                }
            }
        }

        /**
         * 是否正在运行
         */
        @Volatile
        private var running = true

        /**
         * 节点状态
         */
        @Volatile
        private var channelStatus: ChannelStatus = ChannelStatus.LISTENING

        /**
         * 主动模式下的连接客户端
         */
        @Volatile
        private var connection: ClientOperateHandler? = null

        /**
         * 如果处于非连接中的状态，则持有 channel
         */
        @Volatile
        private var channel: Channel? = null

        /**
         * 主动模式下，发送注册的消息
         */
        @Volatile
        private var register: Register? = null

        /**
         * 发送暂存的缓冲区
         */
        private val sendBuffer = ConcurrentHashMap<RequestTypeEnum, AbstractStruct>()

        /**
         * 避免每次连接都从 0 开始，导致连接基本都对准第一个机器
         */
        private var nowConnectCounting = random.nextInt()

        /**
         * 塞进去一个元素就会开始重联
         */
        private val needReConnect = ArrayBlockingQueue<Any>(1)

        fun getChannel(): Channel? {
            return connection?.getChannel()
        }

        /**
         * 放进去自然有线程去负责发送它
         */
        fun sendAsync(body: AbstractStruct) {
            sendBuffer[body.getRequestType()] = body
        }

        /**
         * 收到了来自此节点的注册的消息
         *
         * 注册分几种情况：
         *  - 情况1：本节点没有和对方节点建立连接，或者发起连接的时间晚与对方，可以直接同对方的 channel 建立连接
         *  - 情况2：已经建立连接，则不可与对方建立连接
         */
        fun receiveRegistry(registryProposeTime: Long, channel: Channel) {
            // todo 需要测试
            if (register?.getRegistrySign()?.let {
                        it < registryProposeTime // 早于对方发送 register 消息
                    } != true // 表示晚于对方 register 消息，或者没有开始发送
                    && channelStatus == ChannelStatus.LISTENING
                    && doSend(channel, RegisterResponse(registryProposeTime))) {
                if (clusters.size != 1) {
                    logger.debug("ChannelHolder 被动模式异常，对于多 cluster 的节点（一般是 client 连接 server）不存在被动模式！确定一下是不是哪里有bug")
                    exitProcess(1)
                }

                this.doInStateChangingLockWithDoubleLock({ true }, {
                    logger.debug("当前 ChannelHolder 将变更为被动模式，节点 ${clusters[0].serverName} 对本节点发起了连接")
                    this.activateModeLock.switchOff()
                    this.connection?.shutDown(false)
                    this.channel = channel
                    this.channelStatus = ChannelStatus.ESTABLISHED
                })

                val closeEvent = {
                    this.doInStateChangingLockWithDoubleLock({ true }, {
                        this.channelStatus = ChannelStatus.LISTENING
                        this.channel = null
                    })
                }

                /*
                 * 双重保险
                 */
                try {
                    channel.closeFuture().addListener { closeEvent.invoke() }
                } catch (t: Throwable) {
                    closeEvent.invoke()
                }
            }
        }

        /**
         * 开始尝试进行连接
         */
        fun enableActivateModeAndTryReConnect() {
            if (channelStatus == ChannelStatus.LISTENING) {
                this.activateModeLock.switchOn()
                needReConnect.offer(whatEver)
            }
        }

        /**
         * 连接管理代码
         */
        override fun run() {
            while (running) {
                /*
                 * 只有开启了主动模式，才能拉取触发重连
                 */
                activateModeLock.lockSupplier {
                    needReConnect.take()
                }
                if (channelStatus == ChannelStatus.LISTENING) {
                    val size = clusters.size

                    while (true) {
                        val nowIndex = nowConnectCounting % size
                        nowConnectCounting++
                        val connectLatch = CountDownLatch(1)
                        val nowConnectNode = clusters[nowIndex]

                        logger.debug("正在向节点 $nowConnectNode 发起连接")
                        val connect = ClientOperateHandler(nowConnectNode,
                                doAfterConnectToServer = {
                                    connectLatch.countDown()
                                },
                                doAfterDisConnectToServer = {
                                    activateModeLock.lockSupplier {
                                        this.doInStateChangingLockWithDoubleLock({true},{

                                            channelStatus = ChannelStatus.LISTENING
                                            // 稍微延迟一下不要让重连过于密集
                                            Timer.getInstance().addTask(TimedTask(200) {
                                                needReConnect.offer(whatEver) //触发一次重连
                                            })

                                            connection = null
                                            channel = null
                                            register = null
                                        })
                                    }

                                    logger.info("与节点 $nowConnectNode 的连接已断开")
                                    false // 以前的重连逻辑放在里面去实现，现在把它放到外面来控制
                                })

                        register = connect.getRegister()

                        connect.start()
                        if (connectLatch.await(5, TimeUnit.SECONDS)) {
                            if (activateModeLock.isSwitchOff()){
                                logger.debug("一定是哪里写的有问题！！！！！！！！！！！！！！！！！！！不应该存在收到了 RegisterResponse，但是还被对方主动连接的情况")
                            }
                            connection = connect
                            channel = connect.getChannel()
                            channelStatus = ChannelStatus.INITIALING
                            logger.info("与节点 $nowConnectNode 的连接已经建立")

                            /*
                             * 只要初始化方法中又一个执行失败，则连接失败
                             */
                            if (initialMethod?.any { !it.invoke() } == true) {
                                connect.shutDown()
                            } else {
                                logger.info("与节点 $nowConnectNode 的连接已经初始化成功")
                                channelStatus = ChannelStatus.ESTABLISHED
                                break// 代表成功了
                            }
                        }
                    }
                }
            }
        }


        enum class ChannelStatus {
            /*
             * 还未连接
             */
            LISTENING,

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