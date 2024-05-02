package xyz.chener.ext.dp.core

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.cglib.proxy.Enhancer
import xyz.chener.ext.dp.ConfigType
import xyz.chener.ext.dp.TypeDerivation
import java.nio.file.Files
import java.nio.file.Path


class DynamicProperties<T:Any>(
    private val configPath:String,
    private val entityClass: Class<T>,
    private val listenerType: ListenerType,
    private var configType: ConfigType = ConfigType.AUTO) {
    init {
        if (configType == ConfigType.AUTO) {
            configType = TypeDerivation.deriveType(configPath)
        }
    }

    private val log: Logger = LoggerFactory.getLogger(DynamicProperties::class.java)

    @Throws(Exception::class)
    fun load(callback: Runnable? = null):T{
        if (!Files.exists(Path.of(configPath)) || Files.isDirectory(Path.of(configPath))){
            throw IllegalArgumentException("File path does not exist or is a directory")
        }

        ClassChecker.create(entityClass).checkEntity(entityClass)

        val ep = EntityProxy(loadFromFile())
        val enhancer = Enhancer()
        enhancer.setSuperclass(entityClass)
        enhancer.setCallback(ep)

        FileListenerFactory.createListener(Path.of(configPath),
            if (listenerType == ListenerType.FileHash) FileListener.MODIFY else FileListener.CREATE or FileListener.MODIFY,
            listenerType).registerListener { _, b->
            log.info("Reload config : $b .")
            ep.realObj = loadFromFile()
            runCatching { callback?.run() }
        }

        return enhancer.create()!! as T
    }



    private fun loadFromFile() : T {
        if (!Files.exists(Path.of(configPath)) || Files.isDirectory(Path.of(configPath))){
            throw IllegalArgumentException("File path does not exist or is a directory")
        }
        return EntityLoader.create<T>(configPath,entityClass,configType).load()
    }

}
