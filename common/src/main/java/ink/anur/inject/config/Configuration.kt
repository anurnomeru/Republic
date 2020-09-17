package ink.anur.inject.config

/**
 * Created by Anur IjuoKaruKas on 2020/3/8
 *
 * Mark a class as a configuration, and nigate will inject the value automatic from
 * kanashi.properties
 */
annotation class Configuration(val prefix: String)