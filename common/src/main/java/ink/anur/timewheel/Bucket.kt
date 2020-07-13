package ink.anur.timewheel

import ink.anur.util.TimeUtil
import java.util.concurrent.Delayed
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

/**
 * Created by Anur on 2020/7/11
 */
class Bucket : Delayed {

    private val rootSentinelRunnable = Runnable { }

    /** 当前槽的过期时间  */
    private val expiration = AtomicLong(-1L)

    /** 根节点  */
    private val root = TimedTask(-1L, rootSentinelRunnable)

    init {
        root.pre = root
        root.next = root
    }

    /**
     * 设置某个槽的过期时间
     */
    fun setExpire(expire: Long): Boolean {
        return expiration.getAndSet(expire) != expire
    }

    /**
     * 获取某个槽的过期时间
     */
    fun getExpire(): Long {
        return expiration.get()
    }

    /**
     * 新增任务到bucket
     */
    fun addTask(timedTask: TimedTask) {
        synchronized(timedTask) {
            if (timedTask.bucket == null) {
                timedTask.bucket = this
                val tail = root.pre
                timedTask.next = root
                timedTask.pre = tail
                tail!!.next = timedTask
                root.pre = timedTask
            }
        }
    }

    /**
     * 从bucket移除任务
     */
    private fun removeTask(timedTask: TimedTask) {
        synchronized(timedTask) {
            if (timedTask.bucket == this) { // 这里可能有bug
                timedTask.next!!.pre = timedTask.pre
                timedTask.pre!!.next = timedTask.next
                timedTask.bucket = null
                timedTask.next = null
                timedTask.pre = null
            }
        }
    }

    /**
     * 重新分配槽
     */
    @Synchronized
    fun flush(flush: (TimedTask) -> Unit) {
        var timedTask = root.next // 从尾巴开始（最先加进去的）
        while (timedTask != root) {
            removeTask(timedTask!!)
            flush.invoke(timedTask)
            timedTask = root.next
        }
        expiration.set(-1L)
    }

    override fun getDelay(unit: TimeUnit): Long {
        return max(0, unit.convert(expiration.get() - TimeUtil.getTime(), TimeUnit.MILLISECONDS))
    }

    override fun compareTo(other: Delayed?): Int {
        return if (other is Bucket) {
            expiration.get().compareTo(other.expiration.get())
        } else 0
    }
}