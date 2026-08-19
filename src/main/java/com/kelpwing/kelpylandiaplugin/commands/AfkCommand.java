package com.kelpwing.kelpylandiaplugin.commands;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import com.kelpwing.kelpylandiaplugin.utils.AfkManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /afk - Toggle your AFK status.
 */
public class AfkCommand implements CommandExecutor {

    private final KelpylandiaPlugin plugin;

    public AfkCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        AfkManager afkManager = plugin.getAfkManager();
        if (afkManager == null) {
            player.sendMessage(ChatColor.RED + "AFK system is not enabled.");
            return true;
        }

        afkManager.toggleAfk(player);
        return true;
    }
}
