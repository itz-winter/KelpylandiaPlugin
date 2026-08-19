package com.kelpwing.kelpylandiaplugin.warps.commands;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import com.kelpwing.kelpylandiaplugin.warps.WarpManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * /delwarp <name> - Delete a server warp (admin only).
 */
public class DelWarpCommand implements CommandExecutor, TabCompleter {

    private final KelpylandiaPlugin plugin;

    public DelWarpCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /delwarp <name>");
            return true;
        }

        String name = args[0];
        WarpManager wm = plugin.getWarpManager();

        if (!wm.warpExists(name)) {
            sender.sendMessage(ChatColor.RED + "Warp " + ChatColor.GOLD + name + ChatColor.RED + " does not exist.");
            return true;
        }

        wm.deleteWarp(name);
        sender.sendMessage(ChatColor.GREEN + "Warp " + ChatColor.GOLD + name + ChatColor.GREEN + " has been deleted.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> names = new ArrayList<>(plugin.getWarpManager().getWarpNames());
            names.removeIf(s -> !s.toLowerCase().startsWith(partial));
            Collections.sort(names);
            return names;
        }
        return Collections.emptyList();
    }
}
