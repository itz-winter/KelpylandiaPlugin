package com.kelpwing.kelpylandiaplugin.commands;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /cs or /commandspy - Toggle command spy (see all commands other players run).
 */
public class CommandSpyCommand implements CommandExecutor {

    private final KelpylandiaPlugin plugin;

    public CommandSpyCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        boolean enabled = plugin.getSpyManager().toggleCommandSpy(player.getUniqueId());
        if (enabled) {
            player.sendMessage(ChatColor.GREEN + "CommandSpy " + ChatColor.BOLD + "enabled" + ChatColor.GREEN + ". You will now see all commands other players run.");
        } else {
            player.sendMessage(ChatColor.YELLOW + "CommandSpy " + ChatColor.BOLD + "disabled" + ChatColor.YELLOW + ".");
        }
        return true;
    }
}
