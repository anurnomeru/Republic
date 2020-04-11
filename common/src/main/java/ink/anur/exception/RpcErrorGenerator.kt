package ink.anur.exception

import java.util.concurrent.ConcurrentHashMap

/**
 * Created by Anur IjuoKaruKas on 2020/4/10
 */
class RpcErrorGenerator {
    companion object {
        val RPC_ERROR_MAPPING = ConcurrentHashMap<String, () -> RpcUnderRequestException>()

        init {
            RPC_ERROR_MAPPING["ERR1"] = { RpcUnderRequestException("无法根据指定的接口在 Provider 找到对应的 RPC 实现类，讲道理不应该会出现这样的问题") }
            RPC_ERROR_MAPPING["ERR2"] = { RpcUnderRequestException("在目标 Provider 根据指定接口找到了多个 RPC 实现类，请在 @RepublicInject() 指定别名来对其中一个进行请求！") }
            RPC_ERROR_MAPPING["ERR3"] = { RpcUnderRequestException("在目标 Provider 无法根据指定的别名来找到对应的 RPC 实现，请确认 @RepublicInject() 别名的正确性！") }
        }
    }
}