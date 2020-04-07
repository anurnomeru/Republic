package ink.anur.config

import ink.anur.config.common.ConfigHelper
import ink.anur.config.common.ConfigurationEnum
import ink.anur.inject.NigateBean

/**
 * Created by Anur IjuoKaruKas on 2019/7/5
 */
@NigateBean
class CoordinateConfiguration : ConfigHelper(), CoordinateConfig {
    override fun getReSendBackOfMs(): Long {
        return (getConfig(ConfigurationEnum.COORDINATE_FETCH_BACK_OFF_MS) { it } as String).toLong()
    }
}
