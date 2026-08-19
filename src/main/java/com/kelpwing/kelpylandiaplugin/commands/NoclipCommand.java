package com.kelpwing.kelpylandiaplugin.commands;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /noclip [player] - Toggle noclip mode.
 * When noclip is enabled, double-shifting cycles the player's gamemode:
 *   current → SPECTATOR → current (same mechanic as the vanish double-shift).
 * This lets the player clip through blocks by switching to spectator mode,
 * then double-shift again to return to their previous gamemode.
 */
public class NoclipCommand implements CommandExecutor, TabCompleter, Listener {

    private final KelpylandiaPlugin plugin;

    /** Players who have noclip mode toggled on. */
    private final Set<UUID> noclipEnabled = ConcurrentHashMap.newKeySet();
    /** Stores the gamemode the player had before switching to spectator. */
    private final Map<UUID, GameMode> savedGameMode = new ConcurrentHashMap<>();
    /** Tracks the last time each player pressed shift (for double-shift detection). */
    private final Map<UUID, Long> lastSneakTime = new ConcurrentHashMap<>();
    /** Double-shift threshold in milliseconds. */
    private static final long DOUBLE_SHIFT_MS = 400;

    public NoclipCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player target;

        if (args.length > 0) {
            if (!sender.hasPermission("qol.noclip.others")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to use noclip on others.");
                return true;
            }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Usage: /noclip <player>");
                return true;
            }
            target = (Player) sender;
        }

        UUID uuid = target.getUniqueId();

        if (noclipEnabled.contains(uuid)) {
            // Disable noclip
            disableNoclip(target);
            target.sendMessage(ChatColor.RED + "Noclip disabled.");
            if (!target.equals(sender)) {
                sender.sendMessage(ChatColor.RED + "Noclip disabled for " + target.getName() + ".");
            }
        } else {
            // Enable noclip
            noclipEnabled.add(uuid);
            target.sendMessage(ChatColor.GREEN + "Noclip enabled! " + ChatColor.GRAY + "Double-shift to toggle spectator mode.");
            if (!target.equals(sender)) {
                sender.sendMessage(ChatColor.GREEN + "Noclip enabled for " + target.getName() + ".");
            }
        }

        return true;
    }

    /**
     * Disable noclip for a player, restoring their gamemode if they're in spectator.
     */
    public void disableNoclip(Player player) {
        UUID uuid = player.getUniqueId();

        // If currently in spectator from noclip, restore their previous gamemode
        if (player.getGameMode() == GameMode.SPECTATOR && savedGameMode.containsKey(uuid)) {
            GameMode prev = savedGameMode.remove(uuid);
            player.setGameMode(prev);
        }

        noclipEnabled.remove(uuid);
        savedGameMode.remove(uuid);
        lastSneakTime.remove(uuid);
    }

    public boolean hasNoclip(UUID uuid) {
        return noclipEnabled.contains(uuid);
    }

    // Double-shift listener 

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!noclipEnabled.contains(uuid)) return;

        // Only detect shift-down
        if (!event.isSneaking()) return;

        long now = System.currentTimeMillis();
        Long last = lastSneakTime.get(uuid);
        lastSneakTime.put(uuid, now);

        if (last != null && (now - last) <= DOUBLE_SHIFT_MS) {
            // Double-shift detected - reset so triple-shift doesn't re-trigger
            lastSneakTime.remove(uuid);

            if (player.getGameMode() == GameMode.SPECTATOR) {
                // Restore saved gamemode
                GameMode restored = savedGameMode.getOrDefault(uuid, GameMode.CREATIVE);
                savedGameMode.remove(uuid);
                player.setGameMode(restored);
                player.sendMessage(ChatColor.GREEN + "Returned to " + ChatColor.GOLD + restored.name() + ChatColor.GREEN + " mode.");
            } else {
                // Save current gamemode and switch to spectator
                savedGameMode.put(uuid, player.getGameMode());
                player.setGameMode(GameMode.SPECTATOR);
                player.sendMessage(ChatColor.LIGHT_PURPLE + "Switched to " + ChatColor.GOLD + "SPECTATOR"
                        + ChatColor.LIGHT_PURPLE + " mode. Double-shift again to return.");
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (noclipEnabled.contains(uuid)) {
            disableNoclip(player);
        }
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
