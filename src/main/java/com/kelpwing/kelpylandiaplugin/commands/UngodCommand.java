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
 * /ungod [player] - Remove god mode (invincibility) from a player.
 * Without arguments, removes god mode from the sender.
 * Use * to target all online players.
 */
public class UngodCommand implements CommandExecutor, TabCompleter {

    private final KelpylandiaPlugin plugin;

    public UngodCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        GodCommand godCommand = plugin.getGodCommand();
        if (godCommand == null) {
            sender.sendMessage(ChatColor.RED + "God mode is not enabled.");
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Console must specify a player: /ungod <player>");
                return true;
            }
            removeGod(player, sender);
            return true;
        }

        if (!sender.hasPermission("qol.god.others")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to remove god mode from others.");
            return true;
        }

        if (args[0].equals("*")) {
            int count = 0;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (godCommand.isGod(p)) {
                    removeGod(p, sender);
                    count++;
                }
            }
            sender.sendMessage(ChatColor.GREEN + "Removed god mode from " + ChatColor.GOLD + count + ChatColor.GREEN + " player(s).");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
            return true;
        }

        if (!godCommand.isGod(target)) {
            sender.sendMessage(ChatColor.YELLOW + target.getName() + " is not in god mode.");
            return true;
        }

        removeGod(target, sender);
        if (!(sender instanceof Player p) || !p.equals(target)) {
            sender.sendMessage(ChatColor.GREEN + "Removed god mode from " + ChatColor.GOLD + target.getName() + ChatColor.GREEN + ".");
        }
        return true;
    }

    private void removeGod(Player player, CommandSender sender) {
        GodCommand godCommand = plugin.getGodCommand();
        if (!godCommand.isGod(player)) {
            if (sender.equals(player)) {
                player.sendMessage(ChatColor.YELLOW + "You are not in god mode.");
            }
            return;
        }
        godCommand.removePlayer(player.getUniqueId());
        player.setInvulnerable(false);
        player.sendMessage(ChatColor.YELLOW + "God mode " + ChatColor.BOLD + "disabled" + ChatColor.YELLOW + ".");

        // Persist god=false to disk so state survives a crash
        if (plugin.getPlayerStateManager() != null) {
            plugin.getPlayerStateManager().saveToggle(player.getUniqueId(), "god", false);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("qol.god.others")) {
            GodCommand godCommand = plugin.getGodCommand();
            if (godCommand == null) return Collections.emptyList();

            String prefix = args[0].toLowerCase();
            List<String> names = new ArrayList<>();
            names.add("*");
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (godCommand.isGod(p) && p.getName().toLowerCase().startsWith(prefix)) {
                    names.add(p.getName());
                }
            }
            Collections.sort(names);
            return names;
        }
        return Collections.emptyList();
    }
}
