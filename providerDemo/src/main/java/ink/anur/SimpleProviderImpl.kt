package ink.anur

import SimpleProvider
import ink.anur.inject.rpc.RepublicBean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by Anur IjuoKaruKas on 2022/1/12
 */
@RepublicBean
class SimpleProviderImpl : SimpleProvider {
    private val counter = AtomicInteger()

    override fun foo(): String {
        return "" + counter.addAndGet(1)
    }
}