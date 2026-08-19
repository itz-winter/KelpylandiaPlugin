package com.kelpwing.kelpylandiaplugin.teleport;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Listens for teleport and death events to save locations for /back and /dback.
 */
public class BackListener implements Listener {

    private final KelpylandiaPlugin plugin;

    public BackListener(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Save the player's location before any teleport so /back can return them.
     * Portal travel (nether portals, end portals, end gateways) is excluded -
     * those are world transitions, not deliberate teleports.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        BackManager backManager = plugin.getBackManager();
        if (backManager == null) return;

        // Ignore portal-based teleports - going through a portal is not a "teleport"
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                || cause == PlayerTeleportEvent.TeleportCause.END_PORTAL
                || cause == PlayerTeleportEvent.TeleportCause.END_GATEWAY) {
            return;
        }

        Player player = event.getPlayer();

        // Don't save if source and destination are very close (avoids saving
        // positions from minor adjustments like respawn jitter)
        if (event.getFrom().getWorld() != null && event.getTo() != null) {
            if (event.getFrom().getWorld().equals(event.getTo().getWorld())
                    && event.getFrom().distanceSquared(event.getTo()) < 4) {
                return;
            }
        }

        backManager.savePreviousLocation(player.getUniqueId(), event.getFrom());
    }

    /**
     * Save the player's death location for /dback.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        BackManager backManager = plugin.getBackManager();
        if (backManager == null) return;

        Player player = event.getEntity();
        if (player.getLocation().getWorld() != null) {
            backManager.saveDeathLocation(player.getUniqueId(), player.getLocation());
        }
    }

    /**
     * Clean up stored data when a player quits to prevent memory leaks.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        BackManager backManager = plugin.getBackManager();
        if (backManager == null) return;

        backManager.cleanup(event.getPlayer().getUniqueId());
    }
}
