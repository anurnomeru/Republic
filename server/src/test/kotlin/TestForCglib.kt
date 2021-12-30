import ink.anur.common.struct.RepublicNode
import ink.anur.core.raft.ClusterStateController
import ink.anur.inject.aop.AopRegistry
import ink.anur.inject.bean.Nigate
import ink.anur.inject.event.NigateListenerService
import ink.anur.pojo.rpc.RpcRegistration
import ink.anur.pojo.rpc.meta.RpcRegistrationMeta
import ink.anur.service.rpc.RpcRegistrationHandlerService
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * Created by Anur IjuoKaruKas on 2021/12/30
 */
class TestForCglib {

    @Test
    fun testEnhancer(){
        Nigate.markAsOverRegistry()
        Nigate.registerToNigate(NigateListenerService())

        val clusterStateController = ClusterStateController()
        Nigate.registerToNigate(clusterStateController)

        val mayProxyFor = AopRegistry.MayProxyFor(RpcRegistrationHandlerService())

        runBlocking { 
            launch { 
                Thread.sleep(5000)
                clusterStateController.letClusterValid()
            }
        }
        
       mayProxyFor.handleRequest(RepublicNode.Companion.construct("127.0.0.1:8080"),
           RpcRegistration(RpcRegistrationMeta("127.0.0.1:8080")).buffer)
    }
}