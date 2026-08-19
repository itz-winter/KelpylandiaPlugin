package com.kelpwing.kelpylandiaplugin.utils;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages AFK state for players.
 * Tracks last activity time and auto-marks players AFK after the configured idle period.
 */
public class AfkManager {

    private final KelpylandiaPlugin plugin;

    // Players currently marked AFK
    private final Map<UUID, Boolean> afkPlayers = new ConcurrentHashMap<>();
    // Last activity timestamp per player (millis)
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();

    private final long autoAfkMillis;

    public AfkManager(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
        int autoAfkSeconds = plugin.getConfig().getInt("afk.auto-afk-seconds", 300);
        this.autoAfkMillis = autoAfkSeconds * 1000L;

        // Schedule repeating task to check for idle players (every 20 seconds)
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkIdlePlayers, 20L * 20L, 20L * 20L);
    }

    /** Record activity (resets idle timer, un-AFKs if needed). */
    public void recordActivity(Player player) {
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
        if (isAfk(player)) {
            setAfk(player, false);
        }
    }

    /** Toggle AFK for a player (manual /afk). */
    public void toggleAfk(Player player) {
        setAfk(player, !isAfk(player));
        // When manually going AFK, set activity far in the past so auto-check doesn't immediately un-AFK
        if (isAfk(player)) {
            lastActivity.put(player.getUniqueId(), 0L);
        }
    }

    public boolean isAfk(Player player) {
        return afkPlayers.getOrDefault(player.getUniqueId(), false);
    }

    public void setAfk(Player player, boolean afk) {
        boolean wasAfk = isAfk(player);
        afkPlayers.put(player.getUniqueId(), afk);

        // Never announce AFK status changes for vanished players - they are
        // invisible to other players and leaking their state would reveal them.
        com.kelpwing.kelpylandiaplugin.moderation.commands.VanishCommand vc =
                plugin.getVanishCommand();
        boolean vanished = vc != null && vc.isVanished(player);

        if (afk && !wasAfk) {
            if (!vanished) {
                Bukkit.broadcastMessage(ChatColor.GRAY + "* " + player.getName() + " is now AFK.");
            }
            // Update player list name to show AFK tag
            String currentList = player.getPlayerListName();
            if (currentList == null || currentList.isEmpty()) currentList = player.getName();
            player.setPlayerListName(ChatColor.GRAY + "[AFK] " + ChatColor.RESET + currentList);
        } else if (!afk && wasAfk) {
            if (!vanished) {
                Bukkit.broadcastMessage(ChatColor.GRAY + "* " + player.getName() + " is no longer AFK.");
            }
            // Restore player list name
            NickManager nickManager = plugin.getNickManager();
            if (nickManager != null && nickManager.hasNickname(player.getUniqueId())) {
                nickManager.applyNickname(player);
            } else {
                player.setPlayerListName(player.getName());
            }
        }
    }

    /** Remove player data on quit. */
    public void removePlayer(UUID uuid) {
        afkPlayers.remove(uuid);
        lastActivity.remove(uuid);
    }

    private void checkIdlePlayers() {
        if (autoAfkMillis <= 0) return;
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isAfk(player)) continue; // Already AFK
            Long last = lastActivity.get(player.getUniqueId());
            if (last == null) continue;
            if (now - last >= autoAfkMillis) {
                setAfk(player, true);
            }
        }
    }
}
