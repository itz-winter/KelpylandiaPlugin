package com.kelpwing.kelpylandiaplugin.moderation.listeners;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import com.kelpwing.kelpylandiaplugin.moderation.JailManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;

/**
 * Listener that prevents jailed players from performing any actions.
 * Blocks: movement, chat, commands (except /release by admins), crafting,
 * damage, inventory interaction, item drops/pickups, block break/place, etc.
 */
public class JailListener implements Listener {

    private final KelpylandiaPlugin plugin;

    public JailListener(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isJailed(Player player) {
        JailManager jm = plugin.getJailManager();
        return jm != null && jm.isJailed(player.getUniqueId());
    }

    // Movement 

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!isJailed(event.getPlayer())) return;
        // Allow looking around but not moving position
        if (event.getFrom().getBlockX() != event.getTo().getBlockX() ||
            event.getFrom().getBlockY() != event.getTo().getBlockY() ||
            event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            event.setTo(event.getFrom());
        }
    }

    // Chat 

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!isJailed(event.getPlayer())) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(ChatColor.RED + "You cannot chat while jailed.");
    }

    // Commands (block all except release-related) 

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!isJailed(event.getPlayer())) return;
        // Allow nothing
        event.setCancelled(true);
        event.getPlayer().sendMessage(ChatColor.RED + "You cannot use commands while jailed.");
    }

    // Damage (make invulnerable so they can't die) 

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isJailed(player)) return;
        event.setCancelled(true);
    }

    // Hunger 

    @EventHandler(priority = EventPriority.LOWEST)
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isJailed(player)) return;
        event.setCancelled(true);
    }

    // Inventory 

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!isJailed(player)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!isJailed(player)) return;
        event.setCancelled(true);
    }

    // Item Drops & Pickups 

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (!isJailed(event.getPlayer())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isJailed(player)) return;
        event.setCancelled(true);
    }

    // Block Interaction 

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isJailed(event.getPlayer())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!isJailed(event.getPlayer())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!isJailed(event.getPlayer())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!isJailed(event.getPlayer())) return;
        event.setCancelled(true);
    }
}
