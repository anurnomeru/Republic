package ink.anur.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Created by Anur on 2020/9/8
 */
class TimeUtil {
    companion object {
        private val dtf: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

        fun getTime(): Long = System.currentTimeMillis()

        fun getLocalDateTime(): LocalDateTime = LocalDateTime.now()

        fun getTimeFormatted(): String = LocalDateTime.now().format(dtf)

        fun getTimeFormatted(createdTs: Long): String = dtf.format(
            LocalDateTime.ofInstant(
                Instant.ofEpochMilli(createdTs),
                TimeZone.getDefault().toZoneId()
            )
        )
    }
}