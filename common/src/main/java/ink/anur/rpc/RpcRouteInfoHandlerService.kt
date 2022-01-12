package ink.anur.rpc

import ink.anur.common.struct.RepublicNode
import ink.anur.core.common.AbstractRequestMapping
import ink.anur.debug.Debugger
import ink.anur.exception.RPCNonAvailableProviderException
import ink.anur.inject.bean.NigateBean
import ink.anur.mutex.SwitchableReentrantReadWriteLocker
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.pojo.rpc.RpcRouteInfo
import ink.anur.pojo.rpc.RpcRequest
import java.nio.ByteBuffer
import kotlin.random.Random

/**
 * Created by Anur IjuoKaruKas on 2020/4/10
 */
@NigateBean
class RpcRouteInfoHandlerService : AbstractRequestMapping() {

    private val logger = Debugger(this::class.java)

    private val lk = SwitchableReentrantReadWriteLocker()

    private var random = Random(1)

    private lateinit var providerMapping: MutableMap<String/* bean */,
            MutableMap<String /* methodSign */, MutableSet<String/* serverName */>>>

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

            if (logger.isDebugEnable()) {
                logger.debug("RPC PROVIDER MAPPING: $providerMapping")
            }
        }
    }

    fun searchValidProvider(rpcRequest: RpcRequest): RepublicNode = lk.readLockSupplierCompel {
        val requestMeta = rpcRequest.GetMeta()
        val bean = requestMeta.requestBean ?: requestMeta.requestInterface

        val validProvider = providerMapping[bean]?.let { it[requestMeta.requestMethodSign] }
        if (validProvider == null || validProvider.isEmpty()) {
            throw RPCNonAvailableProviderException("Can not find provider from republic server")
        } else {
            val index = random.nextInt() % validProvider.count()
            var count = 0

            val addr = validProvider.firstOrNull {
                if (count == index) {
                    true
                } else {
                    count++
                    false
                }
            }
            return@readLockSupplierCompel RepublicNode.construct(addr!!)
        }
    }
}