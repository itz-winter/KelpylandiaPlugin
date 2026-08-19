package com.kelpwing.kelpylandiaplugin.commands;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import com.kelpwing.kelpylandiaplugin.utils.VersionHelper;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * /heal [user] - Heal a player to full health + remove fire. Use * for all.
 */
public class HealCommand implements CommandExecutor, TabCompleter {

    private final KelpylandiaPlugin plugin;

    public HealCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Console must specify a player: /heal <player>");
                return true;
            }
            healPlayer(player);
            player.sendMessage(ChatColor.GREEN + "You have been healed!");
            return true;
        }

        if (!sender.hasPermission("qol.heal.others")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to heal others.");
            return true;
        }

        if (args[0].equals("*")) {
            int count = 0;
            for (Player p : Bukkit.getOnlinePlayers()) {
                healPlayer(p);
                p.sendMessage(ChatColor.GREEN + "You have been healed!");
                count++;
            }
            sender.sendMessage(ChatColor.GREEN + "Healed " + ChatColor.GOLD + count + ChatColor.GREEN + " player(s).");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
            return true;
        }

        healPlayer(target);
        target.sendMessage(ChatColor.GREEN + "You have been healed!");
        if (!(sender instanceof Player p) || !p.equals(target)) {
            sender.sendMessage(ChatColor.GREEN + "Healed " + ChatColor.GOLD + target.getName() + ChatColor.GREEN + ".");
        }
        return true;
    }

    private void healPlayer(Player player) {
        double maxHealth = 20.0;
        Attribute maxHealthAttr = VersionHelper.getMaxHealthAttribute();
        if (maxHealthAttr != null && player.getAttribute(maxHealthAttr) != null) {
            maxHealth = player.getAttribute(maxHealthAttr).getValue();
        }
        player.setHealth(maxHealth);
        player.setFireTicks(0);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("qol.heal.others")) {
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
