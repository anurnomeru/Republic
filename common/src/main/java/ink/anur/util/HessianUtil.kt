package ink.anur.util

import com.caucho.hessian.io.HessianInput
import com.caucho.hessian.io.HessianOutput
import com.google.common.collect.Lists
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Serializable

/**
 * Created by Anur IjuoKaruKas on 2020/4/7
 *
 * 操作序列化反序列化相关
 */
class HessianUtil {

    class SomeFuck(val str: String, val ll: Long?) : Serializable

    class MotherFucker(val list: MutableList<Any?>) : Serializable

    class Demo(val str: Any, val long: Long, val int: Int, val map: HashMap<String, Any>) : Serializable

    fun getFuckingCrazy(demo: Demo, fucker: SomeFuck): MotherFucker {
        return MotherFucker(Lists.newArrayList(demo, null, fucker))
    }

    companion object {

        fun ser(any: Any): ByteArray {
            val bos = ByteArrayOutputStream()
            val ho = HessianOutput(bos)
            ho.writeObject(any);

            return bos.toByteArray()
        }

        fun <T> des(byteArray: ByteArray, clazz: Class<out T>): T {
            val bis = ByteArrayInputStream(byteArray)
            val hessianInput = HessianInput(bis)
            return hessianInput.readObject(clazz) as T
        }
    }
}