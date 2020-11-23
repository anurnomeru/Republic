package ink.anur.core.raft

import ink.anur.common.KanashiRunnable
import ink.anur.common.struct.RepublicNode
import ink.anur.config.ElectConfiguration
import ink.anur.config.InetConfiguration
import ink.anur.debug.Debugger
import ink.anur.debug.DebuggerLevel
import ink.anur.exception.NotLeaderException
import ink.anur.inject.bean.NigateBean
import ink.anur.inject.bean.NigateInject
import ink.anur.inject.bean.NigatePostConstruct
import ink.anur.io.common.transport.Connection.Companion.sendAsync
import ink.anur.mutex.ReentrantLocker
import ink.anur.pojo.HeartBeat
import ink.anur.pojo.coordinate.Canvass
import ink.anur.pojo.coordinate.Voting
import ink.anur.timewheel.TimedTask
import ink.anur.timewheel.Timer
import ink.anur.util.TimeUtil
import java.util.*
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

    @NigateInject
    private lateinit var inetConfiguration: InetConfiguration

    @NigateInject
    private lateinit var electionMetaService: ElectionMetaService

    private var electionTimeoutMs: Long = -1L

    private var votesBackOffMs: Long = -1L

    private var heartBeatMs: Long = -1L

    private val random = Random()

    private val reentrantLocker = ReentrantLocker()

    private val logger = Debugger(this::class.java)

    /**
     * 所有正在跑的定时任务
     */
    private var taskMap = ConcurrentHashMap<TaskEnum, TimedTask>()

    @NigatePostConstruct
    private fun init() {
        logger.info("initial RaftCenterController，local server ${inetConfiguration.localServer}")
        this.name = "RaftCenterController"
        this.start()

        electionTimeoutMs = electConfiguration.electionTimeoutMs
        votesBackOffMs = electConfiguration.votesBackOffMs
        heartBeatMs = electConfiguration.heartBeatMs
    }

    override fun run() {
        logger.info("running RaftCenterController... start up")
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
                logger.debug("initial vote box, cause：$reason")

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
            val electionTimeout = electionTimeoutMs + (electionTimeoutMs * random.nextFloat()).toLong()
            logger.trace("become candidate task will be trigger after $electionTimeout")
            val timedTask = TimedTask(electionTimeout, Runnable { this.beginElect(generation) })
            Timer.addTask(timedTask)

            addTask(TaskEnum.BECOME_CANDIDATE, timedTask)
        }
    }


    /**
     * 取消所有的定时任务
     */
    private fun cancelAllTask() {
        reentrantLocker.lockSupplier {
            logger.debug("cancel all task in last generation")
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
            val newGen = electionMetaService.generation + 1
            logger.debug("force update generation ${electionMetaService.generation} => new generation $newGen")
            if (!this.eden(newGen, reason)) {
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
            logger.info("begin sending heart beat...")
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

            if (electionMetaService.generation != generation) {// 存在这么一种情况，虽然取消了选举任务，但是选举任务还是被执行了，所以这里要多做一重处理，避免上个周期的任务被执行
                return@lockSupplier
            }

            if (electionMetaService.beginElectTime == 0L) {// 用于计算耗时
                electionMetaService.beginElectTime = TimeUtil.getTime()
            }

            logger.debug("election timeout, local server is campaigning for election")
            updateGeneration("local server begin election")// meta.getGeneration() ++

            this.becomeCandidate()
            val votes = Voting(agreed = true, fromLeaderNode = false, canvassGeneration = electionMetaService.generation, voteGeneration = electionMetaService.generation)

            // 给自己投票箱投票
            receiveVote(inetConfiguration.localServer, votes)

            // 记录一下，自己给自己投了票
            electionMetaService.voteRecord = inetConfiguration.localServer

            // 开启拉票任务

            // 让其他节点给自己投一票
            val timedTask = TimedTask(0, Runnable {
                // 拉票续约（如果没有得到其他节点的回应，就继续发 voteTask）
                this.canvassingTask(Canvass(electionMetaService.generation))
            })

            Timer.addTask(timedTask)
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
    fun receiveCanvass(republicNode: RepublicNode, canvass: Canvass) {
        reentrantLocker.lockSupplier {
            eden(canvass.generation, "receive higher generation [${canvass.generation}] request from $republicNode")

            logger.debug("receive canvass from $republicNode with generation ${canvass.generation}")
            when {
                canvass.generation < electionMetaService.generation -> {
                    logger.debug("refuse canvass from $republicNode with generation ${canvass.generation}, local generation is ${electionMetaService.generation}")
                    return@lockSupplier
                }
                electionMetaService.voteRecord != null -> logger.debug("refuse canvass from $republicNode, because local server has been vote for ${electionMetaService.voteRecord} on generation ${electionMetaService.generation}")
                else -> electionMetaService.voteRecord = republicNode// 代表投票成功了
            }

            val agreed = electionMetaService.voteRecord == republicNode

            if (agreed) {
                logger.debug("update vote record: on generation ${canvass.generation}, local server vote to node => $republicNode")
            }

            republicNode.sendAsync(Voting(agreed, electionMetaService.isLeader(), canvass.generation, electionMetaService.generation))
        }
    }

    /**
     * 给当前节点的投票箱投票
     */
    fun receiveVote(republicNode: RepublicNode, voting: Voting) {
        reentrantLocker.lockSupplier {
            eden(voting.generation, "receive higher generation [${voting.generation}] request from $republicNode")

            // 已经有过回包了，无需再处理
            if (electionMetaService.box[republicNode] != null) {
                return@lockSupplier
            }
            val voteSelf = republicNode.isLocal()
            if (voteSelf) {
                logger.debug("local server become candidate on generation ${electionMetaService.generation}, vote self")
            } else {
                logger.debug("receive vote on generation ${voting.generation} from $republicNode")
            }

            if (voting.fromLeaderNode) {
                logger.debug("receive vote on generation ${voting.generation} from leader node $republicNode")
                this.receiveHeatBeat(republicNode, voting.generation)
            }

            if (electionMetaService.generation > voting.askVoteGeneration) {// 如果选票的世代小于当前世代，投票无效
                logger.debug("receive invalid vote on generation ${voting.generation} from $republicNode")
                return@lockSupplier
            }

            // 记录一下投票结果
            electionMetaService.box[republicNode] = voting.agreed

            if (voting.agreed) {
                if (!voteSelf) {
                    logger.debug("receive valid vote on generation ${voting.generation} from $republicNode")
                }

                val cluster = electionMetaService.clusters!!
                val voteCount = electionMetaService.box.values.filter { it }.count()

                logger.debug("there is ${cluster.size} node in cluster，elect process ${voteCount}/${electionMetaService.quorum}")

                // 如果获得的选票已经大于了集群数量的一半以上，则成为leader
                if (voteCount == electionMetaService.quorum) {
                    logger.debug("receive all quorum vote, local server become leader")
                    this.becomeLeader()
                }
            } else {
                logger.debug("node $republicNode refuse to vote local server on generation ${voting.generation}")
            }
        }
    }

    fun receiveHeatBeat(leaderRepublicNode: RepublicNode, generation: Long): Boolean {
        return reentrantLocker.lockSupplierCompel {
            var needToSendHeartBeatInfection = true
            // 世代大于当前世代
            if (generation >= electionMetaService.generation) {
                needToSendHeartBeatInfection = false

                if (electionMetaService.getLeader() == null) {
                    eden(generation, "receive higher generation [${generation}] request from $leaderRepublicNode")
                    logger.info("node $leaderRepublicNode become leader on generation $generation")

                    // 如果是leader。则先触发集群无效
                    if (electionMetaService.isLeader()) {
                        electionMetaService.electionStateChanged(false)
                    }

                    // 成为follower
                    electionMetaService.becomeFollower()

                    // 将那个节点设为leader节点
                    electionMetaService.setLeader(leaderRepublicNode)
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
                .forEach { republicNode ->
                    if (!republicNode.isLocal()) {
                        republicNode.sendAsync(HeartBeat(electionMetaService.generation))
                    }
                }

        reentrantLocker.lockSupplier {
            if (electionMetaService.isLeader()) {
                val timedTask = TimedTask(heartBeatMs, Runnable { initHeartBeatTask() })
                Timer.addTask(timedTask)
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
                logger.debug("all node on generation ${electionMetaService.generation} already response for canvass")
            }

            reentrantLocker.lockSupplier {
                if (electionMetaService.isCandidate()) {// 只有节点为候选者才可以拉票
                    electionMetaService.clusters!!
                            .forEach { republicNode ->
                                // 如果还没收到这个节点的选票，就继续发
                                if (!republicNode.isLocal() && electionMetaService.box[republicNode] == null) {
                                    logger.debug("sending canvass to $republicNode on generation ${electionMetaService.generation}...")
                                    republicNode.sendAsync(Canvass(electionMetaService.generation))
                                }
                            }

                    val timedTask = TimedTask(votesBackOffMs, Runnable {
                        // 拉票续约（如果没有得到其他节点的回应，就继续发 voteTask）
                        this.canvassingTask(canvass)
                    })

                    Timer.addTask(timedTask)
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
                    logger.warn("offset has been reach limitation, local server will update generation to ${electionMetaService.generation + 1}")
                    electionMetaService.offset = 0L
                    gen = electionMetaService.generationIncr()
                }

                val offset = electionMetaService.offsetIncr()

                return@lockSupplierCompel GenerationAndOffset(gen, offset)
            } else {
                throw NotLeaderException("can not generate offset if local server not leader")
            }
        }
    }

    private fun addTask(taskEnum: TaskEnum, task: TimedTask) {
        taskMap[taskEnum]?.cancel()
        taskMap[taskEnum] = task
    }
}
