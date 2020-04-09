package ink.anur.inject

import ink.anur.exception.RPCNotSuchMethodException
import ink.anur.util.ClassMetaUtil
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.HashMap
import java.util.HashSet

/**
 * Created by Anur IjuoKaruKas on 2020/4/7
 *
 * 管理 RPC_REQUEST 模块 bean 的所有方法，负责做方法调用
 */
class KanashiRpcBean(private val bean: Any) {
    private val interfacesMapping = HashSet<String>()

    /**
     * key   -> interface
     * value -> interfaceMeta
     */
    private val methodSignMapping = HashMap<String, Method>()

    init {
        val clazz = bean.javaClass
        val interfaces = clazz.interfaces

        for (anInterface in interfaces) {
            interfacesMapping.add(anInterface.simpleName)
            for (method in anInterface.methods) {
                methodSignMapping[ClassMetaUtil.methodSignGen(method)] = method
            }
        }
    }

    @Throws(InvocationTargetException::class, IllegalAccessException::class)
    fun invokeMethod(methodSign: String, vararg args: Any): Any? {
        val method = methodSignMapping[methodSign] ?: throw RPCNotSuchMethodException("不存在此方法")

        return when (args.size) {
            0 -> {
                method.invoke(bean)
            }
            else -> {
                method.invoke(bean, args)
            }
        }

    }

    fun getInterfacesMapping(): Set<String> {
        return interfacesMapping
    }
}