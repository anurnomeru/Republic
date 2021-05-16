package ink.anur.core.server

import ink.anur.inject.bean.NigateBean
import ink.anur.mutex.ReentrantReadWriteLocker
import ink.anur.pojo.rpc.meta.RpcInetSocketAddress
import ink.anur.pojo.rpc.RpcProviderMapping
import ink.anur.pojo.rpc.meta.RpcProviderMappingMeta
import ink.anur.pojo.rpc.meta.RpcRegistrationMeta

/**
 * Created by Anur IjuoKaruKas on 2020/4/9
 */
@NigateBean
class RpcRegistrationCenterService : ReentrantReadWriteLocker() {

    private val providerMapping = mutableMapOf<String/* bean */,
        MutableMap<String /* methodSign */, MutableSet<String/* serverName */>>>()

    private val addressMapping = mutableMapOf<String, RpcInetSocketAddress>()

    private val enableServerMapping = mutableMapOf<String, RpcRegistrationMeta>()

    fun getEnableServerMapping(): MutableSet<String> {
        return readLockSupplierCompel {
            enableServerMapping.keys
        }
    }

    fun getProviderMapping(): RpcProviderMapping {
        return readLockSupplierCompel {
            RpcProviderMapping(RpcProviderMappingMeta(providerMapping, addressMapping))
        }
    }

    fun register(serverName: String, address: RpcInetSocketAddress, registration: RpcRegistrationMeta): String {
        return writeLockSupplierCompel {

            addressMapping[serverName] = address
            enableServerMapping[serverName] = registration

            val rpcBean = registration.RPC_BEAN
            val rpcInterfaceBean = registration.RPC_INTERFACE_BEAN

            for (mutableEntry in rpcBean) {
                val bean = mutableEntry.key
                val kanashiRpcBean = mutableEntry.value
                registerToGuiding(serverName, bean, kanashiRpcBean)
            }
            for (mutableEntry in rpcInterfaceBean) {
                val bean = mutableEntry.key
                val kanashiRpcBeanList = mutableEntry.value
                for (kanashiRpcBean in kanashiRpcBeanList) {
                    registerToGuiding(serverName, bean, kanashiRpcBean)
                }
            }

            return@writeLockSupplierCompel serverName
        }
    }

    fun unRegister(serverName: String) {
        writeLockSupplier {
            val rpcRegistrationMeta = enableServerMapping[serverName] ?: return@writeLockSupplier

            val rpcBean = rpcRegistrationMeta.RPC_BEAN
            val rpcInterfaceBean = rpcRegistrationMeta.RPC_INTERFACE_BEAN

            for (mutableEntry in rpcBean) {
                val bean = mutableEntry.key
                val kanashiRpcBean = mutableEntry.value
                unRegisterFromGuiding(serverName, bean, kanashiRpcBean)
            }
            for (mutableEntry in rpcInterfaceBean) {
                val bean = mutableEntry.key
                val kanashiRpcBeanList = mutableEntry.value
                for (kanashiRpcBean in kanashiRpcBeanList) {
                    unRegisterFromGuiding(serverName, bean, kanashiRpcBean)
                }
            }

            addressMapping.remove(serverName)
            enableServerMapping.remove(serverName)
        }
    }

    private fun registerToGuiding(serverName: String, bean: String, methodSigns: MutableSet<String>) {
        for (methodSign in methodSigns) {
            providerMapping.compute(bean) { _, innerMap ->
                val methodSignMapping = innerMap ?: mutableMapOf()

                methodSignMapping.compute(methodSign) { _, iiMap ->
                    val serverSet = iiMap ?: mutableSetOf()
                    serverSet.add(serverName)
                    return@compute serverSet
                }

                return@compute methodSignMapping
            }
        }
    }

    private fun unRegisterFromGuiding(serverName: String, bean: String, methodSigns: MutableSet<String>) {
        for (methodSign in methodSigns) {
            providerMapping.compute(bean) { _, innerMap ->
                val methodSignMapping = innerMap ?: mutableMapOf()

                methodSignMapping.compute(methodSign) { _, iiMap ->
                    val serverSet = iiMap ?: mutableSetOf()
                    serverSet.remove(serverName)
                    return@compute serverSet
                }

                return@compute methodSignMapping
            }
        }
    }
}