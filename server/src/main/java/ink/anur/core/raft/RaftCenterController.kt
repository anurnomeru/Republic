package ink.anur.core.raft

import ink.anur.common.KanashiRunnable
import ink.anur.config.ElectConfiguration
import ink.anur.config.InetConfig
import ink.anur.core.request.MsgProcessCentreService
import ink.anur.exception.NotLeaderException
import ink.anur.mutex.ReentrantLocker
import ink.anur.inject.NigateBean
import ink.anur.inject.NigateInject
import ink.anur.inject.NigatePostConstruct
import ink.anur.pojo.coordinate.Canvass
import ink.anur.pojo.HeartBeat
import ink.anur.pojo.coordinate.Voting
import ink.anur.timewheel.TimedTask
import ink.anur.timewheel.Timer
import ink.anur.util.TimeUtil
import org.slf4j.LoggerFactory
import java.util.Random
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by Anur IjuoKaruKas on 2020/2/26
 *
 * 选举相关、raft核心控制代码
 */
@NigateBean
class RaftCenterController : KanashiRunnable() {

    @NigateInject
    private lateinit var electConfiguration: ElectConfiguration

    @NigateInject(useLocalFirst = true)
    private lateinit var inetSocketAddressConfiguration: InetConfig

    @NigateInject
    private lateinit var electionMetaService: ElectionMetaService

    @NigateInject
    private lateinit var msgCenterService: MsgProcessCentreService

    private var ELECTION_TIMEOUT_MS: Long = -1L

    private var VOTES_BACK_OFF_MS: Long = -1L

    private var HEART_BEAT_MS: Long = -1L

    private val RANDOM = Random()

    private val reentrantLocker = ReentrantLocker()

    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * 所有正在跑的定时任务
     */
    private var taskMap = ConcurrentHashMap<TaskEnum, TimedTask>()

    @NigatePostConstruct
    private fun init() {
        logger.info("初始化选举控制器 ElectOperator，本节点为 {}", inetSocketAddressConfiguration.getLocalServerName())
        this.name = "RaftCenterController"
        this.start()

        ELECTION_TIMEOUT_MS = electConfiguration.getElectionTimeoutMs()
        VOTES_BACK_OFF_MS = electConfiguration.getVotesBackOffMs()
        HEART_BEAT_MS = electConfiguration.getHeartBeatMs()
    }

    override fun run() {
        logger.info("初始化选举控制器 启动中...")
        this.becomeCandidateAndBeginElectTask(electionMetaService.generation)
    }

    /**
     * 初始化
     *
     * 1、成为follower
     * 2、先取消所有的定时任务
     * 3、重置本地变量
     * 4、新增成为Candidate的定时任务
     */
    private fun eden(generation: Long, reason: String): Boolean {
        return reentrantLocker.lockSupplierCompel {
            if (generation > electionMetaService.generation) {// 如果有选票的世代已经大于当前世代，那么重置投票箱
                logger.debug("初始化投票箱，原因：{}", reason)

                // 1、刷新选举状态
                electionMetaService.eden(generation)

                // 2、取消所有任务
                this.cancelAllTask()

                // 3、新增成为Candidate的定时任务
                this.becomeCandidateAndBeginElectTask(generation)
                return@lockSupplierCompel true
            } else {
                return@lockSupplierCompel false
            }
        }
    }

    /**
     * 成为候选者的任务，（重复调用则会取消之前的任务，收到来自leader的心跳包，就可以重置一下这个任务）
     *
     * 没加锁，因为这个任务需要频繁被调用，只要收到leader来的消息就可以调用一下
     */
    private fun becomeCandidateAndBeginElectTask(generation: Long) {
        reentrantLocker.lockSupplier {
            // The election timeout is randomized to be between 150ms and 300ms.
            val electionTimeout = ELECTION_TIMEOUT_MS + (ELECTION_TIMEOUT_MS * RANDOM.nextFloat()).toLong()
            val timedTask = TimedTask(electionTimeout, Runnable { this.beginElect(generation) })
            Timer.getInstance()
                .addTask(timedTask)

            addTask(TaskEnum.BECOME_CANDIDATE, timedTask)
        }
    }


