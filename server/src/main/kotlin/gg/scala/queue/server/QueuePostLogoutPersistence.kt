package gg.scala.queue.server

import gg.scala.aware.thread.AwareThreadContext
import gg.scala.queue.shared.ScalaQueueRedisService
import gg.scala.queue.shared.models.QueueEntry
import net.evilblock.cubed.serializers.Serializers
import java.util.SortedMap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * @author GrowlyX
 * @since 7/11/2023
 */
object QueuePostLogoutPersistence : Runnable
{
    data class PersistenceConfig(
        val enabled: Boolean = true,
        val priorityRanges: SortedMap<Int, Duration> =
            sortedMapOf(
                1150 to NoPersistence,
                1151 to Duration(1, TimeUnit.MINUTES),
                1250 to Duration(3, TimeUnit.MINUTES),
                1300 to Duration(5, TimeUnit.MINUTES),
                1550 to Duration(10, TimeUnit.MINUTES),
                // staff
                1600 to Duration(15, TimeUnit.MINUTES)
            )
    )

    private val persistence by lazy {
        loadComponent<PersistenceConfig>()
    }
    private val NoPersistence = Duration(0, TimeUnit.MINUTES)

    data class Duration(
        val time: Long, val unit: TimeUnit
    )
    {
        fun toMillis() = if (this.time == 0L) 0L else unit.toMillis(time)
    }

    fun configure()
    {
        persistence

        QueueServiceExecutor
            .scheduleAtFixedRate(
                this,
                0L, 1L,
                TimeUnit.SECONDS
            )
    }

    fun userLoggedOut(entry: QueueEntry)
    {
        for (reversed in persistence.priorityRanges.entries.reversed())
        {
            if (entry.second > reversed.key)
            {
                if (reversed.value.toMillis() == 0L)
                {
                    ScalaQueueCoordinator.removeFromQueue(entry.first)
                    return
                }

                players[entry.first] =
                    System.currentTimeMillis() + reversed.value.toMillis()
                return
            }
        }
    }

    fun userLoggedBackIn(uniqueId: UUID)
    {
        players.remove(uniqueId)

        if (playersRequiringMessage.remove(uniqueId))
        {
            ScalaQueueRedisService
                .createMessage(
                    "message",
                    "content" to Serializers.gson.toJson(
                        mutableMapOf(
                            "$uniqueId" to ScalaQueueUpdateTask.language.userLoggedInTooLate
                        )
                    )
                ).publish(
                    AwareThreadContext.SYNC
                )
        }
    }

    private val playersRequiringMessage = CopyOnWriteArrayList<UUID>()
    private val players = ConcurrentHashMap<UUID, Long>()
    override fun run()
    {
        val now = System.currentTimeMillis()

        players.forEach { (t, u) ->
            if (now > u)
            {
                players.remove(t)
                playersRequiringMessage += t

                ScalaQueueCoordinator.removeFromQueue(t)

                println("[persistence] removed $t from logout cache and from the queue they were previously in.")
                return@forEach
            }
        }
    }
}
