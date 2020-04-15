package ink.anur.config.common

import ink.anur.common.struct.KanashiNode
import ink.anur.exception.ApplicationConfigException
import ink.anur.exception.KanashiException
import java.util.ResourceBundle
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * Created by Anur IjuoKaruKas on 2019/7/5
 *
 * 统一操作配置文件入口
 */
open class ConfigHelper {

    companion object {
        @Volatile
        private var resourceBundle: ResourceBundle? = null

        init {
            try {
                resourceBundle = ResourceBundle.getBundle("kanashi")
            } catch (t: Throwable) {
            }
        }

        val isServer = resourceBundle != null
        private val readLock: Lock
        private val writeLock: Lock
        private val cache = ConcurrentHashMap<ConfigurationEnum, Any>()

        init {
            val readWriteLock = ReentrantReadWriteLock()
            readLock = readWriteLock.readLock()
            writeLock = readWriteLock.writeLock()
        }

        /**
         * 优先获取缓存中的值，如果获取不到再从配置文件获取
         */
        private fun lockSupplier(configEnum: ConfigurationEnum, supplier: () -> Any): Any {
            val t: Any
            try {
                readLock.lock()
                cache.containsKey(configEnum)

                (if (cache.containsKey(configEnum)) {
                    t = cache[configEnum]!!
                } else {
                    t = supplier.invoke()
                    cache[configEnum] = t
                })

            } finally {
                readLock.unlock()
            }
            return t
        }

        /**
         * 刷新配置
         */
        fun refresh() {
            try {
                writeLock.lock()
                cache.clear()
                resourceBundle = ResourceBundle.getBundle("application")
            } finally {
                writeLock.unlock()
            }
        }

        /**
         * 根据key获取某个配置
         */
        fun getConfig(configEnum: ConfigurationEnum, transfer: (String) -> Any?): Any {
            if (!isServer) {
                throw KanashiException("非协调节点无法使用协调配置")
            } else {
                return lockSupplier(configEnum) {
                    transfer.invoke(resourceBundle!!.getString(configEnum.key))
                        ?: throw ApplicationConfigException("读取application.properties配置异常，异常项目：${configEnum.key}，建议：${configEnum.adv}")
                }
            }
        }

        /**
         * 根据key获取某个配置，如果出现问题直接返回 null
         */
        fun getConfigSwallow(configEnum: ConfigurationEnum, transfer: (String) -> Any?): Any? {

            val result = try {
                lockSupplier(configEnum) {
                    transfer.invoke(resourceBundle!!.getString(configEnum.key))
                        ?: throw ApplicationConfigException("读取application.properties配置异常，异常项目：${configEnum.key}，建议：${configEnum.adv}")
                }
            } catch (e: Exception) {
                return null
            }

            return result
        }

        /**
         * 根据key模糊得获取某些配置，匹配规则为 key%
         */
        fun getConfigSimilar(configEnum: ConfigurationEnum, transfer: (Pair<String, String>) -> Any?): Any {
            if (!isServer) {
                throw KanashiException("非协调节点无法使用协调配置")
            } else {
                return lockSupplier(configEnum) {
                    val stringEnumeration = resourceBundle!!.keys
                    val keys = mutableListOf<String>()

                    while (stringEnumeration.hasMoreElements()) {
                        val k = stringEnumeration.nextElement()
                        if (k.startsWith(configEnum.key)) {
                            keys.add(k)
                        }
                    }

                    keys.map {
                        transfer.invoke(
                            Pair(
                                if (it.length > configEnum.key.length) it.substring(configEnum.key.length + 1) else it,
                                resourceBundle!!.getString(it)
                            )) ?: throw ApplicationConfigException("读取application.properties配置异常，异常项目：${configEnum.key}，建议：${configEnum.adv}")
                    }
                }
            }
        }
    }
}
