package com.kelpwing.kelpylandiaplugin.commands;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import com.kelpwing.kelpylandiaplugin.utils.SpyManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * /runat &lt;player|Console&gt; &lt;command...&gt;
 * <p>
 * Forces a command to be executed as the target player with a one-time
 * permission bypass - the target temporarily receives {@code *} so the
 * command succeeds regardless of their actual permissions.
 * <p>
 * The special target name {@code Console} dispatches the command from the
 * server console instead, which allows full control without needing a
 * player online (useful from Discord console or server terminal).
 * <p>
 * <b>Default access:</b> Console only.  Players must have
 * {@code kelpylandia.runat} to use this command in-game.
 */
public class RunAtCommand implements CommandExecutor, TabCompleter {

    private final KelpylandiaPlugin plugin;

    public RunAtCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Console can always use this. Players need the permission.
        if (sender instanceof Player && !sender.hasPermission("qol.runat")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /runat <player|Console> <command...>");
            return true;
        }

        String targetName = args[0];

        // Build the command string from remaining args (strip leading / if present)
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) sb.append(" ");
            sb.append(args[i]);
        }
        String commandStr = sb.toString();
        if (commandStr.startsWith("/")) {
            commandStr = commandStr.substring(1);
        }

        // --- Special target: "Console" - dispatch as the server console ---
        if (targetName.equalsIgnoreCase("console")) {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandStr);
                sender.sendMessage(ChatColor.GREEN + "Executed as " + ChatColor.GOLD + "Console"
                        + ChatColor.GREEN + ": " + ChatColor.WHITE + "/" + commandStr);
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "Error executing command as Console: " + e.getMessage());
            }
            notifySpies(sender, "Console", commandStr);
            return true;
        }

        // --- Normal target: online player ---
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + targetName);
            sender.sendMessage(ChatColor.GRAY + "Use \"Console\" to run as the server console.");
            return true;
        }

        // Attach a temporary * permission so the command succeeds
        PermissionAttachment attachment = target.addAttachment(plugin);
        attachment.setPermission("*", true);
        try {
            target.performCommand(commandStr);
            sender.sendMessage(ChatColor.GREEN + "Executed as " + ChatColor.GOLD + target.getName()
                    + ChatColor.GREEN + " (with bypass): " + ChatColor.WHITE + "/" + commandStr);
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error executing command as " + target.getName() + ": " + e.getMessage());
        } finally {
            // Always remove the temporary permission
            target.removeAttachment(attachment);
        }

        notifySpies(sender, target.getName(), commandStr);
        return true;
    }

    /**
     * Notify CommandSpy watchers about this runat action.
     */
    private void notifySpies(CommandSender sender, String targetName, String commandStr) {
        SpyManager spyManager = plugin.getSpyManager();
        if (spyManager == null) return;

        String senderName = sender instanceof Player ? ((Player) sender).getName() : "CONSOLE";
        String spyMsg = ChatColor.GRAY + "[" + ChatColor.RED + "CS" + ChatColor.GRAY + "] "
                + ChatColor.DARK_PURPLE + "[RUNAT] " + ChatColor.GRAY
                + senderName + " ran as " + targetName + ": " + ChatColor.WHITE + "/" + commandStr;
        for (UUID spyUUID : spyManager.getCommandSpies()) {
            Player spy = Bukkit.getPlayer(spyUUID);
            if (spy != null && spy.isOnline()) {
                if (sender instanceof Player && spy.getUniqueId().equals(((Player) sender).getUniqueId())) continue;
                spy.sendMessage(spyMsg);
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> names = new ArrayList<>();
            // "Console" is always a valid target
            if ("console".startsWith(prefix)) {
                names.add("Console");
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) names.add(p.getName());
            }
            Collections.sort(names);
            return names;
        }
        // No further tab completion - the command itself can be anything
        return Collections.emptyList();
    }
}
