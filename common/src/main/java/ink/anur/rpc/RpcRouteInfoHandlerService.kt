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
 */
@NigateBean
class RpcRouteInfoHandlerService : AbstractRequestMapping() {

    private val logger = Debugger(this::class.java)

    private val lk = ReentrantReadWriteLocker()

    private lateinit var providerMapping: MutableMap<String/* bean */,
            MutableMap<String /* methodSign */, MutableSet<String/* serverName */>>>

    private lateinit var providerAddressMapping: MutableMap<String, RpcInetSocketAddress>

    override fun typeSupport(): RequestTypeEnum {
        return RequestTypeEnum.RPC_ROUTE_INFO
    }

    override fun handleRequest(republicNode: RepublicNode, msg: ByteBuffer) {
        handlerRouteInfo(RpcRouteInfo(msg))
    }

    fun handlerRouteInfo(routeInfo: RpcRouteInfo) {
        val rpcProviderMappingMeta = routeInfo.GetMeta()
        lk.writeLockSupplier {
            providerMapping = rpcProviderMappingMeta.providerMapping
            providerAddressMapping = rpcProviderMappingMeta.addressMapping

            if (logger.isDebugEnable()) {
                logger.debug("RPC PROVIDER MAPPING: $providerMapping")
                logger.debug("RPC ADDRESS MAPPING: $providerAddressMapping")
            }
        }
    }

    fun searchValidProvider(rpcRequest: RpcRequest): Map<String, RpcInetSocketAddress>? {
        return lk.readLockSupplier {
            val requestMeta = rpcRequest.GetMeta()
            val bean = requestMeta.requestBean ?: requestMeta.requestInterface

            val validProvider = providerMapping[bean]?.let { it[requestMeta.requestMethodSign] }
            if (validProvider == null || validProvider.isEmpty()) {
                throw RPCNonAvailableProviderException("Can not find provider from republic server")
            } else {
                return@readLockSupplier providerAddressMapping.filter { validProvider.contains(it.key) }
            }
        }
    }
}