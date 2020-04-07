package ink.anur.core.raft

/**
 * Created by Anur IjuoKaruKas on 2020/2/26
 */
enum class TaskEnum {
    ASK_FOR_VOTES,
    BECOME_CANDIDATE,
    HEART_BEAT,
}