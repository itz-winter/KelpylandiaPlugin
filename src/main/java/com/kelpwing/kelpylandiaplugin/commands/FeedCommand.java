package com.kelpwing.kelpylandiaplugin.commands;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
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

/**
 * /feed [user] - Set a player's food level to 20 (full). Use * for all.
 */
public class FeedCommand implements CommandExecutor, TabCompleter {

    private final KelpylandiaPlugin plugin;

    public FeedCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Console must specify a player: /feed <player>");
                return true;
            }
            feedPlayer(player);
            player.sendMessage(ChatColor.GREEN + "You have been fed!");
            return true;
        }

        if (!sender.hasPermission("qol.feed.others")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to feed others.");
            return true;
        }

        if (args[0].equals("*")) {
            int count = 0;
            for (Player p : Bukkit.getOnlinePlayers()) {
                feedPlayer(p);
                p.sendMessage(ChatColor.GREEN + "You have been fed!");
                count++;
            }
            sender.sendMessage(ChatColor.GREEN + "Fed " + ChatColor.GOLD + count + ChatColor.GREEN + " player(s).");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
            return true;
        }

        feedPlayer(target);
        target.sendMessage(ChatColor.GREEN + "You have been fed!");
        if (!(sender instanceof Player p) || !p.equals(target)) {
            sender.sendMessage(ChatColor.GREEN + "Fed " + ChatColor.GOLD + target.getName() + ChatColor.GREEN + ".");
        }
        return true;
    }

    private void feedPlayer(Player player) {
        player.setFoodLevel(20);
        player.setSaturation(20f);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("qol.feed.others")) {
            String prefix = args[0].toLowerCase();
            List<String> names = new ArrayList<>();
            names.add("*");
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) names.add(p.getName());
            }
            Collections.sort(names);
            return names;
        }
        return Collections.emptyList();
    }
}
