package gg.scala.queue.server

import gg.scala.aware.thread.AwareThreadContext
import gg.scala.commons.agnostic.sync.server.AbstractServer
import gg.scala.commons.agnostic.sync.server.ServerContainer
import gg.scala.commons.agnostic.sync.server.impl.GameServer
import gg.scala.commons.agnostic.sync.server.state.ServerState
import gg.scala.queue.server.ScalaQueueCoordinator.queues
import gg.scala.queue.shared.ScalaQueueRedisService
import net.evilblock.cubed.serializers.Serializers
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * @author GrowlyX
 * @since 1/12/2022
 */
object ScalaQueueUpdateTask : Runnable
{
    class LanguageConfig(
        val updateMessage: String = "\n§eYou're position §b{pos}§e/§b{max}§e in the §b{queue}§e queue.\n§7Ranks get a higher priority in queues: §f§ostore.glade.gg",
        val whitelistedAddition: String = "§cThe server you're queued for is currently whitelisted.",
        val offlineAddition: String = "§cThe server you're queued for is currently offline.",
    )

    private val language = LanguageConfig()

    fun configure()
    {
        val executor = Executors.newSingleThreadScheduledExecutor()

        executor.scheduleAtFixedRate(
            this, 500L,
            5000L,
            TimeUnit.MILLISECONDS
        )
    }

    private fun tick()
    {
        for ((_, queue) in queues)
        {
            val instance = ServerContainer
                .getServersInGroupCasted<GameServer>(queue.serverGroup)
                .firstOrNull()

            val messageMap = mutableMapOf<UUID, String>()

            val offline = instance == null || (instance as AbstractServer).state != ServerState.Loaded
            val whitelisted = instance != null && instance.getWhitelisted() == true

            for (entry in queue.players)
            {
                var baseMessage = language.updateMessage
                    .replace("{pos}", queue.position(entry.first).toString())
                    .replace("{max}", queue.players.size.toString())
                    .replace("{queue}", queue.displayName)

                if (offline)
                {
                    baseMessage += "\n${language.offlineAddition}"
                } else if (whitelisted)
                {
                    baseMessage += "\n${language.whitelistedAddition}"
                }

                messageMap[entry.first] = "$baseMessage\n "
            }

            ScalaQueueRedisService
                .createMessage(
                    "message",
                    "content" to Serializers.gson.toJson(messageMap)
                ).publish(
                    AwareThreadContext.SYNC
                )
        }
    }

    override fun run()
    {
        kotlin.runCatching {
            tick()
        }.onFailure {
            it.printStackTrace()
        }
    }
}
