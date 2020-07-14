package ink.anur.io.common.channel

import ink.anur.mutex.ReentrantLocker
import ink.anur.pojo.Register
import ink.anur.pojo.common.AbstractStruct
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import reactor.core.publisher.Mono
import reactor.core.publisher.toMono
import java.lang.RuntimeException
import java.time.Duration
import java.util.concurrent.CountDownLatch

/**
 * Created by Anur on 2020/7/13
 */
class ConnectionManageService : ReentrantLocker() {

    private val connections = mutableMapOf<String, Connection>()

    private val waitDeck = mutableMapOf<Int, CountDownLatch>()
    private val waitResponse = mutableMapOf<Int, AbstractStruct>()

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            println(11111)
            Mono.defer {
                Thread.sleep(1000)
                return@defer true.toMono()
            }.switchIfEmpty(Mono.just(false))
                    .flatMap { Mono.just("zzz $it zzz") }
                    .timeout(Duration.ofSeconds(3))
            println(11111)
            println(11111)

            Thread.sleep(10000)
        }
    }

    fun send(channel: Channel, struct: AbstractStruct) {
        val cdl = CountDownLatch(1)
        waitDeck[struct.getRequestSign()] = cdl
        Mono.defer {
            channel.write(Unpooled.copyInt(struct.totalSize()))
            struct.writeIntoChannel(channel)
            channel.flush()
            return@defer Unit.toMono()
        }.flatMap {
            cdl.await()
            Mono.just(waitResponse[waitDeck])
        }
    }

    fun syn(server: String, channel: Channel) {
        val connection = connections[server]
        if (connection == null || connection.connectionStatus == ConnectionStatus.CONNECTING) {
            val neoConnection = Connection(server, ConnectionStatus.CONNECTING, channel)
            send(channel, neoConnection.register)
        }
    }
}