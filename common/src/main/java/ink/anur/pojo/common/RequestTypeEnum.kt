package ink.anur.pojo.common

import ink.anur.exception.KanashiException
import java.util.*

/**
 * Created by Anur IjuoKaruKas on 2020/2/22
 */
enum class RequestTypeEnum(val byteSign: Int) {

    /**
     * 颁发发送许可
     */
    SEND_LICENSE(8000),

    /**
     * 颁发发送许可
     */
    SEND_LICENSE_RESPONSE(8001),

    /**
     * 心跳
     */
    HEAT_BEAT(9999),

    /**
     * 向其他 server 进行注册
     */
    SYN(10000),

    /**
     * 拿到其他 server 的注册回复
     */
    SYN_RESPONSE(10001),

    /**
     * 进行拉票
     */
    CANVASS(10002),

    /**
     * 进行投票
     */
    VOTING(10003),

    /**
     * 进行rpc请求
     */
    RPC(99999)
    ;

    companion object {
        private val byteSignMap = HashMap<Int, RequestTypeEnum>()

        init {
            val unique = mutableSetOf<Int>()
            for (value in values()) {
                if (!unique.add(value.byteSign)) {
                    throw KanashiException("RequestTypeEnum 中，byteSign 不可重复。");
                }
                byteSignMap[value.byteSign] = value;
            }
        }

        fun parseByByteSign(byteSign: Int): RequestTypeEnum =
                byteSignMap[byteSign]
                        ?: throw UnsupportedOperationException("can not receive byte sign $byteSign")
    }
}