package ink.anur.io.common.transport

import com.sun.corba.se.spi.transport.CorbaConnection.ESTABLISHED
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
import java.lang.UnsupportedOperationException
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.*

/**
 * Created by Anur on 2020/10/3
 */
class Connection(private val host: String, private val port: Int) {

    @NigateInject
    private lateinit var inetConnection: InetConfiguration

    init {
        Nigate.injectOnly(this)
    }

    private val locker = ReentrantLocker()

    private val shutDownHooker = ShutDownHooker()

    private val connectLicense = ReConnectableClient.License()

    private val sendLicense = ReConnectableClient.License()

    private val contextHandler = ChannelHandlerContextHandler(sendLicense)

    private val asyncSendingQueue = ConcurrentHashMap<RequestTypeEnum, AbstractStruct>()

    /**
     * manage the channelHandlerContext that shield pin mode and socket mode
     */
    private class ChannelHandlerContextHandler(private val sendLicense: ReConnectableClient.License) {

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
                    }
                    ChchMode.SOCKET -> {
                        socket = chc
                    }
                    else -> {
                        throw UnsupportedOperationException()
                    }
                }
                this.mode = mode
                this.sendLicense.enable()
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
                successfulConnected?.invoke()
                true
            }
        }

        @Synchronized
        fun getPinUnEstablished(): ChannelHandlerContext? = takeIf { !established() }?.let { pin }

        enum class ChchMode {
            PIN, SOCKET, UN_CONNECTED
        }
    }

    /**
     * netty client inner connection
     */
    private val client: ReConnectableClient = ReConnectableClient(host, port, shutDownHooker) {
    }

    init {
        KanashiIOExecutors.execute(client)
    }

    private fun establish(chchMode: ChannelHandlerContextHandler.ChchMode, ctx: ChannelHandlerContext,
                          successfulConnected: (() -> Unit)? = null, doAfterDisConnected: (() -> Unit)? = null) {
        this.contextHandler.establish(chchMode, ctx, successfulConnected)
        ctx.pipeline().addLast(ChannelInactiveHandler {
            this.contextHandler.disConnect(doAfterDisConnected)
        })
    }

    /* * syn * */

    private fun tryEstablish() {
        while (true) {
            connectLicense.license()

            if (contextHandler.established()) {
                logger.trace("Connection is already established, so disable [ConnectLicense]")
                connectLicense.disable()
            }

            contextHandler.getPinUnEstablished()?.let {
                logger.trace("now try to establish")

                try {
                    val sendAndWaitForResponse = sendAndWaitForResponse(Syn(inetConnection.localServerAddr), it.channel())
                    if (sendAndWaitForResponse != null) {
                        this.establish(ChannelHandlerContextHandler.ChchMode.PIN, it,
                                {
                                    logger.info("Connection to node $this is established by [Pin Mode]")
                                },
                                {
                                    logger.info("Connection to node $this is disConnected by [Pin Mode]")
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

    private fun mayConnectByRemote(ctx: ChannelHandlerContext, synResponse: SynResponse, doAfterDisConnected: (() -> Unit)) {
        logger.trace("remote node ${ctx.channel().remoteAddress()} try to establish with local server")
        locker.lockSupplier {
            if (connectionStatus.isEstablished() && ctx != this.ctx) {
                // if is already establish then close the remote channel
                logger.trace("remote node ${ctx.channel().remoteAddress()} already establish and ctx will be close")
                ctx.close()
            } else {
                try {
                    sendWithNoSendLicense(ctx.channel(), synResponse)
                } catch (e: Throwable) {
                    logger.trace("sending [SynResponse] to the remote node but can't get response, retrying...")
                }
            }
        }
    }

    private fun tryConnect() {
        this.connectLicense.enable()
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

        return Mono.fromSupplier {
            channel?.also { sendWithNoSendLicense(channel, struct) } ?: send(struct)
            logger.trace("able to wait response identifier $identifier ${channel?.let { ", send with no send license mode" }}")
            cdl.await()
            response[identifier]
        }
                .doFinally {
                    logger.trace("waiting for identifier $identifier: final -> $it")
                    response.remove(identifier)
                    waitDeck.remove(identifier)
                }
                .block(Duration.ofMillis(unit.toMillis(timeout)))
    }

    private fun sendIfHasLicense(struct: AbstractStruct) {
        if (sendLicense.hasLicense()) {
            val channel = ctx!!.channel()
            channel.write(Unpooled.copyInt(struct.totalSize()))
            ByteBufferUtil.writeUnsignedInt(struct.buffer, 0, struct.computeChecksum())
            struct.writeIntoChannel(channel)
            channel.flush()
        }
    }

    override fun toString(): String {
        return "RepublicNode(host='$host', port=$port)"
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {

            println(TimeUtil.getTimeFormatted())
            try {
                Mono.defer {
                    Thread.sleep(100000)
                    Mono.just { }
                }
                        .publishOn(Schedulers.elastic())
                        .map { Thread.sleep(100000) }
                        .block(Duration.ofMillis(2000))
            } catch (t: Throwable) {
                println()
            }
            println(TimeUtil.getTimeFormatted())
        }

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
            return uniqueConnection.computeIfAbsent(this)
            { Connection(this.host, this.port).also { it.tryConnect() } }
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