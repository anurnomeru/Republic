package ink.anur.timewheel

import java.util.concurrent.DelayQueue

/**
 * Created by Anur on 2020/7/11
 */
class TimeWheel(val tickMs: Long, val wheelSize: Int, val delayQueue: DelayQueue<Bucket>, currentTimestamp: Long) {

    /** 时间跨度  */
    private val interval = tickMs * wheelSize

    /** 时间轮指针  */
    private var currentTimestamp: Long = currentTimestamp - currentTimestamp % tickMs

    /** 槽  */
    private val buckets = arrayOfNulls<Bucket>(wheelSize)

    /** 上层时间轮  */
    @Volatile
    private var overflowWheel: TimeWheel? = null

    init {
        for (i in 0 until wheelSize) {
            buckets[i] = Bucket()
        }
    }

    private fun getOverflowWheel(): TimeWheel {
        if (overflowWheel == null) {
            synchronized(this) {
                if (overflowWheel == null) {
                    overflowWheel = TimeWheel(interval, wheelSize, delayQueue, currentTimestamp)
                }
            }
        }
        return overflowWheel!!
    }

    /**
     * 添加任务到某个时间轮
     */
    fun addTask(timedTask: TimedTask): Boolean {
        val expireTimestamp = timedTask.getExpireTimestamp()
        val delayMs = expireTimestamp - currentTimestamp
        if (delayMs < tickMs) { // 到期了
            return false
        } else {

            // 扔进当前时间轮的某个槽中，只有时间【大于某个槽】，才会放进去
            if (delayMs < interval) {
                val bucketIndex = ((delayMs + currentTimestamp) / tickMs % wheelSize).toInt()
                val bucket = buckets[bucketIndex]
                bucket!!.addTask(timedTask)
                if (bucket.setExpire(delayMs + currentTimestamp - (delayMs + currentTimestamp) % tickMs)) {
                    delayQueue.offer(bucket)
                }
            } else {
                val timeWheel = getOverflowWheel() // 当maybeInThisBucket大于等于wheelSize时，需要将它扔到上一层的时间轮
                timeWheel!!.addTask(timedTask)
            }
        }
        return true
    }

    /**
     * 尝试推进一下指针
     */
    fun advanceClock(timestamp: Long) {
        if (timestamp >= currentTimestamp + tickMs) {
            currentTimestamp = timestamp - timestamp % tickMs
            if (overflowWheel != null) {
                getOverflowWheel().advanceClock(timestamp)
            }
        }
    }
}