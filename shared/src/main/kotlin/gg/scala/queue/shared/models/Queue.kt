package gg.scala.queue.shared.models

import java.util.Comparator
import java.util.PriorityQueue
import java.util.UUID

/**
 * @author GrowlyX
 * @since 1/18/2023
 */
data class Queue(
    val id: String,
    val serverGroup: String,
    val displayName: String,
    val players: PriorityQueue<Pair<UUID, Int>> =
        PriorityQueue(
            Comparator.comparing { it.second }
        )
)
{
    fun total() = players.size

    fun position(uniqueId: UUID) = players
        .sortedBy { it.second }
        .indexOfFirst { it.first == uniqueId }
}
