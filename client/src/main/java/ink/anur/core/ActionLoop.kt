package ink.anur.core

import ink.anur.common.KanashiExecutors
import ink.anur.debug.Debugger
import ink.anur.inject.bean.NigateAfterBootStrap
import ink.anur.inject.bean.NigateBean
import ink.anur.inject.bean.NigateInject
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Created by Anur IjuoKaruKas on 2021/12/26
 */
@ObsoleteCoroutinesApi
@NigateBean
class ActionLoop {

    @NigateInject
    private lateinit var kanashiClientConnector: KanashiClientConnector

    private val logger = Debugger(this::class.java)

    @NigateAfterBootStrap
    fun ClientLoop() {
        KanashiExecutors.execute(doLoop())
    }

    fun doLoop() = Runnable {

        val ticker = ticker(200, 0)
        while (true) {
            kanashiClientConnector.ReportAndAcquireRpcRouteInfo()

            runBlocking {
                launch(KanashiExecutors.Dispatcher) {
                    ticker.receive()
                }
            }
        }
    }
}