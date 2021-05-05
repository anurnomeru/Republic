package ink.anur.exception.io

import ink.anur.exception.KanashiException


/**
 * Created by Anur IjuoKaruKas on 2021/4/30
 */
class RequestTimeoutException(message: String) : KanashiException(message){
    companion object{
        @JvmStatic
        fun main(args: Array<String>) {
            println("1".toInt()==1)
        }
    }
}