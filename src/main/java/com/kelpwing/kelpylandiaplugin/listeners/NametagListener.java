package com.kelpwing.kelpylandiaplugin.listeners;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Controls whether the using player's rank prefix is prepended to an entity's
 * custom name when a nametag is applied to it.
 *
 * <p>Controlled by {@code nametag.add-owner-prefix} in config.yml:
 * <ul>
 *   <li>{@code false} (default / null) - vanilla behaviour; no prefix added.</li>
 *   <li>{@code true}  - the player's LuckPerms prefix is prepended to the name.</li>
 * </ul>
 */
public class NametagListener implements Listener {

    private final KelpylandiaPlugin plugin;

    public NametagListener(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNametagUse(PlayerInteractEntityEvent event) {
        // Config gate - null/missing key also treated as false
        if (!plugin.getConfig().getBoolean("nametag.add-owner-prefix", false)) return;

        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();

        // Only apply to non-player living entities (pets, mobs, etc.)
        if (entity instanceof Player) return;
        if (!(entity instanceof LivingEntity)) return;

        // Get the item in the interacting hand
        ItemStack hand = event.getHand() == EquipmentSlot.HAND
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();

        if (hand == null || hand.getType() != Material.NAME_TAG) return;
        if (!hand.hasItemMeta() || !hand.getItemMeta().hasDisplayName()) return;

        String prefix = getPlayerPrefix(player);
        if (prefix.isEmpty()) return; // Nothing to prepend

        // Temporarily modify the nametag so vanilla applies the prefixed name.
        // We schedule a same-tick restoration in case the nametag was NOT consumed
        // (e.g., creative mode, entity already has this name, or the event was later
        // cancelled by another plugin at HIGHEST priority).
        ItemMeta meta = hand.getItemMeta();
        String originalName = meta.getDisplayName();
        String prefixedName = prefix + originalName;

        meta.setDisplayName(prefixedName);
        hand.setItemMeta(meta);

        final EquipmentSlot usedHand = event.getHand();

        Bukkit.getScheduler().runTask(plugin, () -> {
            ItemStack current = usedHand == EquipmentSlot.HAND
                    ? player.getInventory().getItemInMainHand()
                    : player.getInventory().getItemInOffHand();

            // If the nametag is still there with the modified name, restore it
            // (means vanilla didn't consume it - entity couldn't be named, etc.)
            if (current != null
                    && current.getType() == Material.NAME_TAG
                    && current.hasItemMeta()
                    && current.getItemMeta().hasDisplayName()
                    && current.getItemMeta().getDisplayName().equals(prefixedName)) {
                ItemMeta restore = current.getItemMeta();
                restore.setDisplayName(originalName);
                current.setItemMeta(restore);
            }
        });
    }

    // Prefix helper (mirrors ChatFormatUtils.getPlayerPrefix) 

    private static String getPlayerPrefix(Player player) {
        try {
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                String prefix = me.clip.placeholderapi.PlaceholderAPI
                        .setPlaceholders(player, "%luckperms_prefix%");
                if (prefix != null && !prefix.equals("%luckperms_prefix%")) {
                    return prefix;
                }
            }
        } catch (Exception ignored) {}
        return "";
    }
}
