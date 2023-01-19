package gg.scala.queue.spigot.command

import gg.scala.commons.acf.ConditionFailedException
import gg.scala.commons.annotations.commands.customizer.CommandManagerCustomizer
import gg.scala.commons.command.ScalaCommandManager
import gg.scala.queue.shared.models.Queue
import gg.scala.queue.spigot.stream.SpigotRedisService
import org.bukkit.ChatColor

/**
 * @author GrowlyX
 * @since 1/12/2022
 */
object QueueCommandCustomizer
{
    @CommandManagerCustomizer
    fun customize(
        manager: ScalaCommandManager
    )
    {
        manager.commandContexts
            .registerContext(Queue::class.java) {
                val poppedArgument = it.popFirstArg()

                return@registerContext SpigotRedisService.queues.entries
                    .firstOrNull { wrapper ->
                        wrapper.key.equals(poppedArgument, true)
                    }?.value
                    ?: throw ConditionFailedException(
                        "There is no queue named ${ChatColor.YELLOW}$poppedArgument${ChatColor.RED}."
                    )
            }
    }
}
