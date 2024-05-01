package xyz.chener.ext.dp.core

import com.fasterxml.jackson.databind.ObjectMapper
import org.yaml.snakeyaml.Yaml
import xyz.chener.ext.dp.ConfigType
import xyz.chener.ext.dp.PropertiesKey
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import kotlin.jvm.Throws
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaType


interface EntityLoader<T:Any> {
    companion object {
        fun <T:Any> create(configPath:String,entityClass: Class<T>,configType: ConfigType):EntityLoader<T>{
            if (configType == ConfigType.PROPERTIES){
                return PropertiesLoader(configPath,entityClass)
            }
            if (configType == ConfigType.YAML){
                return YamlLoader(configPath,entityClass)
            }
            if (configType == ConfigType.JSON){
                return JsonLoader(configPath,entityClass)
            }
            throw IllegalArgumentException("Unsupported config type.")
        }
    }

    @Throws(Exception::class)
    fun load():T

}


class YamlLoader<T:Any>(private val configPath:String, private val entityClass: Class<T>):EntityLoader<T>{
    override fun load(): T {
        val yml = Files.readString(Path.of(configPath))
        return Yaml().loadAs(yml,entityClass)
    }
}

class PropertiesLoader<T:Any>(private val configPath:String, private val entityClass: Class<T>):EntityLoader<T>{
    override fun load(): T {
        val instance:T = newInstance()

        val pp = Files.readString(Path.of(configPath))
        val p = Properties()
        p.load(StringReader(pp))

        inject(instance,p)

        return instance
    }

    private fun newInstance():T{
        if (ClassChecker.isKtClass(entityClass)){
            val primaryConstructor = entityClass.kotlin.primaryConstructor
                ?: throw IllegalArgumentException("Class [${entityClass.name}] no primary constructor found.")
            return primaryConstructor.call()
        }else{
            return entityClass.getConstructor().newInstance()
        }
    }

    private fun inject(instance:T,properties:Properties){
        if (ClassChecker.isKtClass(entityClass)) {
            val clazz = entityClass.kotlin
            clazz.memberProperties.forEach { prop ->
                val propertiesKey = prop.findAnnotation<PropertiesKey>() ?: return@forEach
                val value = properties[propertiesKey.value]?.toString() ?: return@forEach
                val setter = clazz.members.find { it.name == prop.name } ?: return@forEach

                val javaType = prop.returnType.javaType
                if (javaType is Class<*> && setter is KMutableProperty1<*, *>) {
                    setter.setter.call(instance, stringToAny(value, javaType))
                }
            }
        }else{
            entityClass.declaredFields.forEach { field ->
                field.getAnnotation(PropertiesKey::class.java)?.let {
                    properties[it.value]?.let { value ->
                        val setterName = "set${field.name.replaceFirstChar { rf -> rf.uppercase() }}"
                        try {
                            entityClass.getMethod(setterName, it.clazz.java).invoke(instance, stringToAny(value.toString(), it.clazz.java))
                        }catch (_:Exception){ }
                    }
                }
            }
        }
    }

    private fun stringToAny(value:String,clazz:Class<*>):Any{
        return when(clazz){
            String::class.java -> value
            Int::class.java -> value.toInt()
            Integer::class.java -> value.toInt()
            Long::class.java -> value.toLong()
            Double::class.java -> value.toDouble()
            Float::class.java -> value.toFloat()
            Boolean::class.java -> value.toBoolean()
            Date::class.java -> SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(value)
            else -> value
        }
    }
}

class JsonLoader<T:Any>(private val configPath:String, private val entityClass: Class<T>):EntityLoader<T>{
    override fun load(): T {
        val json = Files.readString(Path.of(configPath))
        return ObjectMapper().readValue(json,entityClass)
    }
}



