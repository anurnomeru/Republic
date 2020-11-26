package ink.anur.core.raft

import ink.anur.common.struct.RepublicNode
import ink.anur.config.InetConfiguration
import ink.anur.inject.event.Event
import ink.anur.inject.bean.NigateBean
import ink.anur.inject.bean.NigateInject
import ink.anur.inject.event.NigateListenerService
import ink.anur.pojo.HeartBeat
import ink.anur.util.TimeUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Created by Anur IjuoKaruKas on 2019/7/8
 *
 * 选举相关的元数据信息都保存在这里
 */
@NigateBean
class ElectionMetaService {

    @NigateInject
    private lateinit var inetConfiguration: InetConfiguration

    @NigateInject
    private lateinit var kanashiListenerService: NigateListenerService

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @Synchronized
    fun eden(newGen: Long) {
        // 0、更新集群信息
        this.clusters = inetConfiguration.cluster
        this.quorum = clusters!!.size / 2 + 1
        logger.trace("update cluster info")

        // 1、成为follower
        this.becomeFollower()

        // 2、重置 ElectMeta 变量
        logger.trace("update generation: $generation => $newGen")
        this.generation = newGen
        this.offset = 0L
        this.voteRecord = null
        this.box.clear()
        this.leader = null

        this.electionStateChanged(false)
    }

    /**
     * 该投票箱的世代信息，如果一直进行选举，一直能达到 [.ELECTION_TIMEOUT_MS]，而选不出 Leader ，也需要15年，generation才会不够用，如果
     * generation 的初始值设置为 Long.Min （现在是0，则可以撑30年，所以完全呆胶布）
     */
    @Volatile
    var generation: Long = 0L

    /**
     * 流水号，用于生成 id，集群内每一次由 Leader 发起的关键操作都会生成一个id [.genGenerationAndOffset] ()}，其中就需要自增 offset 号
     */
    @Volatile
    var offset: Long = 0L

    /**
     * 现在集群的leader是哪个节点
     */
    @Volatile
    private var leader: RepublicNode? = null

    /**
     * 投票箱
     */
    @Volatile
    var box: MutableMap<RepublicNode, Boolean> = mutableMapOf()

    /**
     * 投票给了谁的投票记录
     */
    @Volatile
    var voteRecord: RepublicNode? = null

    /**
     * 缓存一份集群信息，因为集群信息是可能变化的，我们要保证在一次选举中，集群信息是不变的
     */
    @Volatile
    var clusters: List<RepublicNode>? = null

    /**
     * 法定人数
     */
    @Volatile
    var quorum: Int = Int.MAX_VALUE

    /**
     * 当前节点的角色
     */
    @Volatile
    var raftRole: RaftRole = RaftRole.FOLLOWER

    /**
     * 选举是否已经进行完
     */
    @Volatile
    private var electionCompleted: Boolean = false

    /**
     * 心跳内容
     */
    var heartBeat: HeartBeat? = null

    /**
     * 仅用于统计选主用了多长时间
     */
    var beginElectTime: Long = 0

    /**
     * 世代++
     */
    @Synchronized
    fun generationIncr(): Long = ++generation

    /**
     * 偏移量++
     */
    @Synchronized
    fun offsetIncr(): Long = ++offset

    fun isFollower() = raftRole == RaftRole.FOLLOWER
    fun isCandidate() = raftRole == RaftRole.CANDIDATE
    fun isLeader() = raftRole == RaftRole.LEADER

    @Volatile
    var clusterValid = false

    fun setLeader(leader: RepublicNode) {
        this.leader = leader
    }

    fun getLeader(): RepublicNode? {
        return this.leader
    }

    /**
     * 当集群选举状态变更时调用
     */
    fun electionStateChanged(electionCompleted: Boolean): Boolean {
        val changed = electionCompleted != this.electionCompleted

        if (changed) {
            this.electionCompleted = electionCompleted
            clusterValid = if (electionCompleted) {
                kanashiListenerService.onEvent(Event.CLUSTER_VALID)
                true
            } else {
                kanashiListenerService.onEvent(Event.CLUSTER_INVALID)
                false
            }
        }

        return changed
    }

    /**
     * 成为候选者
     */
    @Synchronized
    fun becomeCandidate(): Boolean {
        return if (raftRole == RaftRole.FOLLOWER) {
            logger.trace("local server changed role from $raftRole to ${RaftRole.CANDIDATE}")
            raftRole = RaftRole.CANDIDATE
            this.electionStateChanged(false)
            true
        } else {
            false
        }
    }

    /**
     * 成为追随者
     */
    @Synchronized
    fun becomeFollower() {
        if (raftRole !== RaftRole.FOLLOWER) {
            logger.trace("local server changed role from  $raftRole to ${RaftRole.FOLLOWER}")
            raftRole = RaftRole.FOLLOWER
        }
    }

    /**
     * 当选票大于一半以上时调用这个方法，如何去成为一个leader
     */
    @Synchronized
    fun becomeLeader() {
        val becomeLeaderCostTime = TimeUtil.getTime() - beginElectTime
        beginElectTime = 0L

        logger.info("local server become leader, start sending heart beat......",
                inetConfiguration.localNode, generation, raftRole, RaftRole.LEADER, becomeLeaderCostTime)

        leader = inetConfiguration.localNode
        raftRole = RaftRole.LEADER
        heartBeat = HeartBeat(generation)
        this.electionStateChanged(true)
    }
}