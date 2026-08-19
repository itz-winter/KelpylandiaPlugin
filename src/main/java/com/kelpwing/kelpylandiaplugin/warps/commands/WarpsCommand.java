package com.kelpwing.kelpylandiaplugin.warps.commands;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import com.kelpwing.kelpylandiaplugin.warps.WarpManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Set;

/**
 * /warps - List all available server warps.
 */
public class WarpsCommand implements CommandExecutor {

    private final KelpylandiaPlugin plugin;

    public WarpsCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        WarpManager wm = plugin.getWarpManager();
        Set<String> names = wm.getWarpNames();

        if (names.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "There are no warps set.");
            return true;
        }

        sender.sendMessage(ChatColor.GREEN + "=== Server Warps (" + names.size() + ") ===");
        for (String name : names) {
            sender.sendMessage(ChatColor.GRAY + " - " + ChatColor.GOLD + name);
        }
        sender.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + "/warp <name>" + ChatColor.GRAY + " to teleport.");
        return true;
    }
}