    /**
     * 取消所有的定时任务
     */
    private fun cancelAllTask() {
        reentrantLocker.lockSupplier {
            logger.debug("取消本节点在上个世代的所有定时任务")
            for (task in taskMap.values) {
                task.cancel()
            }
        }
    }

    /**
     * 强制更新世代信息
     */
    private fun updateGeneration(reason: String) {
        reentrantLocker.lockSupplier {
            logger.debug("强制更新当前世代 {} => 新世代 {}", electionMetaService.generation, electionMetaService.generation + 1)

            if (!this.eden(electionMetaService.generation + 1, reason)) {
                updateGeneration(reason)
            }
        }
    }

    /**
     * 成为候选者
     */
    private fun becomeCandidate() {
        reentrantLocker.lockSupplier {
            electionMetaService.becomeCandidate()
        }
    }

    /**
     * 当选票大于一半以上时调用这个方法，如何去成为一个leader
     */
    private fun becomeLeader() {
        return reentrantLocker.lockSupplier {
            this.cancelAllTask()
            electionMetaService.becomeLeader()
            logger.info("开始给集群内的节点发送心跳包")
            this.initHeartBeatTask()
        }
    }


    /**
     * 开始进行选举
     *
     * 1、首先更新一下世代信息，重置投票箱和投票记录
     * 2、成为候选者
     * 3、给自己投一票
     * 4、请求其他节点，要求其他节点给自己投票
     */
    private fun beginElect(generation: Long) {
        reentrantLocker.lockSupplier {

            if (Timer.isShutDown()) {
                return@lockSupplier
            }

            if (electionMetaService.generation != generation) {// 存在这么一种情况，虽然取消了选举任务，但是选举任务还是被执行了，所以这里要多做一重处理，避免上个周期的任务被执行
                return@lockSupplier
            }

            if (electionMetaService.beginElectTime == 0L) {// 用于计算耗时
                electionMetaService.beginElectTime = TimeUtil.getTime()
            }

            logger.debug("Election Timeout 到期，可能期间内未收到来自 Leader 的心跳包或上一轮选举没有在期间内选出 Leader，故本节点即将发起选举")
            updateGeneration("本节点发起了选举")// meta.getGeneration() ++

            // 成为候选者
            logger.debug("本节点正式开始世代 {} 的选举", electionMetaService.generation)
            this.becomeCandidate()
            val votes = Voting(true, false, electionMetaService.generation, electionMetaService.generation)

            // 给自己投票箱投票
            this.receiveVote(inetSocketAddressConfiguration.getLocalServerName(), votes)

            // 记录一下，自己给自己投了票
            electionMetaService.voteRecord = inetSocketAddressConfiguration.getLocalServerName()

            // 开启拉票任务

            // 让其他节点给自己投一票
            val timedTask = TimedTask(0, Runnable {
                // 拉票续约（如果没有得到其他节点的回应，就继续发 voteTask）
                this.canvassingTask(Canvass(electionMetaService.generation))
            })

            Timer.getInstance()
                .addTask(timedTask)
            addTask(TaskEnum.ASK_FOR_VOTES, timedTask)
        }
    }

