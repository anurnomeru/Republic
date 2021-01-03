package ink.anur.rpc

import ink.anur.common.struct.RepublicNode
import ink.anur.core.common.AbstractRequestMapping
import ink.anur.debug.Debugger
import ink.anur.exception.RPCNonAvailableProviderException
import ink.anur.inject.bean.NigateBean
import ink.anur.mutex.ReentrantReadWriteLocker
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.pojo.rpc.RpcRouteInfo
import ink.anur.pojo.rpc.RpcRequest
import ink.anur.pojo.rpc.meta.RpcInetSocketAddress
import java.nio.ByteBuffer

/**
 * Created by Anur IjuoKaruKas on 2020/4/10
 *
 * 由于客户端持有从 leader 发来的整个集群可用服务消息
 */
@NigateBean
class RpcPouteInfoHandlerService : AbstractRequestMapping() {

    private val logger = Debugger(this::class.java)

    private val lk = ReentrantReadWriteLocker()

    /**
     * 记录请求应该发往哪个服务消费者的映射
     * TODO 做诸如负载均衡
     */
    private lateinit var providerMapping: MutableMap<String/* bean */,
            MutableMap<String /* methodSign */, MutableSet<String/* serverName */>>>

    /**
     * 当前所有注册服务的地址
     */
    private lateinit var addressMapping: MutableMap<String, RpcInetSocketAddress>

    override fun typeSupport(): RequestTypeEnum {
        return RequestTypeEnum.RPC_PROVIDER_MAPPING
    }

    /**
     * todo rpc 可用机制
     */
    override fun handleRequest(republicNode: RepublicNode, msg: ByteBuffer) {
        handlerRouteInfo(RpcRouteInfo(msg))
    }

    fun handlerRouteInfo(routeInfo: RpcRouteInfo) {
        val rpcProviderMappingMeta = routeInfo.serializableMeta
        lk.writeLockSupplier {
            providerMapping = rpcProviderMappingMeta.providerMapping
            addressMapping = rpcProviderMappingMeta.addressMapping

            if (logger.isDebugEnable()) {
                logger.debug("RPC PROVIDER MAPPING: $providerMapping")
                logger.debug("RPC ADDRESS MAPPING: $addressMapping")
            }
        }
    }

    fun searchValidProvider(rpcRequest: RpcRequest): Map<String, RpcInetSocketAddress>? {
        return lk.readLockSupplier {
            val requestMeta = rpcRequest.serializableMeta
            val bean = requestMeta.requestBean ?: requestMeta.requestInterface

            val validProvider = providerMapping[bean]?.let { it[requestMeta.requestMethodSign] }
            if (validProvider == null || validProvider.isEmpty()) {
                throw RPCNonAvailableProviderException("无法从注册中心找寻到相应的 Provider")// todo 待完善
            } else {
                return@readLockSupplier addressMapping.filter { validProvider.contains(it.key) }
            }
        }
    }
}