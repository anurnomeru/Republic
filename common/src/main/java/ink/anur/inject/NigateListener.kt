package ink.anur.inject

/**
 * Created by Anur IjuoKaruKas on 2020/3/8
 */
annotation class NigateListener(val onEvent: Event)

enum class Event {

    /**
     * 选举成功
     */
    CLUSTER_VALID,

    /**
     * 当节点没有 leader 时
     */
    CLUSTER_INVALID,

    /**
     * 当集群恢复完毕
     */
    RECOVERY_COMPLETE,
}