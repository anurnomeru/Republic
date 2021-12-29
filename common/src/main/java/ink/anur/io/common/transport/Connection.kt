package ink.anur.io.common.transport

import ink.anur.common.KanashiExecutors
import ink.anur.common.KanashiIOExecutors
import ink.anur.common.struct.RepublicNode
import ink.anur.config.InetConfiguration
import ink.anur.core.common.License
import ink.anur.core.common.RequestMapping
import ink.anur.debug.Debugger
import ink.anur.exception.io.RequestTimeoutException
import ink.anur.inject.bean.Nigate
import ink.anur.inject.bean.NigateInject
import ink.anur.io.client.ReConnectableClient
import ink.anur.io.common.RepublicResponse
import ink.anur.io.common.handler.ChannelActiveHandler
import ink.anur.io.common.transport.Connection.ChannelHandlerContextHandler.ChchMode.*
import ink.anur.pojo.SendLicense
import ink.anur.pojo.SendLicenseResponse
import ink.anur.pojo.Syn
import ink.anur.pojo.SynResponse
import ink.anur.pojo.common.AbstractStruct
import ink.anur.pojo.common.AbstractStruct.Companion.ensureValid
import ink.anur.pojo.common.AbstractStruct.Companion.getIdentifier
import ink.anur.pojo.common.AbstractStruct.Companion.getRequestType
import ink.anur.pojo.common.AbstractStruct.Companion.isResp
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.util.TimeUtil
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import kotlinx.coroutines.*
import java.lang.Runnable
import java.lang.UnsupportedOperationException
import java.nio.ByteBuffer
import java.util.concurrent.*
import kotlin.system.exitProcess

/**
 * Created by Anur on 2020/10/3
 */
