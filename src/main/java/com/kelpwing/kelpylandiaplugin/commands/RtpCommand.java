package com.kelpwing.kelpylandiaplugin.commands;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * /rtp - Randomly teleport within a configurable radius around spawn.
 * Checks for safe locations (not underground, not in water - both toggleable).
 */
public class RtpCommand implements CommandExecutor {

    private final KelpylandiaPlugin plugin;
    /** UUID -> epoch millis when cooldown expires */
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    private static final int MAX_ATTEMPTS = 30;

    public RtpCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        if (cooldowns.containsKey(player.getUniqueId())) {
            long remaining = (cooldowns.get(player.getUniqueId()) - System.currentTimeMillis()) / 1000;
            if (remaining > 0) {
                player.sendMessage(ChatColor.RED + "Please wait " + formatCooldown(remaining) + " before using /rtp again.");
                return true;
            }
            cooldowns.remove(player.getUniqueId());
        }

        int radius = plugin.getConfig().getInt("rtp.radius", 2000);
        boolean allowUnderground = plugin.getConfig().getBoolean("rtp.allow-underground", false);
        boolean allowWater = plugin.getConfig().getBoolean("rtp.allow-water", false);
        int cooldownSec = plugin.getConfig().getInt("rtp.cooldown", 30);

        player.sendMessage(ChatColor.YELLOW + "Finding a safe location...");

        // Mark in-progress (prevent spamming while searching)
        cooldowns.put(player.getUniqueId(), Long.MAX_VALUE);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            World world = player.getWorld();
            Location spawn = world.getSpawnLocation();
            Location safe = null;

            for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
                int x = spawn.getBlockX() + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
                int z = spawn.getBlockZ() + ThreadLocalRandom.current().nextInt(-radius, radius + 1);

                // Schedule chunk check on main thread, but we do a sync callback
                // For simplicity, use a synchronous approach
                final int fx = x;
                final int fz = z;
                final int attemptNum = attempt;

                // We need the highest block - must be done sync. So queue a sync check.
                // For better UX, just do all attempts sync-scheduled.
            }

            // We need to do location finding on the main thread because getHighestBlockYAt
            // and block access require it. Schedule the search synchronously.
            Bukkit.getScheduler().runTask(plugin, () -> {
                findSafeLocationSync(player, spawn, radius, allowUnderground, allowWater, cooldownSec);
            });
        });

        return true;
    }

    private void findSafeLocationSync(Player player, Location spawn, int radius,
                                       boolean allowUnderground, boolean allowWater, int cooldownSec) {
        World world = spawn.getWorld();
        Location safe = null;

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            int x = spawn.getBlockX() + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            int z = spawn.getBlockZ() + ThreadLocalRandom.current().nextInt(-radius, radius + 1);

            int highestY = world.getHighestBlockYAt(x, z);
            if (highestY < 1) continue; // Void

            Location candidate = new Location(world, x + 0.5, highestY + 1, z + 0.5);
            Block ground = world.getBlockAt(x, highestY, z);
            Block feet = world.getBlockAt(x, highestY + 1, z);
            Block head = world.getBlockAt(x, highestY + 2, z);

            // Check if location is in water
            if (!allowWater && (ground.getType() == Material.WATER || ground.getType() == Material.LAVA
                    || feet.getType() == Material.WATER || feet.getType() == Material.LAVA)) {
                continue;
            }

            // Check if location is underground (below surface)
            if (!allowUnderground) {
                // getHighestBlockYAt already gives the surface, so we're on top
                // Just verify there's sky above
                if (!head.getType().isAir() && head.getType() != Material.VOID_AIR) {
                    continue;
                }
            }

            // Make sure ground is solid
            if (!ground.getType().isSolid()) continue;

            // Make sure feet and head positions are safe (air)
            if (!feet.getType().isAir() && feet.getType() != Material.VOID_AIR) continue;
            if (!head.getType().isAir() && head.getType() != Material.VOID_AIR) continue;

            // Check for dangerous blocks
            Material groundType = ground.getType();
            if (groundType == Material.LAVA || groundType == Material.FIRE ||
                groundType == Material.CACTUS || groundType == Material.MAGMA_BLOCK ||
                groundType == Material.SWEET_BERRY_BUSH || groundType == Material.CAMPFIRE) {
                continue;
            }

            safe = candidate;
            break;
        }

        if (safe == null) {
            player.sendMessage(ChatColor.RED + "Could not find a safe location after " + MAX_ATTEMPTS + " attempts. Please try again.");
            cooldowns.remove(player.getUniqueId());
            return;
        }

        player.teleport(safe);
        player.sendMessage(ChatColor.GREEN + "Teleported to a random location! " +
                ChatColor.GRAY + "(" + safe.getBlockX() + ", " + safe.getBlockY() + ", " + safe.getBlockZ() + ")");

        // Apply cooldown (skip if player has bypass permission)
        if (player.hasPermission("qol.rtp.bypass.cooldown")) {
            cooldowns.remove(player.getUniqueId());
        } else {
            long expiresAt = System.currentTimeMillis() + (cooldownSec * 1000L);
            cooldowns.put(player.getUniqueId(), expiresAt);
        }
    }

    private String formatCooldown(long seconds) {
        if (seconds <= 0) return "0s";
        long m = seconds / 60;
        long s = seconds % 60;
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }
}
