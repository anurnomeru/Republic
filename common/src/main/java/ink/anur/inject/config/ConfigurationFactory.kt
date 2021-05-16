package ink.anur.inject.config

import ink.anur.debug.Debugger
import ink.anur.exception.KanashiException
import ink.anur.inject.bean.Nigate
import java.lang.reflect.Modifier
import java.util.*
import kotlin.reflect.KClass

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

    @Suppress("UNCHECKED_CAST")
    @Synchronized
    fun <T> genOrGetByType(prefixAndClass: PrefixAndClass<T>): T? {
        if (mapping.containsKey(prefixAndClass)) {
            return mapping[prefixAndClass] as T
        }
        val prefix = prefixAndClass.prefix
        val clazz = prefixAndClass.clazz

        val result: T
        // 暂不支持过多类型
        when {
            prefixAndClass.isClassOf(Int::class) -> {
                result = getString(prefix)?.toInt() as T
            }
            prefixAndClass.isClassOf(Long::class) -> {
                result = getString(prefix)?.toLong() as T
            }
            prefixAndClass.isClassOf(String::class) -> {
                result = getString(prefix) as T
            }
            else -> {
                // must having no args constructor
                try {
                    result = clazz.getDeclaredConstructor().newInstance()
                } catch (e: Throwable) {
                    logger.error("please make sure member [$clazz] prefix [$prefix] has default constructor or use @ConfigurationIgnore to skip inject it")
                    throw e
                }
                val declaredFields = clazz.declaredFields

                for (field in declaredFields) {
                    if (Modifier.isAbstract(field.modifiers) || Modifier.isStatic(field.modifiers) || Modifier.isFinal(
                            field.modifiers
                        )
                        || field.isAnnotationPresent(ConfigurationIgnore::class.java)
                    ) {
                        continue
                    }

                    val fieldName = field.name
                    genOrGetByType(
                        PrefixAndClass(prefix?.let { it + SPLITTER + fieldName } ?: fieldName,
                            field.type))
                        ?.also {
                        field.isAccessible = true
                        field.set(result, it)
                    }
                }
            }
        }

        return result?.also { mapping[prefixAndClass] = (it as Any) }
    }

    private fun getString(key: String?): String? {
        key ?: throw KanashiException("dot not support empty key")

        return try {
            Nigate.getFromVm(key) ?: properties.getProperty(key)
        } catch (e: Exception) {
            logger.warn("getting config '{}' error", key)
            null
        }
    }

    class PrefixAndClass<T>(val prefix: String? = null, val clazz: Class<T>) {

        fun isClassOf(kClass: KClass<*>): Boolean {
            return kClass.java == clazz || kClass.javaObjectType == clazz || kClass.javaPrimitiveType == clazz
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as PrefixAndClass<*>

            if (clazz != other.clazz) return false
            if (prefix != other.prefix) return false

            return true
        }

        override fun hashCode(): Int {
            var result = clazz.hashCode()
            result = 31 * result + (prefix?.hashCode() ?: 0)
            return result
        }
    }
}