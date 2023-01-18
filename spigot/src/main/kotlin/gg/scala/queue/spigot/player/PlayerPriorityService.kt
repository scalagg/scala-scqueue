package gg.scala.queue.spigot.player

import gg.scala.commons.annotations.custom.CustomAnnotationProcessors
import gg.scala.flavor.inject.Inject
import gg.scala.flavor.service.Configure
import gg.scala.flavor.service.Service
import gg.scala.queue.shared.ScalaQueueRedisService
import gg.scala.queue.spigot.ScalaQueuePlugin
import me.lucko.helper.Events
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.concurrent.CompletableFuture

/**
 * @author GrowlyX
 * @since 1/18/2023
 */
@Service
object PlayerPriorityService
{
    @Inject
    lateinit var plugin: ScalaQueuePlugin

    private var prioritySupplier: PlayerPrioritySupplier =
        object : PlayerPrioritySupplier
        {
            override fun provide(player: Player) = 0
        }

    @Configure
    fun configure()
    {
        val connection = ScalaQueueRedisService
            .configurePopulator {
                internal().connect()
            }

        Events
            .subscribe(PlayerJoinEvent::class.java)
            .handler {
                CompletableFuture
                    .runAsync {
                        connection.sync().hset(
                            "queue:player:priorities",
                            "${it.player.uniqueId}",
                            prioritySupplier.provide(it.player).toString()
                        )
                    }
            }
            .bindWith(plugin)

        Events
            // players will only be queueing while they're on the server,
            // so we can just invalidate their priority in redis on logout
            .subscribe(PlayerQuitEvent::class.java)
            .handler {
                CompletableFuture
                    .runAsync {
                        connection.sync().hdel(
                            "queue:player:priorities",
                            "${it.player.uniqueId}"
                        )
                    }
            }
            .bindWith(plugin)

        CustomAnnotationProcessors
            .process<BindPrioritySupplier> {
                prioritySupplier = it as PlayerPrioritySupplier
                plugin.logger.info("[priority] Using ${it.javaClass.name} as our PlayerPriorityProvider.")
            }
    }
}
