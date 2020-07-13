package ink.anur.timewheel

import com.google.common.util.concurrent.ThreadFactoryBuilder
import ink.anur.common.KanashiExecutors.execute
import ink.anur.util.TimeUtil
import java.util.concurrent.DelayQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Created by Anur on 2020/7/11
 */
class Timer {
    companion object {

        private val bossThreadPool: ExecutorService = Executors.newFixedThreadPool(1, ThreadFactoryBuilder().setPriority(10)
                .setNameFormat("TimeWheelBoss")
                .build())

        /**
         * 对于一个Timer以及附属的时间轮，都只有一个delayQueue
         */
        private val delayQueue = DelayQueue<Bucket>()

        /**
         * 最底层的那个时间轮
         */
        private var timeWheel: TimeWheel = TimeWheel(20, 10, delayQueue, TimeUtil.getTime())

        /**
         * 新建一个Timer，同时新建一个时间轮
         */
        init {
            bossThreadPool.execute {
                while (true) {
                    advanceClock(20)
                }
            }
        }

        /**
         * 将任务添加到时间轮
         */
        fun addTask(timedTask: TimedTask) {
            if (!timeWheel.addTask(timedTask)) {
                if (!timedTask.isCancel()) {
                    execute(Runnable {
                        timedTask.getTask().run()
                    })
                }
            }
        }

        /**
         * 推进一下时间轮的指针，并且将delayQueue中的任务取出来再重新扔进去
         */
        private fun advanceClock(timeout: Long) {
            try {
                val bucket = delayQueue.poll(timeout, TimeUnit.MILLISECONDS)
                if (bucket != null) {
                    timeWheel.advanceClock(bucket.getExpire())
                    bucket.flush { timedTask -> addTask(timedTask) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}