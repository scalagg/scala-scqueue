package gg.scala.queue.server

import gg.scala.aware.thread.AwareThreadContext
import gg.scala.commons.agnostic.sync.server.state.ServerState
import gg.scala.queue.server.ScalaQueueCoordinator.queues
import gg.scala.queue.shared.ScalaQueueRedisService
import net.evilblock.cubed.serializers.Serializers
import org.apache.commons.lang3.time.DurationFormatUtils
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * @author GrowlyX
 * @since 1/12/2022
 */
object ScalaQueueUpdateTask : Runnable
{
    class LanguageConfig(
        val updateMessage: String = "\n§eYou're position §b{pos}§e/§b{max}§e in the §b{queue}§e queue.\n§7Estimated remaining queue time: ~§f{remainingTimeEstimate}\n§7Ranks get a higher priority in queues: §f§ostore.glade.gg",
        val whitelistedAddition: String = "§cThe server you're queued for is currently whitelisted.",
        val offlineAddition: String = "§cThe server you're queued for is currently offline.",
        val userLoggedInTooLate: String = "§cYou have been removed from the queue you were in as you logged back in too late.",
    )

    val language by lazy {
        loadComponent<LanguageConfig>()
    }

    fun configure()
    {
        language

        QueueServiceExecutor
            .scheduleAtFixedRate(
                this,
                0L, 5L,
                TimeUnit.SECONDS
            )
    }

    private fun tick()
    {
        for ((_, queue) in queues)
        {
            val servers = queue.servers()
            val messageMap = mutableMapOf<UUID, String>()

            val offline = servers.isEmpty() || servers
                .none {
                    it.state == ServerState.Loaded
                }

            val whitelisted = servers
                .all {
                    it.getWhitelisted() == true
                }

            for (entry in queue.players)
            {
                val positionOfUser = queue.position(entry.first)

                var baseMessage = language.updateMessage
                    .replace("{pos}", positionOfUser.toString())
                    .replace("{max}", queue.total().toString())
                    .replace("{queue}", queue.displayName)
                    .replace(
                        "{remainingTimeEstimate}",
                        DurationFormatUtils.formatDurationWords(
                            500L * positionOfUser,
                            true, true
                        )
                    )

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
