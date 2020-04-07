package ink.anur.util

import org.slf4j.LoggerFactory

/**
 * Created by Anur IjuoKaruKas on 2020/4/3
 */
object CoreUtils {

    private val logger = LoggerFactory.getLogger(this.javaClass)

    fun swallow(whatEver: () -> Unit) {
        try {
            whatEver.invoke()
        } catch (e: Exception) {
            logger.error("发生了错误，但是这个错误可以被忽略", "", e)
        }
    }
}