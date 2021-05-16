package ink.anur.exception.codeabel_exception

/**
 * Created by Anur IjuoKaruKas on 2021/5/16
 */
class ClusterInvalidException : CodeableException("Cluster is not valid after waiting 3000ms") {
    override fun errorCode(): Int {
        return 2
    }
}