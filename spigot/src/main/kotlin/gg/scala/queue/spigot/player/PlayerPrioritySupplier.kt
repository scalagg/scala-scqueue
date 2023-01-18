package gg.scala.queue.spigot.player

import org.bukkit.entity.Player

/**
 * @author GrowlyX
 * @since 1/18/2023
 */
interface PlayerPrioritySupplier
{
    fun provide(player: Player): Int
}
