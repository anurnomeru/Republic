package ink.anur

import SimpleProvider
import ink.anur.inject.rpc.RepublicBean

/**
 * Created by Anur IjuoKaruKas on 2022/1/12
 */
@RepublicBean
class SimpleProviderImpl : SimpleProvider {
    override fun foo(): String {
        return "foo"
    }
}