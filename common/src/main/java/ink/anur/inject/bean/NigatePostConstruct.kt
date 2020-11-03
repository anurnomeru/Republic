package ink.anur.inject.bean

/**
 * Created by Anur IjuoKaruKas on 2020/2/24
 *
 * 在初始化bean后调用，注意如果在此注解里面做一些阻塞操作项目有可能启动不起来，除非确保它一定不是永久等待
 */
annotation class NigatePostConstruct(val dependsOn: String = "-NONE-")