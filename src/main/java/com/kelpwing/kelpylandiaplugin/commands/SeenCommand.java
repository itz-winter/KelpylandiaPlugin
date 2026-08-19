package com.kelpwing.kelpylandiaplugin.commands;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * /seen <player> - Shows when a player was last online.
 */
public class SeenCommand implements CommandExecutor, TabCompleter {

    private final KelpylandiaPlugin plugin;

    public SeenCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /seen <player>");
            return true;
        }

        String targetName = args[0];

        // Check if online first
        Player online = Bukkit.getPlayerExact(targetName);
        if (online != null) {
            sender.sendMessage(ChatColor.GOLD + online.getName() + ChatColor.GREEN + " is currently " + ChatColor.AQUA + "online" + ChatColor.GREEN + "!");
            return true;
        }

        // Try to find offline player
        @SuppressWarnings("deprecation")
        OfflinePlayer offline = Bukkit.getOfflinePlayer(targetName);

        if (!offline.hasPlayedBefore()) {
            sender.sendMessage(ChatColor.RED + "Player " + ChatColor.GOLD + targetName + ChatColor.RED + " has never played on this server.");
            return true;
        }

        long lastPlayed = offline.getLastPlayed();
        if (lastPlayed == 0) {
            sender.sendMessage(ChatColor.RED + "Could not determine when " + ChatColor.GOLD + targetName + ChatColor.RED + " was last online.");
            return true;
        }

        Duration elapsed = Duration.between(Instant.ofEpochMilli(lastPlayed), Instant.now());
        String timeAgo = formatDuration(elapsed);

        sender.sendMessage(ChatColor.GOLD + offline.getName() + ChatColor.YELLOW + " was last seen " + ChatColor.WHITE + timeAgo + ChatColor.YELLOW + " ago.");
        return true;
    }

    private String formatDuration(Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (sb.length() == 0 || seconds > 0) sb.append(seconds).append("s");
        return sb.toString().trim();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) names.add(p.getName());
            }
            Collections.sort(names);
            return names;
        }
        return Collections.emptyList();
    }
}
