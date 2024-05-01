package xyz.chener.ext.dp

import com.fasterxml.jackson.databind.ObjectMapper
import xyz.chener.ext.dp.core.DynamicProperties

class TestImpl {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val load = DynamicProperties("test.properties", TestJavaEntity::class.java).load{
                println("配置改变")
            }
            while (true){
                println(load)
                Thread.sleep(1000)
            }
        }
    }
}

open class TestKtEntity {

    @PropertiesKey("name")
    open var name: String = ""

    @PropertiesKey(value = "age", clazz = Int::class)
    open var age: Int = 0


    override fun toString(): String {
        return ObjectMapper().writeValueAsString(this)
    }
}