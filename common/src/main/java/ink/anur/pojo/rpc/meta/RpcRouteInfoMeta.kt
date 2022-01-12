package ink.anur.pojo.rpc.meta

import ink.anur.pojo.metastruct.SerializableMeta
import kotlin.text.StringBuilder

class RpcRouteInfoMeta(
    val providerMapping: MutableMap<String/* bean */,
            MutableMap<String /* methodSign */, MutableSet<String/* localNodeAddr */>>> = mutableMapOf(),
) : SerializableMeta {

    fun StringInfo():String {
        val info = StringBuilder("Valid Provider:")
        for (mutableEntry in providerMapping) {
            info.appendLine(" - Bean: ${mutableEntry.key}")
            for (e in mutableEntry.value) {
                info.appendLine(" ==> MethodSign ${e.key}")
                for (s in e.value) {
                    info.appendLine(" >>>>> $s")
                }
                info.appendLine()
            }
        }

        return info.toString()
    }
}

