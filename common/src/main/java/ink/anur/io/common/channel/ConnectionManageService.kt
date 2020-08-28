package ink.anur.io.common.channel

import ink.anur.debug.Debugger
import ink.anur.mutex.ReentrantLocker
import ink.anur.pojo.common.AbstractStruct
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import reactor.core.publisher.Mono
import reactor.core.publisher.toMono
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Created by Anur on 2020/7/13
 */
class ConnectionManageService : ReentrantLocker() {


    private val logger = Debugger(this::class.java)
    private val connections = ConcurrentHashMap<String, Connection>()
    private val waitDeck = ConcurrentHashMap<Int, CountDownLatch>()
    private val waitResponse = ConcurrentHashMap<Int, AbstractStruct>()

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val defer = Mono.defer {
                Thread.sleep(1000)
                return@defer true.toMono()
            }

            val delaySubscription = defer.delaySubscription<String> {
                Thread.sleep(2000)
                println("zzzzzzz")
                return@delaySubscription "zzzz";
            }

            println("zzz1")

            delaySubscription.subscribe()

            Thread.sleep(10000)
        }
    }

    fun doSend(channel: Channel, struct: AbstractStruct) {
        channel.write(Unpooled.copyInt(struct.totalSize()))
        struct.writeIntoChannel(channel)
        channel.flush()
    }

    fun sendAndWaitingResponse(channel: Channel, struct: AbstractStruct, waitTime: Long, timeUnit: TimeUnit) {
        val requestSign = struct.getRequestSign()
        val cdl = CountDownLatch(1)
        waitDeck[requestSign] = cdl

        val sendDefer = Mono.defer {
            Mono.just {
                try {
                    doSend(channel, struct)
                    return@just true
                } catch (t: Throwable) {
                    logger.error("sending msg, Type '${struct.getRequestSign()}' error")
                }
                return@just false
            }
        }

    }

    fun sync(server: String, channel: Channel) {
        val connection = connections[server]
        if (connection == null || connection.connectionStatus == ConnectionStatus.CONNECTING) {
            val neoConnection = Connection(server, ConnectionStatus.CONNECTING, channel)
            doSend(channel, neoConnection.register)
        }
    }


}