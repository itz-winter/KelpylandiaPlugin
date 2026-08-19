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
import java.util.UUID;

/**
 * /unfreeze <player> - Unfreeze a frozen player.
 */
public class UnfreezeCommand implements CommandExecutor, TabCompleter {

    private final KelpylandiaPlugin plugin;

    public UnfreezeCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /unfreeze <player>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
            return true;
        }

        FreezeManager fm = plugin.getFreezeManager();
        if (!fm.isFrozen(target.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + target.getName() + " is not frozen.");
            return true;
        }

        fm.unfreeze(target.getUniqueId());
        target.sendMessage(ChatColor.GREEN + "You have been unfrozen.");
        sender.sendMessage(ChatColor.GREEN + "Unfroze " + ChatColor.GOLD + target.getName() + ChatColor.GREEN + ".");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> names = new ArrayList<>();
            FreezeManager fm = plugin.getFreezeManager();
            for (UUID uuid : fm.getFrozenPlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.getName().toLowerCase().startsWith(prefix)) {
                    names.add(p.getName());
                }
            }
            Collections.sort(names);
            return names;
        }
        return Collections.emptyList();
    }
}
