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
import java.lang.UnsupportedOperationException
import java.nio.ByteBuffer
import java.util.concurrent.*
import kotlin.system.exitProcess

/**
 * Created by Anur on 2020/10/3
 */
class Connection(private val host: String, private val port: Int) : Runnable {

    @NigateInject
    private lateinit var inetConnection: InetConfiguration

    init {
        Nigate.injectOnly(this)
        if (inetConnection.localServer.host == host && inetConnection.localServer.port == port) {
            logger.error("can not connect by self!")
            exitProcess(1)
        }
    }

    private val createdTs = TimeUtil.getTime()

    private val randomSeed = ThreadLocalRandom.current().nextLong()

    private val shutDownHooker = ShutDownHooker()

    private val pinLicense = ReConnectableClient.License()

    private val sendLicense = ReConnectableClient.License()

    private val contextHandler = ChannelHandlerContextHandler(sendLicense, pinLicense)

    private val asyncSendingQueue = ConcurrentHashMap<RequestTypeEnum, AbstractStruct>()

    private val connectionThread = Thread(this).also { it.name = "$this Connection Thread" }

    /* * initial * */

    /**
     * netty client inner connection
     */
    private val client: ReConnectableClient = ReConnectableClient(host, port, shutDownHooker, {
        logger.trace("ReConnectableClient is connecting to remote node $this, setting to pin.")
        contextHandler.settingPin(it)
    })

    init {
        KanashiIOExecutors.execute(client)
        KanashiIOExecutors.execute(connectionThread)
    }

    /* * establish * */

    private fun establish(mode: ChannelHandlerContextHandler.ChchMode, ctx: ChannelHandlerContext,
                          successfulConnected: (() -> Unit)? = null, doAfterDisConnected: (() -> Unit)? = null) {
        this.contextHandler.establish(mode, ctx, successfulConnected)
        ctx.pipeline().addLast(ChannelInactiveHandler {
            this.contextHandler.disConnect(doAfterDisConnected)
        })
    }

    /* * syn * */

    private fun tryEstablishLicense() {
        this.pinLicense.enable()
    }

    override fun run() {
        logger.info("connection [Pin Mode] to node $this start to work")

        while (true) {
            pinLicense.license()

            if (contextHandler.established()) {
                logger.trace("connection is already established, so disable [ConnectLicense]")
                pinLicense.disable()
            }

            contextHandler.getPinUnEstablished()?.let {
                logger.trace("now try to establish")

                try {
                    val sendAndWaitForResponse = sendAndWaitForResponse(Syn(inetConnection.localServerAddr, createdTs, randomSeed), it.channel())
                    if (sendAndWaitForResponse != null) {
                        this.establish(ChannelHandlerContextHandler.ChchMode.PIN, it,
                                {
                                    logger.info("connection to node $this is established by [Pin Mode]")
                                },
                                {
                                    logger.info("connection to node $this is disConnected by [Pin Mode]")
                                })
                    }
                } catch (e: Throwable) {
                    logger.trace("sending [Syn] to the remote node but can't get response, retrying...")
                    return@let
                }
            }
        }
    }

    /* * syn response * */

    private fun mayConnectByRemote(ctx: ChannelHandlerContext, syn: Syn, doAfterDisConnected: (() -> Unit)) {
        logger.trace("remote node from ${ctx.channel().remoteAddress()} attempt to establish with local server")

        if (contextHandler.established() && contextHandler.getSocket() != null && contextHandler.getSocket() != ctx) {
            // if is already establish then close the remote channel
            logger.trace("remote node ${ctx.channel().remoteAddress()} already establish and ctx will be close")
            ctx.close()
        } else {
            try {
                if (syn.allowConnect(createdTs, randomSeed)) {
                    sendWithNoSendLicense(ctx.channel(), SynResponse(inetConnection.localServerAddr).asResponse(syn))
                    this.establish(ChannelHandlerContextHandler.ChchMode.PIN, ctx,
                            {
                                logger.info("connection to node $this is established by [Socket Mode]")
                            },
                            {
                                logger.info("connection to node $this is disConnected by [Socket Mode]")
                                doAfterDisConnected.invoke()
                            })
                } else {
                    logger.trace("remote node $this attempt to established with local server but refused. try to establish initiative.")
                    pinLicense.enable()
                }
            } catch (e: Throwable) {
                logger.trace("sending [SynResponse] to the remote node but can't get response, retrying...")
            }
        }
    }


    private fun sendToQueue(struct: AbstractStruct) {
        asyncSendingQueue[struct.getRequestType()] = struct
    }

    private fun send(struct: AbstractStruct) {
        sendLicense.license()
        contextHandler.getChannelHandlerContext()?.channel()?.also {
            this.sendWithNoSendLicense(it, struct)
        } ?: also {
            logger.error("sending struct with license but can not find channel!")
        }
    }

