package xyz.chener.ext.dp.core

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.cglib.proxy.Enhancer
import xyz.chener.ext.dp.ConfigType
import xyz.chener.ext.dp.TypeDerivation
import java.lang.RuntimeException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import kotlin.io.path.absolute
import kotlin.jvm.Throws



class DynamicProperties<T:Any>(
    private val configPath:String,
    private val entityClass: Class<T>,
    private var configType: ConfigType = ConfigType.AUTO ) {
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

        registerListener{
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


    private fun registerListener(callback:Runnable){
        val watchService = FileSystems.getDefault().newWatchService()
        Path.of(configPath).absolute().parent.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY)
        Thread.ofVirtual().name("dp-${Path.of(configPath).fileName}").start{
            while (true){
                val take = watchService.take()
                run jmp@{
                    take.pollEvents().forEach{
                        if (it.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                            val context = it.context()
                            if (context is Path && context.fileName.toString() == Path.of(configPath).fileName.toString()){
                                // reload
                                try {
                                    callback.run()
                                }catch (e:Exception){
                                    log.error("Failed to reload config file.",e)
                                }
                            }
                            return@jmp
                        }
                    }
                }
                if (!take.reset()){
                    break
                }
            }
        }
    }

}
