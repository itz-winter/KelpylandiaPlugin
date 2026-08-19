package com.kelpwing.kelpylandiaplugin.commands;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * /r <message> - Reply to the last player who whispered you (or you whispered to).
 * Also works from console.
 */
public class ReplyCommand implements CommandExecutor {

    private final KelpylandiaPlugin plugin;

    public ReplyCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MsgCommand msgCmd = plugin.getMsgCommand();
        if (msgCmd == null) {
            sender.sendMessage(ChatColor.RED + "Messaging system is not enabled.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /r <message>");
            return true;
        }

        UUID senderUUID = (sender instanceof Player p) ? p.getUniqueId() : MsgCommand.CONSOLE_UUID;

        UUID lastUUID = msgCmd.getLastConversation(senderUUID);
        if (lastUUID == null) {
            sender.sendMessage(ChatColor.RED + "You have nobody to reply to.");
            return true;
        }

        // Resolve the target
        CommandSender target;
        if (lastUUID.equals(MsgCommand.CONSOLE_UUID)) {
            target = Bukkit.getConsoleSender();
        } else {
            Player targetPlayer = Bukkit.getPlayer(lastUUID);
            if (targetPlayer == null || !targetPlayer.isOnline()) {
                sender.sendMessage(ChatColor.RED + "That player is no longer online.");
                return true;
            }
            target = targetPlayer;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(args[i]);
        }

        msgCmd.sendPrivateMessage(sender, target, sb.toString());
        return true;
    }
}
