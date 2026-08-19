package com.kelpwing.kelpylandiaplugin.chat.listeners;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import com.kelpwing.kelpylandiaplugin.chat.Channel;
import com.kelpwing.kelpylandiaplugin.moderation.commands.VanishCommand;
import com.kelpwing.kelpylandiaplugin.utils.PlayerStateManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.FixedMetadataValue;

public class PlayerListener implements Listener {

    private final KelpylandiaPlugin plugin;

    public PlayerListener(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Set player to default channel
        plugin.getChannelManager().setPlayerToDefaultChannel(player);
        
        // Restore persisted toggle states (fly, god, vanish, spy, wt)
        PlayerStateManager stateManager = plugin.getPlayerStateManager();
        if (stateManager != null) {
            stateManager.restoreState(player);
        }
        
        // If restoreState made the player vanished, suppress the join message immediately
        // so no handler at any priority leaks it
        VanishCommand vc = plugin.getVanishCommand();
        if (vc != null && vc.isVanished(player)) {
            event.setJoinMessage(null);
            // Tag for DiscordSRV so it also skips its join message
            player.setMetadata("DiscordSRV:silentjoin",
                    new FixedMetadataValue(plugin, true));
        } else {
            // Ensure no stale metadata from a previous session
            player.removeMetadata("DiscordSRV:silentjoin", plugin);
        }

        // Clean up the silentjoin metadata next tick - it only needs to exist
        // during the event cycle so DiscordSRV's MONITOR handler sees it.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.removeMetadata("DiscordSRV:silentjoin", plugin);
            }
        });
        
        plugin.getLogger().info("Player " + player.getName() + " joined and was assigned to default channel"
                + (vc != null && vc.isVanished(player) ? " (vanished - join message suppressed)" : ""));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Save toggle states before cleanup
        PlayerStateManager stateManager = plugin.getPlayerStateManager();
        if (stateManager != null) {
            stateManager.saveState(player);
        }
        
        // Remove player from channel manager
        plugin.getChannelManager().removePlayerFromChannel(player);
        
        plugin.getLogger().info("Player " + player.getName() + " left and was removed from channels");
    }
}
