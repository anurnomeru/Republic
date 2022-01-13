package ink.anur.common

import com.google.common.util.concurrent.ThreadFactoryBuilder
import kotlinx.coroutines.CoroutineDispatcher
import org.slf4j.LoggerFactory
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

/**
 * Created by Anur IjuoKaruKas on 2019/7/14
 *
 * 全局线程池
 */
object KanashiExecutors {

    val Dispatcher = object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            execute(block)
        }
    }

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
        val threadCount = coreCount * 2
        logger.info("Creating Kanashi Executors => core thread $threadCount, max thread $threadCount")
        Pool = _KanashiExecutors(
            logger,
            threadCount,
            threadCount,
            5,
            TimeUnit.SECONDS,
            MissionQueue,
            ThreadFactoryBuilder().setNameFormat("Kanashi Executors")
                .setDaemon(true)
                .build()
        )
    }

    fun execute(runnable: Runnable) {
        Pool.execute(runnable)
    }

    fun <T> submit(task: Callable<T>): Future<T> {
        return Pool.submit(task)
    }

    fun getPool(): ExecutorService {
        return Pool
    }

    fun getBlockSize(): Int {
        return MissionQueue.size
    }
}