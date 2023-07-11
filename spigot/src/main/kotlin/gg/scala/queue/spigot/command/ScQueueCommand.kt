package gg.scala.queue.spigot.command

import gg.scala.commons.acf.annotation.CommandAlias
import gg.scala.commons.acf.annotation.CommandPermission
import gg.scala.commons.acf.annotation.Description
import gg.scala.commons.acf.annotation.Subcommand
import gg.scala.commons.annotations.commands.AssignPermission
import gg.scala.commons.annotations.commands.AutoRegister
import gg.scala.commons.command.ScalaCommand
import gg.scala.commons.issuer.ScalaPlayer
import gg.scala.flavor.inject.Inject
import gg.scala.queue.shared.ScalaQueueRedisService
import gg.scala.queue.shared.models.Queue
import gg.scala.queue.spigot.ScalaQueuePlugin
import net.evilblock.cubed.util.CC

/**
 * @author GrowlyX
 * @since 1/12/2022
 */
@AutoRegister
@CommandAlias("scqueue|queueadmin")
@CommandPermission("scqueue.command.admin")
object ScQueueCommand : ScalaCommand()
{
    @Inject
    lateinit var plugin: ScalaQueuePlugin

    @AssignPermission
    @Subcommand("request-metrics")
    @Description("Request metrics for a queue.")
    fun onRequestMetrics(player: ScalaPlayer, queue: Queue)
    {
        player.sendMessage(
            "${CC.GREEN}Requesting metrics from the coordinator..."
        )

        ScalaQueueRedisService
            .createMessageBackbone(
                "manager-metrics",
                "player" to player.uniqueId,
                "queue" to queue.id
            )
            .publish()
    }
}
