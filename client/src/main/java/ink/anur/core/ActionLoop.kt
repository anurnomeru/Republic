package ink.anur.core

import ink.anur.common.KanashiExecutors
import ink.anur.inject.bean.NigateAfterBootStrap
import ink.anur.inject.bean.NigateBean
import ink.anur.inject.bean.NigateInject
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.runBlocking

/**
 * Created by Anur IjuoKaruKas on 2021/12/26
 */
@ObsoleteCoroutinesApi
@NigateBean
class ActionLoop {

    @NigateInject
    private lateinit var kanashiClientConnector: KanashiClientConnector

    @NigateAfterBootStrap
    fun ClientLoop() {
        KanashiExecutors.execute(doLoop())
    }

    fun doLoop() = Runnable {
        val ticker = ticker(200, 0)
        while (true) {
            runBlocking {
                ticker.receive()
            }

            kanashiClientConnector.ReportAndAcquireRpcRouteInfo()
        }
    }
}