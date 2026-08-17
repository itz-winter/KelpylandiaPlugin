package com.kelpwing.kelpylandiaplugin.listeners;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

/**
 * Handles silk-touch drops for blocks that normally do not drop themselves.
 *
 * Supported blocks (each independently toggled in config.yml under "silk:"):
 *   silk.spawners             - drop spawner with mob type preserved; same-type spawners stack
 *   silk.budding-amethryst    - drop budding amethyst (normally unbreakable)
 *   silk.reinforced-deepslate - drop reinforced deepslate (normally unbreakable)
 *   silk.suspicious-sand      - drop suspicious sand with silk touch
 *   silk.suspicious-gravel    - drop suspicious gravel with silk touch
 *
 * Materials added after the 1.16.5 API baseline are resolved at runtime via
 * Material.getMaterial() so the plugin compiles cleanly while working on 1.21.x servers.
 */
public class SilkListener implements Listener {

    private final KelpylandiaPlugin plugin;

    // Runtime-resolved materials - null if server version predates the material
    private final Material buddingAmethyst;
    private final Material reinforcedDeepslate;
    private final Material suspiciousSand;
    private final Material suspiciousGravel;

    public SilkListener(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
        buddingAmethyst     = Material.getMaterial("BUDDING_AMETHYST");
        reinforcedDeepslate = Material.getMaterial("REINFORCED_DEEPSLATE");
        suspiciousSand      = Material.getMaterial("SUSPICIOUS_SAND");
        suspiciousGravel    = Material.getMaterial("SUSPICIOUS_GRAVEL");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block    block = event.getBlock();
        Material type  = block.getType();

        boolean hasSilkTouch = event.getPlayer()
                .getInventory()
                .getItemInMainHand()
                .containsEnchantment(Enchantment.SILK_TOUCH);

        // Spawners - checked before the silk-touch guard so that non-silk breaks
        // still return early without falling through to other checks.
        // Stacking: two spawner items with the same EntityType in BlockStateMeta
        // are byte-for-byte equal and Bukkit will naturally merge them in inventory.
        if (type == Material.SPAWNER && plugin.getConfig().getBoolean("silk.spawners", false)) {
            if (hasSilkTouch) {
                event.setDropItems(false);
                event.setExpToDrop(0);
                block.getWorld().dropItemNaturally(
                        block.getLocation(),
                        buildSpawnerItem((CreatureSpawner) block.getState()));
            }
            return;
        }

        // All remaining blocks require Silk Touch
        if (!hasSilkTouch) return;

        // Budding Amethyst
        if (buddingAmethyst != null && type == buddingAmethyst
                && plugin.getConfig().getBoolean("silk.budding-amethryst", false)) {
            event.setDropItems(false);
            block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(buddingAmethyst));
            return;
        }

        // Reinforced Deepslate
        if (reinforcedDeepslate != null && type == reinforcedDeepslate
                && plugin.getConfig().getBoolean("silk.reinforced-deepslate", false)) {
            event.setDropItems(false);
            block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(reinforcedDeepslate));
            return;
        }

        // Suspicious Sand
        if (suspiciousSand != null && type == suspiciousSand
                && plugin.getConfig().getBoolean("silk.suspicious-sand", false)) {
            event.setDropItems(false);
            block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(suspiciousSand));
            return;
        }

        // Suspicious Gravel
        if (suspiciousGravel != null && type == suspiciousGravel
                && plugin.getConfig().getBoolean("silk.suspicious-gravel", false)) {
            event.setDropItems(false);
            block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(suspiciousGravel));
        }
    }

    /**
     * Builds a SPAWNER ItemStack that carries the mob type from the broken block
     * and applies a custom display name from the "silk.spawner-naming" config key.
     *
     * Placeholder: {mob_name_all_caps} -> e.g. ZOMBIE, SKELETON
     *
     * BlockStateMeta ensures that two items with the same EntityType and the same
     * display name are identical, so they stack freely in any inventory.
     */
    private ItemStack buildSpawnerItem(CreatureSpawner spawner) {
        ItemStack item = new ItemStack(Material.SPAWNER, 1);

        if (item.getItemMeta() instanceof BlockStateMeta) {
            BlockStateMeta bsm   = (BlockStateMeta) item.getItemMeta();
            CreatureSpawner copy = (CreatureSpawner) bsm.getBlockState();

            EntityType spawnedType = spawner.getSpawnedType();
            if (spawnedType != null && spawnedType != EntityType.UNKNOWN) {
                copy.setSpawnedType(spawnedType);
            }

            bsm.setBlockState(copy);

            // Apply custom display name from config, e.g. "&7[&b{mob_name_all_caps}&7]&r Monster Spawners"
            String namingTemplate = plugin.getConfig().getString(
                    "silk.spawner-naming", "&7[&b{mob_name_all_caps}&7]&r Monster Spawners");

            if (namingTemplate != null && !namingTemplate.isEmpty()) {
                String mobName = spawnedType != null && spawnedType != EntityType.UNKNOWN
                        ? spawnedType.name()          // already ALL_CAPS, e.g. "ZOMBIE"
                        : "UNKNOWN";

                String displayName = ChatColor.translateAlternateColorCodes('&',
                        namingTemplate.replace("{mob_name_all_caps}", mobName));

                bsm.setDisplayName(displayName);
            }

            item.setItemMeta(bsm);
        }

        return item;
    }
}