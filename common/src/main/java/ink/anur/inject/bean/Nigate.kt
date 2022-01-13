package ink.anur.inject.bean

import com.google.common.collect.Lists
import ink.anur.common.KanashinUlimitedExecutors
import ink.anur.exception.*
import ink.anur.inject.aop.AopRegistry
import ink.anur.inject.config.Configuration
import ink.anur.inject.config.ConfigurationFactory
import ink.anur.inject.event.NigateListenerService
import ink.anur.inject.rpc.RepublicBean
import ink.anur.inject.rpc.KanashiRpcBean
import ink.anur.inject.rpc.Republic
import ink.anur.rpc.RpcSenderService
import ink.anur.util.ClassMetaUtil
import ink.anur.util.TimeUtil
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.TestOnly
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy.newProxyInstance
import java.net.JarURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaType
import kotlin.system.exitProcess

/**
 * Created by Anur IjuoKaruKas on 2020/2/23
 *
 * 扫描系列
 */
@Suppress("UNCHECKED_CAST")
object Nigate {

    private val vmArgs = ConcurrentHashMap<String, String>()

    private val quitCh = Channel<Any>(0)

    suspend fun start(args: Array<String>) {
        for (arg in args) {
            val splitter = arg.indexOf("=")
            vmArgs[arg.substring(0, splitter)] = arg.substring(splitter + 1)

        }

        try {
            logger.info(
                "      \n\n  _____           _____            _       _   _    _____ \n" +
                        " |  __ \\         |  __ \\          | |     | | (_)  / ____|\n" +
                        " | |__) |   ___  | |__) |  _   _  | |__   | |  _  | |     \n" +
                        " |  _  /   / _ \\ |  ___/  | | | | | '_ \\  | | | | | |     \n" +
                        " | | \\ \\  |  __/ | |      | |_| | | |_) | | | | | | |____ \n" +
                        " |_|  \\_\\  \\___| |_|       \\__,_| |_.__/  |_| |_|  \\_____|\n" +
                        "                                                          \n" +
                        "                                                          "
            )


            val start = TimeUtil.getTime()
            logger.info("Nigate ==> Registering..")
            beanContainer.doScan()
            beanContainer.overRegistry = true
            val allBeans = beanContainer.getManagedBeans()
            logger.info("Nigate ==> Register complete")
            logger.info("Nigate ==> Injecting..")

            for (bean in allBeans) {
                beanContainer.inject(bean)
            }
            logger.info("Nigate ==> Inject complete")

            logger.info("Nigate ==> Invoking postConstruct..")
            beanContainer.postConstruct(allBeans, true)
            logger.info("Nigate ==> Invoke postConstruct complete")

            logger.info("Nigate ==> Registering listener..")
            val nigateListenerService = getBeanByClass(NigateListenerService::class.java)
            for (bean in allBeans) {
                nigateListenerService.registerListenEvent(bean)
            }
            logger.info("Nigate ==> Register complete")

            logger.info("Nigate Started in ${(TimeUtil.getTime() - start) / 1000f} seconds")

            logger.info("Nigate ==> Notify after bootstrap")
            beanContainer.afterBootStrap(allBeans)
        } catch (e: Exception) {
            e.printStackTrace()
            exitProcess(1)
        }

        quitCh.receive()
    }

    fun getFromVm(key: String): String? {
        return vmArgs[key]
    }

    /// ==================================

    /**
     * bean 容器
     */
    private val beanContainer = NigateBeanContainer()

    private val hasBeanPostConstruct: MutableSet<String> = mutableSetOf()

    private val logger = LoggerFactory.getLogger(this::class.java)

    fun getRPCBeanByName(name: String): KanashiRpcBean? {
        return beanContainer.getRPCBeanByName(name)
    }

    fun getRPCBeanByInterface(interfaceName: String): MutableList<KanashiRpcBean>? {
        return beanContainer.getRPCBeanByInterface(interfaceName)
    }

    fun <T> getBeanByClass(clazz: Class<T>): T = beanContainer.getBeanByClass(clazz)

    fun getBeanByName(name: String): Any = beanContainer.getBeanByName(name)

    @TestOnly
    fun markAsOverRegistry() {
        beanContainer.overRegistry = true
    }

