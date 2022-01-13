package ink.anur

import SimpleProvider
import ink.anur.common.KanashinUlimitedExecutors
import ink.anur.inject.bean.NigateAfterBootStrap
import ink.anur.inject.bean.NigateBean
import ink.anur.inject.rpc.Republic
import ink.anur.inject.rpc.RepublicBean
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * Created by Anur IjuoKaruKas on 2022/1/13
 */
@NigateBean
class SimpleConsumer {

    @Republic
    private lateinit var simpleProvider: SimpleProvider

    private val logger = LoggerFactory.getLogger(this::class.java)

    @NigateAfterBootStrap
    fun rpcLoop() {
        val ticker = ticker(2000)

        KanashinUlimitedExecutors.execute(Runnable {
            runBlocking {
                while (true){
                    ticker.receive()
                    try {
                        logger.info("receive from provider: ${simpleProvider.foo()}")
                    } catch (e: Exception) {
                        logger.info("error when sending rpc request")
                    }
                }
            }
        })

    }
}