import ink.anur.core.raft.meta.ProposalMeta
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.pojo.rpc.RpcRegistration
import ink.anur.pojo.rpc.meta.RpcRegistrationMeta
import ink.anur.util.HessianUtil
import kotlin.test.Test

/**
 * Created by Anur IjuoKaruKas on 2021/12/30
 */
class TestForProposalMeta {

    val meta = RpcRegistrationMeta(
        mapOf<String/* bean */, HashSet<String /* method */>>(), mapOf(),
    )

    val rpcRegistration = RpcRegistration(
        meta
    )

    @Test
    fun testSer() {
        //        val proposal = Proposal(ProposalMeta(RequestTypeEnum.RPC_REGISTRATION, rpcRegistration))
        //        val getMeta = proposal.GetMeta()
    }

    @Test
    fun testSerRegMeta() {
        val ser = HessianUtil.ser(rpcRegistration)
        val des = HessianUtil.des(ser, RpcRegistration::class.java)

        assert(des.GetMeta().RPC_BEAN == meta.RPC_BEAN)
    }

    @Test
    fun testSerMeta() {
        val ser = HessianUtil.ser(meta)
        val des = HessianUtil.des(ser, RpcRegistrationMeta::class.java)

        assert(des.RPC_BEAN == meta.RPC_BEAN)
    }
}