package ink.anur.inject.aop

import net.sf.cglib.proxy.Enhancer
import net.sf.cglib.proxy.MethodInterceptor
import net.sf.cglib.proxy.MethodProxy
import org.slf4j.LoggerFactory
import java.lang.reflect.Method
import kotlin.reflect.KClass


/**
 * Created by Anur IjuoKaruKas on 2021/12/30
 */
object AopRegistry {
    private val logger = LoggerFactory.getLogger(this.javaClass)
    private val aops = mutableMapOf<KClass<out Annotation>, InvocationHolder>()
    private val aopsPriority = mutableMapOf<KClass<out Annotation>, Int>()

    fun Register(clazz: KClass<out Annotation>, priority: Int, invocationHolder: InvocationHolder) {
        aops[clazz] = invocationHolder
        aopsPriority[clazz] = priority
    }

    fun <T> MayProxyFor(bean: T): T {
        var result = bean

        val clazz = bean!!::class.java
        val annotationToMethods = clazz.methods
            .flatMap { method -> method.annotations.map { Pair(method, it) } }
            .groupBy({ it.second.annotationClass }, { it.first })

        for (annotation in annotationToMethods.keys.sortedBy { Priority(it) }) {
            getInvocationHolder(annotation)?.also {
                val methods = annotationToMethods[annotation]!!
                result = ProxyInstance(methods, it).genProxy(bean)
                logger.debug(
                    "Create cglib proxy for class $clazz according by annotation $annotation for methods " +
                            "${methods.map { method -> method.name }}"
                )
            }
        }
        return result
    }

    private fun getInvocationHolder(clazz: KClass<out Annotation>): InvocationHolder? {
        return aops[clazz]
    }

    private fun Priority(clazz: KClass<out Annotation>): Int? {
        return aopsPriority[clazz]
    }

    class InvocationHolder(
        val preInvoke: ((bean: Any?, args: Array<out Any>?) -> Any?),
    )

    class ProxyInstance(proxyMethods: List<Method>, private val holder: InvocationHolder) : MethodInterceptor {

        private val interceptorFun = proxyMethods.toSet()

        fun <T> genProxy(target: T): T {
            val enhancer = Enhancer()
            enhancer.setSuperclass(target!!::class.java)
            enhancer.setCallback(this)
            return enhancer.create() as T
        }


        @Throws(Throwable::class)
        override fun intercept(
            obj: Any?, method: Method?, args: Array<out Any>?,
            proxy: MethodProxy
        ): Any? {
            var bean = obj
            if (interceptorFun.contains(method)) {
                bean = holder.preInvoke(obj, args)
            }

            return proxy.invokeSuper(bean, args)
        }
    }
}