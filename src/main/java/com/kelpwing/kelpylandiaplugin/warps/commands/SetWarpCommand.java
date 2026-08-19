package com.kelpwing.kelpylandiaplugin.warps.commands;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import com.kelpwing.kelpylandiaplugin.warps.WarpManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * /setwarp <name> - Create or overwrite a server warp (admin only).
 */
public class SetWarpCommand implements CommandExecutor, TabCompleter {

    private final KelpylandiaPlugin plugin;

    public SetWarpCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Usage: /setwarp <name>");
            return true;
        }

        String name = args[0];

        // Validate name (alphanumeric + dashes/underscores only)
        if (!name.matches("[a-zA-Z0-9_-]+")) {
            player.sendMessage(ChatColor.RED + "Warp names can only contain letters, numbers, dashes, and underscores.");
            return true;
        }

        WarpManager wm = plugin.getWarpManager();
        boolean overwrite = wm.warpExists(name);
        wm.setWarp(name, player.getLocation());

        if (overwrite) {
            player.sendMessage(ChatColor.GREEN + "Warp " + ChatColor.GOLD + name + ChatColor.GREEN + " has been updated!");
        } else {
            player.sendMessage(ChatColor.GREEN + "Warp " + ChatColor.GOLD + name + ChatColor.GREEN + " has been created!");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            // Suggest existing warp names for overwriting
            String partial = args[0].toLowerCase();
            List<String> names = new ArrayList<>(plugin.getWarpManager().getWarpNames());
            names.removeIf(s -> !s.toLowerCase().startsWith(partial));
            Collections.sort(names);
            return names;
        }
        return Collections.emptyList();
    }
}
