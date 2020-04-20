package ink.anur.service.log

import ink.anur.common.KanashiExecutors
import ink.anur.core.common.AbstractRequestMapping
import ink.anur.core.log.LogService
import ink.anur.core.raft.ElectionMetaService
import ink.anur.core.raft.RaftCenterController
import ink.anur.core.request.MsgProcessCentreService
import ink.anur.debug.Debugger
import ink.anur.inject.Event
import ink.anur.inject.NigateBean
import ink.anur.inject.NigateInject
import ink.anur.inject.NigateListener
import ink.anur.inject.NigateListenerService
import ink.anur.mutex.ReentrantLocker
import ink.anur.mutex.ReentrantReadWriteLocker
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.pojo.log.Fetch
import ink.anur.pojo.log.GenerationAndOffset
import ink.anur.pojo.log.RecoveryComplete
import ink.anur.pojo.log.RecoveryReporter
import ink.anur.pojo.log.meta.RecoveryCompleteMeta
import ink.anur.util.TimeUtil
import io.netty.channel.Channel
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

/**
 * Created by Anur IjuoKaruKas on 2020/4/19
 *
 * 恢复时，集群对leader发送自己当前的最新进度
 */
@NigateBean
class RecoveryReporterHandlerService : AbstractRequestMapping() {

    private val logger = Debugger(this::class.java)

    @NigateInject
    private lateinit var electionMetaService: ElectionMetaService

    @NigateInject
    private lateinit var msgProcessCentreService: MsgProcessCentreService

    @NigateInject
    private lateinit var nigateListenerService: NigateListenerService

    @NigateInject
    private lateinit var logService: LogService

    @NigateInject
    private lateinit var raftCenterController: RaftCenterController

    private val locker = ReentrantReadWriteLocker()

    override fun typeSupport(): RequestTypeEnum {
        return RequestTypeEnum.RECOVERY_REPORTER
    }

    override fun handleRequest(fromServer: String, msg: ByteBuffer, channel: Channel) {
        val recoveryReporter = RecoveryReporter(msg)

        if (recoveryReporter.getNowGen() != electionMetaService.generation) {
            logger.error("收到了过期世代的 RecoveryReporter")
            return
        }

        if (!electionMetaService.isLeader()) {
            logger.error("不是leader却收到了 RecoveryReporter !??")
        } else {
            this.receive(fromServer, recoveryReporter.getCommited())
        }
    }

    /**
     * 当集群可用，向 leader 发送当前日志进度
     */
    @NigateListener(onEvent = Event.CLUSTER_VALID)
    private fun whileClusterValid() {
        // 集群可用后选重置所有状态
        reset()

        // 当集群可用时， leader节点会受到一个来自自己的 recovery Report
        if (electionMetaService.isLeader()) {
            this.receive(electionMetaService.getLeader()!!, logService.getCurrentGao())
        } else {
            // 如果不是 leader，则需要各个节点汇报自己的 log 进度，给 leader 发送  recovery Report
            msgProcessCentreService.sendAsyncByName(electionMetaService.getLeader()!!, RecoveryReporter(logService.getCurrentGao(), electionMetaService.generation))
        }
    }

    /**
     * 当集群恢复完毕后，开始启动向 leader 的 fetch 任务
     */
    @NigateListener(onEvent = Event.RECOVERY_COMPLETE)
    private fun whileRecoveryComplete() {
        if (!electionMetaService.isLeader()) {
            startToFetchFrom(electionMetaService.getLeader()!!)
        }
    }

    @NigateListener(onEvent = Event.CLUSTER_INVALID)
    fun reset() {
        logger.debug("RecoveryReportHandlerService RESET is triggered")
        locker.writeLocker {
            cancelFetchTask()
            recoveryMap.clear()
            recoveryComplete = false
            recoveryTimer = TimeUtil.getTime()
        }
    }

    /**
     * 单纯记录 Recovery 需要花费多少时间
     */
    private var recoveryTimer: Long = 0

    /**
     * 这是个通知集合，表示当受到节点的 RecoveryReporter 后，会将最新的 GAO 通知给对方
     */
    @Volatile
    private var waitShutting = ConcurrentHashMap<String, GenerationAndOffset>()

