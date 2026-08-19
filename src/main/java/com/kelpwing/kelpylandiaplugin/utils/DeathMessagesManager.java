package com.kelpwing.kelpylandiaplugin.utils;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import com.kelpwing.kelpylandiaplugin.integrations.DiscordIntegration;
import com.kelpwing.kelpylandiaplugin.moderation.commands.VanishCommand;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages custom death messages loaded from deathmessages.yml.
 * 
 * When a player uses /suicide, their UUID is marked. On the resulting
 * {@link PlayerDeathEvent}, this listener replaces the vanilla death message
 * with a random custom message from the config. Because the message is set
 * directly on the event, other plugins (Discord bridges, Paper, etc.) will
 * pick it up naturally.
 *
 * Supports {player}, {player_name}, {world} placeholders and PlaceholderAPI.
 */
public class DeathMessagesManager implements Listener {

    private final KelpylandiaPlugin plugin;
    private final List<String> messages = new ArrayList<>();

    /**
     * Players marked to receive a custom death message on their next death.
     * Entries are removed as soon as the death event fires.
     */
    private final Set<UUID> pendingSuicide = Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    public DeathMessagesManager(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    // ===================== Suicide Tracking =====================

    /**
     * Mark a player so their next death will use a custom death message.
     * Called by {@code SuicideCommand} right before setting health to 0.
     */
    public void markSuicide(UUID playerUUID) {
        pendingSuicide.add(playerUUID);
    }

    /**
     * Intercept the death event for players that used /suicide.
     * Replaces the vanilla death message with a random custom one.
     * Because we modify the event itself, every other plugin listening
     * to PlayerDeathEvent (Discord bridges, scoreboards, etc.) sees
     * our custom message instead of the default.
     */
    @SuppressWarnings("deprecation") // getDeathMessage/setDeathMessage - safe across 1.16-1.21
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // Suppress death messages for vanished players entirely
        VanishCommand vc = plugin.getVanishCommand();
        if (vc != null && vc.isVanished(player)) {
            event.setDeathMessage(null);
            return;
        }

        if (!pendingSuicide.remove(player.getUniqueId())) return;

        String customMessage = getRandomMessage(player);
        event.setDeathMessage(customMessage);
    }

    /**
     * Relay the final death message to Discord after all other listeners have run.
     * Fires at MONITOR priority so it reads the message exactly as it appears in-game,
     * whether set by us (suicide) or by vanilla/other plugins (regular death).
     */
    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeathDiscordRelay(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // Skip vanished players - no message was shown in-game
        VanishCommand vc = plugin.getVanishCommand();
        if (vc != null && vc.isVanished(player)) return;

        if (!plugin.getConfig().getBoolean("discord.events.broadcast-deaths", true)) return;

        DiscordIntegration discord = plugin.getDiscordIntegration();
        if (discord == null || !discord.isEnabled()) return;

        // Use the in-game death message (may be vanilla or our custom one)
        String rawMessage = event.getDeathMessage();
        if (rawMessage == null || rawMessage.isEmpty()) return;

        // Strip Minecraft colour codes before sending to Discord
        String cleanMessage = ChatColor.stripColor(rawMessage);
        discord.sendDeathMessage(player, cleanMessage);
    }

    // ===================== Message Loading =====================

    /**
     * Load (or reload) death messages from deathmessages.yml.
     */
    public void loadMessages() {
        messages.clear();

        // Save default if it doesn't exist
        File file = new File(plugin.getDataFolder(), "deathmessages.yml");
        if (!file.exists()) {
            plugin.saveResource("deathmessages.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<String> loaded = config.getStringList("messages");

        if (loaded.isEmpty()) {
            // Fallback if file is empty or malformed
            messages.add("{player} died");
        } else {
            messages.addAll(loaded);
        }

        plugin.getLogger().info("Loaded " + messages.size() + " custom death messages.");
    }

    /**
     * Get a random death message formatted for the given player.
     * Replaces built-in placeholders and applies PlaceholderAPI if available.
     */
    public String getRandomMessage(Player player) {
        String template = messages.get(ThreadLocalRandom.current().nextInt(messages.size()));
        return formatMessage(template, player);
    }

    /**
     * Format a death message template with placeholders.
     */
    private String formatMessage(String template, Player player) {
        String message = template
                .replace("{player}", player.getDisplayName())
                .replace("{player_name}", player.getName())
                .replace("{world}", player.getWorld().getName());

        // Apply PlaceholderAPI if available
        if (plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                message = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, message);
            } catch (Exception ignored) {
                // PlaceholderAPI not available or errored
            }
        }

        // Translate color codes
        message = ChatColor.translateAlternateColorCodes('&', message);

        return message;
    }

    /**
     * Get the number of loaded messages.
     */
    public int getMessageCount() {
        return messages.size();
    }
}
