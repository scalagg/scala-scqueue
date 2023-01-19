package gg.scala.queue.server

import com.google.gson.reflect.TypeToken
import gg.scala.aware.AwareHub
import gg.scala.aware.thread.AwareThreadContext
import gg.scala.aware.uri.WrappedAwareUri
import gg.scala.commons.agnostic.sync.ServerSync
import gg.scala.commons.agnostic.sync.server.ServerContainer
import gg.scala.commons.agnostic.sync.server.impl.GameServer
import gg.scala.commons.agnostic.sync.server.state.ServerState
import gg.scala.queue.shared.ScalaQueueRedisService
import gg.scala.queue.shared.models.Queue
import gg.scala.queue.shared.models.QueueEntry
import net.evilblock.cubed.serializers.Serializers
import java.io.File
import java.lang.Thread.sleep
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.collections.HashMap
import kotlin.system.exitProcess

/**
 * @author GrowlyX
 * @since 8/21/2022
 */
object ScalaQueueCoordinator
{
    val queues by lazy {
        File("queues.json")
            .let {
                if (!it.exists())
                {
                    val serializable = mutableMapOf(
                        "practice" to Queue("practice", "na-practice", "NA Practice")
                    )

                    it.createNewFile()
                    it.appendText(
                        Serializers.gson.toJson(serializable)
                    )
                    return@let serializable
                }

                Serializers.gson
                    .fromJson<HashMap<String, Queue>>(
                        it.readText(), object : TypeToken<HashMap<String, Queue>>()
                        {}.type
                    )
            }!!
    }

    @JvmStatic
    fun main(args: Array<String>)
    {
        queues

        println()

        AwareHub.configure(
            WrappedAwareUri()
        ) {
            Serializers.gson
        }

        val connection = ScalaQueueRedisService
            .configurePopulatorBackbone {
                this.internal().connect()
            }

        fun pushQueueUpdate()
        {
            for (queue in queues)
            {
                connection.sync().hset(
                    "queue:queues",
                    queue.key,
                    Serializers.gson.toJson(queue.value)
                )
            }

            ScalaQueueRedisService
                .createMessage("update-ondemand")
                .publish(AwareThreadContext.SYNC)
        }

        fun find(uniqueId: UUID) = queues.entries
            .firstOrNull { queue ->
                queue.value.players.any { player -> player.first == uniqueId }
            }

        ScalaQueueRedisService
            .configureBackbone {
                listen("join") {
                    val uniqueId = retrieve<UUID>("player")

                    if (find(uniqueId) != null)
                    {
                        return@listen
                    }

                    val queue = queues[retrieve("queue")]
                        ?: return@listen

                    val priority = connection.sync()
                        .hget("queue:player:priorities", uniqueId.toString())
                        ?.toInt() ?: 0

                    queue.players.add(
                        QueueEntry(uniqueId, priority)
                    )
                    pushQueueUpdate()
                }

                listen("leave") {
                    val uniqueId = retrieve<UUID>("player")
                    val queue = find(uniqueId)
                        ?: return@listen

                    queue.value.players.removeIf { it.first == uniqueId }
                    pushQueueUpdate()
                }
            }

        ScalaQueueRedisService.start()
        ScalaQueueRedisService.startBackbone()

        connection.sync()
            .hgetall("queue:queues")
            .forEach {
                connection.sync()
                    .hdel("queue:queues", it.key)
            }

        pushQueueUpdate()

        ServerSync.configureIndependent()
        ScalaQueueUpdateTask.configure()

        println("starting queue loop")

        while (true)
        {
            kotlin.runCatching {
                queues.forEach { (_, queue) ->
                    if (queue.players.size > 0)
                    {
                        val servers = ServerContainer
                            .getServersInGroupCasted<GameServer>(queue.serverGroup)

                        if (servers.isEmpty())
                        {
                            return@forEach
                        }

                        val leastPlayerServer = servers
                            .filter {
                                it.state == ServerState.Loaded
                            }
                            .apply {
                                if (isEmpty())
                                {
                                    return@forEach
                                }
                            }
                            .minBy {
                                it.getPlayersCount() ?: Int.MAX_VALUE
                            }

                        val priorityQueue = PriorityQueue(queue.players)

                        if (leastPlayerServer.getWhitelisted() == true)
                        {
                            priorityQueue.removeIf {
                                !(leastPlayerServer
                                    .getMetadataValue<List<String>>(
                                        "server", "whitelisted-players"
                                    )
                                    ?.contains(it.toString())
                                    ?: false)
                            }
                        }

                        if (priorityQueue.isNotEmpty())
                        {
                            val uniqueId = priorityQueue.poll()
                            pushQueueUpdate()

                            ScalaQueueRedisService
                                .createMessage(
                                    "redirect",
                                    "player" to uniqueId.first,
                                    "server" to leastPlayerServer.id,
                                    "display" to queue.displayName,
                                )
                                .publish(AwareThreadContext.SYNC)
                        }
                    }
                }
            }.onFailure {
                Logger.getGlobal().log(
                    Level.SEVERE, "Failed to iterate through queue", it
                )
            }

            kotlin.runCatching {
                sleep(500)
            }.onFailure {
                Logger.getGlobal().log(
                    Level.SEVERE, "Failed to continue queue process, restarting.", it
                )
                exitProcess(1)
            }
        }
    }
}