    /**
     * 注册某个bean
     */
    fun registerToNigate(injected: Any, alias: String? = null) {
        if (!beanContainer.overRegistry) {
            throw KanashiException("Can not register a bean before constructors init!")
        }
        val newArrayList = Lists.newArrayList(injected)
        beanContainer.register(injected, alias, false)
        beanContainer.inject(injected)
        beanContainer.postConstruct(newArrayList, false)
        beanContainer.afterBootStrap(newArrayList)
        getBeanByClass(NigateListenerService::class.java).registerListenEvent(injected)
    }

    /**
     * 单纯的注入
     */
    fun injectOnly(injected: Any) {
        beanContainer.inject(injected)
    }

    class NigateBeanContainer {

        companion object {
            const val UndefineAlias = "-UNDEFINE-"
        }

        /**
         * 必须完成初始注册才能做很多事情，否则直接抛出异常（在初始化阶段，<clinit> 和 <init> 使用注入会牙白！！！）
         */
        var overRegistry: Boolean = false

        /**
         * 专门为远程调用准备的映射
         */
        private val rpcBean = mutableMapOf<String, KanashiRpcBean>()

        /**
         * 远程调用下，接口下的实现(可能有多个)
         */
        private val rpcInterfaceBean = mutableMapOf<String, MutableList<KanashiRpcBean>>()

        /**
         * bean 名字与 bean 的映射，只能一对一
         */
        private val nameToBeanMapping = mutableMapOf<String, Any>()

        /**
         * bean 名字与 bean 的映射，只能一对一
         */
        private val beanToNameMapping = mutableMapOf<Any, String>()

        /**
         * bean 类型与 bean 的映射，可以一对多
         */
        private val beanClassMapping = mutableMapOf<Class<*>, MutableSet<Any>>()

        /**
         * 存储 “类” 与其 “类定义” 的映射集合
         */
        private val beanDefinitionMapping = mutableMapOf<String, NigateBeanDefinition>()

        /**
         * 存储接口类与其 “实现们” 的映射集合
         */
        private val interfaceMapping = mutableMapOf<Class<*>, MutableSet<Class<*>>>()

        /**
         * 延迟加载的 BEAN
         */
        private val lazyPostConstructBean = mutableMapOf<Any, Any>()

        /**
         * 已经进行了 postConstruct
         */
        private val hasBeenPostConstruct = mutableSetOf<Any>()

        /**
         * 避免因为路径问题导致初始化重复扫描
         */
        private val duplicateCleaner = mutableSetOf<String>()

        /**
         * TODO 懒得做 read only
         */
        fun getRpcInterfaces() = rpcInterfaceBean

        /**
         * TODO 懒得做 read only
         */
        fun getRpcBeans() = rpcBean

        /**
         * register bean to beanDefinitionMapping
         */
        private fun autoRegister(path: String, clazz: Class<*>, fromJar: Boolean) {
            if (duplicateCleaner.add(path)) {
                val annotations = clazz.annotations.associateBy { it::annotationClass.get() }
                when {
                    annotations.containsKey(Configuration::class) -> {
                        val annotation = annotations[Configuration::class] as Configuration
                        ConfigurationFactory.genOrGetByType(
                            ConfigurationFactory.PrefixAndClass(
                                annotation.prefix,
                                clazz
                            )
                        )
                            ?.also {
                                val name = register(
                                    it, null, false
                                )
                                beanDefinitionMapping[name] = NigateBeanDefinition(fromJar, path)
                            }
                        logger.debug("$clazz register as Configuration")
                        return
                    }
                    annotations.containsKey(RepublicBean::class) -> {
                        val annotation = annotations[RepublicBean::class] as RepublicBean
                        val name = register(newNigateInstance(clazz), annotation.alias, true)
                        beanDefinitionMapping[name] = NigateBeanDefinition(fromJar, path)
                        logger.debug("$clazz register as KanashiRpc")
                        return
                    }
                    annotations.containsKey(NigateBean::class) -> {
                        val annotation = annotations[NigateBean::class] as NigateBean
                        val name = register(newNigateInstance(clazz), annotation.alias, false)
                        beanDefinitionMapping[name] = NigateBeanDefinition(fromJar, path)
                        logger.debug("$clazz register as NigateBean")
                        return
                    }
                }
            }
        }

