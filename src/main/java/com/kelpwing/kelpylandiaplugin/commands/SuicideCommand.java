package com.kelpwing.kelpylandiaplugin.commands;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import com.kelpwing.kelpylandiaplugin.utils.DeathMessagesManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /suicide - Kill yourself with a custom death message from deathmessages.yml.
 * The player is marked for a custom death message, then killed. The actual
 * message replacement happens in {@link DeathMessagesManager}'s PlayerDeathEvent
 * listener, so other plugins and Paper pick it up through the normal event chain.
 */
public class SuicideCommand implements CommandExecutor {

    private final KelpylandiaPlugin plugin;

    public SuicideCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        // Mark this player so the death event listener replaces the vanilla message
        DeathMessagesManager dmm = plugin.getDeathMessagesManager();
        if (dmm != null) {
            dmm.markSuicide(player.getUniqueId());
        }

        player.setHealth(0);
        return true;
    }
}
