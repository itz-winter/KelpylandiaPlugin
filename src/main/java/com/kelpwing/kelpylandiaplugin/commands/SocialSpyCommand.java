package com.kelpwing.kelpylandiaplugin.commands;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /ss or /socialspy - Toggle social spy (see all whispers and channel messages).
 */
public class SocialSpyCommand implements CommandExecutor {

    private final KelpylandiaPlugin plugin;

    public SocialSpyCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        boolean enabled = plugin.getSpyManager().toggleSocialSpy(player.getUniqueId());
        if (enabled) {
            player.sendMessage(ChatColor.GREEN + "SocialSpy " + ChatColor.BOLD + "enabled" + ChatColor.GREEN + ". You will now see all private messages and channel chat.");
        } else {
            player.sendMessage(ChatColor.YELLOW + "SocialSpy " + ChatColor.BOLD + "disabled" + ChatColor.YELLOW + ".");
        }
        return true;
    }
}