        fun <T> newNigateInstance(clazz: Class<T>): T {
            return AopRegistry.MayProxyFor(clazz.getDeclaredConstructor().newInstance())
        }

        /**
         * 注册一个bean，优先取 alias 取不到则使用 bean 的 simpleName
         */
        fun register(bean: Any, alias: String? = null, registerToRPC: Boolean): String {
            val clazz = bean.javaClass
            val actualName = if (alias == null || alias == UndefineAlias) clazz.simpleName.let {
                ClassMetaUtil.firstCharToLowCase(it)
            } else alias

            if (nameToBeanMapping[actualName] != null) {
                throw DuplicateBeanException("bean $clazz 存在重复注册的情况，请使用 @NigateBean(name = alias) 为其中一个起别名")
            }

            nameToBeanMapping[actualName] = bean
            beanToNameMapping[bean] = actualName

            if (registerToRPC) {
                val kanashiRpcBean = KanashiRpcBean(bean)
                rpcBean[actualName] = kanashiRpcBean
                for (anInterface in kanashiRpcBean.getInterfacesMapping()) {
                    rpcInterfaceBean.compute(anInterface) { _, list ->
                        (list ?: mutableListOf()).also {
                            it.add(kanashiRpcBean)
                        }
                    }
                }
            }

            beanClassMapping.compute(bean.javaClass) { _, v ->
                val set = v ?: mutableSetOf()
                set.add(bean)
                set
            }

            clazz.interfaces.forEach {
                interfaceMapping.compute(it) { _, v ->
                    val set = v ?: mutableSetOf()
                    set.add(clazz)
                    set
                }
            }
            return actualName
        }

        private fun <T> getBeanByClassFirstThenName(clazz: Class<T>): T {
            val result: T?
            result = try {
                try {
                    getBeanByClass(clazz)
                } catch (t: Throwable) {
                    Nigate.getBeanByName(clazz.simpleName) as T
                }
            } catch (t: Throwable) {
                throw NoSuchBeanException("无法根据类 ${clazz.simpleName} 找到唯一的 Bean 或 无法根据名字 ${clazz.simpleName} 找到指定的 Bean ")
            }
            return result!!
        }

        fun getBeanByName(name: String): Any = nameToBeanMapping[name]
            ?: throw NoSuchBeanException("bean named $name is not managed")

        fun <T> getBeanByClass(clazz: Class<T>): T {
            val l = beanClassMapping[clazz]
                ?: throw NoSuchBeanException("bean with type $clazz is not managed")
            if (l.size > 1) {
                throw DuplicateBeanException("bean $clazz 存在多实例的情况，请使用 @NigateInject(name = alias) 选择注入其中的某个 bean")
            }
            return (l.first()) as T
        }

        /**
         * 为某个bean注入成员变量，如果注入的接口是一个 RPC 接口，则会为接口创建一个动态代理
         */
        fun inject(injected: Any) {
            for (kProperty in injected::class.memberProperties) {
                for (annotation in kProperty.annotations) {
                    if (annotation.annotationClass == NigateInject::class) {
                        annotation as NigateInject
                        val javaField = kProperty.javaField!!

                        val fieldName = kProperty.name
                        val fieldClass = kProperty.returnType.javaType as Class<*>
                        var injection: Any? = null
                        if (annotation.name == UndefineAlias) { // 如果没有指定别名注入
                            if (fieldClass.isInterface) { // 如果是 接口类型
                                val mutableSet = interfaceMapping[fieldClass]
                                if (mutableSet == null) {
                                    throw NoSuchBeanException("不存在接口类型为 $fieldClass 的 Bean！")
                                } else {
                                    if (mutableSet.size == 1) { // 如果只有一个实现，则注入此实现
                                        injection = getBeanByClassFirstThenName(mutableSet.first())
                                    } else if (annotation.useLocalFirst) { // 如果优先使用本地写的类
                                        val localBean =
                                            mutableSet.takeIf { beanDefinitionMapping[it.javaClass.simpleName]?.fromJar == false }
                                        when {
                                            localBean == null -> throw DuplicateBeanException(
                                                "bean ${injected.javaClass} " +
                                                        " 将注入的属性 $fieldName 为接口类型，且存在多个来自【依赖】的子类实现，请改用 @NigateInject(name) 来指定别名注入"
                                            )
                                            localBean.size > 1 -> throw DuplicateBeanException(
                                                "bean ${injected.javaClass} " +
                                                        " 将注入的属性 $fieldName 为接口类型，且存在多个来自【本地】的子类实现，请改用 @NigateInject(name) 来指定别名注入"
                                            )
                                            else -> injection = getBeanByClassFirstThenName(mutableSet.first())
                                        }
                                    }
                                }
                            } else { // 如果不是接口类型，直接根据类来注入
                                injection = getBeanByClass(fieldClass)
                            }
                        } else { // 如果指定了别名，直接根据别名注入
                            injection = getBeanByName(annotation.name)
                            if (!fieldClass.isInterface) {
                                throw RPCInjectUnSupportException()
                            }
                        }

                        javaField.isAccessible = true
                        javaField.set(injected, injection)
                    } else if (annotation.annotationClass == Republic::class) {
                        annotation as Republic
                        val javaField = kProperty.javaField!!
                        val fieldClass = kProperty.returnType.javaType as Class<*>

                        val alias = if (annotation.alias.equals(UndefineAlias)) {
                            null
                        } else {
                            annotation.alias
                        }

                        javaField.isAccessible = true
                        javaField.set(injected, RpcRequestInvocation(fieldClass, alias).proxyBean)
                    }
                }
            }
        }

