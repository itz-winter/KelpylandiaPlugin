package com.kelpwing.kelpylandiaplugin.moderation.commands;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import com.kelpwing.kelpylandiaplugin.moderation.FreezeManager;
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
 * /freeze <player> - Freeze a player in their current location.
 * Player is immune to everything while frozen. Relog will not unfreeze them.
 */
public class FreezeCommand implements CommandExecutor, TabCompleter {

    private final KelpylandiaPlugin plugin;

    public FreezeCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /freeze <player>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
            return true;
        }

        // Prevent self-freezing
        if (sender instanceof Player && target.getUniqueId().equals(((Player) sender).getUniqueId())) {
            sender.sendMessage(ChatColor.RED + "You cannot freeze yourself!");
            return true;
        }

        FreezeManager fm = plugin.getFreezeManager();
        if (fm.isFrozen(target.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + target.getName() + " is already frozen.");
            return true;
        }

        fm.freeze(target.getUniqueId());
        target.sendMessage(ChatColor.AQUA + "You have been frozen by a staff member.");
        sender.sendMessage(ChatColor.GREEN + "Froze " + ChatColor.GOLD + target.getName() + ChatColor.GREEN + ".");
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
