package ink.anur.core

import ink.anur.inject.aop.AopRegistry
import ink.anur.inject.bean.Nigate

/**
 * Created by Anur IjuoKaruKas on 2022/1/13
 */
annotation class OnRpcValid {
    companion object {
        init {
            AopRegistry.Register(
                OnRpcValid::class, 1,
                AopRegistry.InvocationHolder(preInvoke = { any, args ->
                    Nigate.getBeanByClass(RpcStateController::class.java).Acquire()
                    any
                })
            )
        }
    }
}
