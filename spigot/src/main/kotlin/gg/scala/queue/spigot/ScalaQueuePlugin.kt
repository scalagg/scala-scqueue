package gg.scala.queue.spigot

import gg.scala.commons.ExtendedScalaPlugin
import gg.scala.commons.agnostic.sync.ServerSync
import gg.scala.commons.annotations.commands.ManualRegister
import gg.scala.commons.command.ScalaCommandManager
import me.lucko.helper.plugin.ap.Plugin
import me.lucko.helper.plugin.ap.PluginDependency
import gg.scala.queue.spigot.command.JoinQueueCommand
import net.kyori.adventure.key.Key

/**
 * @author GrowlyX
 * @since 1/18/2023
 */
@Plugin(
    name = "ScQueue",
    version = "%remote%/%branch%/%id%"
)
@PluginDependency("scala-commons")
class ScalaQueuePlugin : ExtendedScalaPlugin()
{
    @ManualRegister
    fun manualRegister(
        manager: ScalaCommandManager
    )
    {
        ServerSync.getLocalGameServer()
            .setMetadata(
                Key.key("queue", "joinable"),
                "hub" in ServerSync.getLocalGameServer().groups
            )

        if (
            "hub" in ServerSync.getLocalGameServer().groups
        )
        {
            flavor().inject(JoinQueueCommand)
            manager.registerCommand(JoinQueueCommand)
        }
    }
}
