package gg.scala.queue.spigot.cloud

import gg.scala.cloudsync.shared.discovery.CloudSyncDiscoveryService
import gg.scala.commons.annotations.plugin.SoftDependency
import gg.scala.flavor.service.Configure
import gg.scala.flavor.service.Service
import gg.scala.flavor.service.ignore.IgnoreAutoScan

/**
 * @author GrowlyX
 * @since 1/18/2023
 */
@Service
@IgnoreAutoScan
@SoftDependency("cloudsync")
object CloudSyncService
{
    @Configure
    fun configure()
    {
        CloudSyncDiscoveryService
            .discoverable.assets.add(
                "gg.scala.queue:spigot:scala-queue-spigot"
            )
    }
}
