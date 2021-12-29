package ink.anur.util

import java.lang.reflect.Method

/**
 * Created by Anur IjuoKaruKas on 2020/4/7
 */
object ClassMetaUtil {

    fun firstCharToLowCase(name: String): String {
        return name.first().lowercase() + name.substring(1, name.length)
    }


    fun methodSignGen(method: Method): String {
        return method.name + "." + method.parameterCount + "." + method.returnType
    }
}