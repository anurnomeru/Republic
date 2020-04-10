package ink.anur.core.rpc

import ink.anur.inject.NigateBean
import ink.anur.mutex.ReentrantReadWriteLocker
import ink.anur.pojo.rpc.RpcRegistrationMeta
import java.net.InetSocketAddress

/**
 * Created by Anur IjuoKaruKas on 2020/4/9
 *
 * RPC 注册中心
 * todo 实际上server集群运行的话，需要有一个主从的同步机制，目前先写单节点注册中心，让它整个流程能跑起来再说
 */
@NigateBean
class RpcRegistrationCenterService : ReentrantReadWriteLocker() {

    /**
     * 记录请求应该发往哪个服务消费者的映射
     * TODO 做诸如负载均衡
     */
    private val providerMapping = mutableMapOf<String/* bean */,
        MutableMap<String /* methodSign */, MutableSet<String/* serverName */>>>()

    /**
     * 记录已有的 server 们的一个映射
     */
    private val enableServerMapping = mutableMapOf<String, RpcRegistrationMeta>()

    /**
     * 当前所有注册服务的地址
     */
    private val addressMapping = mutableMapOf<String, InetSocketAddress>()

    /**
     * 向注册中心注册并获得一个服务名（用于取消注册）
     */
    fun register(serverName: String, address: InetSocketAddress, registration: RpcRegistrationMeta): String {
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
        }
    }

    /**
     * 将 rpc 信息注册到注册中心
     */
    private fun registerToGuiding(serverName: String, bean: String, methodSigns: MutableSet<String>) {
        for (methodSign in methodSigns) {
            providerMapping.compute(serverName) { _, innerMap ->
                val methodSignMapping = innerMap ?: mutableMapOf()

                methodSignMapping.compute(methodSign) { _, iiMap ->
                    val serverSet = iiMap ?: mutableSetOf()
                    serverSet.add(bean)
                    return@compute serverSet
                }

                return@compute methodSignMapping
            }
        }
    }

    /**
     * 将 rpc 信息从注册中心移除
     */
    private fun unRegisterFromGuiding(serverName: String, bean: String, methodSigns: MutableSet<String>) {
        for (methodSign in methodSigns) {
            providerMapping.compute(serverName) { _, innerMap ->
                val methodSignMapping = innerMap ?: mutableMapOf()

                methodSignMapping.compute(methodSign) { _, iiMap ->
                    val serverSet = iiMap ?: mutableSetOf()
                    serverSet.remove(bean)
                    return@compute serverSet
                }

                return@compute methodSignMapping
            }
        }
    }
}