        fun getManagedBeans(): MutableCollection<Any> {
            return nameToBeanMapping.values
        }

        fun postConstruct(beans: MutableCollection<Any>, onStartUp: Boolean) {
            val removes = mutableListOf<Any>()

            for (bean in beans) {
                for (memberFunction in bean::class.memberFunctions) {
                    for (annotation in memberFunction.annotations) {
                        if (annotation.annotationClass == NigatePostConstruct::class) {
                            annotation as NigatePostConstruct
                            val dependsOn = nameToBeanMapping[annotation.dependsOn]
                            if (annotation.dependsOn != "-NONE-" && !hasBeenPostConstruct.contains(dependsOn)) {
                                dependsOn
                                    ?: throw NoSuchBeanException("$bean 依赖的 bean ${annotation.dependsOn} 不存在")
                                lazyPostConstructBean[bean] = dependsOn
                                if (lazyPostConstructBean[dependsOn] != null && lazyPostConstructBean[dependsOn] == bean) {
                                    throw NigateException("bean ${beanToNameMapping[dependsOn]} 与 ${beanToNameMapping[bean]} 的 @NigatePostConstruct 构成了循环依赖！")
                                }
                            } else {

                                hasBeenPostConstruct.add(bean)
                                removes.add(
                                    bean
                                )

                                try {
                                    memberFunction.isAccessible = true
                                    memberFunction.call(bean)
                                } catch (e: Exception) {
                                    logger.error("class [${bean::class}] invoke post construct method [${memberFunction.name}] error : ${e.message}")
                                    e.printStackTrace()
                                    if (onStartUp) {
                                        exitProcess(1)
                                    }
                                }
                                val name = beanToNameMapping[bean]
                                    ?: throw NoSuchBeanException("无法根据类 ${bean.javaClass.simpleName} 找到唯一的 Bean 找到指定的 BeanName")
                                hasBeanPostConstruct.add(name)
                            }
                        }
                    }
                }
            }
            for (remove in removes) {
                lazyPostConstructBean.remove(remove)
            }

            if (lazyPostConstructBean.keys.size > 0) {
                postConstruct(lazyPostConstructBean.keys, onStartUp)
            }
        }

        fun afterBootStrap(beans: MutableCollection<Any>) {
            for (bean in beans) {
                for (memberFunction in bean::class.memberFunctions) {
                    for (annotation in memberFunction.annotations) {
                        if (annotation.annotationClass == NigateAfterBootStrap::class) {
                            annotation as NigateAfterBootStrap
                            try {
                                memberFunction.isAccessible = true
                                memberFunction.call(bean)
                            } catch (e: Exception) {
                                logger.error("class [${bean::class}] invoke after bootstrap method [${memberFunction.name}] error : ${e.message}")
                                e.printStackTrace()
                            }
                            val name = beanToNameMapping[bean]
                                ?: throw NoSuchBeanException("无法根据类 ${bean.javaClass.simpleName} 找到唯一的 Bean 找到指定的 BeanName")
                            hasBeanPostConstruct.add(name)
                        }
                    }
                }
            }
        }

