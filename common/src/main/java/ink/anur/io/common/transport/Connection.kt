package ink.anur.io.common.transport

import ink.anur.common.struct.RepublicNode
import ink.anur.debug.Debugger
import ink.anur.io.client.ReConnectableClient
import ink.anur.io.common.handler.ChannelInactiveHandler
import ink.anur.mutex.ReentrantLocker
import ink.anur.pojo.common.AbstractStruct
import ink.anur.util.ByteBufferUtil
import ink.anur.util.TimeUtil
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Created by Anur on 2020/10/3
 */
class Connection(private val host: String, private val port: Int, private val ts: Long, @Volatile var connectionStatus: ConnectionStatus) {

    companion object {
        private val waitDeck = ConcurrentHashMap<Int /* sign */, CountDownLatch>()
        private val response = ConcurrentHashMap<Int /* sign */, AbstractStruct>()
        private val unique = ConcurrentHashMap<Connection, Connection>()
        private val logger = Debugger(this::class.java)

        private fun RepublicNode.getConnection(): Connection {
            val connection = Connection(host, port, TimeUtil.getTime(), ConnectionStatus.UN_CONNECTED)
            return unique.computeIfAbsent(connection) { connection }
        }

        private fun ChannelHandlerContext.getConnection(): Connection {
            val socketAddress = this.channel().remoteAddress() as InetSocketAddress
            val connection = Connection(socketAddress.hostName, socketAddress.port, TimeUtil.getTime(), ConnectionStatus.UN_CONNECTED)
            return unique.computeIfAbsent(connection) { connection }
        }

        fun RepublicNode.send(struct: AbstractStruct) {
            getConnection().send(struct)
        }

        /**
         * receive a msg and signal cdl
         */
        fun ChannelHandlerContext.receive(struct: AbstractStruct) {
            getConnection().mayConnectByRemote(this)

            val sign = struct.getIdentifier()
            response[sign] = struct
            waitDeck[sign]?.countDown()
        }

        /**
         * actually send the struct and wait for response
         *
         * caution: may disconnect when sending
         */
        fun <T> RepublicNode.sendAndWaitingResponse(struct: AbstractStruct, timeout: Long = 3000, unit: TimeUnit = TimeUnit.MILLISECONDS): T {
            val sign = struct.getIdentifier()
            val cdl = CountDownLatch(1)

            return Mono
                    .create<AbstractStruct> {
                        send(struct)
                    }
                    .publishOn(Schedulers.elastic())
                    .map {
                        cdl.await()
                        response[sign]
                    }
                    .timeout(Duration.ofMillis(unit.toMillis(timeout)))
                    .doFinally {
                        response.remove(sign)
                        waitDeck.remove(sign)
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

    private fun send(struct: AbstractStruct) {
        sendLicense.license()
        val channel = ctx!!.channel()
        channel.write(Unpooled.copyInt(struct.totalSize()))
        ByteBufferUtil.writeUnsignedInt(struct.buffer, 0, struct.computeChecksum())
        struct.writeIntoChannel(channel)
        channel.flush()
    }

    private val client: ReConnectableClient = ReConnectableClient(host, port, connectLicense, shutDownHooker) {
        if (connectionStatus.isEstablished()) {
            this.connectLicense.disable()
            this.shutDownHooker.shutdown()
            logger.info("Already connect to remote server {}:{}, so stop try connect to remote server", host, port)
        } else {
            this.connect(it) {
                logger.info("Connect to remote server {}:{}", host, port)
            }
        }
    }

    private fun mayConnectByRemote(ctx: ChannelHandlerContext) {
        locker.lockSupplier {
            if (connectionStatus.isEstablished() && ctx != this.ctx) {
                ctx.close()
            } else {
                this.connect(ctx) {
                    logger.info("Connect to remote server {}:{} cause passive connection", host, port)
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
                logger.info("Disconnect from remote server {}:{}", host, port)
                this.sendLicense.disable()
                this.ctx = null
                this.connectionStatus = ConnectionStatus.UN_CONNECTED
                this.connectLicense.enable()
            }
        }
    }

    /**
     * 主动连接 : SYN_SENT -> ESTABLISHED
     * 被动连接 ：SYN_RECV -> ESTABLISHED
     */
    enum class ConnectionStatus {

        UN_CONNECTED,

        ESTABLISHED;

        fun isEstablished(): Boolean {
            return this == ESTABLISHED
        }
    }
}