package xyz.chener.ext.dp.core

import java.lang.reflect.Modifier

interface ClassChecker {

    companion object {
        fun create(clazz: Class<*>):ClassChecker{
            if (clazz.isAnnotationPresent(Metadata::class.java)) {
                return KotlinEntityChecker()
            }
            return JavaEntityChecker()
        }
        fun isKtClass(clazz: Class<*>):Boolean{
            return clazz.isAnnotationPresent(Metadata::class.java)
        }
    }

    fun checkEntity(clazz: Class<*>)

}

class JavaEntityChecker:ClassChecker{

    override fun checkEntity(clazz: Class<*>) {
        Modifier.isAbstract(clazz.modifiers).takeIf { it }?.let {
            throw IllegalArgumentException("The class[${clazz.name}] is abstract.")
        }

        Modifier.isInterface(clazz.modifiers).takeIf { it }?.let {
            throw IllegalArgumentException("The class[${clazz.name}] is interface.")
        }

        clazz.declaredFields.forEach { field ->
            val getMethodName = "get${field.name.replaceFirstChar { it.uppercase() }}"
            if (!checkOpenFun(clazz,getMethodName)){
                throw IllegalArgumentException("The get or set of field[${field.name}] a is not public or final.")
            }
        }
    }

    private fun checkOpenFun(clazz: Class<*>,methodName:String):Boolean{
        try {
            val method = clazz.getDeclaredMethod(methodName)
            if (Modifier.isPublic(method.modifiers) && !Modifier.isFinal(method.modifiers)) {
                return true
            }
        }catch (_:Exception){ }
        return false
    }

}

class KotlinEntityChecker:ClassChecker{

    override fun checkEntity(clazz: Class<*>) {
        if (clazz.kotlin.isAbstract){
            throw IllegalArgumentException("The class[${clazz.name}] is abstract.")
        }

        clazz.kotlin.members.forEach {
            if (it.isAbstract || it.isFinal){
                throw IllegalArgumentException("The field[${it.name}] is abstract or final.")
            }
        }
    }
}

