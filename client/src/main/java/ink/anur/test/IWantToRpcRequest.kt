package ink.anur.test

import ink.anur.common.KanashiExecutors
import ink.anur.inject.KanashiRpcInject
import ink.anur.inject.NigateAfterBootStrap
import ink.anur.inject.NigateBean

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
            var current = count
            while (true) {
                Thread.sleep(1000)
                val now = count
                println("每秒流速 ${now - current}")
                current = now
            }
        })

        KanashiExecutors.execute(Runnable {
            while (true) {
                val rpcResult = whatEverInterface.rpc("Anur", 996L)
                count++
            }
        }
        )
    }
}