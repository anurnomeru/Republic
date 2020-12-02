package ink.anur.rpc.common

/**
 * Created by Anur on 2020/12/2
 */
enum class RPCError(val cause: String) {
    A("remote node no such rpc bean that search by interface"),
    B("remote node can not location unique rpc bean by interface"),
    C("remote node can not location unique rpc bean by bean name");

    companion object {
        fun valueOfNullable(s: String): RPCError? {
            return try {
                valueOf(s)
            } catch (e: Exception) {
                null
            }
        }
    }
}