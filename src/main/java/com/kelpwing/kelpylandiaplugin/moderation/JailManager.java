package com.kelpwing.kelpylandiaplugin.moderation;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import com.kelpwing.kelpylandiaplugin.integrations.DiscordIntegration;
import com.kelpwing.kelpylandiaplugin.utils.DurationParser;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the jail system.
 * Jailed players cannot move, chat, craft, die, or interact.
 * They see a title screen showing remaining time and reason.
 */
public class JailManager {

    private final KelpylandiaPlugin plugin;
    private File jailFile;
    private FileConfiguration jailConfig;

    /** Active jail entries: playerUUID -> JailEntry */
    private final Map<UUID, JailEntry> jailedPlayers = new ConcurrentHashMap<>();
    private BukkitTask titleTask;

    public JailManager(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
        setupFile();
        loadJails();
        startTitleTask();
    }

    // ===================== Data Class =====================

    public static class JailEntry {
        private final UUID playerUUID;
        private final String playerName;
        private final String staffName;
        private final String reason;
        private final long jailTime; // ms since epoch
        private final long duration; // ms (always > 0, cannot be permanent)
        
        public JailEntry(UUID playerUUID, String playerName, String staffName, String reason, long jailTime, long duration) {
            this.playerUUID = playerUUID;
            this.playerName = playerName;
            this.staffName = staffName;
            this.reason = reason;
            this.jailTime = jailTime;
            this.duration = duration;
        }

        public UUID getPlayerUUID() { return playerUUID; }
        public String getPlayerName() { return playerName; }
        public String getStaffName() { return staffName; }
        public String getReason() { return reason; }
        public long getJailTime() { return jailTime; }
        public long getDuration() { return duration; }

        public long getExpireTime() { return jailTime + duration; }

        public boolean isExpired() { return System.currentTimeMillis() >= getExpireTime(); }

        public long getRemainingMs() {
            return Math.max(0, getExpireTime() - System.currentTimeMillis());
        }

        public String getRemainingFormatted() {
            long remaining = getRemainingMs();
            if (remaining <= 0) return "Expired";

            long totalSec = remaining / 1000;
            long days = totalSec / 86400;
            long hours = (totalSec % 86400) / 3600;
            long minutes = (totalSec % 3600) / 60;
            long seconds = totalSec % 60;

            StringBuilder sb = new StringBuilder();
            if (days > 0) sb.append(days).append("d ");
            if (hours > 0) sb.append(hours).append("h ");
            if (minutes > 0) sb.append(minutes).append("m ");
            sb.append(seconds).append("s");
            return sb.toString().trim();
        }
    }

    // ===================== File Handling =====================

