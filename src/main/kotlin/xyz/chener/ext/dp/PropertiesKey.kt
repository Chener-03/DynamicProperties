package xyz.chener.ext.dp

import kotlin.reflect.KClass


@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention
@MustBeDocumented
annotation class PropertiesKey(val value: String,val clazz:KClass<*> = String::class)
