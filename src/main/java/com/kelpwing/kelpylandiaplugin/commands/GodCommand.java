package com.kelpwing.kelpylandiaplugin.commands;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /god [user] - Toggle god mode (invincibility). Use * to target all online players.
 * The set of god-mode players is exposed so GodListener can check it.
 */
public class GodCommand implements CommandExecutor, TabCompleter {

    private final KelpylandiaPlugin plugin;
    private final Set<UUID> godPlayers = ConcurrentHashMap.newKeySet();

    public GodCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Console must specify a player: /god <player>");
                return true;
            }
            toggleGod(player);
            return true;
        }

        if (!sender.hasPermission("qol.god.others")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to toggle god mode for others.");
            return true;
        }

        if (args[0].equals("*")) {
            int count = 0;
            for (Player p : Bukkit.getOnlinePlayers()) {
                toggleGod(p);
                count++;
            }
            sender.sendMessage(ChatColor.GREEN + "Toggled god mode for " + ChatColor.GOLD + count + ChatColor.GREEN + " player(s).");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
            return true;
        }

        toggleGod(target);
        if (!(sender instanceof Player p) || !p.equals(target)) {
            sender.sendMessage(ChatColor.GREEN + "Toggled god mode for " + ChatColor.GOLD + target.getName() + ChatColor.GREEN + ".");
        }
        return true;
    }

    private void toggleGod(Player player) {
        UUID uuid = player.getUniqueId();
        if (godPlayers.contains(uuid)) {
            godPlayers.remove(uuid);
            player.setInvulnerable(false);
            player.sendMessage(ChatColor.YELLOW + "God mode " + ChatColor.BOLD + "disabled" + ChatColor.YELLOW + ".");
            persistGod(uuid, false);
        } else {
            godPlayers.add(uuid);
            player.setInvulnerable(true);
            player.sendMessage(ChatColor.GREEN + "God mode " + ChatColor.BOLD + "enabled" + ChatColor.GREEN + ". You are now invincible.");
            persistGod(uuid, true);
        }
    }

    private void persistGod(UUID uuid, boolean value) {
        if (plugin.getPlayerStateManager() != null) {
            plugin.getPlayerStateManager().saveToggle(uuid, "god", value);
        }
    }

    public boolean isGod(Player player) {
        return godPlayers.contains(player.getUniqueId());
    }

    public void removePlayer(UUID uuid) {
        godPlayers.remove(uuid);
    }

    /** Restore god state without sending messages (used by state persistence on login). */
    public void restoreGod(UUID uuid) {
        godPlayers.add(uuid);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("qol.god.others")) {
            String prefix = args[0].toLowerCase();
            List<String> names = new ArrayList<>();
            names.add("*");
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) names.add(p.getName());
            }
            Collections.sort(names);
            return names;
        }
        return Collections.emptyList();
    }
}
