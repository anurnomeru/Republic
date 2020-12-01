package ink.anur.exception

/**
 * Created by Anur IjuoKaruKas on 2020/4/10
 *
 * 无法从 broker 获取到想要请求的类
 */
class RPCNonAvailableProviderException(message: String) : KanashiException(message)