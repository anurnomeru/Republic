package ink.anur.timewheel

/**
 * Created by Anur IjuoKaruKas on 2020/4/20
 *
 * 对原本定时任务的一个增强，可周期运行，如果被取消，则不再周期运行
 */
class CycleTimedTask(delayMs: Long, private val period: Long, task: Runnable) : TimedTask(delayMs, task) {

    fun resetExpire() {
        this.expireTimestamp = System.currentTimeMillis() + period
    }
}