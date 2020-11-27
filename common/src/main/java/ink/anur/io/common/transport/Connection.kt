package ink.anur.io.common.transport

import ink.anur.common.KanashiExecutors
import ink.anur.common.KanashiIOExecutors
import ink.anur.common.struct.RepublicNode
import ink.anur.config.InetConfiguration
import ink.anur.core.common.License
import ink.anur.core.common.RequestMapping
import ink.anur.debug.Debugger
import ink.anur.inject.bean.Nigate
import ink.anur.inject.bean.NigateInject
import ink.anur.io.client.ReConnectableClient
import ink.anur.io.common.handler.ChannelInactiveHandler
import ink.anur.pojo.SendLicense
import ink.anur.pojo.SendLicenseResponse
import ink.anur.pojo.Syn
import ink.anur.pojo.SynResponse
import ink.anur.pojo.common.AbstractStruct
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
        if (inetConnection.localNode.host == host && inetConnection.localNode.port == port) {
            logger.error("can not connect by self!")
            exitProcess(1)
        }
    }

    private val republicNode = RepublicNode.construct(host, port)

    private val createdTs = TimeUtil.getTime()

    private val randomSeed = ThreadLocalRandom.current().nextLong()

    private val shutDownHooker = ShutDownHooker()

    private val pinLicense = License()

    private val sendLicense = License()

    private val contextHandler = ChannelHandlerContextHandler(republicNode, sendLicense, pinLicense)

    private val asyncSendingQueue = ConcurrentHashMap<RequestTypeEnum, AbstractStruct>()

    private val connectionThread = Thread(this).also { it.name = "$republicNode Connection Thread" }

    /* * initial * */

    /**
     * netty client inner connection
     */
    private val client: ReConnectableClient = ReConnectableClient(host, port, shutDownHooker, {
        logger.trace("ReConnectableClient is connecting to remote node $republicNode, setting it pin and waiting for establish.")
        contextHandler.settingPin(it)
    })

    init {
        KanashiIOExecutors.execute(client)
        KanashiIOExecutors.execute(connectionThread)
    }

    /* * syn * */

    private fun tryEstablishLicense() {
        this.pinLicense.enable()
    }

    override fun run() {
        logger.info("connection [Pin] mode to node $republicNode start to work")

        while (true) {
            pinLicense.license()

            if (contextHandler.established()) {
                logger.trace("connection is already established, so disable [ConnectLicense]")
                pinLicense.disable()
            }

            contextHandler.getPinUnEstablished()?.let {
                logger.trace("now try to establish")

                try {
                    val sendAndWaitForResponse = sendWithNoSendLicenseAndWaitForResponse(it.channel(), Syn(inetConnection.localNodeAddr, createdTs, randomSeed))
                    if (sendAndWaitForResponse != null) {
                        this.contextHandler.establish(ChannelHandlerContextHandler.ChchMode.PIN, it)
                    }
                } catch (e: Throwable) {
                    logger.trace("sending [Syn] to the remote node but can't get response, retrying...")
                    return@let
                }
            }
        }
    }

    /* * syn response * */

    private fun mayConnectByRemote(ctx: ChannelHandlerContext, syn: Syn) {

        if (contextHandler.established() && contextHandler.getSocket() != null && contextHandler.getSocket() != ctx) {
            // if is already establish then close the remote channel
            logger.trace("local node already establish and syn ctx from ${syn.getAddr()} will be close")
            ctx.close()
        } else {
            logger.trace("remote node ${syn.getAddr()} attempt to establish with local server")
            try {
                if (syn.allowConnect(createdTs, randomSeed)) {
                    logger.trace("allowing remote node ${syn.getAddr()} establish to local ")
                    sendWithNoSendLicense(ctx.channel(), SynResponse(inetConnection.localNodeAddr).asResp(syn))
                    if (this.contextHandler.establish(ChannelHandlerContextHandler.ChchMode.SOCKET, ctx)) {
                        sendWithNoSendLicenseAndWaitForResponse(ctx.channel(), SendLicense(inetConnection.localNodeAddr))
                                ?.also { this.sendLicense() }
                                ?: let {
                                    logger.error("establish to remote node ${syn.getAddr()} but ")
                                    ctx.close()
                                }
                    }
                } else {
                    logger.trace("remote node $republicNode attempt to established with local node but refused. try to establish initiative.")
                    pinLicense.enable()
                }
            } catch (e: Throwable) {
                logger.trace("sending [SynResponse] to the remote node but can't get response, retrying...")
            }
        }
    }

    /* send license */

    private fun sendLicense() {
        contextHandler.sendLicense()
    }

    /* * send with no send license * */

    /**
     * while the connection is not established, using this method for sending msg
     */
    private fun sendWithNoSendLicense(channel: Channel, struct: AbstractStruct) {
        doSend(channel, struct);
    }

    private fun sendWithNoSendLicenseAndWaitForResponse(channel: Channel, struct: AbstractStruct, timeout: Long = 3000, unit: TimeUnit = TimeUnit.MILLISECONDS): ByteBuffer? {
        val identifier = struct.getIdentifier()
        val cdl = CountDownLatch(1)

        try {
            logger.trace("waiting response for identifier $identifier")
            waitDeck[identifier] = cdl
            sendWithNoSendLicense(channel, struct)
            if (!cdl.await(timeout, unit)) {
                logger.trace("waiting response for identifier $identifier fail")
            }
            return response[identifier]
        } finally {
            response.remove(identifier)
            waitDeck.remove(identifier)
        }
    }

    /* * normal sending * */

    private fun send(struct: AbstractStruct) {
        sendLicense.license()
        contextHandler.getChannelHandlerContext()?.channel()?.also {
            this.sendWithNoSendLicense(it, struct)
        } ?: also {
            logger.error("sending struct with license but can not find channel!")
        }
    }

    private fun sendAndWaitForResponse(struct: AbstractStruct, timeout: Long = 3000, unit: TimeUnit = TimeUnit.MILLISECONDS): ByteBuffer? {
        val respIdentifier = struct.getRespIdentifier()
        val cdl = CountDownLatch(1)

        try {
            send(struct)
            logger.trace("waiting for response [resp identifier] $respIdentifier")
            cdl.await(timeout, unit)
            return response[respIdentifier]
        } finally {
            response.remove(respIdentifier)
            waitDeck.remove(respIdentifier)
        }
    }

    private fun sendAndWaitForResponseErrorCaught(struct: AbstractStruct): ByteBuffer? {
        return try {
            sendAndWaitForResponse(struct, 3000, TimeUnit.MILLISECONDS)
        } catch (t: Throwable) {
            logger.trace(t.stackTrace.toString())
            null
        }
    }

    /* * send async * */

    private fun sendToQueue(struct: AbstractStruct) {
        asyncSendingQueue[struct.getRequestType()] = struct
    }

    private fun sendIfHasLicense(struct: AbstractStruct) {
        if (sendLicense.hasLicense()) send(struct)
    }

    override fun toString(): String {
        return "RepublicNode(host='$host', port=$port)"
    }

    companion object {
        private val waitDeck = ConcurrentHashMap<Int /* sign */, CountDownLatch>()
        private val response = ConcurrentHashMap<Int /* sign */, ByteBuffer>()
        private val uniqueConnection = ConcurrentHashMap<RepublicNode, Connection>()
        private val requestMappingRegister = mutableMapOf<RequestTypeEnum, RequestMapping>()
        private val logger = Debugger(this::class.java)

        private val TSUBASA = ConcurrentHashMap<Channel, RepublicNode>()

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

        private fun doSend(channel: Channel, struct: AbstractStruct) {
            synchronized(channel) {
                channel.write(Unpooled.copyInt(struct.totalSize()))
                ByteBufferUtil.writeUnsignedInt(struct.buffer, 0, struct.computeChecksum())
                struct.writeIntoChannel(channel)
                channel.flush()
                logger.debug("==> send msg type:{} and identifier:{}", struct.getRequestType(), struct.getIdentifier())
            }
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

        /* * receive * */

        /**
         * receive a msg and signal cdl
         */
        fun ChannelHandlerConte

        // todo
        xt.receive(msg: ByteBuffer)
        {
            val requestType = msg.getRequestType()
            val identifier = msg.getIdentifier()

            logger.debug("<== receive msg type:{} and identifier:{}", requestType, identifier)
            val republicNode = this.tsubasa()

            val requestMapping = requestMappingRegister[requestType]
            when (requestType) {

                /* establishing */
                RequestTypeEnum.SYN -> this.mayConnectByRemote(Syn(msg))

                /* send licensing */
                RequestTypeEnum.SEND_LICENSE -> this.sendLicense(SendLicense(msg))

                /* handling */
                else -> {
                    waitDeck[identifier]?.let {
                        response[identifier] = msg
                        it.countDown()
                    } ?: also {
                        when {
                            republicNode == null -> {
                                logger.error("haven't established but receive msg type:[$requestType] from channel ${this.channel().remoteAddress()}")
                            }
                            requestMapping == null -> {
                                logger.error("msg type:[$requestType] from $republicNode has not custom requestMapping !!!")
                            }
                            else -> {
                                try {
                                    requestMapping.handleRequest(republicNode, msg)// 收到正常的请求
                                } catch (e: Exception) {
                                    logger.error("Error occur while process msg type:[$requestType] from $republicNode", e)
                                }
                            }
                        }
                    }
                }
            }
        }

        private fun ChannelHandlerContext.sendLicense(sendLicense: SendLicense) {
            val republicNode = RepublicNode.construct(sendLicense.getAddr())
            republicNode.getConnection().sendLicense()
            doSend(this.channel(), SendLicenseResponse().asResp(sendLicense))
        }

        private fun ChannelHandlerContext.mayConnectByRemote(syn: Syn) {
            val republicNode = RepublicNode.construct(syn.getAddr())
            republicNode.getConnection().mayConnectByRemote(this, syn)
        }

        /**
         * 絆を探して
         */
        private fun ChannelHandlerContext.tsubasa(): RepublicNode? {
            return TSUBASA[this.channel()]
        }

        /**
         * 絆を結ぶ
         */
        private fun ChannelHandlerContext.tsubasa(republicNode: RepublicNode) {
            TSUBASA[this.channel()] = republicNode
        }

        /**
         * 别れ
         */
        private fun RepublicNode.wakare() {
            TSUBASA.entries.find { it.value == this }?.key?.let { TSUBASA.remove(it) }
        }
    }

    /**
     * manage the channelHandlerContext that shield pin mode and socket mode
     */
    private class ChannelHandlerContextHandler(
            private val republicNode: RepublicNode,
            private val sendLicense: License,
            private val pinLicense: License) {

        @Volatile
        private var pin: ChannelHandlerContext? = null

        @Volatile
        private var socket: ChannelHandlerContext? = null

        @Volatile
        private var mode = ChchMode.UN_CONNECTED

        fun established() = mode != ChchMode.UN_CONNECTED

        @Synchronized
        fun establish(mode: ChchMode, ctx: ChannelHandlerContext): Boolean {
            return if (established()) {
                logger.trace("already established to $republicNode!")
                ctx.close()
                false
            } else {
                try {
                    ctx.pipeline().addLast(ChannelInactiveHandler {
                        disConnect()
                    })

                    when (mode) {
                        ChchMode.PIN -> {
                            pin = ctx
                            socket?.close()
                        }
                        ChchMode.SOCKET -> {
                            socket = ctx
                            pin?.close()
                        }
                        else -> {
                            throw UnsupportedOperationException()
                        }
                    }
                    this.mode = mode
                    this.pinLicense.disable()
                    logger.info("connection establish with $republicNode using [$mode] mode with channel: ${ctx.channel().remoteAddress()}")
                    ctx.tsubasa(republicNode)
                    true
                } catch (e: Exception) {
                    logger.error("error occur while establish with $republicNode!", e)
                    ctx.close()
                    false
                }
            }
        }

        @Synchronized
        fun disConnect(): Boolean {
            return if (!established()) {
                false
            } else {
                logger.error("remote node $republicNode using [$mode] is disconnect")
                this.mode = ChchMode.UN_CONNECTED
                this.sendLicense.disable()
                this.pinLicense.enable()
                republicNode.wakare()
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

        @Synchronized
        fun sendLicense() {
            if (mode != ChchMode.UN_CONNECTED) {
                sendLicense.enable()
                logger.info("remote node $republicNode publish SendLicense to local")
            } else {
                logger.error("remote node $republicNode publish SendLicense to local but never established")
            }
        }

        enum class ChchMode {
            PIN, SOCKET, UN_CONNECTED
        }
    }
}