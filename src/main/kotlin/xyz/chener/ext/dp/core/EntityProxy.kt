package xyz.chener.ext.dp.core

import org.springframework.cglib.proxy.MethodInterceptor
import org.springframework.cglib.proxy.MethodProxy
import java.lang.reflect.Method

open class EntityProxy(obj: Any) : MethodInterceptor {

    @Volatile
    var realObj:Any = obj

    override fun intercept(obj: Any?, method: Method?, args: Array<out Any>?, proxy: MethodProxy?): Any? {
        if (obj == null || !obj::class.java.name.startsWith(realObj::class.java.name)){
            throw IllegalArgumentException("Class miss match")
        }
        return proxy?.invoke(realObj, args)
    }
}
