package ink.anur.test

import ink.anur.inject.KanashiRpc

/**
 * Created by Anur IjuoKaruKas on 2020/4/10
 */
@KanashiRpc
class Provider : WhatEverInterface {

    /**
     * 实现 provider 的逻辑
     */
    override fun rpc(): List<Any> {
        return arrayListOf("Anur", "LaLaLa")
    }
}