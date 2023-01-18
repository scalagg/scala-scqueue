package gg.scala.queue.server

import gg.minequest.ranked.shared.QuestRedisService
import gg.minequest.ranked.shared.models.QueueEntry
import gg.minequest.ranked.shared.models.ScheduledMatch
import gg.scala.aware.AwareHub
import gg.scala.aware.thread.AwareThreadContext
import gg.scala.aware.uri.WrappedAwareUri
import gg.scala.commons.agnostic.sync.ServerSync
import gg.scala.commons.agnostic.sync.server.ServerContainer
import gg.scala.commons.agnostic.sync.server.impl.GameServer
import net.evilblock.cubed.serializers.Serializers
import java.lang.Thread.sleep
import java.util.LinkedList
import java.util.UUID
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
    @JvmStatic
    fun main(args: Array<String>)
    {
        val queued = LinkedList<QueueEntry>()

        AwareHub.configure(
            WrappedAwareUri()
        ) {
            Serializers.gson
        }

        val connection = QuestRedisService
            .configurePopulator {
                this.internal().connect()
            }

        QuestRedisService.configure {
            listen("queue-join") {
                val entry = retrieve<QueueEntry>("entry")

                if (queued.none {
                        it.player == entry.player
                })
                {
                    queued.add(entry)

                    connection.sync().hset(
                        "pokemon:ranked:queued",
                        entry.player.toString(),
                        entry.queueTime.toString()
                    )
                }
            }

            listen("queue-leave") {
                val entry = retrieve<UUID>("player")

                queued.removeIf {
                    it.player == entry
                }

                connection.sync().hdel(
                    "pokemon:ranked:queued",
                    entry.toString()
                )
            }
        }

        QuestRedisService.start()

        ServerSync.configureIndependent()

        // strategy to prevent only high/low elo players being picked first.
        // this simple switch strategy prevents this from happening by switching
        // between -1 & 1 as the multiplier for the sortByDescending functionality.
        var currentStrategy = -1

        thread {
            while (true)
            {
                kotlin.runCatching {
                    if (queued.size >= 2)
                    {
                        queued.sortByDescending {
                            currentStrategy * it.elo
                        }

                        val first = queued.pollFirst()
                        val second = queued.pollFirst()

                        val server = ServerContainer
                            .getServersInGroupCasted<GameServer>("pokemon")
                            .filter {
                                it.getWhitelisted() == false
                            }
                            .minByOrNull {
                                // we don't want to send a player to a dead server >.<
                                it.getPlayersCount() ?: Int.MAX_VALUE
                            }
                            ?: return@runCatching

                        println("Starting match on ${server.id} with ${first.player} & ${second.player}")

                        val scheduled =
                            ScheduledMatch(
                                UUID.randomUUID(),
                                first, second, server.id
                            )

                        val serialized = Serializers
                            .gson.toJson(scheduled)

                        connection.sync().hset(
                            "pokemon:ranked:matches",
                            scheduled.uniqueId.toString(),
                            serialized
                        )

                        listOf(first, second).forEach {
                            QuestRedisService.createMessage(
                                "queue-redirect",
                                "uniqueId" to it.player,
                                "server" to server.id,
                                "match" to serialized
                            ).publish(
                                AwareThreadContext.SYNC
                            )

                            connection.sync().hdel(
                                "pokemon:ranked:queued",
                                it.player.toString()
                            )
                        }

                        currentStrategy =
                            if (currentStrategy == -1) 1 else -1
                    }
                }.onFailure {
                    Logger.getGlobal().log(
                        Level.SEVERE, "Failed to iterate through queue", it
                    )
                }

                kotlin.runCatching {
                    sleep(1000)
                }.onFailure {
                    Logger.getGlobal().log(
                        Level.SEVERE, "Failed to continue queue process, restarting.", it
                    )
                    exitProcess(1)
                }
            }
        }
    }
}
