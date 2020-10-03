package ink.anur.io.common.transport

import ink.anur.common.struct.RepublicNode
import ink.anur.config.InetConfiguration
import ink.anur.exception.SynErrorException
import ink.anur.inject.bean.Nigate
import ink.anur.inject.bean.NigateInject
import ink.anur.io.client.ReConnectableClient
import ink.anur.mutex.ReentrantLocker
import ink.anur.pojo.common.AbstractStruct
import ink.anur.pojo.connection.Establish
import ink.anur.pojo.connection.Syn
import ink.anur.pojo.connection.SynAck
import ink.anur.util.ByteBufferUtil
import ink.anur.util.TimeUtil
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Created by Anur on 2020/7/13
 *
 * manage a connection's status by channel
 */
open class Connection(private val host: String, private val port: Int, private val ts: Long, @Volatile var connectionStatus: ConnectionStatus) {

    companion object {

        private val waitDeck = ConcurrentHashMap<Int /* sign */, CountDownLatch>()
        private val response = ConcurrentHashMap<Int /* sign */, AbstractStruct>()
        private val unique = ConcurrentHashMap<Connection, Connection>()

        private fun getConnection(host: String, port: Int, status: ConnectionStatus): Connection {
            val connection = Connection(host, port, TimeUtil.getTime(), status)
            return unique.computeIfAbsent(connection) { connection }
        }

        fun Channel.getNode(): RepublicNode {

        }

        /**
         * receive a msg and signal cdl
         */
        fun Channel.receive(struct: AbstractStruct) {
            val sign = struct.getIdentifier()
            response[sign] = struct
            waitDeck[sign]?.countDown()
        }

        fun RepublicNode.isEstablished(): Boolean {
            return getConnection(this.host, this.port, ConnectionStatus.SYN_SEND).isEstablished()
        }

        fun RepublicNode.sendSync(): Boolean {
            return getConnection(this.host, this.port, ConnectionStatus.SYN_SEND)
                    .tryConnect(this)
        }

        fun RepublicNode.recvSync(syn: Syn): Boolean {
            return getConnection(this.host, this.port, ConnectionStatus.SYN_RECV)
                    .recvConnect(syn, this)
        }
    }

    @Volatile
    private var channel: Channel? = null

    @NigateInject
    private lateinit var inetConfiguration: InetConfiguration

    private val shutDownHooker = ReConnectableClient.ShutDownHooker()

    private val connectLicense = ReConnectableClient.License()

    private val sendLicense = ReConnectableClient.License()

    private val client: ReConnectableClient = ReConnectableClient(host, port, connectLicense, shutDownHooker, {
        if (it.sendSync()) {
            channel = it
        } else {
            channel = null
            connectLicense.disable()
            shutDownHooker.shutdown()
        }
    })

    init {
        Nigate.injectOnly(this)
    }

    /**
     * make sure that 1 process modify this connection
     */
    private val synLock = ReentrantLocker()

    private val recvLock = ReentrantLocker()

    /**
     * actually send the struct
     *
     * caution: may disconnect when sending
     */
    fun doSend(struct: AbstractStruct) {
        sendLicense.license()
        val c = channel!!
        c.write(Unpooled.copyInt(struct.totalSize()))
        ByteBufferUtil.writeUnsignedInt(struct.buffer, 0, struct.computeChecksum())
        struct.writeIntoChannel(c)
        c.flush()
    }

    /**
     * actually send the struct and wait for response
     *
     * caution: may disconnect when sending
     */
    fun <T> sendAndWaitingResponse(struct: AbstractStruct, timeout: Long = inetConfiguration.timeoutMs, unit: TimeUnit = TimeUnit.MILLISECONDS): T {
        val sign = struct.getIdentifier()
        val cdl = CountDownLatch(1)

        return Mono
                .create<AbstractStruct> {
                    doSend(struct)
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

    /**
     * try connect, make sure that only one process try connection to remote server
     *
     * it will block until connect to remote server
     *
     * @return true mean this channel successful connect to remote sever
     * or channel should be disconnected from remote server
     */
    fun tryConnect(channel: Channel): Boolean {
        val connect =
                if (isEstablished()) {
                    Connect.CONNECTED
                } else {
                    synLock.lockSupplierCompel {
                        if (connectionStatus == ConnectionStatus.ESTABLISHED) {
                            return@lockSupplierCompel Connect.CONNECTED
                        }

                        val synAck: SynAck = sendAndWaitingResponse(
                                Syn(ts)
                        )
                                ?: throw SynErrorException()

                        if (synAck.reject()) {
                            return@lockSupplierCompel Connect.REJECT
                        } else {
                            doSend(
                                    Establish(ts).asResponse(synAck)
                            )
                            this.establish(channel)
                            return@lockSupplierCompel Connect.SUCCESSFUL
                        }
                    }
                }

        if (connect.isReject()) {
            tryConnect(channel)
        }

        return connect.isSuccessful()
    }

    fun recvConnect(syn: Syn, channel: Channel): Boolean {
        if (isEstablished()) {
            return false
        } else {
            return recvLock.lockSupplierCompel {
                val remoteTs = syn.getTs()
                if (remoteTs < ts) { // mean remote syn send before current
                    val establish: Establish = sendAndWaitingResponse(
                            SynAck(ts).asResponse(syn)
                    )
                    this.establish(channel)
                } else {
                    doSend(
                            SynAck(SynAck.Reject).asResponse(syn)
                    )
                    false
                }
            }
        }
    }

    @Synchronized
    fun establish(channel: Channel): Boolean {
        if (isEstablished()) {
            return false
        }

        this.connectionStatus = ConnectionStatus.ESTABLISHED
        this.channel = channel
        return true
    }

    fun isEstablished(): Boolean {
        return connectionStatus.isEstablished()
    }

    enum class Connect {
        CONNECTED,
        SUCCESSFUL,
        REJECT;

        fun isReject(): Boolean {
            return this == REJECT
        }

        fun isSuccessful(): Boolean {
            return this == SUCCESSFUL
        }
    }

    /**
     * 主动连接 : SYN_SENT -> ESTABLISHED
     * 被动连接 ：SYN_RECV -> ESTABLISHED
     */
    enum class ConnectionStatus {

        /**
         * 此状态还不可发送
         */
        SYN_SEND,

        /**
         * 此状态还不可发送
         */
        SYN_RECV,

        /**
         * 此状态可发送
         */
        ESTABLISHED;

        fun isEstablished(): Boolean {
            return this == ESTABLISHED
        }
    }
}