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
import java.util.concurrent.ThreadLocalRandom;

/**
 * /smite <player> - Strike lightning on a player (like EssentialsX).
 * A random message is chosen from the "smite.messages" config list and broadcast.
 * The {player} placeholder is replaced with the target's display name.
 */
public class SmiteCommand implements CommandExecutor, TabCompleter {

    private final KelpylandiaPlugin plugin;

    public SmiteCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /smite <player>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
            return true;
        }

        // Strike lightning at the target's location (visual + damage)
        target.getWorld().strikeLightning(target.getLocation());

        // Pick a random smite message from config and broadcast it
        List<String> messages = plugin.getConfig().getStringList("smite.messages");
        if (messages != null && !messages.isEmpty()) {
            String raw = messages.get(ThreadLocalRandom.current().nextInt(messages.size()));
            String formatted = ChatColor.translateAlternateColorCodes('&',
                    raw.replace("{player}", target.getDisplayName())
                       .replace("{sender}", sender.getName()));
            Bukkit.broadcastMessage(formatted);
        }

        // Notify the sender
        if (!(sender instanceof Player) || !((Player) sender).equals(target)) {
            sender.sendMessage(ChatColor.GOLD + "You smote " + target.getName() + "!");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) names.add(p.getName());
            }
            Collections.sort(names);
            return names;
        }
        return Collections.emptyList();
    }
}
