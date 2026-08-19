package com.kelpwing.kelpylandiaplugin.commands;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import com.kelpwing.kelpylandiaplugin.utils.NickManager;
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
 * /nick [nickname]          - Set your nickname, or reset if no nickname given.
 * /nick <player> [nickname] - Set/reset another player's nickname (requires kelpylandia.nickname.others).
 *
 * Supports {@code &} color codes in nicknames.
 */
public class NickCommand implements CommandExecutor, TabCompleter {

    private final KelpylandiaPlugin plugin;

    public NickCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        NickManager nickManager = plugin.getNickManager();

        // --- No arguments: reset own nickname ---
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Usage: /nick <player> [nickname]");
                return true;
            }
            if (!player.hasPermission("qol.nickname")) {
                player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                return true;
            }
            nickManager.removeNickname(player);
            player.sendMessage(ChatColor.GREEN + "Your nickname has been reset.");
            return true;
        }

        // --- One argument: could be setting own nick, or admin resetting another player ---
        if (args.length == 1) {
            // Check if the argument matches an online player AND the sender has the admin perm
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target != null && sender.hasPermission("qol.nickname.others") && !target.equals(sender)) {
                // Admin reset another player's nickname
                nickManager.removeNickname(target);
                sender.sendMessage(ChatColor.GREEN + "Reset " + target.getName() + "'s nickname.");
                target.sendMessage(ChatColor.GREEN + "Your nickname has been reset by an admin.");
                return true;
            }

            // Otherwise treat it as setting your own nickname
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Usage: /nick <player> <nickname>");
                return true;
            }
            if (!player.hasPermission("qol.nickname")) {
                player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                return true;
            }

            String nick = args[0];
            nickManager.setNickname(player, nick);
            player.sendMessage(ChatColor.GREEN + "Nickname set to: " + ChatColor.translateAlternateColorCodes('&', nick));
            return true;
        }

        // --- Two+ arguments: admin setting another player's nickname ---
        if (!sender.hasPermission("qol.nickname.others")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to change other players' nicknames.");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
            return true;
        }

        // Join remaining args as the nickname (supports spaces if needed)
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) sb.append(' ');
            sb.append(args[i]);
        }
        String nick = sb.toString();

        nickManager.setNickname(target, nick);
        sender.sendMessage(ChatColor.GREEN + "Set " + target.getName() + "'s nickname to: " + ChatColor.translateAlternateColorCodes('&', nick));
        target.sendMessage(ChatColor.GREEN + "Your nickname has been set to: " + ChatColor.translateAlternateColorCodes('&', nick));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("qol.nickname.others")) {
            // Suggest online player names for admins
            List<String> names = new ArrayList<>();
            String prefix = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) {
                    names.add(p.getName());
                }
            }
            Collections.sort(names);
            return names;
        }
        return Collections.emptyList();
    }
}
