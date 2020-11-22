package ink.anur.io.common.transport

import ink.anur.common.KanashiExecutors
import ink.anur.common.KanashiIOExecutors
import ink.anur.common.struct.RepublicNode
import ink.anur.config.InetConfiguration
import ink.anur.core.common.RequestMapping
import ink.anur.debug.Debugger
import ink.anur.debug.DebuggerLevel
import ink.anur.inject.bean.Nigate
import ink.anur.inject.bean.NigateInject
import ink.anur.io.client.ReConnectableClient
import ink.anur.io.common.handler.ChannelInactiveHandler
import ink.anur.mutex.ReentrantLocker
import ink.anur.pojo.Syn
import ink.anur.pojo.SynResponse
import ink.anur.pojo.common.AbstractStruct
import ink.anur.pojo.common.AbstractStruct.Companion.getIdentifier
import ink.anur.pojo.common.AbstractStruct.Companion.getRequestType
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.util.ByteBufferUtil
import ink.anur.util.TimeUtil
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.*

/**
 * Created by Anur on 2020/10/3
 */
class Conn(private val host: String, private val port: Int) {

    @NigateInject
    private lateinit var inetConnection: InetConfiguration

    init {
        Nigate.injectOnly(this)
    }

    @Volatile
    private var connectionStatus: Connection.ConnectionStatus = Connection.ConnectionStatus.UN_CONNECTED

    private val locker = ReentrantLocker()

    private val shutDownHooker = ShutDownHooker()

    private val connectLicense = ReConnectableClient.License()

    private val sendLicense = ReConnectableClient.License()

    private val asyncSendingQueue = ConcurrentHashMap<RequestTypeEnum, AbstractStruct>()

    private val chcg = ChannelHandlerContextGeneration()

    /**
     * netty client inner connection
     */
    private val client: ReConnectableClient = ReConnectableClient(host, port, shutDownHooker,
            {
                chcg.upGen(it)
            })

    private fun tryConnection() {
        if (connectionStatus.isEstablished()) {
            return
        } else {
            val ctx = chcg.getChannelHandlerContextGeneration().getCtx()?.let {
                try {
                    val sendAndWaitForResponse = sendAndWaitForResponse(Syn(inetConnection.localServerAddr), it.channel())
                } catch (e: Throwable) {
                    Connection.logger.trace("sending [Syn] to the remote node but can't get response, retrying...")
                    continue
                }
            }

            this.establish(ctx, successfulConnected, doAfterDisConnected)
        }
    }

    private class ChannelHandlerContextGeneration(
            @Volatile private var gen: Long = Long.MAX_VALUE,
            @Volatile private var ctx: ChannelHandlerContext? = null
    ) {

        /**
         * todo 实现一个子类来实现真正的 immutable 不过这个优先级不高
         */
        private var immutableCache: ChannelHandlerContextGeneration = ChannelHandlerContextGeneration(gen, ctx)

        @Synchronized
        fun upGen(ctx: ChannelHandlerContext) {
            this.gen++
            this.ctx = ctx
            this.immutableCache = ChannelHandlerContextGeneration(gen, ctx)
        }

        @Synchronized
        fun getChannelHandlerContextGeneration(): ChannelHandlerContextGeneration {
            return immutableCache
        }

        @Synchronized
        fun getCtx() = this.ctx
    }

    private fun sendToQueue(struct: AbstractStruct) {
        asyncSendingQueue[struct.getRequestType()] = struct
    }

    private fun send(struct: AbstractStruct) {
        sendLicense.license()
        val channel = ctx!!.channel()
        channel.write(Unpooled.copyInt(struct.totalSize()))
        ByteBufferUtil.writeUnsignedInt(struct.buffer, 0, struct.computeChecksum())
        struct.writeIntoChannel(channel)
        channel.flush()
    }

