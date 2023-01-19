package gg.scala.queue.spigot.command

import gg.scala.commons.annotations.commands.AutoRegister
import gg.scala.commons.command.ScalaCommand
import gg.scala.commons.acf.ConditionFailedException
import gg.scala.commons.acf.annotation.CommandAlias
import gg.scala.commons.acf.annotation.CommandPermission
import gg.scala.queue.shared.ScalaQueueRedisService
import gg.scala.queue.spigot.stream.SpigotRedisService
import org.bukkit.ChatColor
import org.bukkit.entity.Player

/**
 * @author GrowlyX
 * @since 1/12/2022
 */
@AutoRegister
object LeaveQueueCommand : ScalaCommand()
{
    @CommandAlias("leaveq|leavequeue")
    @CommandPermission("scqueue.command.leavequeue")
    fun onJoinQueue(player: Player)
    {
        val queued = SpigotRedisService
            .findQueuePlayerIsIn(player)
            ?: throw ConditionFailedException(
                "You're not currently queued for a server."
            )

        ScalaQueueRedisService
            .createMessageBackbone(
                "leave",
                "player" to player.uniqueId
            )
            .publish()

        player.sendMessage(
            "${ChatColor.RED}You've left the ${ChatColor.YELLOW}${queued.displayName}${ChatColor.RED} queue."
        )
    }
}
