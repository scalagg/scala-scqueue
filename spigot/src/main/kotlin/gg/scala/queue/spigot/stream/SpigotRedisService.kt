package gg.scala.queue.spigot.stream

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import gg.scala.flavor.inject.Inject
import gg.scala.flavor.service.Configure
import gg.scala.flavor.service.Service
import gg.scala.lemon.redirection.impl.VelocityRedirectSystem
import gg.scala.queue.shared.ScalaQueueRedisService
import gg.scala.queue.shared.models.Queue
import gg.scala.queue.spigot.ScalaQueuePlugin
import net.evilblock.cubed.serializers.Serializers
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bungee.BungeeUtil
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.PriorityQueue
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * @author GrowlyX
 * @since 1/18/2023
 */
@Service(priority = 1000)
object SpigotRedisService
{
    @Inject
    lateinit var plugin: ScalaQueuePlugin

    val queues = ConcurrentHashMap<String, Queue>()

    fun findQueuePlayerIsIn(player: Player) =
        queues.values.firstOrNull {
            it.players.any { queue -> queue.first == player.uniqueId }
        }

    val connection = ScalaQueueRedisService
        .configurePopulator {
            internal().connect()
        }!!

    @Configure
    fun configure()
    {
        ScalaQueueRedisService
            .configure {
                listen("redirect") {
                    val uniqueId = this.retrieve<UUID>("player")
                    val server = this.retrieve<String>("server").removeSurrounding("\"")
                    val display = this.retrieve<String>("display").removeSurrounding("\"")

                    val player = Bukkit.getPlayer(uniqueId)
                        ?: return@listen

                    player.sendMessage(
                        "${CC.GREEN}You're now being sent to ${CC.YELLOW}$display${CC.GREEN}."
                    )

                    BungeeUtil.sendToServer(player, server)
                }

                listen("update-ondemand") {
                    pullQueuesFromRedis()
                }

                listen("message") {
                    val content =
                        retrieve<Map<String, String>>("content")

                    for ((uuid, message) in content)
                    {
                        val bukkit = Bukkit
                            .getPlayer(UUID.fromString(uuid))
                            ?: continue

                        // prevent 1.7 formatting issues
                        message.split("\n")
                            .forEach {
                                bukkit.sendMessage(it)
                            }
                    }
                }
            }

        ScalaQueueRedisService.start()
        ScalaQueueRedisService
            .configureBackbone {
                // we don't need to configure pubsub for backbone on spigot servers
                this.publishConnection = ScalaQueueRedisService
                    .configurePopulator {
                        this.publishConnection
                    }
            }

        pullQueuesFromRedis()
    }

    private fun pullQueuesFromRedis()
    {
        val queues = connection.sync()
            .hgetall("queue:queues")
            .mapValues {
                Serializers.gson.fromJson(it.value, Queue::class.java)
            }

        for (queue in queues)
        {
            this.queues[queue.key] = queue.value
        }
    }
}
