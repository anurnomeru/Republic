package ink.anur

import ink.anur.inject.KanashiRpcInject
import ink.anur.inject.NigateAfterBootStrap
import ink.anur.inject.NigateBean

/**
 * Created by Anur IjuoKaruKas on 2020/4/10
 */
@NigateBean
class Tester {

    @KanashiRpcInject
    private lateinit var tester:TestInter

    @NigateAfterBootStrap
    private fun test(){

        tester.test111()
    }
}