package ink.anur.exception.codeabel_exception

/**
 * Created by Anur IjuoKaruKas on 2021/5/16
 */
class NotLeaderException : CodeableException("Current node is not the leader of cluster, may reelect occur during processing.") {
    override fun errorCode(): Int {
        return 1
    }
}