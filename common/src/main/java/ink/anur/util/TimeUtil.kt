package ink.anur.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Created by Anur on 2020/9/8
 */
class TimeUtil {
    companion object {
        private val dtf: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

        fun getTime(): Long = System.currentTimeMillis()

        fun getLocalDateTime(): LocalDateTime = LocalDateTime.now()

        fun getTimeFormatted(): String = LocalDateTime.now().format(dtf)
    }
}