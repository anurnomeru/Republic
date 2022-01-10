package ink.anur

import ink.anur.inject.bean.Nigate
import kotlinx.coroutines.runBlocking
import net.sf.cglib.proxy.Enhancer

/**
 * Created by Anur IjuoKaruKas on 2020/2/22
 */
object Bootstrap {

    @JvmStatic
    fun main(args: Array<String>) {
        runBlocking { Nigate.start(args) }
    }
}