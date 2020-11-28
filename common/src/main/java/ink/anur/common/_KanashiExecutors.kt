package ink.anur.common

import org.slf4j.Logger
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class _KanashiExecutors(val logger: Logger, corePoolSize: Int, maximumPoolSize: Int, keepAliveTime: Long, unit: TimeUnit?, workQueue: BlockingQueue<Runnable>?, threadFactory: ThreadFactory?) :
        ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory) {

    override fun afterExecute(r: Runnable?, t: Throwable?) {
        super.afterExecute(r, t)

        var thr = t
        if (t == null && r is Future<*>) {
            try {
                val future = r as Future<*>
                if (future.isDone) {
                    future.get()
                }
            } catch (ce: CancellationException) {
                thr = ce
            } catch (ee: ExecutionException) {
                thr = ee.cause
            } catch (ie: InterruptedException) {
                Thread.currentThread()
                        .interrupt()
            }
        }

        thr?.let { logger.error("Error occur: ", it) }
    }
}