class Connection(
    private val host: String, private val port: Int,
    /**
     * client Mode will never try to establish positive to remote node
     *
     * TODO switching mode for now is un supported
     */
    private val clientMode: Boolean = false
) : Runnable {

    @NigateInject
    private lateinit var inetConnection: InetConfiguration

    init {
        Nigate.injectOnly(this)
        if (inetConnection.localNode.host == host && inetConnection.localNode.port == port) {
            logger.error("can not connect by self!")
            exitProcess(1)
        }
    }

    val republicNode = RepublicNode.construct(host, port)

    private val createdTs = TimeUtil.getTime()

    private val randomSeed = ThreadLocalRandom.current().nextLong()

    private val shutDownHooker = ShutDownHooker()

    private val pinLicense = License()

    private val sendLicense = License()

    private var currentWaitDeck = ConcurrentSkipListSet<Int>()

    private val contextHandler =
        ChannelHandlerContextHandler(republicNode, sendLicense, pinLicense, clientMode, currentWaitDeck)

    private val asyncSendingQueue = ConcurrentHashMap<RequestTypeEnum, AbstractStruct>()

    private val connectionThread = Thread(this).also { it.name = "$republicNode Connection Thread" }

    @Volatile
    private var running: Boolean = true

    val destroyLicense = License()

    /* * initial * */

    /**
     * netty client inner connection
     */
    private val client: ReConnectableClient = ReConnectableClient(host, port, shutDownHooker, {
        logger.trace("ReConnectableClient is connecting to remote node $republicNode, setting it pin and waiting for establish.")
        contextHandler.settingPin(it)
    })

    init {
        if (!clientMode) {
            KanashiIOExecutors.execute(client)
            KanashiIOExecutors.execute(connectionThread)
        }
    }

    fun Channel(): Channel? = contextHandler.getChannelHandlerContext()?.channel()

    /* * destroy * */

    fun registerDestroyHandler(func: (ChannelHandlerContext?) -> Unit) {
        contextHandler.getChannelHandlerContext()
            ?.pipeline()
            ?.addLast(
                ChannelActiveHandler(
                    channelInactiveHook = func
                )
            )
            ?: func(contextHandler.getChannelHandlerContext())
    }

    /**
     * do not calling this for shut down a connect
     * use ChannelHandlerContextHandler.disConnect instead
     */
    fun destroy() {
        running = false
        shutDownHooker.shutdown()
        contextHandler.deEstablish()
        uniqueConnection.remove(republicNode)
        destroyLicense.enable()
    }

    /* * syn * */

    private fun tryEstablishLicense() {
        if (!contextHandler.established()) {
            this.pinLicense.enable()
        }
    }

    override fun run() {
        logger.info("connection [Pin] mode to node $republicNode start to work")

        while (running) {
            pinLicense.license()
            tryEstablish()
            Thread.sleep(1000)
        }

        logger.info("connection [Pin] mode to node $republicNode is destroy")
    }

    private fun tryEstablish() {

        /**
         * 1. disable pin license if is already establish (may connected by socket mode)
         * 2. get the channel and start handshake
         * 3. publish send license after successful handshake
         */

        if (contextHandler.established()) {
            logger.trace("connection is already established, so disable [ConnectLicense]")
            pinLicense.disable()
        }

        contextHandler.getPinUnEstablished()?.let {
            logger.trace("now try to establish")

            try {
                val channel = it.channel()
                val deferred = sendWithNoSendLicenseAndWaitForResponse(
                    channel,
                    Syn(inetConnection.localNodeAddr, createdTs, randomSeed, clientMode),
                    SynResponse::class.java
                )

                val result = runBlocking {
                    deferred
                        .await()
                        .Resp()
                }

                this.contextHandler.establish(PIN, it)

                sendWithNoSendLicenseAndWaitForResponseAsync(
                    channel,
                    SendLicense(inetConnection.localNodeAddr),
                    SendLicenseResponse::class.java,
                    consumer = { resp ->
                        resp.also { this.publishSendLicense() }
                    },
                    handler = CoroutineExceptionHandler { _, e ->
                        logger.error(
                            "establish to remote node ${result.getAddr()} but remote node did not publish send license",
                            e
                        )
                        channel.close()
                    }
                )
            } catch (e: Throwable) {
                logger.trace("sending [Syn] to the remote node but can't get response, retrying...", e)
                return@let
            }
        }
    }

    /* * syn response * */

    private fun mayConnectByRemote(ctx: ChannelHandlerContext, syn: Syn) {
        if (contextHandler.established() && contextHandler.getSocket() != null && contextHandler.getSocket() != ctx) { // if is already establish then close the remote channel
            logger.trace("local node already establish and syn ctx from ${syn.getAddr()} will be close")
            ctx.close()
        } else {
            logger.trace("remote node ${syn.getAddr()} attempt to establish with local server")
            try {
                if (syn.allowConnect(createdTs, randomSeed, republicNode.addr) || syn.clientMode()) {
                    logger.trace("allowing remote node ${syn.getAddr()} establish to local ")
                    if (this.contextHandler.establish(SOCKET, ctx)) {
                        sendWithNoSendLicense(ctx.channel(), SynResponse(inetConnection.localNodeAddr).asResp(syn))
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

    private fun publishSendLicense() {
        contextHandler.publishSendLicense()
    }

    /* * send with no send license * */

    fun <T> sendWithNoSendLicenseAndWaitForResponse(
        channel: Channel,
        struct: AbstractStruct,
        expect: Class<T>,
        timeout: Long = 3000,
        unit: TimeUnit = TimeUnit.MILLISECONDS
    ): Deferred<RepublicResponse<T>> = runBlocking {
        async {
            doSendAndWaitForResponse(struct, expect, timeout, unit) {
                sendWithNoSendLicense(channel, it)
            }
        }
    }

    fun <T> sendWithNoSendLicenseAndWaitForResponseAsync(
        channel: Channel,
        struct: AbstractStruct,
        expect: Class<T>,
        timeout: Long = 3000,
        unit: TimeUnit = TimeUnit.MILLISECONDS,
        consumer: (T) -> Unit,
        handler: CoroutineExceptionHandler,
    ) = runBlocking(handler) {
        launch {
            val response = doSendAndWaitForResponse(struct, expect, timeout, unit) {
                sendWithNoSendLicense(channel, it)
            }
            consumer.invoke(response.Resp())
        }
    }

    /* * normal sending * */

    fun <T> sendAndWaitForResponse(
        struct: AbstractStruct,
        expect: Class<T>,
        timeout: Long = 3000,
        unit: TimeUnit = TimeUnit.MILLISECONDS
    ) = runBlocking {
        async {
            doSendAndWaitForResponse(struct, expect, timeout, unit) {
                send(it)
            }
        }
    }

    fun <T> sendAndWaitForResponseAsync(
        struct: AbstractStruct,
        expect: Class<T>,
        timeout: Long = 3000,
        unit: TimeUnit = TimeUnit.MILLISECONDS,
        consumer: (T) -> Unit,
        handler: CoroutineExceptionHandler
    ) = runBlocking(handler) {
        async {
            val response = doSendAndWaitForResponse(struct, expect, timeout, unit) {
                send(it)
            }
            consumer.invoke(response.Resp())
        }
    }

    /* * send async * */

    fun sendAsync(struct: AbstractStruct) {
        asyncSendingQueue[struct.getRequestType()] = struct
    }

    fun sendIfHasLicense(struct: AbstractStruct): Boolean {
        if (sendLicense.hasLicense()) {
            send(struct)
            return true
        }
        return false
    }

    /* * sender * */

    /**
     * while the connection is not established, using this method for sending msg
     */
    fun sendWithNoSendLicense(channel: Channel, struct: AbstractStruct) {
        doSend(channel, struct)
    }

    /**
     * synchronized
     */
    fun send(struct: AbstractStruct) {
        sendLicense.license()
        Channel()?.also {
            this.sendWithNoSendLicense(it, struct)
        } ?: also {
            logger.error("sending struct with license but can not find channel!")
        }
    }

    /**
     * synchronized and wait for response
     */
    private fun <T> doSendAndWaitForResponse(
        struct: AbstractStruct, expect: Class<T>,
        timeout: Long = 3000, unit: TimeUnit = TimeUnit.MILLISECONDS,
        howToSend: (AbstractStruct) -> Unit
    ): RepublicResponse<T> {
        struct.raiseResp()
        val resIdentifier = struct.getRespIdentifier()
        val cdl = CountDownLatch(1)

        try {
            logger.trace("waiting response for res identifier $resIdentifier")

            currentWaitDeck.add(resIdentifier)
            waitDeck[resIdentifier] = cdl
            howToSend.invoke(struct)

            @Suppress("BlockingMethodInNonBlockingContext") if (!cdl.await(timeout, unit)) {
                logger.trace("waiting response for res identifier $resIdentifier fail")
            } else {
                logger.trace("receive response for res identifier $resIdentifier")
            }

            return ((response[resIdentifier]
                ?.let { RepublicResponse(expect.getDeclaredConstructor(ByteBuffer::class.java).newInstance(it)) }
                ?: RepublicResponse.ExceptionWith(RequestTimeoutException("Request timeout after ${timeout}ms of waiting for response from $republicNode"))))

        } finally {
            currentWaitDeck.remove(resIdentifier)
            response.remove(resIdentifier)
            waitDeck.remove(resIdentifier)
        }
    }

    override fun toString(): String {
        return "RepublicNode(host='$host', port=$port)"
    }

    fun waitForSendLicense(long: Long, tu: TimeUnit): Boolean {
        return sendLicense.license(tu.toNanos(long))
    }

    fun established(): Boolean {
        return contextHandler.established()
    }

    companion object {
        private val validClientConnection = ConcurrentSkipListSet<RepublicNode>()
        private val asyncWaitDeck = ConcurrentHashMap<Int /* identifier */, () -> Unit>()
        private val waitDeck = ConcurrentHashMap<Int /* identifier */, CountDownLatch>()
        private val response = ConcurrentHashMap<Int /* identifier */, ByteBuffer>()
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
                            if (conn.sendIfHasLicense(abstractStruct)) {
                                iterable.remove()
                            }
                        } catch (t: Throwable) { // ignore
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
                struct.computeChecksum()
                channel.write(Unpooled.copyInt(struct.totalSize()))
                struct.writeIntoChannel(channel)
                channel.flush()
                struct.getRequestType().takeIf { it != RequestTypeEnum.HEAT_BEAT }?.also {
                    logger.debug("==> send msg [type:{}]", it)
                }
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
        fun RepublicNode.getOrCreateConnection(clientMode: Boolean = false): Connection {
            if (!uniqueConnection.containsKey(this)) {
                synchronized(Connection::class.java) {
                    if (!uniqueConnection.containsKey(this)) {
                        logger.debug(
                            "init connection to ${this.host}:${this.port} ${
                                clientMode.takeIf { it }?.let { "with client mode." } ?: ""
                            }"
                        )
                        uniqueConnection[this] =
                            Connection(this.host, this.port, clientMode)
                                .also { it.tryEstablishLicense() }
                    }
                }
            }
            return uniqueConnection[this]!!
        }

        fun RepublicNode.getConnection(): Connection? = uniqueConnection[this]

        fun RepublicNode.sendAsync(struct: AbstractStruct) {
            getOrCreateConnection().sendAsync(struct)
        }

        fun RepublicNode.send(struct: AbstractStruct) {
            getOrCreateConnection().send(struct)
        }

        /**
         * actually send the struct and wait for response
         *
         * caution: may disconnect when sending
         */
        fun <T> RepublicNode.sendAndWaitingResponse(
            struct: AbstractStruct,
            expect: Class<T>,
            timeout: Long = 3000,
            unit: TimeUnit = TimeUnit.MILLISECONDS
        ): Deferred<RepublicResponse<T>> {
            return getOrCreateConnection().sendAndWaitForResponse(struct, expect, timeout, unit)
        }

        /* * receive * */

        /**
         * receive a msg and signal cdl
         */
        fun ChannelHandlerContext.receive(msg: ByteBuffer) {
            msg.ensureValid()

            val requestType = msg.getRequestType()
            val identifier = msg.getIdentifier()
            val resp = msg.isResp()

            requestType.takeIf { it != RequestTypeEnum.HEAT_BEAT }?.also {
                logger.debug("<== receive msg [type:${it}]")
            }
            val republicNode = this.tsubasa()

            if (resp) {
                val wd = waitDeck[identifier]?.also {
                    response[identifier] = msg
                    it.countDown()
                }
                val awd = asyncWaitDeck[identifier]?.also {
                    response[identifier] = msg
                    KanashiIOExecutors.execute(Runnable { it.invoke() })
                }
                if (wd == null && awd == null) {
                    logger.error("receive un deck msg [type:${requestType}] [identifier:${identifier}], may timeout or error occur!")
                }
            } else {
                val requestMapping = requestMappingRegister[requestType]
                when (requestType) {

                    /* establishing */
                    RequestTypeEnum.SYN -> this.mayConnectByRemote(Syn(msg))

                    /* send licensing */
                    RequestTypeEnum.SEND_LICENSE -> this.publishSendLicense(SendLicense(msg))

                    /* handling */
                    else -> {
                        when {
                            republicNode == null -> {
                                logger.error(
                                    "haven't established but receive msg type:[$requestType] from channel ${
                                        this.channel().remoteAddress()
                                    }"
                                )
                            }
                            requestMapping == null -> {
                                logger.error("msg type:[$requestType] from $republicNode has not custom requestMapping !!!")
                            }
                            else -> {
                                try {
                                    requestMapping.handleRequest(republicNode, msg) // 收到正常的请求
                                } catch (e: Exception) {
                                    logger.error(
                                        "Error occur while process msg type:[$requestType] from $republicNode",
                                        e
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        private fun ChannelHandlerContext.publishSendLicense(sendLicense: SendLicense) {
            val republicNode = RepublicNode.construct(sendLicense.getAddr())
            republicNode.getOrCreateConnection().publishSendLicense()
            doSend(this.channel(), SendLicenseResponse().asResp(sendLicense))
        }

        private fun ChannelHandlerContext.mayConnectByRemote(syn: Syn) {
            val republicNode = RepublicNode.construct(syn.getAddr())
            republicNode.getOrCreateConnection(syn.clientMode()).mayConnectByRemote(this, syn)
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
        private val pinLicense: License,
        private val clientMode: Boolean,
        private val currentWaitDeck: ConcurrentSkipListSet<Int>
    ) {

        @Volatile
        private var pin: ChannelHandlerContext? = null

        @Volatile
        private var socket: ChannelHandlerContext? = null

        @Volatile
        private var mode = UN_CONNECTED

        fun established() = mode != UN_CONNECTED

        /**
         * establish is do after connected or connect to other node
         *  - if connect to other node, use ChchMode.PIN mode
         *  - else use ChchMode.SOCKET mode
         */
        @Synchronized
        fun establish(mode: ChchMode, ctx: ChannelHandlerContext): Boolean {
            return if (established()) {
                logger.trace("already established to $republicNode!")
                ctx.close()
                false
            } else {
                try {
                    ctx.pipeline().addLast(
                        ChannelActiveHandler(
                            channelInactiveHook = { deEstablish() }
                        )
                    )

                    when (mode) {
                        PIN -> {
                            pin = ctx
                            socket?.close()
                        }
                        SOCKET -> {
                            socket = ctx
                            pin?.close()
                        }
                        else -> {
                            throw UnsupportedOperationException()
                        }
                    }
                    this.mode = mode
                    this.pinLicense.disable()
                    logger.info(
                        "connection establish with $republicNode using [$mode] mode with channel: ${
                            ctx.channel().remoteAddress()
                        }"
                    )
                    ctx.tsubasa(republicNode)

                    if (clientMode) {
                        validClientConnection.add(republicNode)
                    }

                    true
                } catch (e: Exception) {
                    logger.error("error occur while establish with $republicNode!", e)
                    deEstablish()
                    false
                }
            }
        }

        /**
         * deEstablish do not disconnect from channel
         * and start to try pin to the remote
         */
        @Synchronized
        fun deEstablish(): Boolean {
            return if (!established()) {
                false
            } else {
                logger.error("remote node $republicNode using [$mode] is disconnect")

                // while disconnect to remote node, wake up sleeping thread
                // this is a 'fast recovery' mode, it is unnecessary to wait the response from disconnected node
                for (identifier in currentWaitDeck.iterator()) {
                    waitDeck[identifier]?.countDown()
                }

                when (mode) {
                    PIN -> pin?.channel()?.close()
                    SOCKET -> socket?.channel()?.close()
                    UN_CONNECTED -> TODO()
                }

                this.mode = UN_CONNECTED
                this.sendLicense.disable()
                this.pinLicense.enable()
                republicNode.wakare()

                if (clientMode) { // todo may bug
                    //                    republicNode.getConnection()?.destroy()
                    validClientConnection.remove(republicNode)
                }

                true
            }
        }

        /*
         * high frequency call
         */
        fun getChannelHandlerContext(): ChannelHandlerContext? {
            return when (mode) {
                PIN -> {
                    pin
                }
                SOCKET -> {
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
        fun getPinUnEstablished(): ChannelHandlerContext? {
            if (!established()) {
                if (pin?.channel()?.isOpen == true) {
                    return pin
                } else {
                    pin = null
                }
            }
            return null
        }

        @Synchronized
        fun settingPin(it: ChannelHandlerContext?) {
            pin = it
            pinLicense.enable()
        }

        @Synchronized
        fun publishSendLicense() {
            if (mode != UN_CONNECTED) {
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