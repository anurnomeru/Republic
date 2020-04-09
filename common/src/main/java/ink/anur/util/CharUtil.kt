package ink.anur.util

import kotlin.random.Random

/**
 * Created by Anur IjuoKaruKas on 2020/4/9
 */
class CharUtil {
    companion object {
        private val random = Random(100)

        private val str = "0123456789abcdefghijklmnopqrstuvwxyz"

        fun randomName(length: Int): String {
            val sb = StringBuffer()
            val charLength = str.length

            for (i in 0..length)
                sb.append(str[random.nextInt(charLength)])
            return sb.toString()
        }
    }
}