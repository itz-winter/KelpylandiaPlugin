package com.kelpwing.kelpylandiaplugin.listeners;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import com.kelpwing.kelpylandiaplugin.moderation.commands.VanishCommand;
import com.kelpwing.kelpylandiaplugin.utils.VanishManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.ChatColor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class VanishListener implements Listener {

    private final KelpylandiaPlugin plugin;
    private final VanishManager vanishManager;
    private final VanishCommand vanishCommand;
    
    /** Tracks the last time each player pressed shift (for double-shift detection). */
    private final Map<UUID, Long> lastSneakTime = new HashMap<>();
    /** Stores the gamemode the player had before switching to spectator. */
    private final Map<UUID, GameMode> savedGameMode = new HashMap<>();
    /** Double-shift threshold in milliseconds. */
    private static final long DOUBLE_SHIFT_MS = 400;

    public VanishListener(KelpylandiaPlugin plugin, VanishCommand vanishCommand) {
        this.plugin = plugin;
        this.vanishManager = plugin.getVanishManager();
        this.vanishCommand = vanishCommand;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (vanishCommand.isVanished(player) && !vanishManager.canPickupItems(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            if (vanishCommand.isVanished(player)) {
                // Suppress sounds and animations for vanished players
                // This is handled by making the interaction silent
                event.getInventory().getViewers().clear();
                event.getInventory().getViewers().add(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            if (vanishCommand.isVanished(player)) {
                // Sync silent container contents back to the real container
                vanishManager.handleSilentContainerClose(player, event.getInventory());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!vanishCommand.isVanished(player)) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        Material type = block.getType();

        // --- Silent door/trapdoor/gate toggle ---
        BlockData data = block.getBlockData();
        if (data instanceof Openable) {
            event.setCancelled(true);
            Openable openable = (Openable) data;
            openable.setOpen(!openable.isOpen());
            block.setBlockData(openable, false); // false = don't apply physics (no sound to others)
            player.sendBlockChange(block.getLocation(), block.getBlockData()); // sync for the player
            return;
        }

        // --- Silent container open (no chest lid animation for other players) ---
        if (isContainer(type) && block.getState() instanceof Container) {
            event.setCancelled(true);
            Container container = (Container) block.getState();
            Inventory real = container.getInventory();
            // Open a virtual copy so the block's viewer count never increments
            Inventory copy = Bukkit.createInventory(null, real.getSize(), container.getCustomName() != null ? container.getCustomName() : prettyName(type));
            copy.setContents(real.getContents());
            player.openInventory(copy);
            // Schedule sync-back when the player closes it
            vanishManager.trackSilentContainer(player, copy, real);
        }
    }

    /** Checks whether a material is a container that shows an open/close animation. */
    private boolean isContainer(Material type) {
        String name = type.name();
        return name.equals("CHEST") || name.equals("TRAPPED_CHEST")
            || name.equals("BARREL") || name.contains("SHULKER_BOX")
            || name.equals("ENDER_CHEST");
    }

    /** Pretty-print a container name for the inventory title. */
    private String prettyName(Material type) {
        String name = type.name().replace('_', ' ').toLowerCase();
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        
        if (vanishCommand.isVanished(player) && event.getRightClicked() instanceof Player) {
            Player target = (Player) event.getRightClicked();
            
            if (player.isSneaking()) {
                // Shift+Right click to clear target
                vanishManager.clearVanishTarget(player);
                player.sendMessage("§cTarget cleared!");
            } else {
                // Right click to set target
                vanishManager.setVanishTarget(player, target);
                player.sendMessage("§aTarget set to: " + target.getName());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        
        if (vanishCommand.isVanished(player)) {
            // Check for nearby players to auto-target
            Player currentTarget = vanishManager.getVanishTarget(player);
            
            for (Player nearbyPlayer : player.getWorld().getPlayers()) {
                if (nearbyPlayer != player && !vanishCommand.isVanished(nearbyPlayer)) {
                    double distance = player.getLocation().distance(nearbyPlayer.getLocation());
                    
                    // Auto-target if within 3 blocks and no current target
                    if (distance <= 3.0 && currentTarget == null) {
                        vanishManager.setVanishTarget(player, nearbyPlayer);
                        player.sendMessage("§eAuto-targeted: " + nearbyPlayer.getName());
                        break;
                    }
                    // Clear target if current target is too far away
                    else if (currentTarget == nearbyPlayer && distance > 10.0) {
                        vanishManager.clearVanishTarget(player);
                        player.sendMessage("§7Target cleared (too far away)");
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (!vanishCommand.isVanished(player)) return;
        
        // Only detect shift-down (isSneaking = true means they started sneaking)
        if (!event.isSneaking()) return;
        
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastSneakTime.get(uuid);
        lastSneakTime.put(uuid, now);
        
        if (last != null && (now - last) <= DOUBLE_SHIFT_MS) {
            // Double-shift detected - toggle between spectator and saved gamemode
            lastSneakTime.remove(uuid); // reset so triple-shift doesn't re-trigger
            
            if (player.getGameMode() == GameMode.SPECTATOR) {
                // Restore saved gamemode
                GameMode restored = savedGameMode.getOrDefault(uuid, GameMode.SURVIVAL);
                savedGameMode.remove(uuid);
                player.setGameMode(restored);
                player.sendMessage(ChatColor.GREEN + "Returned to " + ChatColor.GOLD + restored.name() + ChatColor.GREEN + " mode.");
            } else {
                // Save current gamemode and switch to spectator
                savedGameMode.put(uuid, player.getGameMode());
                player.setGameMode(GameMode.SPECTATOR);
                player.sendMessage(ChatColor.LIGHT_PURPLE + "Switched to " + ChatColor.GOLD + "SPECTATOR" + ChatColor.LIGHT_PURPLE + " mode. Double-shift again to return.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        boolean wasVanished = vanishCommand.isVanished(player);
        
        // Hide quit message if player was vanished (must happen synchronously
        // inside the event handler so the message suppression takes effect)
        if (wasVanished) {
            event.setQuitMessage(null);
            // Restore gamemode if in spectator from vanish toggle
            GameMode saved = savedGameMode.remove(player.getUniqueId());
            if (saved != null && player.getGameMode() == GameMode.SPECTATOR) {
                player.setGameMode(saved);
            }
        }
        
        // Defer vanish cleanup to the next tick so every quit handler in this
        // event cycle still sees the player as vanished (prevents duplicate leave messages)
        final UUID uuid = player.getUniqueId();
        final boolean vanished = wasVanished;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (vanished) {
                vanishCommand.removeVanishedPlayer(player);
            }
            vanishManager.cleanup(player);
            lastSneakTime.remove(uuid);
        });
        
        // Clean up PvP data
        if (plugin.getPvpCommand() != null) {
            plugin.getPvpCommand().cleanupPlayer(player.getUniqueId());
        }
    }
}
