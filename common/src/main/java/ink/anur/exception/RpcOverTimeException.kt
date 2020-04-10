package ink.anur.exception

/**
 * Created by Anur IjuoKaruKas on 2020/4/10
 *
 * 请求已经落到了 provider，但是超时了
 */
class RpcOverTimeException(message: String) : KanashiException(message)