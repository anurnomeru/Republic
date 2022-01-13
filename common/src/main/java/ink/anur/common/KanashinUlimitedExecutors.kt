package ink.anur.common

import com.google.common.util.concurrent.ThreadFactoryBuilder
import kotlinx.coroutines.CoroutineDispatcher
import org.slf4j.LoggerFactory
import java.util.concurrent.*
import kotlin.coroutines.CoroutineContext

/**
 * Created by Anur IjuoKaruKas on 2019/7/14
 *
 * 全局线程池
 */
object KanashinUlimitedExecutors {

    val Dispatcher = object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            KanashiExecutors.execute(block)
        }
    }

    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * 线程池的 Queue
     */
    private val MissionQueue = SynchronousQueue<Runnable>()

    /**
     * 统一管理的线程池
     */
    private val Pool: ExecutorService

    init {
        logger.info("Creating Kanashi Executors => core thread 0, max thread ${Int.MAX_VALUE}")
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