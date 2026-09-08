package dev.openrune

import java.lang.reflect.Method

/** Read-only revision-240 component audit for the stock Adventure Paths interface. */
public fun main() {
    val cache = ServerCacheManager.init(REVISION)
    try {
        val paths = checkNotNull(ServerCacheManager.getEnum(2620))
        printDefinition("enum", 2620, paths)
        println("enum=2620 values=${paths.values}")
        paths.values.values.filterIsInstance<Int>().forEach { taskEnumId ->
            val taskEnum = checkNotNull(ServerCacheManager.getEnum(taskEnumId))
            println("task-enum=$taskEnumId values=${taskEnum.values}")
            taskEnum.values.values.filterIsInstance<Int>().forEach { structId ->
                val struct = checkNotNull(ServerCacheManager.getStruct(structId))
                println("task-struct=$structId params=${struct.params.orEmpty().toSortedMap()}")
            }
        }
        val type = checkNotNull(ServerCacheManager.getInterface(INTERFACE_ID))
        type.components.toSortedMap().forEach { (child, component) ->
            val values =
                component.javaClass.methods
                    .asSequence()
                    .filter(::isPrintableGetter)
                    .sortedBy(Method::getName)
                    .mapNotNull { method ->
                        runCatching {
                                method.name.removePrefix("get") to
                                    printable(method.invoke(component))
                            }
                            .getOrNull()
                    }
                    .joinToString(" ") { (name, value) -> "$name=$value" }
            println("child=$child $values")
        }
    } finally {
        cache.close()
    }
}

private fun printDefinition(kind: String, id: Int, definition: Any?) {
    if (definition == null) {
        println("$kind=$id missing")
        return
    }
    val values =
        definition.javaClass.methods
            .asSequence()
            .filter(::isPrintableGetter)
            .sortedBy(Method::getName)
            .mapNotNull { method ->
                runCatching {
                        method.name.removePrefix("get") to printable(method.invoke(definition))
                    }
                    .getOrNull()
            }
            .joinToString(" ") { (name, value) -> "$name=$value" }
    println("$kind=$id $values")
}

private fun isPrintableGetter(method: Method): Boolean =
    method.parameterCount == 0 &&
        method.name.startsWith("get") &&
        method.name != "getClass" &&
        (method.returnType.isPrimitive ||
            method.returnType == String::class.java ||
            method.returnType.isArray ||
            method.returnType.isEnum)

private fun printable(value: Any?): String =
    when (value) {
        null -> "null"
        is Array<*> -> value.contentToString()
        is IntArray -> value.contentToString()
        is LongArray -> value.contentToString()
        is BooleanArray -> value.contentToString()
        else -> value.toString().replace('\n', ' ')
    }

private const val REVISION = 240
private const val INTERFACE_ID = 642