    /**
     * while the connection is not established, using this method for sending msg
     */
    private fun sendWithNoSendLicense(channel: Channel, struct: AbstractStruct) {
        channel.write(Unpooled.copyInt(struct.totalSize()))
        ByteBufferUtil.writeUnsignedInt(struct.buffer, 0, struct.computeChecksum())
        struct.writeIntoChannel(channel)
        channel.flush()
    }

    companion object {
        private val waitDeck = ConcurrentHashMap<Int /* sign */, CountDownLatch>()
        private val response = ConcurrentHashMap<Int /* sign */, ByteBuffer>()
        private val channelRemoteNodeMapping = ConcurrentHashMap<Channel, RepublicNode>()
        private val uniqueConnection = ConcurrentHashMap<RepublicNode, Conn>()
        private val requestMappingRegister = mutableMapOf<RequestTypeEnum, RequestMapping>()
        private val logger = Debugger(this::class.java).switch(DebuggerLevel.INFO)

        /**
         * registry RequestMapping for handle response
         */
        fun registerRequestMapping(typeEnum: RequestTypeEnum, requestMapping: RequestMapping) {
            requestMappingRegister[typeEnum] = requestMapping
        }

        /**
         * getting inner connection, connection is unique for a RepublicNode
         *
         * if connection is first created, then try connect to the remote node
         * until connection establish
         */
        private fun RepublicNode.getConnection(): Conn {
            return uniqueConnection.computeIfAbsent(this)
            { Conn(this.host, this.port).also { it.tryConnect() } }
        }

        /**
         * get the RepublicNode that bind to the channel handler context
         */
        private fun ChannelHandlerContext.getRepublicNodeByChannel(): RepublicNode? {
            return channelRemoteNodeMapping[this.channel()]
        }

        private fun ChannelHandlerContext.mayConnectByRemote(syn: Syn) {
            val republicNode = RepublicNode.construct(syn.getAddr())
            republicNode.getConnection().mayConnectByRemote(this, SynResponse(Nigate.getBeanByClass(InetConfiguration::class.java).localServerAddr).asResponse(syn) as SynResponse) { channelRemoteNodeMapping.remove(this.channel()) }
        }

        /**
         * receive a msg and signal cdl
         */
        fun ChannelHandlerContext.receive(msg: ByteBuffer) {
            val requestType = msg.getRequestType()
            val identifier = msg.getIdentifier()
            val republicNode = this.getRepublicNodeByChannel()

            if (republicNode == null) {
                if (requestType == RequestTypeEnum.SYN) {
                    this.mayConnectByRemote(Syn(msg))
                } else {
                    logger.error("never connect to remote node but receive msg type:{}", requestType)
                }
            } else {
                logger.debug("receive msg type:{} and identifier:{}", requestType, identifier)
                waitDeck[identifier]?.let {
                    response[identifier] = msg
                    it.countDown()
                } ?: also {
                    try {
                        val requestMapping = requestMappingRegister[requestType]

                        if (requestMapping != null) {
                            requestMapping.handleRequest(republicNode, msg)// 收到正常的请求
                        } else {
                            logger.error("msg type:[$requestType] from $republicNode has not custom requestMapping ！！！")
                        }

                    } catch (e: Exception) {
                        logger.error("Error occur while process msg type:[$requestType] from $republicNode", e)
                    }
                }
            }
        }

        fun RepublicNode.sendAsync(struct: AbstractStruct) {
            getConnection().sendToQueue(struct)
        }

        fun RepublicNode.send(struct: AbstractStruct) {
            getConnection().send(struct)
        }

        /**
         * actually send the struct and wait for response
         *
         * caution: may disconnect when sending
         */
        fun <T> RepublicNode.sendAndWaitingResponse(struct: AbstractStruct, timeout: Long = 3000, expect: Class<T>, unit: TimeUnit = TimeUnit.MILLISECONDS): T {
            return expect.getConstructor(ByteBuffer::class.java).newInstance(
                    getConnection().sendAndWaitForResponse(struct, timeout, unit)
            )
        }
    }
}