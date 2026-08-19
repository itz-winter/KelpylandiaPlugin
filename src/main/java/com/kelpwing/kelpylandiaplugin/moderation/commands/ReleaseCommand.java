package com.kelpwing.kelpylandiaplugin.moderation.commands;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import com.kelpwing.kelpylandiaplugin.moderation.JailManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * /release <player> [reason] - Release a jailed player.
 */
public class ReleaseCommand implements CommandExecutor, TabCompleter {

    private final KelpylandiaPlugin plugin;

    public ReleaseCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /release <player> [reason]");
            return true;
        }

        // Find the player (online or by name in jail data)
        JailManager jm = plugin.getJailManager();
        Player target = Bukkit.getPlayerExact(args[0]);
        UUID targetUUID = null;
        String targetName = args[0];

        if (target != null) {
            targetUUID = target.getUniqueId();
            targetName = target.getName();
        } else {
            // Search jail data for offline player
            for (var entry : jm.getJailedPlayers().entrySet()) {
                if (entry.getValue().getPlayerName().equalsIgnoreCase(args[0])) {
                    targetUUID = entry.getKey();
                    targetName = entry.getValue().getPlayerName();
                    break;
                }
            }
        }

        if (targetUUID == null || !jm.isJailed(targetUUID)) {
            sender.sendMessage(ChatColor.RED + targetName + " is not jailed.");
            return true;
        }

        // Build reason
        String reason = null;
        if (args.length > 1) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                if (i > 1) sb.append(" ");
                sb.append(args[i]);
            }
            reason = sb.toString();
        }

        String staffName = sender instanceof Player ? sender.getName() : "Console";
        jm.releasePlayer(targetUUID, staffName, reason);

        sender.sendMessage(ChatColor.GREEN + "Released " + ChatColor.GOLD + targetName + ChatColor.GREEN + " from jail.");

        Bukkit.broadcastMessage(ChatColor.GREEN + "[Jail] " + ChatColor.GOLD + targetName +
                ChatColor.GREEN + " has been released from jail by " + ChatColor.GOLD + staffName + ChatColor.GREEN + ".");

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> names = new ArrayList<>();
            // Suggest jailed player names
            for (JailManager.JailEntry entry : plugin.getJailManager().getJailedPlayers().values()) {
                if (entry.getPlayerName().toLowerCase().startsWith(prefix)) {
                    names.add(entry.getPlayerName());
                }
            }
            Collections.sort(names);
            return names;
        }
        return Collections.emptyList();
    }
}
