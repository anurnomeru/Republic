package ink.anur

import ink.anur.common.KanashiExecutors
import ink.anur.inject.KanashiRpcInject
import ink.anur.inject.NigateAfterBootStrap
import ink.anur.inject.NigateBean
import ink.anur.test.WhatEverInterface

/**
 * Created by Anur IjuoKaruKas on 2020/4/10
 */
@NigateBean
class IWantToRpcRequest {

    @KanashiRpcInject
    private lateinit var whatEverInterface: WhatEverInterface

    @Volatile
    var count = 0

    @NigateAfterBootStrap
    private fun afterBootstrap() {
        KanashiExecutors.execute(Runnable {
            while (true) {
                count++
                println("发送了 ${count}")
                val rpcResult = whatEverInterface.rpc("Anur", 996L)
            }
        }
        )
    }
}