    private void setupFile() {
        jailFile = new File(plugin.getDataFolder(), "jails.yml");
        if (!jailFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                jailFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create jails.yml!");
                e.printStackTrace();
            }
        }
        jailConfig = YamlConfiguration.loadConfiguration(jailFile);
    }

    private void loadJails() {
        jailedPlayers.clear();
        if (jailConfig.getKeys(false).isEmpty()) return;

        for (String uuidStr : jailConfig.getKeys(false)) {
            UUID uuid;
            try { uuid = UUID.fromString(uuidStr); } catch (Exception e) { continue; }

            ConfigurationSection s = jailConfig.getConfigurationSection(uuidStr);
            if (s == null) continue;

            long jailTime = s.getLong("jail-time", 0);
            long duration = s.getLong("duration", 0);

            JailEntry entry = new JailEntry(
                uuid,
                s.getString("player-name", "Unknown"),
                s.getString("staff-name", "Console"),
                s.getString("reason", "No reason given"),
                jailTime,
                duration
            );

            if (!entry.isExpired()) {
                jailedPlayers.put(uuid, entry);
            } else {
                // Expired - remove from file
                jailConfig.set(uuidStr, null);
            }
        }
        save();
        plugin.getLogger().info("Loaded " + jailedPlayers.size() + " active jail entries.");
    }

    private void save() {
        try {
            jailConfig.save(jailFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save jails.yml!");
        }
    }

    // ===================== Jail API =====================

    public void jailPlayer(UUID uuid, String playerName, String staffName, String reason, long durationMs) {
        long now = System.currentTimeMillis();
        JailEntry entry = new JailEntry(uuid, playerName, staffName, reason, now, durationMs);
        jailedPlayers.put(uuid, entry);

        // Save to file
        String path = uuid.toString();
        jailConfig.set(path + ".player-name", playerName);
        jailConfig.set(path + ".staff-name", staffName);
        jailConfig.set(path + ".reason", reason);
        jailConfig.set(path + ".jail-time", now);
        jailConfig.set(path + ".duration", durationMs);
        save();

        // Send Discord embed
        sendJailEmbed(playerName, staffName, reason, durationMs, true);
    }

    public void releasePlayer(UUID uuid, String staffName, String releaseReason) {
        JailEntry entry = jailedPlayers.remove(uuid);
        if (entry == null) return;

        // Remove from file
        jailConfig.set(uuid.toString(), null);
        save();

        // Clear title for the player
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            player.resetTitle();
            player.sendMessage(ChatColor.GREEN + "You have been released from jail!");
        }

        // Send Discord embed
        sendJailEmbed(entry.getPlayerName(), staffName, releaseReason != null ? releaseReason : "Released", 0, false);
    }

    public boolean isJailed(UUID uuid) {
        JailEntry entry = jailedPlayers.get(uuid);
        if (entry == null) return false;
        if (entry.isExpired()) {
            releaseExpired(uuid);
            return false;
        }
        return true;
    }

    public JailEntry getJailEntry(UUID uuid) {
        return jailedPlayers.get(uuid);
    }

    public Map<UUID, JailEntry> getJailedPlayers() {
        return Collections.unmodifiableMap(jailedPlayers);
    }

    private void releaseExpired(UUID uuid) {
        JailEntry entry = jailedPlayers.remove(uuid);
        if (entry == null) return;
        jailConfig.set(uuid.toString(), null);
        save();

        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            player.resetTitle();
            player.sendMessage(ChatColor.GREEN + "Your jail sentence has expired. You are free!");
        }
    }

    // ===================== Title Task =====================

    /**
     * Repeating task that shows jailed players a title screen with remaining time and reason.
     * Also auto-releases expired jails.
     */
    private void startTitleTask() {
        titleTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Iterator<Map.Entry<UUID, JailEntry>> it = jailedPlayers.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, JailEntry> mapEntry = it.next();
                JailEntry entry = mapEntry.getValue();

                if (entry.isExpired()) {
                    it.remove();
                    jailConfig.set(mapEntry.getKey().toString(), null);
                    Player p = Bukkit.getPlayer(mapEntry.getKey());
                    if (p != null && p.isOnline()) {
                        p.resetTitle();
                        p.sendMessage(ChatColor.GREEN + "Your jail sentence has expired. You are free!");
                    }
                    continue;
                }

                Player player = Bukkit.getPlayer(mapEntry.getKey());
                if (player == null || !player.isOnline()) continue;

                // Show title: "JAILED" with remaining time as subtitle
                String title = ChatColor.RED + "" + ChatColor.BOLD + "JAILED";
                String subtitle = ChatColor.YELLOW + entry.getRemainingFormatted() +
                        ChatColor.GRAY + " - " + ChatColor.WHITE + entry.getReason();

                player.sendTitle(title, subtitle, 0, 40, 10);
            }

            // Save once if any were removed
            save();
        }, 20L, 20L); // every second
    }

    public void shutdown() {
        if (titleTask != null) {
            titleTask.cancel();
            titleTask = null;
        }
    }

    // ===================== Discord =====================

    private void sendJailEmbed(String playerName, String staffName, String reason, long durationMs, boolean isJail) {
        DiscordIntegration discord = plugin.getDiscordIntegration();
        if (discord == null || !discord.isEnabled()) return;

        String action = isJail ? "JAIL" : "RELEASE";
        long durationMinutes = durationMs / (1000 * 60);
        discord.sendPunishmentMessage(action, staffName, playerName, reason, durationMinutes);
    }
}
