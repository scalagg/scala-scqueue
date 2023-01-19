package gg.scala.queue.shared

import gg.scala.aware.Aware
import gg.scala.aware.AwareBuilder
import gg.scala.aware.codec.codecs.interpretation.AwareMessageCodec
import gg.scala.aware.message.AwareMessage
import java.util.logging.Logger

/**
 * @author GrowlyX
 * @since 8/21/2022
 */
object ScalaQueueRedisService
{
    private val aware by lazy {
        AwareBuilder
            .of<AwareMessage>("queue:spigot")
            .codec(AwareMessageCodec)
            .logger(Logger.getAnonymousLogger())
            .build()
    }

    private val backbone by lazy {
        AwareBuilder
            .of<AwareMessage>("queue:backbone")
            .codec(AwareMessageCodec)
            .logger(Logger.getAnonymousLogger())
            .build()
    }

    fun configureBackbone(
        lambda: Aware<AwareMessage>.() -> Unit
    )
    {
        this.backbone.lambda()
    }

    fun <T> configurePopulatorBackbone(
        lambda: Aware<AwareMessage>.() -> T
    ): T
    {
        return this.backbone.lambda()
    }

    fun createMessageBackbone(packet: String, vararg pairs: Pair<String, Any?>): AwareMessage =
        AwareMessage.of(packet, this.backbone, *pairs)

    fun startBackbone()
    {
        this.backbone.connect()
            .toCompletableFuture()
            .join()
    }

    fun configure(
        lambda: Aware<AwareMessage>.() -> Unit
    )
    {
        this.aware.lambda()
    }

    fun <T> configurePopulator(
        lambda: Aware<AwareMessage>.() -> T
    ): T
    {
        return this.aware.lambda()
    }

    fun createMessage(packet: String, vararg pairs: Pair<String, Any?>): AwareMessage =
        AwareMessage.of(packet, this.aware, *pairs)

    fun start()
    {
        this.aware.connect()
            .toCompletableFuture()
            .join()
    }
}
