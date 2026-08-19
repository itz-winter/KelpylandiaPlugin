package com.kelpwing.kelpylandiaplugin.homes.commands;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import com.kelpwing.kelpylandiaplugin.homes.Home;
import com.kelpwing.kelpylandiaplugin.homes.HomeManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
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
 * /home [name] - Teleport to a home.
 * If no name is given, teleports to "home" or opens GUI if multiple homes exist.
 */
public class HomeCommand implements CommandExecutor, TabCompleter {

    private final KelpylandiaPlugin plugin;

    public HomeCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.getConfig().getBoolean("homes.enabled", true)) {
            sender.sendMessage(ChatColor.RED + "Homes are currently disabled.");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        if (!player.hasPermission("qol.homes.teleport")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use homes.");
            return true;
        }

        HomeManager homeManager = plugin.getHomeManager();

        // No args: if only one home, tp there; if multiple, open GUI
        if (args.length == 0) {
            List<Home> homes = homeManager.getHomeList(player.getUniqueId());
            if (homes.isEmpty()) {
                player.sendMessage(ChatColor.RED + "You don't have any homes set.");
                player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + "/sethome [name]" + ChatColor.GRAY + " to create one.");
                return true;
            }

            // If exactly one home, teleport there
            if (homes.size() == 1) {
                teleportToHome(player, homes.get(0));
                return true;
            }

            // If they have a "home" default, go there
            Home defaultHome = homeManager.getHome(player.getUniqueId(), "home");
            if (defaultHome != null) {
                teleportToHome(player, defaultHome);
                return true;
            }

            // Multiple homes, no default - open GUI
            plugin.getHomeGUI().openGUI(player);
            return true;
        }

        // With args: teleport to the named home (supports user:homename for admins)
        String homeName = args[0];

        // Check for admin syntax: user:homename
        if (homeName.contains(":") && player.hasPermission("qol.homes.others")) {
            String[] parts = homeName.split(":", 2);
            String targetName = parts[0];
            String targetHomeName = parts[1].isEmpty() ? "home" : parts[1];

            @SuppressWarnings("deprecation")
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetName);
            if (targetPlayer == null || (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline())) {
                player.sendMessage(ChatColor.RED + "Player " + ChatColor.GOLD + targetName + ChatColor.RED + " has never played on this server.");
                return true;
            }

            UUID targetUUID = targetPlayer.getUniqueId();
            Home home = homeManager.getHome(targetUUID, targetHomeName);
            if (home == null) {
                player.sendMessage(ChatColor.RED + "Player " + ChatColor.GOLD + targetName + ChatColor.RED + " doesn't have a home named " + ChatColor.GOLD + targetHomeName + ChatColor.RED + ".");
                // Show available homes for that player
                List<String> homeNames = new ArrayList<>(homeManager.getHomeNames(targetUUID));
                if (!homeNames.isEmpty()) {
                    player.sendMessage(ChatColor.GRAY + "Their homes: " + ChatColor.YELLOW + String.join(", ", homeNames));
                }
                return true;
            }

            teleportToHome(player, home);
            player.sendMessage(ChatColor.GRAY + "(Teleported to " + ChatColor.GOLD + targetName + ChatColor.GRAY + "'s home)");
            return true;
        }

        Home home = homeManager.getHome(player.getUniqueId(), homeName);
        if (home == null) {
            player.sendMessage(ChatColor.RED + "You don't have a home named " + ChatColor.GOLD + homeName + ChatColor.RED + ".");
            player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + "/homes" + ChatColor.GRAY + " to see your homes.");
            return true;
        }

        teleportToHome(player, home);
        return true;
    }

    private void teleportToHome(Player player, Home home) {
        Location location = home.getLocation();
        if (location == null) {
            player.sendMessage(ChatColor.RED + "The world for home " + ChatColor.GOLD + home.getName() + ChatColor.RED + " no longer exists.");
            return;
        }

        // Apply teleport cooldown check
        if (plugin.getTpaManager() != null && plugin.getTpaManager().isOnCooldown(player)) {
            long remaining = plugin.getTpaManager().getCooldownRemaining(player);
            player.sendMessage(ChatColor.RED + "You must wait " + ChatColor.GOLD +
                String.format("%.1f", remaining / 1000.0) + "s" + ChatColor.RED + " before teleporting again.");
            return;
        }

        player.teleport(location);
        player.sendMessage(ChatColor.GREEN + "Teleported to home " + ChatColor.GOLD + home.getName() + ChatColor.GREEN + "!");

        // Apply cooldown and invulnerability
        if (plugin.getTpaManager() != null) {
            plugin.getTpaManager().applyCooldown(player);
            plugin.getTpaManager().applyInvulnerability(player);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return Collections.emptyList();
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> suggestions = new ArrayList<>();

            // Own homes
            List<String> names = new ArrayList<>(plugin.getHomeManager().getHomeNames(player.getUniqueId()));
            for (String name : names) {
                if (name.toLowerCase().startsWith(partial)) suggestions.add(name);
            }

            // Admin syntax: user:homename
            if (player.hasPermission("qol.homes.others") && partial.contains(":")) {
                String[] parts = partial.split(":", 2);
                String targetName = parts[0];
                String homePartial = parts.length > 1 ? parts[1] : "";

                @SuppressWarnings("deprecation")
                OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetName);
                if (targetPlayer != null && (targetPlayer.hasPlayedBefore() || targetPlayer.isOnline())) {
                    List<String> targetHomes = new ArrayList<>(plugin.getHomeManager().getHomeNames(targetPlayer.getUniqueId()));
                    for (String h : targetHomes) {
                        if (h.toLowerCase().startsWith(homePartial)) {
                            suggestions.add(targetName + ":" + h);
                        }
                    }
                }
            } else if (player.hasPermission("qol.homes.others")) {
                // Suggest online player names with colon suffix
                for (Player p : Bukkit.getOnlinePlayers()) {
                    String name = p.getName().toLowerCase();
                    if (name.startsWith(partial)) {
                        suggestions.add(p.getName() + ":");
                    }
                }
            }

            return suggestions;
        }
        return Collections.emptyList();
    }
}
