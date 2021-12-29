package ink.anur.core

import ink.anur.common.KanashiExecutors
import ink.anur.inject.bean.NigateAfterBootStrap
import ink.anur.inject.bean.NigateBean
import ink.anur.inject.bean.NigateInject

/**
 * Created by Anur IjuoKaruKas on 2021/12/26
 */
@NigateBean
class ActionLoop {

    @NigateInject
    private lateinit var kanashiClientConnector: KanashiClientConnector

    @NigateAfterBootStrap
    fun ClientLoop() {
        KanashiExecutors.execute(doLoop())
    }

    fun doLoop(): Runnable {
        return Runnable {
            while (true) {
                kanashiClientConnector.ReportAndAcquireRpcRouteInfo()
            }
        }
    }
}