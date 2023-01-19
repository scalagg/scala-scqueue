package gg.scala.queue.shared.models

import java.util.UUID

/**
 * @author GrowlyX
 * @since 1/18/2023
 */
class QueueEntry(
    val first: UUID,
    val second: Int
) : Comparable<QueueEntry>
{
    override fun compareTo(other: QueueEntry) =
        other.second.compareTo(second)
}
