package com.kelpwing.kelpylandiaplugin.chat;

// Formatting helpers only - IC command-box logic is in CommandsDisplay.java.

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import org.bukkit.entity.Player;

public class ChatUtils {

    // IC command-box logic lives in CommandsDisplay.java.
    // This class is kept only for chat formatting helpers.

    // Chat formatting helpers

    /**
     * Builds the fully-formatted chat prefix (everything before the message body).
     */
    public static String formatPrefix(KelpylandiaPlugin plugin, Player player, Channel channel) {
        String format = channel.getFormat();
        format = format.replace("{prefix}", getPlayerPrefix(player));
        format = format.replace("{suffix}", getPlayerSuffix(player));
        format = format.replace("{player}", player.getName());
        format = format.replace("{displayname}", player.getDisplayName());
        format = format.replace("{channel}", channel.getDisplayName());
        format = org.bukkit.ChatColor.translateAlternateColorCodes('&', format);
        int msgIdx = format.indexOf("{message}");
        if (msgIdx >= 0) return format.substring(0, msgIdx);
        return format + ": ";
    }

    /**
     * Builds the complete chat line as a legacy-colour string.
     */
    public static String formatMessage(KelpylandiaPlugin plugin, Player player, Channel channel, String message) {
        String format = channel.getFormat();
        format = format.replace("{prefix}", getPlayerPrefix(player));
        format = format.replace("{suffix}", getPlayerSuffix(player));
        format = format.replace("{player}", player.getName());
        format = format.replace("{displayname}", player.getDisplayName());
        format = format.replace("{channel}", channel.getDisplayName());
        format = format.replace("{message}", message);
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', format);
    }

    private static String getPlayerPrefix(Player player) {
        try {
            if (org.bukkit.Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                String prefix = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, "%luckperms_prefix%");
                if (prefix != null && !prefix.equals("%luckperms_prefix%")) return prefix;
            }
        } catch (Exception ignored) {}
        return "";
    }

    private static String getPlayerSuffix(Player player) {
        try {
            if (org.bukkit.Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                String suffix = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, "%luckperms_suffix%");
                if (suffix != null && !suffix.equals("%luckperms_suffix%")) return suffix;
            }
        } catch (Exception ignored) {}
        return "";
    }

    public static boolean hasPermission(Player player, String permission) {
        return player.hasPermission(permission);
    }

    public static boolean isPlayerMuted(Player player) {
        KelpylandiaPlugin plugin = KelpylandiaPlugin.getInstance();
        if (plugin == null) return false;
        Long muteExpiry = plugin.getMutedPlayers().get(player.getUniqueId());
        if (muteExpiry == null) return false;
        long now = System.currentTimeMillis();
        if (muteExpiry > 0 && muteExpiry <= now) {
            plugin.getMutedPlayers().remove(player.getUniqueId());
            return false;
        }
        return true;
    }
}