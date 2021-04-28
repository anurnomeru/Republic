package ink.anur.log.common

import ink.anur.pojo.metastruct.MetaStruct
import ink.anur.pojo.metastruct.SerializableMeta

/**
 * Created by Anur IjuoKaruKas on 2019/7/12
 */
class OperationAndOffset<T : SerializableMeta>(val operation: MetaStruct<T>, val offset: Long)