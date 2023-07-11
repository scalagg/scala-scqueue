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
    @Transient
    var totalQueued = 0

    @Transient
    var totalSent = 0

    @Transient
    var totalLeft = 0

    fun percentageSent() = (if (totalQueued == 0) 0 else
        totalSent / totalQueued) * 100.0f

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
