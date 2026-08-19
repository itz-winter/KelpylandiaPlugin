package com.kelpwing.kelpylandiaplugin.commands;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import com.kelpwing.kelpylandiaplugin.utils.SpyManager;
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
 * /sudo <user> <command|message> - Force a player to run a command or send a chat message.
 * If the text starts with "/" it is treated as a command, otherwise as a chat message.
 */
public class SudoCommand implements CommandExecutor, TabCompleter {

    private final KelpylandiaPlugin plugin;

    public SudoCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /sudo <player> <command or message>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
            return true;
        }

        // Build the command/message string from remaining args
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) sb.append(" ");
            sb.append(args[i]);
        }
        String text = sb.toString();

        if (text.startsWith("/")) {
            // Run as command (strip the leading /)
            target.performCommand(text.substring(1));
            sender.sendMessage(ChatColor.GREEN + "Forced " + ChatColor.GOLD + target.getName() + ChatColor.GREEN + " to run: " + ChatColor.WHITE + text);
        } else {
            // Send as chat message
            target.chat(text);
            sender.sendMessage(ChatColor.GREEN + "Forced " + ChatColor.GOLD + target.getName() + ChatColor.GREEN + " to say: " + ChatColor.WHITE + text);
        }

        // Notify CommandSpy users about the sudo action
        SpyManager spyManager = plugin.getSpyManager();
        if (spyManager != null) {
            String senderName = sender instanceof Player ? ((Player) sender).getName() : "CONSOLE";
            String spyMsg = ChatColor.GRAY + "[" + ChatColor.RED + "CS" + ChatColor.GRAY + "] "
                    + ChatColor.DARK_RED + "[SUDO] " + ChatColor.GRAY
                    + senderName + " forced " + target.getName() + ": " + ChatColor.WHITE + text;
            for (UUID spyUUID : spyManager.getCommandSpies()) {
                Player spy = Bukkit.getPlayer(spyUUID);
                if (spy != null && spy.isOnline()) {
                    // Don't notify the sender themselves
                    if (sender instanceof Player && spy.getUniqueId().equals(((Player) sender).getUniqueId())) continue;
                    spy.sendMessage(spyMsg);
                }
            }
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
