package ink.anur.io.common.channel

import ink.anur.debug.Debugger
import ink.anur.inject.bean.NigateBean
import ink.anur.mutex.ReentrantLocker
import ink.anur.pojo.common.AbstractStruct
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Created by Anur on 2020/7/13
 */
@NigateBean
class TransportCenterService : ReentrantLocker() {

    private val logger = Debugger(this::class.java)
    private val connections = ConcurrentHashMap<String, Connection>()
    private val waitDeck = ConcurrentHashMap<Int /* sign */, CountDownLatch>()
    private val response = ConcurrentHashMap<Int /* sign */, AbstractStruct>()

    fun doSend(channel: Channel, struct: AbstractStruct) {
        channel.write(Unpooled.copyInt(struct.totalSize()))
        struct.writeIntoChannel(channel)
        channel.flush()
    }

    fun receive(struct: AbstractStruct) {
        val sign = struct.getRequestSign()
        response[sign] = struct
        waitDeck[sign]?.countDown()
    }

    fun sendAndWaitingResponse(channel: Channel, struct: AbstractStruct, timeout: Long, unit: TimeUnit): AbstractStruct? {
        val sign = struct.getRequestSign()
        val cdl = CountDownLatch(1)

        return Mono
                .create<AbstractStruct> {

                    doSend(channel, struct)
                    cdl.await()
                    it.success(response[sign])
                }
                .timeout(Duration.ofMillis(unit.toMillis(timeout)))
                .doFinally {
                    response.remove(sign)
                    waitDeck.remove(sign)
                }
                .block()
    }

//    /**
//     * mark a channel sync
//     */
//    fun sync(server: String, channel: Channel) {
//        val connection = connections[server]
//        if (connection == null || connection.connectionStatus == ConnectionStatus.CONNECTING) {
//            val neoConnection = Connection(server, ConnectionStatus.CONNECTING, channel)
//            val sendAndWaitingResponse = sendAndWaitingResponse(channel, neoConnection.register,)
//        }
//    }
}