package ink.anur

import ink.anur.inject.bean.Nigate


/**
 * Created by Anur IjuoKaruKas on 2020/2/22
 */
object Bootstrap {

    @Volatile
    private var RUNNING = true

    @JvmStatic
    fun main(args: Array<String>) {
        Nigate
        while (RUNNING) {
            Thread.sleep(10000)
        }
    }

}