    /**
     * 某个节点来请求本节点给他投票了，只有当世代大于当前世代，才有投票一说，其他情况都是失败的
     *
     * 返回结果
     *
     * 为true代表接受投票成功。
     * 为false代表已经给其他节点投过票了，
     */
    fun receiveCanvass(serverName: String, canvass: Canvass) {
        reentrantLocker.lockSupplier {
            eden(canvass.generation, "收到了来自 $serverName 世代更高的请求 [${canvass.generation}]，故触发 EDEN")

            logger.debug("收到节点 {} 的拉票请求，其世代为 {}", serverName, canvass.generation)
            when {
                canvass.generation < electionMetaService.generation -> {
                    logger.debug("拒绝来自 $serverName 的拉票请求，其世代 ${canvass.generation} 小于当前世代 ${electionMetaService.generation}")
                    return@lockSupplier
                }
                electionMetaService.voteRecord != null -> logger.debug("拒绝来自 $serverName 的拉票请求，在世代 ${electionMetaService.generation} 本节点已投票给 => ${electionMetaService.voteRecord} 节点")
                else -> electionMetaService.voteRecord = serverName// 代表投票成功了
            }

            val agreed = electionMetaService.voteRecord == serverName

            if (agreed) {
                logger.debug("投票记录更新成功：在世代 ${canvass.generation}，本节点投票给 => ${serverName} 节点")
            }

            msgCenterService.sendAsync(serverName, Voting(agreed, electionMetaService.isLeader(), canvass.generation, electionMetaService.generation))
        }
    }

    /**
     * 给当前节点的投票箱投票
     */
    fun receiveVote(serverName: String, voting: Voting) {
        reentrantLocker.lockSupplier {
            eden(voting.generation, "收到了来自 $serverName 世代更高的请求 [${voting.generation}]，故触发 EDEN")

            // 已经有过回包了，无需再处理
            if (electionMetaService.box[serverName] != null) {
                return@lockSupplier
            }
            val voteSelf = serverName == inetSocketAddressConfiguration.getLocalServerName()
            if (voteSelf) {
                logger.debug("本节点在世代 {} 转变为候选者，给自己先投一票", electionMetaService.generation)
            } else {
                logger.debug("收到来自节点 {} 的投票应答，其世代为 {}", serverName, voting.generation)
            }

            if (voting.fromLeaderNode) {
                logger.debug("来自节点 {} 的投票应答表明其身份为 Leader，本轮拉票结束。", serverName)
                this.receiveHeatBeat(serverName, voting.generation)
            }

            if (electionMetaService.generation > voting.askVoteGeneration) {// 如果选票的世代小于当前世代，投票无效
                logger.debug("来自节点 {} 的投票应答世代是以前世代 {} 的选票，选票无效", serverName, voting.askVoteGeneration)
                return@lockSupplier
            }

            // 记录一下投票结果
            electionMetaService.box[serverName] = voting.agreed

            if (voting.agreed) {
                if (!voteSelf) {
                    logger.debug("来自节点 {} 的投票应答有效，投票箱 + 1", serverName)
                }

                val kanashiNodeList = electionMetaService.clusters!!
                val voteCount = electionMetaService.box.values.filter { it }.count()

                logger.debug("集群中共 {} 个节点，本节点当前投票箱进度 {}/{}", kanashiNodeList.size, voteCount, electionMetaService.quorum)

                // 如果获得的选票已经大于了集群数量的一半以上，则成为leader
                if (voteCount == electionMetaService.quorum) {
                    logger.debug("选票过半，本节点将上位成为 leader 节点")
                    this.becomeLeader()
                }
            } else {
                logger.debug("节点 {} 在世代 {} 的投票应答为：拒绝给本节点在世代 {} 的选举投票（当前世代 {}）", serverName, voting.generation, voting.askVoteGeneration,
                    electionMetaService.generation)
            }
        }
    }

    fun receiveHeatBeat(leaderServerName: String, generation: Long): Boolean {
        return reentrantLocker.lockSupplierCompel {
            var needToSendHeartBeatInfection = true
            // 世代大于当前世代
            if (generation >= electionMetaService.generation) {
                needToSendHeartBeatInfection = false

                if (electionMetaService.getLeader() == null) {
                    eden(generation, "收到了来自 $leaderServerName 世代更高的请求 [$generation]，故触发 EDEN")
                    logger.info("集群中，其他节点 {} 已经成功在世代 {} 上位成为 Leader，本节点将成为 Follower，直到与 Leader 的网络通讯出现问题", leaderServerName, generation)

                    // 如果是leader。则先触发集群无效
                    if (electionMetaService.isLeader()) {
                        electionMetaService.electionStateChanged(false)
                    }

                    // 成为follower
                    electionMetaService.becomeFollower()

                    // 将那个节点设为leader节点
                    electionMetaService.setLeader(leaderServerName)
                    electionMetaService.beginElectTime = 0L
                    electionMetaService.electionStateChanged(true)
                }

                // 重置成为候选者任务
                this.becomeCandidateAndBeginElectTask(electionMetaService.generation)
            }
            return@lockSupplierCompel needToSendHeartBeatInfection
        }
    }


