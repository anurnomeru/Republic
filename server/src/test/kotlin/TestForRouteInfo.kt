import ink.anur.pojo.rpc.RpcRegistration
import ink.anur.pojo.rpc.RpcRouteInfo
import ink.anur.pojo.rpc.meta.RpcRouteInfoMeta
import ink.anur.util.HessianUtil
import org.junit.Test

/**
 * Created by Anur IjuoKaruKas on 2022/1/11
 */
class TestForRouteInfo {

    @Test
    fun testRouteInfoSer() {
        val rpcRouteInfo = RpcRouteInfo(RpcRouteInfoMeta())
        val getMeta = RpcRouteInfo(rpcRouteInfo.buffer).GetMeta()

        println()
    }
}