package gg.scala.queue.server

import net.evilblock.cubed.serializers.Serializers
import java.io.File

val prettyPrinting by lazy {
    Serializers.gson
        .newBuilder()
        .setPrettyPrinting()
        .create()
}

/**
 * @author GrowlyX
 * @since 7/11/2023
 */
inline fun <reified T> loadComponent(): T & Any
{
    return File("${T::class.simpleName}.json")
        .let {
            if (!it.exists())
            {
                it.createNewFile()
                it.appendText(
                    prettyPrinting.toJson(T::class.java.newInstance())
                )
            }

            Serializers.gson
                .fromJson(
                    it.readText(), T::class.java
                )
        }!!
}