        private fun getClasses(packagePath: String) {
            val path = packagePath.replace(".", "/")
            val resources = Thread.currentThread().contextClassLoader.getResources(path)

            while (resources.hasMoreElements()) {
                val url = resources.nextElement()
                val protocol = url.protocol
                if ("jar".equals(protocol, ignoreCase = true)) {
                    try {
                        getJarClasses(url, packagePath)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }

                } else if ("file".equals(protocol, ignoreCase = true)) {
                    getFileClasses(url, packagePath)
                }
            }
        }

        //获取file路径下的class文件
        private fun getFileClasses(url: URL, packagePath: String) {
            val filePath = url.file
            val dir = File(filePath)
            val list = dir.list() ?: return
            for (classPath in list) {
                if (classPath.endsWith(".class")) {
                    val path = "$packagePath.${classPath.replace(".class", "")}"
                    registerByClassPath(path, false)
                } else {
                    getClasses("$packagePath.$classPath")
                }
            }
        }

        //使用JarURLConnection类获取路径下的所有类
        @Throws(IOException::class)
        private fun getJarClasses(url: URL, packagePath: String) {
            val conn = url.openConnection() as JarURLConnection
            val jarFile = conn.jarFile
            val entries = jarFile.entries()
            while (entries.hasMoreElements()) {
                val jarEntry = entries.nextElement()
                val name = jarEntry.name
                if (name.contains(".class") && name.replace("/".toRegex(), ".").startsWith(packagePath)) {
                    val className = name.substring(0, name.lastIndexOf(".")).replace("/", ".")
                    registerByClassPath(className, true)
                }
            }
        }

        private fun registerByClassPath(classPath: String, fromJar: Boolean) {
            try {
                val clazz = Class.forName(classPath)
                for (annotation in clazz.annotations) {
                    beanContainer.autoRegister(classPath, clazz, fromJar)
                }
            } catch (e: ClassNotFoundException) {
                logger.debug(e.toString())
            } catch (e: NoClassDefFoundError) {
                logger.debug("Error when scan $classPath but skip: $e")
            }
        }

        fun doScan() {
            val mainClassPkg = System.getProperty("sun.java.command")
            getClasses(mainClassPkg.substring(0, maxOf(0, mainClassPkg.indexOfLast { it == Char('.'.code) })))
            getClasses("ink.anur")
//            getClasses("service")
        }

        fun getRPCBeanByInterface(interfaceName: String): MutableList<KanashiRpcBean>? {
            return rpcInterfaceBean[interfaceName]
        }

        fun getRPCBeanByName(name: String): KanashiRpcBean? {
            return rpcBean[name]
        }
    }

    class NigateBeanDefinition(
        /**
         * fromJar 代表此实现是有默认的实现而且实现在继承的maven里就已经写好
         */
        val fromJar: Boolean,

        val path: String
    )

    /**
     * 获取当前 Provider 下所有提供服务的 bean 的方法定义 methodSign
     */
    fun getRpcBeanPath(): Map<String/* bean */, HashSet<String /* method */>> {
        return beanContainer.getRpcBeans()
            .mapValues { HashSet(it.value.getMethodMapping().keys) }
    }

    /**
     * 获取当前 Provider 下所有提供服务的 接口 的方法定义 methodSign
     */
    fun getRpcInterfacePath(): Map<String/* bean */, List<HashSet<String /* method */>>> {
        return beanContainer.getRpcInterfaces()
            .mapValues { it.value.map { bean -> HashSet(bean.getMethodMapping().keys) } }
    }

    class RpcRequestInvocation(interfaces: Class<out Any>, private val alias: String?) : InvocationHandler {

        private val interfaceName = interfaces.simpleName

        val proxyBean: Any = newProxyInstance(interfaces.classLoader, arrayOf(interfaces), this)

        override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any? {
            val rpcSenderService = getBeanByClass(RpcSenderService::class.java)
            return runBlocking {
                return@runBlocking rpcSenderService.sendRpcRequest(method, interfaceName, alias, args)
            }
        }
    }
}