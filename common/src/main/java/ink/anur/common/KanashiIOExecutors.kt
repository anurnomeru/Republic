package ink.anur.common

import com.google.common.util.concurrent.ThreadFactoryBuilder
import org.slf4j.LoggerFactory
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

/**
 * Created by Anur IjuoKaruKas on 2019/7/14
 *
 * 全局线程池
 */
object KanashiIOExecutors {

    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * 线程池的 Queue
     */
    private val MissionQueue = LinkedBlockingDeque<Runnable>()

    /**
     * 统一管理的线程池
     */
    private val Pool: ExecutorService

    init {
        val coreCount = Runtime.getRuntime()
                .availableProcessors()
        logger.info("创建 Kanashi IO 线程池")
        Pool = _KanashiExecutors(logger, 0, Int.MAX_VALUE, 5, TimeUnit.SECONDS, MissionQueue, ThreadFactoryBuilder().setNameFormat("Kanashi Pool")
                .setDaemon(true)
                .build())
    }

    fun execute(runnable: Runnable) {
        Pool.execute(runnable)
    }

    fun getPool(): ExecutorService {
        return Pool
    }

    fun <T> submit(task: Callable<T>): Future<T> {
        return Pool.submit(task)
    }

    fun getBlockSize(): Int {
        return MissionQueue.size
    }
}