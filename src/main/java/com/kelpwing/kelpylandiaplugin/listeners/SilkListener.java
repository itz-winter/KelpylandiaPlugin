package com.kelpwing.kelpylandiaplugin.listeners;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import java.lang.reflect.Method;

public class SilkListener implements Listener {
    private final KelpylandiaPlugin plugin;
    private final NamespacedKey SPAWNER_TYPE_KEY;
    private final Material buddingAmethyst;
    private final Material reinforcedDeepslate;
    private final Material suspiciousSand;
    private final Material suspiciousGravel;

    public SilkListener(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
        this.SPAWNER_TYPE_KEY = new NamespacedKey(plugin, "spawner_entity_type");
        buddingAmethyst     = Material.getMaterial("BUDDING_AMETHYST");
        reinforcedDeepslate = Material.getMaterial("REINFORCED_DEEPSLATE");
        suspiciousSand      = Material.getMaterial("SUSPICIOUS_SAND");
        suspiciousGravel    = Material.getMaterial("SUSPICIOUS_GRAVEL");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        boolean hasSilkTouch = event.getPlayer().getInventory().getItemInMainHand()
                .containsEnchantment(Enchantment.SILK_TOUCH);
        if (type == Material.SPAWNER && plugin.getConfig().getBoolean("silk.spawners", false)) {
            if (hasSilkTouch) {
                event.setDropItems(false);
                event.setExpToDrop(0);
                CreatureSpawner s = (CreatureSpawner) block.getState();
                block.getWorld().dropItemNaturally(block.getLocation(), buildSpawnerItem(resolveEntityType(s)));
            }
            return;
        }
        if (!hasSilkTouch) return;
        if (buddingAmethyst != null && type == buddingAmethyst && plugin.getConfig().getBoolean("silk.budding-amethryst", false)) {
            event.setDropItems(false);
            block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(buddingAmethyst));
            return;
        }
        if (reinforcedDeepslate != null && type == reinforcedDeepslate && plugin.getConfig().getBoolean("silk.reinforced-deepslate", false)) {
            event.setDropItems(false);
            block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(reinforcedDeepslate));
            return;
        }
        if (suspiciousSand != null && type == suspiciousSand && plugin.getConfig().getBoolean("silk.suspicious-sand", false)) {
            event.setDropItems(false);
            block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(suspiciousSand));
            return;
        }
        if (suspiciousGravel != null && type == suspiciousGravel && plugin.getConfig().getBoolean("silk.suspicious-gravel", false)) {
            event.setDropItems(false);
            block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(suspiciousGravel));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.SPAWNER) return;
        ItemStack item = event.getItemInHand();
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        String storedType = meta.getPersistentDataContainer().get(SPAWNER_TYPE_KEY, PersistentDataType.STRING);
        if (storedType == null || storedType.equals("UNKNOWN")) return;
        EntityType entityType;
        try { entityType = EntityType.valueOf(storedType); } catch (IllegalArgumentException e) { return; }
        final Block placed = event.getBlockPlaced();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (placed.getType() != Material.SPAWNER) return;
            CreatureSpawner cs = (CreatureSpawner) placed.getState();
            cs.setSpawnedType(entityType);
            cs.update(true, false);
        }, 1L);
    }

    private EntityType resolveEntityType(CreatureSpawner spawner) {
        try {
            EntityType t = spawner.getSpawnedType();
            if (t != null && t != EntityType.UNKNOWN) return t;
        } catch (Exception ignored) {}
        try {
            Method getSnapshotNbt = spawner.getClass().getMethod("getSnapshotNBT");
            Object compound = getSnapshotNbt.invoke(spawner);
            String id = nbtPath(compound, "SpawnData", "entity", "id");
            if (id == null || id.isEmpty()) id = nbtPath(compound, "SpawnData", null, "id");
            if (id != null && !id.isEmpty()) {
                EntityType t = entityTypeFromKey(id);
                if (t != EntityType.UNKNOWN) return t;
            }
        } catch (Exception ignored) {}
        return EntityType.UNKNOWN;
    }

    private String nbtPath(Object compound, String parent, String middle, String key) {
        try {
            Method getCompound = compound.getClass().getMethod("getCompound", String.class);
            Object parentTag = getCompound.invoke(compound, parent);
            Object target = (middle != null) ? getCompound.invoke(parentTag, middle) : parentTag;
            Method getString = target.getClass().getMethod("getString", String.class);
            String val = (String) getString.invoke(target, key);
            return (val != null && !val.isEmpty()) ? val : null;
        } catch (Exception e) { return null; }
    }

    private EntityType entityTypeFromKey(String key) {
        String name = key.contains(":") ? key.substring(key.lastIndexOf(':') + 1) : key;
        try { return EntityType.valueOf(name.toUpperCase()); } catch (IllegalArgumentException ignored) {}
        for (EntityType et : EntityType.values()) {
            try { if (et.getKey().getKey().equalsIgnoreCase(name)) return et; } catch (Exception ignored) {}
        }
        return EntityType.UNKNOWN;
    }

    private ItemStack buildSpawnerItem(EntityType entityType) {
        ItemStack item = new ItemStack(Material.SPAWNER, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        if (meta instanceof BlockStateMeta) {
            BlockStateMeta bsm = (BlockStateMeta) meta;
            CreatureSpawner copy = (CreatureSpawner) bsm.getBlockState();
            if (entityType != null && entityType != EntityType.UNKNOWN) copy.setSpawnedType(entityType);
            bsm.setBlockState(copy);
            meta = bsm;
        }
        String typeName = (entityType != null && entityType != EntityType.UNKNOWN) ? entityType.name() : "UNKNOWN";
        meta.getPersistentDataContainer().set(SPAWNER_TYPE_KEY, PersistentDataType.STRING, typeName);
        String namingTemplate = plugin.getConfig().getString("silk.spawner-naming", "&7[&b{mob_name_all_caps}&7]&r Monster Spawners");
        if (namingTemplate != null && !namingTemplate.isEmpty()) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', namingTemplate.replace("{mob_name_all_caps}", typeName)));
        }
        item.setItemMeta(meta);
        return item;
    }
}