    /**
     * 记录各个节点在 Recovery 时上报的最新 GAO 是多少
     */
    @Volatile
    private var recoveryMap = ConcurrentHashMap<String, GenerationAndOffset>()

    /**
     * 是否已经完成了 Recovery
     */
    @Volatile
    private var recoveryComplete = false

    /**
     * 在半数节点上报了 GAO 后，leader 决定去同步消息到这个进度
     */
    @Volatile
    var fetchTo: GenerationAndOffset? = null

    private fun receive(serverName: String, latestGao: GenerationAndOffset) {
        logger.info("节点 $serverName 提交了其最大进度 $latestGao ")
        recoveryMap[serverName] = latestGao

        if (!recoveryComplete) {
            if (recoveryMap.size >= electionMetaService.quorum) {
                var latest: MutableMap.MutableEntry<String, GenerationAndOffset>? = null

                // 找寻提交的所有的提交的 GAO 里面最大的
                recoveryMap.entries.forEach(Consumer {
                    if (latest == null || it.value > latest!!.value) {
                        latest = it
                    }
                })

                if (latest!!.value == logService.getCurrentGao()) {
                    val cost = TimeUtil.getTime() - recoveryTimer
                    logger.info("已有过半节点提交了最大进度，且集群最大进度 ${latest!!.value} 与 Leader 节点相同，集群已恢复，耗时 $cost ms ")

                    shuttingWhileRecoveryComplete()
                } else {
                    val latestNode = latest!!.key
                    val latestGAO = latest!!.value
                    fetchTo = latestGAO

                    logger.info("已有过半节点提交了最大进度，集群最大进度于节点 $serverName ，进度为 $latestGAO ，Leader 将从其同步最新数据")

                    // 开始进行 fetch
                    startToFetchFrom(latestNode)
                }
            }
        }

        sendRecoveryComplete(serverName, latestGao)
    }

    @Volatile
    private var version: Long = Long.MIN_VALUE

    private var fetchLock = ReentrantLocker()

    /**
     * 新建一个 Fetcher 用于拉取消息，将其发送给 Leader，并在收到回调后，调用 CONSUME_FETCH_RESPONSE 消费回调，且重启拉取定时任务
     */
    private fun startToFetchFrom(fetchFrom: String) {
        cancelFetchTask()
        logger.info("开始向 $fetchFrom fetch 消息 -->")

        fetchFromTask(fetchFrom, version)
    }

    private fun fetchFromTask(fetchFrom: String, version: Long) {
        KanashiExecutors.execute(Runnable {
            while (version == this.version) {
                fetchMutex {
                    msgProcessCentreService.sendAsyncByName(fetchFrom, Fetch(logService.getCurrentGao()))
                }
                Thread.sleep(75)
            }
        })
    }

    private fun fetchMutex(whatEver: () -> Unit) {
        fetchLock.lockSupplier(whatEver)
    }

    private fun cancelFetchTask() {
        version++
    }

    /**
     * 触发向各个节点发送 RecoveryComplete，发送完毕后 触发 RECOVERY_COMPLETE
     * // TODO 避免 client 重复触发！！
     */
    fun shuttingWhileRecoveryComplete() {
        cancelFetchTask()
        raftCenterController.setGenerationAndOffset(logService.getCurrentGao())
        recoveryComplete = true
        waitShutting.entries.forEach(Consumer { sendRecoveryComplete(it.key, it.value) })
        nigateListenerService.onEvent(Event.RECOVERY_COMPLETE)
    }

    /**
     * 向节点发送 RecoveryComplete 告知已经 Recovery 完毕
     *
     * 如果还没同步完成，则将其暂存到 waitShutting 中
     */
    private fun sendRecoveryComplete(serverName: String, latestGao: GenerationAndOffset) {
        if (recoveryComplete) {
            msgProcessCentreService.sendAsyncByName(serverName, RecoveryComplete(
                RecoveryCompleteMeta(electionMetaService.generation, logService.getAllGensGao())
            ))
        } else {
            waitShutting[serverName] = latestGao
        }
    }
}