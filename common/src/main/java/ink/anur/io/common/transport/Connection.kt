package ink.anur.io.common.transport

import ink.anur.common.KanashiExecutors
import ink.anur.common.struct.RepublicNode
import ink.anur.core.common.RequestMapping
import ink.anur.debug.Debugger
import ink.anur.io.client.ReConnectableClient
import ink.anur.io.common.handler.ChannelInactiveHandler
import ink.anur.mutex.ReentrantLocker
import ink.anur.pojo.common.AbstractStruct
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.util.ByteBufferUtil
import ink.anur.util.TimeUtil
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.*
import kotlin.system.exitProcess

/**
 * Created by Anur on 2020/10/3
 */
class Connection(private val host: String, private val port: Int, private val ts: Long, @Volatile var connectionStatus: ConnectionStatus) {

    companion object {
        private val waitDeck = ConcurrentHashMap<Int /* sign */, CountDownLatch>()
        private val response = ConcurrentHashMap<Int /* sign */, ByteBuffer>()
        private val uniqueConnection = ConcurrentHashMap<RepublicNode, Connection>()
        private val requestMappingRegister = mutableMapOf<RequestTypeEnum, RequestMapping>()
        private val logger = Debugger(this::class.java)

        private val t = Thread {
            while (true) {
                for (conn in uniqueConnection.values) {
                    val struct = conn.asyncSendingQueue.poll()

                    while (struct != null) {
                        try {
                            conn.sendIfHasLicense(struct)
                        } catch (t: Throwable) {
                            // ignore
                            logger.error("Async sending struct type:[${struct.getRequestType()}] to $conn error occur!")
                            conn.asyncSendingQueue.push(struct)
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
         * 注册 RequestMapping
         */
        fun registerRequestMapping(typeEnum: RequestTypeEnum, requestMapping: RequestMapping) {
            requestMappingRegister[typeEnum] = requestMapping
        }

        private fun RepublicNode.getConnection(): Connection {
            return uniqueConnection.computeIfAbsent(this) { Connection(this.host, this.port, TimeUtil.getTime(), ConnectionStatus.UN_CONNECTED) }
        }

        private fun ChannelHandlerContext.getRepublicNode(): RepublicNode {
            return RepublicNode.construct(this.channel().remoteAddress() as InetSocketAddress)
        }

        /**
         * receive a msg and signal cdl
         */
        fun ChannelHandlerContext.receive(msg: ByteBuffer) {
            val republicNode = getRepublicNode().also { it.getConnection().mayConnectByRemote(this) }

            val requestType = RequestTypeEnum.parseByByteSign(msg.getInt(AbstractStruct.RequestTypeOffset))
            val identifier = msg.getInt(AbstractStruct.IdentifierOffset)
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
            val identifier = struct.getIdentifier()
            val cdl = CountDownLatch(1)

            return Mono
                    .create<AbstractStruct> {
                        send(struct)
                    }
                    .publishOn(Schedulers.elastic())
                    .map {
                        cdl.await()
                        expect.getConstructor(ByteBuffer::class.java).newInstance(response[identifier])
                    }
                    .timeout(Duration.ofMillis(unit.toMillis(timeout)))
                    .doFinally {
                        response.remove(identifier)
                        waitDeck.remove(identifier)
                    }
                    .block() as T
        }
    }

    @Volatile
    private var ctx: ChannelHandlerContext? = null

    private val locker = ReentrantLocker()

    private val shutDownHooker = ShutDownHooker()

    private val connectLicense = ReConnectableClient.License()

    private val sendLicense = ReConnectableClient.License()

    private val asyncSendingQueue = LinkedBlockingDeque<AbstractStruct>()

    private fun sendToQueue(struct: AbstractStruct) {
        asyncSendingQueue.push(struct)
    }

    private fun send(struct: AbstractStruct) {
        sendLicense.license()
        val channel = ctx!!.channel()
        channel.write(Unpooled.copyInt(struct.totalSize()))
        ByteBufferUtil.writeUnsignedInt(struct.buffer, 0, struct.computeChecksum())
        struct.writeIntoChannel(channel)
        channel.flush()
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

    private val client: ReConnectableClient = ReConnectableClient(host, port, connectLicense, shutDownHooker) {
        if (connectionStatus.isEstablished()) {
            this.connectLicense.disable()
            this.shutDownHooker.shutdown()
            logger.info("Already connect to remote server $this, so stop try connect to remote server")
        } else {
            this.connect(it) {
                logger.info("Connect to remote server $this")
            }
        }
    }

    init {
        client.start()
    }

    private fun mayConnectByRemote(ctx: ChannelHandlerContext) {
        locker.lockSupplier {
            if (connectionStatus.isEstablished() && ctx != this.ctx) {
                ctx.close()
            } else {
                this.connect(ctx) {
                    logger.info("Connect to remote server $this cause passive connection")
                }
            }
        }
    }

    private fun connect(ctx: ChannelHandlerContext, successfulConnected: () -> Unit) {
        return locker.lockSupplier {
            connectionStatus.isEstablished()

            this.connectionStatus = ConnectionStatus.ESTABLISHED
            this.ctx = ctx
            this.sendLicense.enable()
            ctx.pipeline().addLast(ChannelInactiveHandler {
                this.disconnect()
            })
            successfulConnected.invoke()
        }
    }

    private fun disconnect() {
        locker.lockSupplier {
            if (connectionStatus.isEstablished()) {
                logger.info("Disconnect from remote server $this")
                this.sendLicense.disable()
                this.ctx = null
                this.connectionStatus = ConnectionStatus.UN_CONNECTED
                this.connectLicense.enable()
            }
        }
    }

    override fun toString(): String {
        return "RepublicNode(host='$host', port=$port)"
    }


    enum class ConnectionStatus {

        UN_CONNECTED,

        ESTABLISHED;

        fun isEstablished(): Boolean {
            return this == ESTABLISHED
        }
    }
}