package ink.anur.common.pool

import com.google.common.util.concurrent.ThreadFactoryBuilder
import ink.anur.common.Shutdownable
import ink.anur.common._KanashiExecutors
import ink.anur.exception.DuplicateHandlerPoolException
import ink.anur.exception.NoSuchHandlerPoolException
import org.slf4j.LoggerFactory
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

/**
 * Created by Anur IjuoKaruKas on 2020/2/22
 *
 * 只需要对外暴露这个池子即可
 */
class EventDriverPool<T> private constructor(private val clazz: Class<T>,
                                             private val poolSize: Int,
                                             private val consumeInternal: Long,
                                             private val timeUnit: TimeUnit,
                                             private val howToConsumeItem: ((T) -> Unit)?,
                                             private val initLatch: CountDownLatch) : Shutdownable {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
        private val HANDLER_POOLS = mutableMapOf<Any, Any>()

        /**
         * 注册一个池子
         */
        fun <T> register(clazz: Class<T>,
                         poolSize: Int,
                         consumeInternal: Long,
                         timeUnit: TimeUnit,
                         howToConsumeItem: ((T) -> Unit)?) {
            synchronized(clazz) {
                if (HANDLER_POOLS[clazz] != null) {
                    throw DuplicateHandlerPoolException("class $clazz is already register in Handler pool")
                }
                val initLatch = CountDownLatch(poolSize)
                HANDLER_POOLS[clazz] = EventDriverPool(clazz, poolSize, consumeInternal, timeUnit, howToConsumeItem, initLatch)
                logger.info("初始化 [$clazz] 处理池成功，共有 $poolSize 个请求池被创建")
            }
        }

        /**
         * 向池子注入元素供给消费
         */
        fun offer(t: Any) {
            getPool(t.javaClass).offer(t)
        }

        /**
         * 获取某个消费池
         */
        private fun <T> getPool(clazz: Class<T>): EventDriverPool<T> {
            val any = HANDLER_POOLS[clazz] ?: throw NoSuchHandlerPoolException("class $clazz has not register in Handler pool")
            return any as EventDriverPool<T>
        }
    }

    private val pool = _KanashiExecutors(logger, 0, poolSize, 60, TimeUnit.SECONDS, LinkedBlockingDeque(), ThreadFactoryBuilder().setNameFormat("EventDriverPool - $clazz")
        .build())

    private val handlers = mutableListOf<PoolHandler<T>>()

    private val requestQueue: ArrayBlockingQueue<T> = ArrayBlockingQueue(Runtime.getRuntime().availableProcessors() * 2)

    init {
        for (i in 0 until poolSize) {
            val poolHandler = PoolHandler(clazz, consumeInternal, timeUnit, howToConsumeItem, initLatch);
            handlers.add(poolHandler)
            pool.submit(poolHandler)
        }
    }

    private fun offer(something: T) {
        requestQueue.put(something)
    }

    private fun poll(timeout: Long, unit: TimeUnit): T? {
        return requestQueue.poll(timeout, unit)
    }

    override fun shutDown() {
        for (handler in handlers) {
            handler.shutDown()
        }
    }

    /**
     * Created by Anur IjuoKaruKas on 2020/2/22
     *
     * 如何去消费池子里的东西
     */
    private class PoolHandler<T>(
        private val clazz: Class<T>,
        private val consumeInternal: Long,
        private val timeUnit: TimeUnit,
        private val howToConsumeItem: ((T) -> Unit)?,
        private val initLatch: CountDownLatch
    ) : Runnable, Shutdownable {

        @Volatile
        private var shutdown = false

        override fun run() {
            initLatch.await()
            while (true) {
                val consume = getPool(clazz).poll(consumeInternal, timeUnit)
                consume?.also { howToConsumeItem?.invoke(it) }
                if (shutdown) break
            }
        }

        override fun shutDown() {
            shutdown = true
        }
    }
}