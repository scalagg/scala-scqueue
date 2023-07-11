package gg.scala.queue.shared.models

import gg.scala.commons.agnostic.sync.server.ServerContainer
import gg.scala.commons.agnostic.sync.server.impl.GameServer
import java.util.*

/**
 * @author GrowlyX
 * @since 1/18/2023
 */
data class Queue(
    val id: String,
    val server: String, val behavior: QueueBehavior,
    val displayName: String,
    val players: PriorityQueue<QueueEntry> = PriorityQueue()
)
{
    fun total() = players.size

    fun servers() = when (behavior)
    {
        QueueBehavior.Grouped -> ServerContainer
            .getServersInGroupCasted<GameServer>(server)
        QueueBehavior.Single -> listOfNotNull(
            ServerContainer.getServer(server)
        ).filterIsInstance<GameServer>()
    }

    fun position(uniqueId: UUID) = players
        .sortedByDescending { it.second }
        .indexOfFirst { it.first == uniqueId } + 1
}
