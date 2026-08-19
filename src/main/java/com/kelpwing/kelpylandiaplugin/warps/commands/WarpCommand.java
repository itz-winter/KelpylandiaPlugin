package com.kelpwing.kelpylandiaplugin.warps.commands;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import com.kelpwing.kelpylandiaplugin.warps.WarpManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * /warp <name> - Teleport to a server warp.
 */
public class WarpCommand implements CommandExecutor, TabCompleter {

    private final KelpylandiaPlugin plugin;

    public WarpCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        WarpManager wm = plugin.getWarpManager();

        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Usage: /warp <name>");
            player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + "/warps" + ChatColor.GRAY + " to see available warps.");
            return true;
        }

        String name = args[0];
        Location loc = wm.getWarp(name);
        if (loc == null) {
            player.sendMessage(ChatColor.RED + "Warp " + ChatColor.GOLD + name + ChatColor.RED + " does not exist.");
            return true;
        }

        player.teleport(loc);
        player.sendMessage(ChatColor.GREEN + "Warped to " + ChatColor.GOLD + name + ChatColor.GREEN + "!");
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
