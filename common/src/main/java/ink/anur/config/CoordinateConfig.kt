package ink.anur.config

/**
 * Created by Anur IjuoKaruKas on 2020/3/6
 */
interface CoordinateConfig {

    /**
     * 获取重新发送消息的退避时间
     */
    fun getReSendBackOfMs(): Long
}