    /**
     * 心跳任务
     */
    private fun initHeartBeatTask() {
        electionMetaService.clusters!!
            .forEach { kanashiNode ->
                if (!kanashiNode.isLocalNode()) {
                    msgCenterService.sendAsync(kanashiNode.serverName, HeartBeat(electionMetaService.generation))
                }
            }

        reentrantLocker.lockSupplier {
            if (!Timer.isShutDown() && electionMetaService.isLeader()) {
                val timedTask = TimedTask(HEART_BEAT_MS, Runnable { initHeartBeatTask() })
                Timer.getInstance()
                    .addTask(timedTask)
                addTask(TaskEnum.HEART_BEAT, timedTask)
            }
        }
    }

    /**
     * 拉票请求的任务
     */
    private fun canvassingTask(canvass: Canvass) {
        if (electionMetaService.isCandidate()) {
            if (electionMetaService.clusters!!.size == electionMetaService.box.size) {
                logger.debug("所有的节点都已经应答了本世代 {} 的拉票请求，拉票定时任务执行完成", electionMetaService.generation)
            }

            reentrantLocker.lockSupplier {
                if (!Timer.isShutDown() && electionMetaService.isCandidate()) {// 只有节点为候选者才可以拉票
                    electionMetaService.clusters!!
                        .forEach { kanashiNode ->
                            // 如果还没收到这个节点的选票，就继续发
                            if (electionMetaService.box[kanashiNode.serverName] == null) {
                                logger.debug("正向节点 {} [{}:{}] 发送世代 {} 的拉票请求...", kanashiNode.serverName, kanashiNode.host, kanashiNode.port, electionMetaService.generation)
                            }

                            msgCenterService.sendAsync(kanashiNode.serverName, Canvass(electionMetaService.generation))
                        }

                    val timedTask = TimedTask(VOTES_BACK_OFF_MS, Runnable {
                        // 拉票续约（如果没有得到其他节点的回应，就继续发 voteTask）
                        this.canvassingTask(canvass)
                    })

                    Timer.getInstance()
                        .addTask(timedTask)
                    addTask(TaskEnum.ASK_FOR_VOTES, timedTask)
                } else {
                    // do nothing
                }
            }
        }
    }

    /**
     * 生成对应一次操作的id号（用于给其他节点发送日志同步消息，并且得到其ack，以便知道消息是否持久化成功）
     */
    fun genGenerationAndOffset(): GenerationAndOffset {
        return reentrantLocker.lockSupplierCompel {
            if (electionMetaService.isLeader()) {
                var gen = electionMetaService.generation

                // 当流水号达到最大时，进行世代的自增，
                if (electionMetaService.offset == java.lang.Long.MAX_VALUE) {
                    logger.warn("流水号 offset 已达最大值，节点将更新自身世代 {} => {}", electionMetaService.generation, electionMetaService.generation + 1)
                    electionMetaService.offset = 0L
                    gen = electionMetaService.generationIncr()
                }

                val offset = electionMetaService.offsetIncr()

                return@lockSupplierCompel GenerationAndOffset(gen, offset)
            } else {
                throw NotLeaderException("不是 Leader 的节点无法生成id号")
            }
        }
    }

    private fun addTask(taskEnum: TaskEnum, task: TimedTask) {
        taskMap[taskEnum]?.cancel()
        taskMap[taskEnum] = task
    }
}
