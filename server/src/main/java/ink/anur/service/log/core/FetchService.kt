//package ink.anur.service.log.core
//
//import ink.anur.common.KanashiRunnable
//import ink.anur.core.log.LogService
//import ink.anur.core.raft.ElectionMetaService
//import ink.anur.core.request.MsgProcessCentreService
//import ink.anur.inject.Event
//import ink.anur.inject.NigateBean
//import ink.anur.inject.NigateInject
//import ink.anur.inject.NigateListener
//import ink.anur.pojo.log.Fetch
//import ink.anur.pojo.log.GenerationAndOffset
//import ink.anur.timewheel.TimedTask
//import ink.anur.timewheel.Timer
//import org.slf4j.LoggerFactory
//import java.util.concurrent.ArrayBlockingQueue
//
///**
// * Created by Anur IjuoKaruKas on 2020/4/20
// *
// * 专门用于管理 fetch 任务，发起 fetch 请求
// */
//@NigateBean
//class FetchService : KanashiRunnable() {
//    override fun run() {
//        while (version == this.version) {
//            val take = fetchControlQueue.take()
//            msgProcessCentreService.sendAsyncByName(take.fetchFrom, Fetch(take.fetchFromGao))
//        }
//    }
//
//    private val logger = LoggerFactory.getLogger(this::class.java)
//
//    @NigateInject
//    private lateinit var electionMetaService: ElectionMetaService
//
//    @NigateInject
//    private lateinit var msgProcessCentreService: MsgProcessCentreService
//
//    @NigateInject
//    private lateinit var logService: LogService
//
//    @Volatile
//    private var version: Long = Long.MIN_VALUE
//
//    /**
//     * 控制应该从哪个 gao 开始 fetch 的队列
//     */
//    private var fetchControlQueue = ArrayBlockingQueue<FetchMeta>(1)
//
//    /**
//     * 控制应该 fetch 到哪里
//     *
//     * 如果为空，则会不断的进行 fetch（非集群恢复阶段）
//     */
//    private var fetchUntil: GenerationAndOffset? = null
//
//    private fun delayFetchTask(){
//        Timer.getInstance().addTask(TimedTask(5000){
//
//        })
//    }
//
//    /**
//     * 当集群恢复完毕后，开始启动向 leader 的 fetch 任务
//     */
//    @NigateListener(onEvent = Event.RECOVERY_COMPLETE)
//    private fun whileRecoveryComplete() {
//        if (!electionMetaService.isLeader()) {
//            startToFetchFrom(electionMetaService.getLeader()!!)
//        }
//    }
//
//    /**
//     * 当集群不可用时，暂停所有任务
//     */
//    @NigateListener(onEvent = Event.CLUSTER_VALID)
//    private fun whileClusterValid() {
////        cancelFetchTask()
//    }
//
//    /**
//     * 新建一个 Fetcher 用于拉取消息，将其发送给 Leader，并在收到回调后，调用 CONSUME_FETCH_RESPONSE 消费回调，且重启拉取定时任务
//     */
//    private fun startToFetchFrom(fetchFrom: String, fetchFromGao: GenerationAndOffset?) {
////        cancelFetchTask()
//        logger.info("开始向 $fetchFrom fetch 消息 -->")
//
////        fetchFromTask(fetchFrom, version, fetchFromGao)
//    }
//
//    class FetchMeta(val fetchFrom: String, val fetchFromGao: GenerationAndOffset)
//}