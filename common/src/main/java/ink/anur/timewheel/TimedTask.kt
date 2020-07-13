package ink.anur.timewheel

import ink.anur.util.TimeUtil

/**
 * Created by Anur on 2020/7/11
 */
class TimedTask(private val delayMs: Long, private val task: Runnable) {

    /**
     * 过期时间戳
     */
    private var expireTimestamp: Long = TimeUtil.getTime() + delayMs

    /**
     * 是否被取消
     */
    @Volatile
    private var cancel = false

    var bucket: Bucket? = null

    var next: TimedTask? = null

    var pre: TimedTask? = null

    fun cancel() {
        cancel = true
    }

    fun isCancel(): Boolean {
        return cancel
    }

    fun getTask(): Runnable {
        return task
    }

    fun getExpireTimestamp(): Long {
        return expireTimestamp
    }
}