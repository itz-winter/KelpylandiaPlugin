package com.kelpwing.kelpylandiaplugin.moderation.commands;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import com.kelpwing.kelpylandiaplugin.moderation.models.Punishment;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class HistoryCommand implements CommandExecutor {
    private final KelpylandiaPlugin plugin;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public HistoryCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("qol.mod.history")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /hist <player>");
            return true;
        }

        String targetName = args[0];
        
        // Get player history as Punishment objects directly - avoids lossy Map conversion
        List<Punishment> history = plugin.getFileManager().getPlayerHistoryPunishments(targetName);

        sender.sendMessage(ChatColor.YELLOW + "========================");
        sender.sendMessage(ChatColor.GOLD + "Punishment History for " + targetName + ":");
        
        if (history.isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + "No punishment history found.");
        } else {
            int count = 1;
            for (Punishment punishment : history) {
                String action = punishment.getAction();
                String reason = punishment.getReason();
                String punisher = punishment.getPunisher();
                long durationMinutes = punishment.getDurationMinutes();
                LocalDateTime timestamp = punishment.getTimestamp();
                
                String date = timestamp != null ? timestamp.format(DATE_FORMAT) : "Unknown";
                String durationText = formatDuration(durationMinutes);
                
                // Color code based on action
                ChatColor actionColor = getActionColor(action);
                
                sender.sendMessage(actionColor + "" + count + ". " + action);
                sender.sendMessage(ChatColor.GRAY + "   Reason: " + reason);
                sender.sendMessage(ChatColor.GRAY + "   Staff: " + punisher);
                sender.sendMessage(ChatColor.GRAY + "   Duration: " + durationText);
                sender.sendMessage(ChatColor.GRAY + "   Date: " + date);
                if (punishment.isActive()) {
                    sender.sendMessage(ChatColor.GREEN + "   Status: Active");
                } else {
                    sender.sendMessage(ChatColor.DARK_GRAY + "   Status: Expired/Revoked");
                }
                
                count++;
                
                // Limit to 10 most recent entries to avoid spam
                if (count > 10) {
                    sender.sendMessage(ChatColor.YELLOW + "... and " + (history.size() - 10) + " more entries");
                    break;
                }
            }
        }
        
        sender.sendMessage(ChatColor.YELLOW + "========================");
        return true;
    }

    private String formatDuration(long durationMinutes) {
        if (durationMinutes < 0) return "Permanent";
        if (durationMinutes == 0) return "Permanent";
        
        long minutes = durationMinutes;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) return days + " day(s)";
        if (hours > 0) return hours + " hour(s)";
        return minutes + " minute(s)";
    }

    private ChatColor getActionColor(String action) {
        switch (action.toUpperCase()) {
            case "BAN":
            case "IPBAN":
                return ChatColor.DARK_RED;
            case "KICK":
            case "MUTE":
                return ChatColor.RED;
            case "WARN":
                return ChatColor.YELLOW;
            case "UNBAN":
            case "UNMUTE":
            case "UNWARN":
                return ChatColor.GREEN;
            default:
                return ChatColor.WHITE;
        }
    }
}
