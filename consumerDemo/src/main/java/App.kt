import ink.anur.inject.bean.Nigate
import kotlinx.coroutines.runBlocking

/**
 * Created by Anur IjuoKaruKas on 2022/1/12
 */
object App {

    @JvmStatic
    fun main(args: Array<String>) {
        runBlocking { Nigate.start(args) }
    }
}