package ink.anur.exception

/**
 * Created by Anur IjuoKaruKas on 2020/4/10
 *
 * 请求已经落到了 provider，但是还是抛出了异常
 */
class RPCUnderRequestException(message: String) : KanashiException(message)