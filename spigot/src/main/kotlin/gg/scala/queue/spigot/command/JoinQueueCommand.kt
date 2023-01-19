package gg.scala.queue.spigot.command

import com.google.common.cache.CacheBuilder
import gg.scala.commons.command.ScalaCommand
import gg.scala.commons.acf.ConditionFailedException
import gg.scala.commons.acf.annotation.CommandAlias
import gg.scala.commons.acf.annotation.CommandPermission
import gg.scala.flavor.inject.Inject
import gg.scala.queue.shared.ScalaQueueRedisService
import gg.scala.queue.shared.models.Queue
import gg.scala.queue.spigot.ScalaQueuePlugin
import gg.scala.queue.spigot.stream.SpigotRedisService
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * @author GrowlyX
 * @since 1/12/2022
 */
object JoinQueueCommand : ScalaCommand()
{
    @Inject
    lateinit var plugin: ScalaQueuePlugin

    private val coolDown = CacheBuilder.newBuilder()
        .expireAfterWrite(1L, TimeUnit.SECONDS)
        .build<UUID, Long>()

    @CommandAlias("joinq|joinqueue|queue")
    @CommandPermission("scqueue.command.joinqueue")
    fun onJoinQueue(player: Player, server: Queue)
    {
        val coolDown = coolDown.getIfPresent(player.uniqueId)

        if (coolDown != null)
        {
            throw ConditionFailedException("Please wait a moment before joining another queue.")
        }

        JoinQueueCommand.coolDown.put(
            player.uniqueId,
            System.currentTimeMillis()
        )

        if (
            SpigotRedisService.findQueuePlayerIsIn(player) != null
        )
        {
            throw ConditionFailedException("You're already queued for a server.")
        }

        ScalaQueueRedisService
            .createMessageBackbone(
                "join",
                "player" to player.uniqueId,
                "queue" to server.id
            )
            .publish()

        player.sendMessage(
            "${ChatColor.GREEN}You've been added to the ${ChatColor.YELLOW}${server.displayName}${ChatColor.GREEN} queue."
        )
    }
}
