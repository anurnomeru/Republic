package ink.anur.aop

import ink.anur.core.raft.ClusterStateController
import ink.anur.inject.aop.AopRegistry
import ink.anur.inject.bean.Nigate

/**
 * Created by Anur IjuoKaruKas on 2021/12/29
 */
annotation class OnClusterValid {
    companion object {
        init {
            AopRegistry.Register(
                OnClusterValid::class, 1,
                AopRegistry.InvocationHolder(preInvoke = { any, args ->
                    Nigate.getBeanByClass(ClusterStateController::class.java).Acquire()
                    any
                })
            )
        }
    }
}