    private fun sendIfHasLicense(struct: AbstractStruct) {
        if (sendLicense.hasLicense()) {
            contextHandler.getChannelHandlerContext()?.channel()?.also {
                this.sendWithNoSendLicense(it, struct)
            } ?: also {
                logger.error("sending struct with license but can not find channel!")
            }
        }
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

    private fun sendAndWaitForResponseErrorCaught(struct: AbstractStruct, channel: Channel?): ByteBuffer? {
        return try {
            sendAndWaitForResponse(struct, 3000, TimeUnit.MILLISECONDS, channel)
        } catch (t: Throwable) {
            logger.trace(t.stackTrace.toString())
            null
        }
    }

    private fun sendAndWaitForResponse(struct: AbstractStruct, channel: Channel?): ByteBuffer? {
        return sendAndWaitForResponse(struct, 3000, TimeUnit.MILLISECONDS, channel)
    }

    private fun sendAndWaitForResponse(struct: AbstractStruct, timeout: Long = 3000, unit: TimeUnit = TimeUnit.MILLISECONDS, channel: Channel? = null): ByteBuffer? {
        val identifier = struct.getIdentifier()
        val cdl = CountDownLatch(1)

        try {
            channel?.also { sendWithNoSendLicense(channel, struct) } ?: send(struct)
            logger.trace("waiting for response identifier $identifier ${channel?.let { ", send with no send license mode" }}")
            cdl.await(timeout, unit)
            return response[identifier]
        } finally {
            response.remove(identifier)
            waitDeck.remove(identifier)
        }
    }

    override fun toString(): String {
        return "RepublicNode(host='$host', port=$port)"
    }

    companion object {
        private val waitDeck = ConcurrentHashMap<Int /* sign */, CountDownLatch>()
        private val response = ConcurrentHashMap<Int /* sign */, ByteBuffer>()
        private val channelRemoteNodeMapping = ConcurrentHashMap<Channel, RepublicNode>()
        private val uniqueConnection = ConcurrentHashMap<RepublicNode, Connection>()
        private val requestMappingRegister = mutableMapOf<RequestTypeEnum, RequestMapping>()
        private val logger = Debugger(this::class.java).switch(DebuggerLevel.INFO)

        /*
         * this thread is using for sending async
         */
        private val t = Thread {
            while (true) {
                for (conn in uniqueConnection.values) {
                    val iterable = conn.asyncSendingQueue.iterator()

                    while (iterable.hasNext()) {
                        val (requestTypeEnum, abstractStruct) = iterable.next()
                        try {
                            conn.sendIfHasLicense(abstractStruct)
                        } catch (t: Throwable) {
                            // ignore
                            logger.error("Async sending struct type:[$requestTypeEnum] to $conn error occur!")
                            conn.asyncSendingQueue.putIfAbsent(requestTypeEnum, abstractStruct)
                        }
                    }
                }
                Thread.sleep(100)
            }
        }

        init {
            t.name = "AsyncSender"
            KanashiExecutors.execute(t)
        }

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
        private fun RepublicNode.getConnection(): Connection {
            if (!uniqueConnection.containsKey(this)) {
                synchronized(Connection::class.java) {
                    if (!uniqueConnection.containsKey(this)) {
                        uniqueConnection[this] = Connection(this.host, this.port).also { it.tryEstablishLicense() }
                    }
                }
            }
            return uniqueConnection[this]!!
        }

        /**
         * get the RepublicNode that bind to the channel handler context
         */
        private fun ChannelHandlerContext.getRepublicNodeByChannel(): RepublicNode? {
            return channelRemoteNodeMapping[this.channel()]
        }

        private fun ChannelHandlerContext.mayConnectByRemote(syn: Syn) {
            val republicNode = RepublicNode.construct(syn.getAddr())
            republicNode.getConnection().mayConnectByRemote(
                    this,
                    syn
            )
            { channelRemoteNodeMapping.remove(this.channel()) }
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

    /**
     * manage the channelHandlerContext that shield pin mode and socket mode
     */
    private class ChannelHandlerContextHandler(
            private val sendLicense: ReConnectableClient.License,
            private val pinLicense: ReConnectableClient.License) {

        @Volatile
        private var pin: ChannelHandlerContext? = null

        @Volatile
        private var socket: ChannelHandlerContext? = null

        @Volatile
        private var mode = ChchMode.UN_CONNECTED

        fun established() = mode != ChchMode.UN_CONNECTED

        @Synchronized
        fun establish(mode: ChchMode, chc: ChannelHandlerContext, doAtLast: (() -> Unit)? = null): Boolean {
            return if (established()) {
                false
            } else {
                when (mode) {
                    ChchMode.PIN -> {
                        pin = chc
                        socket?.close()
                    }
                    ChchMode.SOCKET -> {
                        socket = chc
                        pin?.close()
                    }
                    else -> {
                        throw UnsupportedOperationException()
                    }
                }
                this.mode = mode
                this.sendLicense.enable()
                this.pinLicense.disable()
                doAtLast?.invoke()
                true
            }
        }

        @Synchronized
        fun disConnect(successfulConnected: (() -> Unit)? = null): Boolean {
            return if (!established()) {
                false
            } else {
                this.mode = ChchMode.UN_CONNECTED
                this.sendLicense.disable()
                this.pinLicense.enable()
                successfulConnected?.invoke()
                true
            }
        }

        /*
         * high frequency call
         */
        fun getChannelHandlerContext(): ChannelHandlerContext? {
            return when (mode) {
                ChchMode.PIN -> {
                    pin
                }
                ChchMode.SOCKET -> {
                    socket
                }
                else -> {
                    null
                }
            }
        }

        @Synchronized
        fun getSocket(): ChannelHandlerContext? = socket

        @Synchronized
        fun getPinUnEstablished(): ChannelHandlerContext? = takeIf { !established() }?.let { pin }

        @Synchronized
        fun settingPin(it: ChannelHandlerContext) {
            pin = it
            pinLicense.enable()
        }

        enum class ChchMode {
            PIN, SOCKET, UN_CONNECTED
        }
    }
}