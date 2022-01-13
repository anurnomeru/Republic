import ink.anur.inject.bean.Nigate
import ink.anur.inject.event.NigateListenerService
import ink.anur.pojo.rpc.RpcRouteInfo
import ink.anur.pojo.rpc.meta.RpcRouteInfoMeta
import ink.anur.rpc.RpcRouteInfoHandlerService
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.junit.Test


/**
 * Created by Anur IjuoKaruKas on 2022/1/11
 */
@ObsoleteCoroutinesApi
class TestForRouteInfo {

    @Test
    fun testRouteInfoSer() {
        val rpcRouteInfo = RpcRouteInfo(RpcRouteInfoMeta())
        rpcRouteInfo.computeChecksum()

        val getMeta = RpcRouteInfo(rpcRouteInfo.buffer).GetMeta()

        println()
    }

    @Test
    fun testReceive() {
        Nigate.markAsOverRegistry()
        Nigate.registerToNigate(NigateListenerService())
        Nigate.registerToNigate(RpcRouteInfoHandlerService())

        val bean = Nigate.getBeanByClass(RpcRouteInfoHandlerService::class.java)

        bean.handlerRouteInfo(RpcRouteInfo(RpcRouteInfoMeta()))
    }
}