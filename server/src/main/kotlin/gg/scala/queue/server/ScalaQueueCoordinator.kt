package gg.scala.queue.server

import gg.scala.aware.AwareHub
import gg.scala.aware.thread.AwareThreadContext
import gg.scala.aware.uri.WrappedAwareUri
import gg.scala.commons.agnostic.sync.ServerSync
import gg.scala.commons.agnostic.sync.server.state.ServerState
import gg.scala.queue.shared.ScalaQueueRedisService
import gg.scala.queue.shared.models.Queue
import gg.scala.queue.shared.models.QueueBehavior
import gg.scala.queue.shared.models.QueueEntry
import net.evilblock.cubed.serializers.Serializers
import java.lang.Thread.sleep
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * @author GrowlyX
 * @since 8/21/2022
 */
object ScalaQueueCoordinator
{
    data class PriorityConfig(
        val rankPriorityAllowsExceedPlayerCount: Int = 1600
    )

    data class QueueConfig(
        val queues: MutableMap<String, Queue> = mutableMapOf(
            "practice" to Queue(
                "practice",
                "practice",
                QueueBehavior.Single,
                "NA Practice"
            )
        )
    )

    private val priorities by lazy {
        loadComponent<PriorityConfig>()
    }

    private val backingQueues by lazy {
        loadComponent<QueueConfig>()
    }

    val queues: Map<String, Queue>
        get() = backingQueues.queues

    @JvmStatic
    fun main(args: Array<String>)
    {
        queues
        priorities

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

        connection.sync().del("queue:queues")

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

        queues.forEach { (_, queue) ->
            thread {
                while (true)
                {
                    runCatching {
                        if (queue.players.size > 0)
                        {
                            val servers = queue.servers()
                            if (servers.isEmpty())
                            {
                                return@runCatching
                            }

                            val leastPlayerServer = servers
                                .filter {
                                    it.state == ServerState.Loaded
                                }
                                .apply {
                                    if (isEmpty())
                                    {
                                        return@runCatching
                                    }
                                }
                                .minBy {
                                    it.getPlayersCount() ?: Int.MAX_VALUE
                                }

                            val playerCount = leastPlayerServer
                                .getPlayersCount()
                                ?: Int.MAX_VALUE

                            val maxPlayerCount = leastPlayerServer
                                .getMaxPlayers()
                                ?: Int.MIN_VALUE

                            val serverAtCapacity = playerCount >= maxPlayerCount

                            val priorityQueue = PriorityQueue(queue.players)

                            if (leastPlayerServer.getWhitelisted() == true)
                            {
                                val whitelistedPlayers = leastPlayerServer
                                    .getMetadataValue<List<String>>(
                                        "server", "whitelisted-players"
                                    )
                                    ?: return@runCatching

                                priorityQueue.removeIf {
                                    !whitelistedPlayers.contains(it.first.toString())
                                }
                            }

                            if (priorityQueue.isNotEmpty())
                            {
                                val entry = priorityQueue.poll()

                                if (serverAtCapacity)
                                {
                                    if (entry.second <= priorities.rankPriorityAllowsExceedPlayerCount)
                                    {
                                        return@runCatching
                                    }
                                }

                                queue.players.remove(entry)
                                pushQueueUpdate()

                                ScalaQueueRedisService
                                    .createMessage(
                                        "redirect",
                                        "player" to entry.first,
                                        "server" to leastPlayerServer.id,
                                        "display" to queue.displayName,
                                    )
                                    .publish(AwareThreadContext.SYNC)
                            }
                        }
                    }.onFailure {
                        Logger.getGlobal().log(
                            Level.SEVERE, "Failed to iterate through queue", it
                        )
                    }

                    if (queue.players.isEmpty())
                    {
                        while (queue.players.isEmpty())
                        {
                            sleep(50L)
                        }

                        continue
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

        thread {
            while (true)
            {
                sleep(Long.MAX_VALUE)
            }
        }
    }
}
