package gg.scala.queue.shared.models

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
    val players: PriorityQueue<UUID>
)
