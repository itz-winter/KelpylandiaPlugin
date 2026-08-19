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
 * /fly [user] - Toggle flight mode. Use * to target all online players.
 */
public class FlyCommand implements CommandExecutor, TabCompleter {

    private final KelpylandiaPlugin plugin;

    public FlyCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            // Self-toggle
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Console must specify a player: /fly <player>");
                return true;
            }
            toggleFly(player);
            return true;
        }

        // Target a player (or *)
        if (!sender.hasPermission("qol.fly.others")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to toggle flight for others.");
            return true;
        }

        if (args[0].equals("*")) {
            int count = 0;
            for (Player p : Bukkit.getOnlinePlayers()) {
                toggleFly(p);
                count++;
            }
            sender.sendMessage(ChatColor.GREEN + "Toggled flight for " + ChatColor.GOLD + count + ChatColor.GREEN + " player(s).");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
            return true;
        }

        toggleFly(target);
        if (!(sender instanceof Player p) || !p.equals(target)) {
            sender.sendMessage(ChatColor.GREEN + "Toggled flight for " + ChatColor.GOLD + target.getName() + ChatColor.GREEN + ".");
        }
        return true;
    }

    private void toggleFly(Player player) {
        boolean newState = !player.getAllowFlight();
        player.setAllowFlight(newState);
        if (!newState) player.setFlying(false);
        player.sendMessage(newState
            ? ChatColor.GREEN + "Flight " + ChatColor.BOLD + "enabled" + ChatColor.GREEN + "."
            : ChatColor.YELLOW + "Flight " + ChatColor.BOLD + "disabled" + ChatColor.YELLOW + ".");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("qol.fly.others")) {
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
