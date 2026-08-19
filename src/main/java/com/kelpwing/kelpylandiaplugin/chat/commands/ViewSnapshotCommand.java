package com.kelpwing.kelpylandiaplugin.chat.commands;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import com.kelpwing.kelpylandiaplugin.chat.ItemDisplayManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

/**
 * Internal command executed when a player clicks an [item], [inv], or
 * [enderchest] component in chat. Opens the frozen snapshot inventory.
 * <p>
 * Command name: {@code qol:viewsnapshot} (no tab-complete, no manual use)
 */
public class ViewSnapshotCommand implements CommandExecutor {

    private final KelpylandiaPlugin plugin;

    public ViewSnapshotCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can view item snapshots.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Invalid snapshot link.");
            return true;
        }

        UUID snapshotId;
        try {
            snapshotId = UUID.fromString(args[0]);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "Invalid snapshot link.");
            return true;
        }

        ItemDisplayManager idm = plugin.getItemDisplayManager();
        if (idm == null) {
            sender.sendMessage(ChatColor.RED + "Item display is not enabled.");
            return true;
        }

        Inventory snapshot = idm.getSnapshot(snapshotId);
        if (snapshot == null) {
            sender.sendMessage(ChatColor.RED + "This item snapshot has expired.");
            return true;
        }

        ((Player) sender).openInventory(snapshot);
        return true;
    }
}
