package ink.anur.rpc

import ink.anur.common.KanashiExecutors
import ink.anur.common.struct.RepublicNode
import ink.anur.core.common.AbstractRequestMapping
import ink.anur.inject.bean.Nigate
import ink.anur.inject.bean.NigateBean
import ink.anur.io.common.transport.Connection.Companion.send
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.pojo.rpc.RpcRequest
import ink.anur.pojo.rpc.RpcResponse
import ink.anur.pojo.rpc.meta.RpcRequestMeta
import ink.anur.pojo.rpc.meta.RpcResponseMeta
import java.nio.ByteBuffer

/**
 * Created by Anur IjuoKaruKas on 2020/4/7
 *
 * 负责接收并处理 rpc 请求的 bean
 *
 * TODO : 与 handler 在线程上解耦，先同一线程没事，做出来再说
 */
@NigateBean
class RpcHandlerService : AbstractRequestMapping() {

    private val nigate = Nigate

    override fun typeSupport(): RequestTypeEnum {
        return RequestTypeEnum.RPC_REQUEST
    }

    override fun handleRequest(republicNode: RepublicNode, msg: ByteBuffer) {
        val rpcRequestMeta = RpcRequest(msg)
        val requestMeta = rpcRequestMeta.serializableMeta

        if (requestMeta.requestBean == null) {
            val rpcBeanByInterfaces = nigate.getRPCBeanByInterface(requestMeta.requestInterface)
            when {
                rpcBeanByInterfaces == null -> {
                    republicNode.send(RpcResponse(RpcResponseMeta("ERR1", error = true)))
                }
                rpcBeanByInterfaces.size > 1 -> {
                    republicNode.send(RpcResponse(RpcResponseMeta("ERR2", error = true)))
                }
                else -> {
                    val rpcBean = rpcBeanByInterfaces[0]
                    val result = requestMeta.requestParams?.let { rpcBean.invokeMethod(requestMeta.requestMethodSign, *it) }
                            ?: rpcBean.invokeMethod(requestMeta.requestMethodSign)

                    republicNode.send(RpcResponse(RpcResponseMeta(result)))
                }
            }
        } else {
            when (val rpcBean = nigate.getRPCBeanByName(requestMeta.requestBean)) {
                null -> {
                    republicNode.send(RpcResponse(RpcResponseMeta("ERR3", error = true)))
                }
                else -> {
                    val result = requestMeta.requestParams?.let { rpcBean.invokeMethod(requestMeta.requestMethodSign, *it) }
                            ?: rpcBean.invokeMethod(requestMeta.requestMethodSign)
                    republicNode.send(RpcResponse(RpcResponseMeta(result)))
                }
            }
        }
    }
}