package com.kelpwing.kelpylandiaplugin.commands;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * /flyspeed <speed> [player] - Set fly speed (0-10, default 1).
 * /walkspeed <speed> [player] - Set walk speed (0-10, default 1).
 *
 * Internally Bukkit uses -1.0 to 1.0 but we expose a 0-10 scale like EssentialsX.
 * Default fly speed = 1 (0.1 internal), default walk speed = 2 (0.2 internal).
 */
public class FlySpeedCommand implements CommandExecutor, TabCompleter {

    private final KelpylandiaPlugin plugin;

    public FlySpeedCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean isWalkSpeed = label.toLowerCase().contains("walk");

        if (args.length < 1) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Usage: /" + label + " <speed> [player]");
                return true;
            }
            // Show current speed
            float current = isWalkSpeed ? player.getWalkSpeed() : player.getFlySpeed();
            float displayVal = current * 10f;
            sender.sendMessage(ChatColor.GREEN + "Your current " + (isWalkSpeed ? "walk" : "fly") + " speed: " + ChatColor.GOLD + String.format("%.1f", displayVal));
            return true;
        }

        // Parse speed value
        float speed;
        try {
            speed = Float.parseFloat(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid speed value. Must be a number between 0 and 10.");
            return true;
        }

        if (speed < 0 || speed > 10) {
            sender.sendMessage(ChatColor.RED + "Speed must be between 0 and 10.");
            return true;
        }

        // Convert 0-10 scale to Bukkit's -1.0 to 1.0 scale
        float internalSpeed = speed / 10f;

        // Determine target player
        Player target;
        if (args.length >= 2) {
            if (!sender.hasPermission("qol.flyspeed.others")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to change others' speed.");
                return true;
            }
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Console must specify a player: /" + label + " <speed> <player>");
                return true;
            }
            target = (Player) sender;
        }

        // Apply speed
        if (isWalkSpeed) {
            target.setWalkSpeed(internalSpeed);
        } else {
            target.setFlySpeed(internalSpeed);
        }

        String speedType = isWalkSpeed ? "Walk" : "Fly";
        target.sendMessage(ChatColor.GREEN + speedType + " speed set to " + ChatColor.GOLD + String.format("%.1f", speed) + ChatColor.GREEN + ".");
        if (!target.equals(sender)) {
            sender.sendMessage(ChatColor.GREEN + "Set " + ChatColor.GOLD + target.getName() + ChatColor.GREEN + "'s " + speedType.toLowerCase() + " speed to " + ChatColor.GOLD + String.format("%.1f", speed) + ChatColor.GREEN + ".");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
        }
        if (args.length == 2 && sender.hasPermission("qol.flyspeed.others")) {
            String prefix = args[1].toLowerCase();
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
