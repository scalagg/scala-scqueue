package gg.scala.queue.shared.models

import java.util.*

/**
 * @author GrowlyX
 * @since 1/18/2023
 */
data class Queue(
    val id: String,
    val serverGroup: String,
    val displayName: String,
    val players: PriorityQueue<QueueEntry> = PriorityQueue()
)
{
    fun total() = players.size

    fun position(uniqueId: UUID) = players
        .sortedBy { it.second }
        .indexOfFirst { it.first == uniqueId } + 1
}
