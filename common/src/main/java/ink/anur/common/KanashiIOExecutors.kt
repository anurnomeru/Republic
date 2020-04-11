package ink.anur.common

import com.google.common.util.concurrent.ThreadFactoryBuilder
import org.slf4j.LoggerFactory
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.TimeUnit

/**
 * Created by Anur IjuoKaruKas on 2019/7/14
 *
 * 线程池，0个核心线程池，可以创建很多个线程
 */
object KanashiIOExecutors {

    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * 线程池的 Queue
     */
    private var MissionQueue = SynchronousQueue<Runnable>()

    /**
     * 统一管理的线程池
     */
    val Pool = _KanashiExecutors(logger, 0, Int.MAX_VALUE, 20, TimeUnit.SECONDS, MissionQueue, ThreadFactoryBuilder().setNameFormat("Kanashi IO Pool").build())

    init {
        logger.info("创建 Kanashi IO 线程池")
    }

    fun execute(runnable: Runnable) {
        Pool.execute(runnable)
    }

    fun <T> submit(task: Callable<T>): Future<T> {
        return Pool.submit(task)
    }

    fun getBlockSize(): Int {
        return MissionQueue.size
    }
}