package com.kelpwing.kelpylandiaplugin.homes.commands;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import com.kelpwing.kelpylandiaplugin.homes.HomeManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /renhome <oldName> <newName> - Rename an existing home.
 * Aliases: /renamehome
 */
public class RenameHomeCommand implements CommandExecutor, TabCompleter {

    private final KelpylandiaPlugin plugin;

    public RenameHomeCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;
        HomeManager homeManager = plugin.getHomeManager();
        if (homeManager == null) {
            player.sendMessage(ChatColor.RED + "The homes system is disabled.");
            return true;
        }
        if (!player.hasPermission("qol.homes.rename")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to rename homes.");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /" + label + " <currentName> <newName>");
            return true;
        }

        String oldName = args[0];
        String newName = args[1];

        // Validate new name length and characters
        if (newName.length() > 32) {
            player.sendMessage(ChatColor.RED + "Home name cannot be longer than 32 characters.");
            return true;
        }
        if (!newName.matches("[a-zA-Z0-9_\\-]+")) {
            player.sendMessage(ChatColor.RED + "Home name can only contain letters, numbers, underscores, and hyphens.");
            return true;
        }

        if (!homeManager.hasHome(player.getUniqueId(), oldName)) {
            player.sendMessage(ChatColor.RED + "You don't have a home named " + ChatColor.GOLD + oldName + ChatColor.RED + ".");
            return true;
        }

        if (homeManager.hasHome(player.getUniqueId(), newName) && !oldName.equalsIgnoreCase(newName)) {
            player.sendMessage(ChatColor.RED + "You already have a home named " + ChatColor.GOLD + newName + ChatColor.RED + ".");
            return true;
        }

        boolean success = homeManager.renameHome(player.getUniqueId(), oldName, newName);
        if (success) {
            player.sendMessage(ChatColor.GREEN + "Home " + ChatColor.GOLD + oldName
                    + ChatColor.GREEN + " renamed to " + ChatColor.GOLD + newName + ChatColor.GREEN + ".");
        } else {
            player.sendMessage(ChatColor.RED + "Failed to rename home. Please try again.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return new ArrayList<>();
        Player player = (Player) sender;
        HomeManager homeManager = plugin.getHomeManager();
        if (homeManager == null) return new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return homeManager.getHomeNames(player.getUniqueId()).stream()
                    .filter(n -> n.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
