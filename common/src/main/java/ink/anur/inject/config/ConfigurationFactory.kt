package ink.anur.inject.config

import ink.anur.config.ElectConfiguration
import ink.anur.config.InetConfiguration
import ink.anur.debug.Debugger
import ink.anur.exception.KanashiException
import java.util.*

/**
 * Created by Anur on 2020/9/16
 *
 * identity -> prefix + clazz
 */
object ConfigurationFactory {

    private const val SPLITTER = "."
    private val logger = Debugger(this::class.java)
    private val mapping = mutableMapOf<PrefixAndClass<*>, Any>()

    @Volatile
    private var properties: Properties = Properties()

    init {
        properties.load(ClassLoader.getSystemResourceAsStream("kanashi.properties"))
    }

    @Synchronized
    fun <T> genOrGetByType(prefixAndClass: PrefixAndClass<T>): T? {
        if (mapping[prefixAndClass] != null) {
            return mapping[prefixAndClass] as T
        }
        val prefix = prefixAndClass.prefix
        val clazz = prefixAndClass.clazz

        // 暂不支持过多类型
        when {
            String::class.java == clazz -> {
                return getString(prefix) as T
            }
            Integer::class.java == clazz || Int::class.java == clazz -> {
                return getString(prefix)?.toInt() as T
            }
            Long::class.java == clazz -> {
                return getString(prefix)?.toLong() as T
            }
            else -> {
                // must having no args constructor
                val result = clazz.getDeclaredConstructor().newInstance()
                val declaredFields = clazz.declaredFields

                for (field in declaredFields) {
                    val fieldName = field.name
                    val value = genOrGetByType(PrefixAndClass(field.type, prefix?.let { it + SPLITTER + fieldName }
                            ?: fieldName))

                    field.isAccessible = true
                    field.set(result, value)
                }
                return result
            }
        }
    }

    private fun getString(key: String?): String? {
        key ?: throw KanashiException("dot not support empty key")

        return try {
            properties.getProperty(key)
        } catch (e: Exception) {
            logger.warn("getting config '{}' error", key)
            null
        }
    }

    class PrefixAndClass<T>(val clazz: Class<T>, val prefix: String? = null)
}