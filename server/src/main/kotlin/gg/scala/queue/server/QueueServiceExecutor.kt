package gg.scala.queue.server

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

/**
 * @author GrowlyX
 * @since 7/10/2023
 */
object QueueServiceExecutor : ScheduledExecutorService by Executors
    .newScheduledThreadPool(3)
