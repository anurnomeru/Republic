package ink.anur.test

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

    @NigateAfterBootStrap
    private fun afterBootstrap() {
        val rpcResult = whatEverInterface.rpc("Anur", 996L)
        for (any in rpcResult) {
            println(any)
        }
    }
}