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

    data class RedisConfig(
        val hostname: String = "127.0.0.1",
        val port: Int = 6379,
        val password: String? = null,
        val database: Int? = 0
    )

    private val connection by lazy {
        ScalaQueueRedisService
            .configurePopulatorBackbone {
                this.internal().connect()
            }
    }

    private val priorities by lazy {
        loadComponent<PriorityConfig>()
    }

    private val backingQueues by lazy {
        loadComponent<QueueConfig>()
    }

    private val redis by lazy {
        loadComponent<RedisConfig>()
    }

    val queues: Map<String, Queue>
        get() = backingQueues.queues

    fun removeFromQueue(uniqueId: UUID, manual: Boolean = false)
    {
        val queue = find(uniqueId)
            ?: return

        if (manual)
        {
            queue.value.totalLeft += 1
        }

        queue.value.players.removeIf { it.first == uniqueId }
        pushQueueUpdate()
    }

    fun find(uniqueId: UUID) = queues.entries
        .firstOrNull { queue ->
            queue.value.players.any { player -> player.first == uniqueId }
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

    @JvmStatic
    fun main(args: Array<String>)
    {
        redis
        queues
        priorities

        AwareHub.configure(
            WrappedAwareUri(redis.hostname, redis.port, redis.password, redis.database!!)
        ) {
            Serializers.gson
        }

        connection
        connection.sync().del("queue:queues")

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

                    queue.totalQueued += 1
                    queue.players.add(
                        QueueEntry(uniqueId, priority)
                    )
                    pushQueueUpdate()
                }

                listen("leave") {
                    val uniqueId = retrieve<UUID>("player")
                    removeFromQueue(uniqueId, true)
                }

                listen("manager-metrics") {
                    val uniqueId = retrieve<UUID>("player")
                    val queue = queues[retrieve("queue")]
                        ?: return@listen

                    ScalaQueueRedisService
                        .createMessage(
                            "message",
                            "content" to Serializers.gson.toJson(
                                mutableMapOf(
                                    "$uniqueId" to """
                                        Metrics for ${queue.id}:
                                         Behavior: ${queue.behavior.name}
                                         Current Queued: ${queue.total()}
                                         
                                         Effective Servers: ${queue.servers().size}
                                         Available Servers: ${queue.servers().count { it.state == ServerState.Loaded }}
                                         Whitelisted Servers: ${queue.servers().count { it.getWhitelisted() == true }}
                                         
                                         Total Queued: ${queue.totalQueued}
                                         Total Sent: ${queue.totalSent}
                                         Total Left: ${queue.totalLeft}
                                         %age sent: ${queue.percentageSent()}
                                    """.trimIndent()
                                )
                            )
                        ).publish(
                            AwareThreadContext.SYNC
                        )
                }

                listen("network-login") {
                    val uniqueId = retrieve<UUID>("player")
                    QueuePostLogoutPersistence.userLoggedBackIn(uniqueId)
                }

                listen("network-logout") {
                    val uniqueId = retrieve<UUID>("player")
                    find(uniqueId)
                        ?.apply {
                            QueuePostLogoutPersistence
                                .userLoggedOut(
                                    value.players.first { it.first == uniqueId }
                                )
                        }
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
        QueuePostLogoutPersistence.configure()

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

                                queue.totalSent += 1
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
                        sleep(200)
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
