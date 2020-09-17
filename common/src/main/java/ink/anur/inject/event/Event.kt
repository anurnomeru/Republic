package ink.anur.inject.event

/**
 * Created by Anur on 2020/7/11
 */
enum class Event {

    /**
     * 选举成功
     */
    CLUSTER_VALID,

    /**
     * 当节点没有 leader 时
     */
    CLUSTER_INVALID,
}