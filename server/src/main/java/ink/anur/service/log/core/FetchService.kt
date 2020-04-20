package ink.anur.service.log.core

import ink.anur.core.log.LogService
import ink.anur.core.raft.ElectionMetaService
import ink.anur.core.request.MsgProcessCentreService
import ink.anur.inject.Event
import ink.anur.inject.NigateBean
import ink.anur.inject.NigateInject
import ink.anur.inject.NigateListener
import ink.anur.pojo.log.Fetch
import ink.anur.pojo.log.FetchResponse
import ink.anur.pojo.log.GenerationAndOffset
import ink.anur.pojo.log.RecoveryComplete
import ink.anur.pojo.log.meta.RecoveryCompleteMeta
import ink.anur.timewheel.CycleTimedTask
import ink.anur.timewheel.Timer
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch

/**
 * Created by Anur IjuoKaruKas on 2020/4/20
 *
 * 专门用于管理 fetch 任务，发起 fetch 请求
 */
@NigateBean
class FetchService {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @NigateInject
    private lateinit var electionMetaService: ElectionMetaService

    @NigateInject
    private lateinit var msgProcessCentreService: MsgProcessCentreService

    @NigateInject
    private lateinit var logService: LogService

    @Volatile
    private var fetchMeta: FetchMeta? = null

    /**
     * 处理来自 leader 的 recoveryComplete 指令
     */
    fun recoveryHandler(recoveryCompleteMeta: RecoveryCompleteMeta) {
        if (recoveryCompleteMeta.nowGen == electionMetaService.generation) {
            val thisNodeLogsGao = logService.getAllGensGao()
            val leaderLogsGao = recoveryCompleteMeta.allGensGao

            // 先求出交集
            val retainAll = thisNodeLogsGao.retainAll(leaderLogsGao)

//            thisNodeLogsGao.removeAll(retainAll)


        } else {
            logger.error("收到了来自世代 ${recoveryCompleteMeta.nowGen} 的无效 RECOVERY_COMPLETE，因为当前世代已经是 ${electionMetaService.generation}")
        }
    }

    /**
     * 如何处理 fetch 到的消息
     */
    fun fetchHandler(fr: FetchResponse, fromServer: String) {
        // 如果当前没有 fetch 任务直接返回
        val fetchMeta = fetchMeta ?: return

        if (fetchMeta.fromServer != fromServer) {
            logger.error("收到了不符合当前 fetch 请求服务的 fetch response，可能在集群主节点切换后发生，但是如果频率很高，需要看看是不是哪里有 bug")
            return
        }

        val read = fr.read()
        val iterator = read.iterator()
        val gen = fr.generation
        var lastOffset: Long? = null
        iterator.forEach {
            logService.append(gen, it.offset, it.logItem)
            lastOffset = it.offset
        }

        if (lastOffset != null) {
            val fetchLast = GenerationAndOffset(gen, lastOffset!!)
            /*
             * 如果已经 fetch 到了想要的进度，触发complete，否则触发send，并重置过期时间
             */
            if (fetchLast == fetchMeta.fetchUntil) {
                fetchMeta.complete()
            } else {
                fetchMeta.doSendAndResetTaskExp(fetchLast)
            }
        }
    }

    /**
     * 从某个节点开始进行 fetch，从日志 generationAndOffsetStart 拉取直到 generationAndOffsetUntil
     */
    @Synchronized
    private fun startToFetchFrom(fromServer: String, start: GenerationAndOffset, end: GenerationAndOffset?, waitUntilComplete: Boolean): Boolean {
        /*
         * 取消之前的任务
         */
        this.fetchMeta?.cancel()
        val fm = FetchMeta(fromServer, start, end, msgProcessCentreService)
        this.fetchMeta = fm

        return if (waitUntilComplete) {
            fm.waitUntilFailOrFetchComplete().also { fetchMeta = null }
        } else {
            true
        }
    }

    /**
     * 当集群不可用时，暂停所有任务
     */
    @NigateListener(onEvent = Event.CLUSTER_VALID)
    @Synchronized
    private fun whileClusterValid() {
        val f = fetchMeta
        fetchMeta = null
        f?.cancel()
    }

    /**
     * 控制一个 fetch 任务的管理 bean
     */
    class FetchMeta(

        /**
         * 从哪个服务进行 fetch
         */
        val fromServer: String,

        /**
         * 控制应该从哪里开始 fetch
         */
        @Volatile
        var fetchFrom: GenerationAndOffset,

        /**
         * 控制应该 fetch 到哪里
         *
         * 如果为空，则会不断的进行 fetch（非集群恢复阶段）
         */
        val fetchUntil: GenerationAndOffset?,

        /**
         * 一个不优雅的设计，但是 = = 就这么样
         */
        val msgProcessCentreService: MsgProcessCentreService) {

        @Volatile
        private var success: Boolean? = null

        private val taskCompleteLatch = CountDownLatch(1)

        private val doSend = { msgProcessCentreService.sendAsyncByName(fromServer, Fetch(fetchFrom)) }

        private val task: CycleTimedTask = CycleTimedTask(0, 5000L,
            Runnable { doSend.invoke() })

        init {
            Timer.getInstance().addTask(task)
        }

        /**
         * 手动触发一次任务，并重置定时任务的过期时间
         */
        @Synchronized
        fun doSendAndResetTaskExp(fetchFrom: GenerationAndOffset) {
            if (success == null) {
                task.resetExpire()
                this.fetchFrom = fetchFrom
                doSend.invoke()
            }
        }

        /**
         * 取消任务
         */
        @Synchronized
        fun cancel() {
            if (success == null) {
                success = false
                task.cancel()
                taskCompleteLatch.countDown()
            }
        }

        /**
         * 触发任务完成
         */
        @Synchronized
        fun complete() {
            if (success == null) {
                task.cancel()
                success = true
            }
            taskCompleteLatch.countDown()
        }

        /**
         * 阻塞等待任务完成或者被取消，如果是无止境任务（非恢复时 FetchUntil 为空，则会一直阻塞下去，直到集群失效，不要乱用！！！）
         */
        fun waitUntilFailOrFetchComplete(): Boolean {
            taskCompleteLatch.await()
            return success!!// 不可能为空
        }
    }
}