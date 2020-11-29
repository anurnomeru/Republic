//package ink.anur.rpc
//
//import ink.anur.common.KanashiExecutors
//import ink.anur.core.common.AbstractRequestMapping
//import ink.anur.core.request.MsgProcessCentreService
//import ink.anur.inject.bean.Nigate
//import ink.anur.inject.bean.NigateBean
//import ink.anur.inject.bean.NigateInject
//import ink.anur.pojo.common.RequestTypeEnum
//import ink.anur.pojo.rpc.RpcRequest
//import ink.anur.pojo.rpc.meta.RpcRequestMeta
//import ink.anur.pojo.rpc.RpcResponse
//import ink.anur.pojo.rpc.meta.RpcResponseMeta
//import io.netty.channel.Channel
//import java.nio.ByteBuffer
//
///**
// * Created by Anur IjuoKaruKas on 2020/4/7
// *
// * 负责接收并处理 rpc 请求的 bean
// *
// * TODO : 与 handler 在线程上解耦，先同一线程没事，做出来再说
// */
//@NigateBean
//class RpcHandlerService : AbstractRequestMapping() {
//
//    @NigateInject
//    private lateinit var msgProcessCentreService: MsgProcessCentreService
//
//    override fun typeSupport(): RequestTypeEnum {
//        return RequestTypeEnum.RPC
//    }
//
//    override fun handleRequest(fromServer: String, msg: ByteBuffer, channel: Channel) {
//        val rpcRequest = RpcRequest(msg)
//        invokeRequestMetaAsync(rpcRequest.requestMeta, fromServer)
//    }
//
//    fun invokeRequestMetaAsync(requestMeta: RpcRequestMeta, fromServer: String) {
//        KanashiExecutors.execute(Runnable {
//            if (requestMeta.requestBean == null) {
//                val rpcBeanByInterfaces = Nigate.getRPCBeanByInterface(requestMeta.requestInterface)
//                when {
//                    rpcBeanByInterfaces == null -> {
//                        msgProcessCentreService.sendAsync(fromServer, RpcResponse(RpcResponseMeta("ERR1", requestMeta.msgSign, error = true)))
//                    }
//                    rpcBeanByInterfaces.size > 1 -> {
//                        msgProcessCentreService.sendAsync(fromServer, RpcResponse(RpcResponseMeta("ERR2", requestMeta.msgSign, error = true)))
//                    }
//                    else -> {
//                        val kanashiRpcBean = rpcBeanByInterfaces[0]
//                        val result = requestMeta.requestParams?.let { kanashiRpcBean.invokeMethod(requestMeta.requestMethodSign, *it) }
//                            ?: kanashiRpcBean.invokeMethod(requestMeta.requestMethodSign)
//
//                        msgProcessCentreService.sendAsync(fromServer, RpcResponse(RpcResponseMeta(result, requestMeta.msgSign)))
//                    }
//                }
//            } else {
//                when (val kanashiRpcBean = Nigate.getRPCBeanByName(requestMeta.requestBean)) {
//                    null -> {
//                        msgProcessCentreService.sendAsync(fromServer, RpcResponse(RpcResponseMeta("ERR3", requestMeta.msgSign, error = true)))
//                    }
//                    else -> {
//                        val result = requestMeta.requestParams?.let { kanashiRpcBean.invokeMethod(requestMeta.requestMethodSign, *it) }
//                            ?: kanashiRpcBean.invokeMethod(requestMeta.requestMethodSign)
//                        msgProcessCentreService.sendAsync(fromServer, RpcResponse(RpcResponseMeta(result, requestMeta.msgSign)))
//                    }
//                }
//            }
//        })
//    }
//}