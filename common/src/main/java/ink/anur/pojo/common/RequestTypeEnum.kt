package ink.anur.pojo.common

import ink.anur.pojo.EmptyStruct
import ink.anur.pojo.Register
import ink.anur.pojo.RegisterResponse
import ink.anur.pojo.coordinate.Voting
import ink.anur.exception.KanashiException
import ink.anur.pojo.coordinate.Canvass
import ink.anur.pojo.HeartBeat
import ink.anur.pojo.rpc.RpcRegistration
import ink.anur.pojo.rpc.RpcRegistrationResponse
import ink.anur.pojo.rpc.RpcRequest
import ink.anur.pojo.rpc.RpcResponse
import java.util.HashMap

/**
 * Created by Anur IjuoKaruKas on 2020/2/22
 */
enum class RequestTypeEnum(val byteSign: Int, val clazz: Class<out AbstractStruct>) {

    /**
     * 无类型
     */
    EMPTY_STRUCT(-1, EmptyStruct::class.java),

    /**
     * 心跳
     */
    HEAT_BEAT(9999, HeartBeat::class.java),

    /**
     * 协调从节点向主节点注册
     */
    REGISTER(10000, Register::class.java),

    /**
     * 协调从节点向主节点注册 的回复
     */
    REGISTER_RESPONSE(10001, RegisterResponse::class.java),

    /**
     * 进行拉票
     */
    CANVASS(10002, Canvass::class.java),

    /**
     * 进行投票
     */
    VOTING(10003, Voting::class.java),

    /**
     * 进行rpc请求
     */
    RPC_REQUEST(99999, RpcRequest::class.java),

    /**
     * 进行rpc回复
     */
    RPC_RESPONSE(99998, RpcResponse::class.java),

    /**
     * 进行rpc注册
     */
    RPC_REGISTRATION(9997, RpcRegistration::class.java),

    /**
     * 进行rpc注册的回复
     */
    RPC_REGISTRATION_RESPONSE(9997, RpcRegistrationResponse::class.java)
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

        fun parseByByteSign(byteSign: Int): RequestTypeEnum = byteSignMap[byteSign] ?: throw UnsupportedOperationException()
    }
}