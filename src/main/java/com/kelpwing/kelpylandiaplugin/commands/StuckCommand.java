package com.kelpwing.kelpylandiaplugin.commands;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /stuck - Teleport a player out of the nether roof or void.
 * Disabled by default in config.
 */
public class StuckCommand implements CommandExecutor {

    private final KelpylandiaPlugin plugin;

    public StuckCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Location loc = player.getLocation();
        World world = loc.getWorld();

        boolean stuck = false;
        Location safeLoc = null;

        // Check if in the void (below Y=0 or below min build height)
        int minY;
        try {
            minY = world.getMinHeight();
        } catch (NoSuchMethodError e) {
            minY = 0; // Pre-1.17
        }

        if (loc.getY() < minY + 1) {
            stuck = true;
            // Teleport to spawn of the same world
            safeLoc = world.getSpawnLocation();
        }

        // Check if on the nether roof (Nether, Y >= 128, and solid above)
        if (!stuck && world.getEnvironment() == World.Environment.NETHER && loc.getBlockY() >= 127) {
            Block above = world.getBlockAt(loc.getBlockX(), 128, loc.getBlockZ());
            if (above.getType() == Material.BEDROCK || above.getType().isSolid()) {
                stuck = true;
                // Find safe location below the roof
                safeLoc = findSafeNetherLocation(world, loc.getBlockX(), loc.getBlockZ());
            }
        }

        // Also check if completely enclosed in blocks (generic stuck)
        if (!stuck) {
            Block feet = loc.getBlock();
            Block head = world.getBlockAt(loc.getBlockX(), loc.getBlockY() + 1, loc.getBlockZ());
            if (feet.getType().isSolid() && head.getType().isSolid()) {
                stuck = true;
                safeLoc = world.getSpawnLocation();
            }
        }

        if (!stuck) {
            player.sendMessage(ChatColor.YELLOW + "You don't appear to be stuck.");
            return true;
        }

        if (safeLoc == null) {
            safeLoc = world.getSpawnLocation();
        }

        player.teleport(safeLoc);
        player.sendMessage(ChatColor.GREEN + "You have been unstuck!");
        return true;
    }

    private Location findSafeNetherLocation(World world, int x, int z) {
        // Search downward from Y=126 for a safe spot (air above solid)
        for (int y = 126; y > 1; y--) {
            Block ground = world.getBlockAt(x, y, z);
            Block feet = world.getBlockAt(x, y + 1, z);
            Block head = world.getBlockAt(x, y + 2, z);

            if (ground.getType().isSolid() &&
                !ground.getType().name().contains("LAVA") &&
                (feet.getType().isAir() || feet.getType() == Material.VOID_AIR) &&
                (head.getType().isAir() || head.getType() == Material.VOID_AIR)) {
                return new Location(world, x + 0.5, y + 1, z + 0.5);
            }
        }
        return world.getSpawnLocation();
    }
}
