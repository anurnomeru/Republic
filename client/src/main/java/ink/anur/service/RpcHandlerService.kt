package ink.anur.service

import ink.anur.common.KanashiExecutors
import ink.anur.core.common.AbstractRequestMapping
import ink.anur.core.request.MsgProcessCentreService
import ink.anur.exception.KanashiException
import ink.anur.exception.RpcUnderRequestException
import ink.anur.inject.Nigate
import ink.anur.inject.NigateBean
import ink.anur.inject.NigateInject
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.pojo.rpc.RpcRequest
import ink.anur.pojo.rpc.RpcRequestMeta
import ink.anur.pojo.rpc.RpcResponse
import ink.anur.pojo.rpc.RpcResponseMeta
import io.netty.channel.Channel
import java.nio.ByteBuffer

/**
 * Created by Anur IjuoKaruKas on 2020/4/7
 *
 * 负责接收并处理 rpc 请求的服务
 *
 * TODO : 与 handler 在线程上解耦，先同一线程没事，做出来再说
 */
@NigateBean
class RpcHandlerService : AbstractRequestMapping() {

    @NigateInject
    private lateinit var msgProcessCentreService: MsgProcessCentreService

    override fun typeSupport(): RequestTypeEnum {
        return RequestTypeEnum.RPC_REQUEST
    }

    override fun handleRequest(fromServer: String, msg: ByteBuffer, channel: Channel) {
        val rpcRequest = RpcRequest(msg)
        val requestMeta = rpcRequest.requestMeta
        KanashiExecutors.execute(Runnable {
            val requestBean = requestMeta.requestBean
            if (requestBean == null) {
                val rpcBeanByInterfaces = Nigate.getRPCBeanByInterface(requestMeta.requestInterface)
                when {
                    rpcBeanByInterfaces == null -> {
                        msgProcessCentreService.sendAsync(fromServer, RpcResponse(RpcResponseMeta("ERR1", requestMeta.msgSign, error = true)))
                    }
                    rpcBeanByInterfaces.size > 1 -> {
                        msgProcessCentreService.sendAsync(fromServer, RpcResponse(RpcResponseMeta("ERR2", requestMeta.msgSign, error = true)))
                    }
                    else -> {
                        val result = try {
                            val kanashiRpcBean = rpcBeanByInterfaces[0]
                            requestMeta.requestParams?.let { kanashiRpcBean.invokeMethod(requestMeta.requestMethodSign, *it) }
                                ?: kanashiRpcBean.invokeMethod(requestMeta.requestMethodSign)
                        } catch (e: Exception) {
                            msgProcessCentreService.sendAsync(fromServer, RpcResponse(RpcResponseMeta(e.message, requestMeta.msgSign, error = true)))
                        }

                        msgProcessCentreService.sendAsync(fromServer, RpcResponse(RpcResponseMeta(result, requestMeta.msgSign)))
                    }
                }
            } else {
                when (val kanashiRpcBean = Nigate.getRPCBeanByName(requestBean)) {
                    null -> {
                        msgProcessCentreService.sendAsync(fromServer, RpcResponse(RpcResponseMeta("ERR3", requestMeta.msgSign, error = true)))
                    }
                    else -> {
                        val result = try {
                            requestMeta.requestParams?.let { kanashiRpcBean.invokeMethod(requestMeta.requestMethodSign, *it) }
                                ?: kanashiRpcBean.invokeMethod(requestMeta.requestMethodSign)
                        } catch (e: Exception) {
                            msgProcessCentreService.sendAsync(fromServer, RpcResponse(RpcResponseMeta(e.message, requestMeta.msgSign, error = true)))
                        }
                        msgProcessCentreService.sendAsync(fromServer, RpcResponse(RpcResponseMeta(result, requestMeta.msgSign)))
                    }
                }
            }
        })
    }

    var count = 0

}