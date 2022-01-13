package ink.anur

import SimpleProvider
import ink.anur.common.KanashinUlimitedExecutors
import ink.anur.core.OnRpcValid
import ink.anur.inject.bean.NigateAfterBootStrap
import ink.anur.inject.bean.NigateBean
import ink.anur.inject.bean.NigateInject
import ink.anur.inject.rpc.Republic
import ink.anur.inject.rpc.RepublicBean
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * Created by Anur IjuoKaruKas on 2022/1/13
 */
@NigateBean
open class SimpleConsumer {

    @Republic
    private lateinit var simpleProvider: SimpleProvider

    private val logger = LoggerFactory.getLogger(this::class.java)

    @NigateAfterBootStrap
    fun rpcLoop() {
        Thread.sleep(5000)
        while (true) {
            try {
                logger.info("receive from provider: ${simpleProvider.foo()}")
            } catch (e: Exception) {
                logger.info("$e")
            }
        }
    }
}