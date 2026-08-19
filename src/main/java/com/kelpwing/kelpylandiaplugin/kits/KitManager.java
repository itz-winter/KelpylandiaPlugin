package com.kelpwing.kelpylandiaplugin.kits;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages kits: loading/saving from kits.yml, cooldown tracking, and giving kits to players.
 * 
 * Kit definitions live under "kits.<name>" in kits.yml.
 * Player cooldowns live under "cooldowns.<uuid>.<kitName>" in kits.yml.
 */
public class KitManager {

    private final KelpylandiaPlugin plugin;
    private File kitsFile;
    private FileConfiguration kitsConfig;

    /** All loaded kits, keyed by lowercase name. */
    private final Map<String, Kit> kits = new LinkedHashMap<>();

    /** Per-player cooldowns: UUID -> (kitName -> last-claim-epoch-millis). */
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    public KitManager(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
        setupFile();
        loadKits();
        loadCooldowns();
    }

    // File setup 

    private void setupFile() {
        kitsFile = new File(plugin.getDataFolder(), "kits.yml");
        if (!kitsFile.exists()) {
            // Copy the default resource if bundled, else create empty
            if (plugin.getResource("kits.yml") != null) {
                plugin.saveResource("kits.yml", false);
            } else {
                try {
                    plugin.getDataFolder().mkdirs();
                    kitsFile.createNewFile();
                } catch (IOException e) {
                    plugin.getLogger().severe("Could not create kits.yml!");
                    e.printStackTrace();
                }
            }
        }
        kitsConfig = YamlConfiguration.loadConfiguration(kitsFile);
    }

    // Load / Save kits 

    private void loadKits() {
        kits.clear();
        ConfigurationSection kitsSection = kitsConfig.getConfigurationSection("kits");
        if (kitsSection == null) return;

        for (String kitName : kitsSection.getKeys(false)) {
            ConfigurationSection sec = kitsSection.getConfigurationSection(kitName);
            if (sec == null) continue;

            long cooldown = sec.getLong("cooldown", 0);
            String permission = sec.getString("permission", null);
            String iconName = sec.getString("icon", "CHEST");
            Material icon = Material.matchMaterial(iconName);
            if (icon == null) icon = Material.CHEST;
            String description = sec.getString("description", "");
            boolean giveOnFirstJoin = sec.getBoolean("give-on-first-join", false);
            boolean oneTime = sec.getBoolean("one-time", false);

            // Items are stored as a list of serialized ItemStacks.
            // Bukkit's YAML loader may auto-deserialize them into ItemStack/CraftItemStack
            // objects, or they may still be raw Maps - handle both cases.
            List<ItemStack> items = new ArrayList<>();
            List<?> rawItems = sec.getList("items");
            if (rawItems != null) {
                for (Object obj : rawItems) {
                    try {
                        if (obj instanceof ItemStack is) {
                            // Already deserialized by Bukkit's YAML loader
                            items.add(is);
                        } else if (obj instanceof Map<?, ?> map) {
                            // Raw map - deserialize manually
                            @SuppressWarnings("unchecked")
                            ItemStack item = ItemStack.deserialize(toStringMap(map));
                            items.add(item);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to load item in kit '" + kitName + "': " + e.getMessage());
                    }
                }
            }

            Kit kit = new Kit(kitName, items, cooldown, permission, icon, description, giveOnFirstJoin, oneTime);
            kits.put(kitName.toLowerCase(), kit);
        }

        plugin.getLogger().info("Loaded " + kits.size() + " kit(s).");
    }

    public void saveKit(Kit kit) {
        String path = "kits." + kit.getName();
        kitsConfig.set(path + ".cooldown", kit.getCooldownSeconds());
        kitsConfig.set(path + ".permission", kit.getPermission());
        kitsConfig.set(path + ".icon", kit.getDisplayIcon().name());
        kitsConfig.set(path + ".description", kit.getDescription());
        kitsConfig.set(path + ".give-on-first-join", kit.isGiveOnFirstJoin());
        kitsConfig.set(path + ".one-time", kit.isOneTime());

        // Serialize items
        List<Map<String, Object>> serializedItems = new ArrayList<>();
        for (ItemStack item : kit.getItems()) {
            if (item != null && item.getType() != Material.AIR) {
                serializedItems.add(item.serialize());
            }
        }
        kitsConfig.set(path + ".items", serializedItems);

        saveFile();
        kits.put(kit.getName().toLowerCase(), kit);
    }

    public void deleteKit(String name) {
        String lower = name.toLowerCase();
        kitsConfig.set("kits." + name, null);
        kits.remove(lower);
        saveFile();
    }

    public void reload() {
        kitsConfig = YamlConfiguration.loadConfiguration(kitsFile);
        loadKits();
        loadCooldowns();
    }

    // Cooldown management 

    private void loadCooldowns() {
        cooldowns.clear();
        ConfigurationSection cdSection = kitsConfig.getConfigurationSection("cooldowns");
        if (cdSection == null) return;

        for (String uuidStr : cdSection.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                continue;
            }
            ConfigurationSection playerCd = cdSection.getConfigurationSection(uuidStr);
            if (playerCd == null) continue;

            Map<String, Long> playerCooldowns = new HashMap<>();
            for (String kitName : playerCd.getKeys(false)) {
                playerCooldowns.put(kitName.toLowerCase(), playerCd.getLong(kitName, 0));
            }
            cooldowns.put(uuid, playerCooldowns);
        }
    }

    private void saveCooldown(UUID uuid, String kitName, long epochMillis) {
        Map<String, Long> playerCd = cooldowns.computeIfAbsent(uuid, k -> new HashMap<>());
        playerCd.put(kitName.toLowerCase(), epochMillis);
        kitsConfig.set("cooldowns." + uuid.toString() + "." + kitName.toLowerCase(), epochMillis);
        saveFile();
    }

    /**
     * Returns remaining cooldown in seconds, or 0 if ready.
     * Returns -1 if the kit is one-time and already claimed.
     */
    public long getRemainingCooldown(UUID uuid, Kit kit) {
        Map<String, Long> playerCd = cooldowns.get(uuid);
        if (playerCd == null) return 0;

        Long lastClaim = playerCd.get(kit.getName().toLowerCase());
        if (lastClaim == null) return 0;

        if (kit.isOneTime()) {
            return -1; // already claimed once - never again
        }

        if (kit.hasNoCooldown() || kit.getCooldownSeconds() <= 0) {
            return 0;
        }

        long elapsed = (System.currentTimeMillis() - lastClaim) / 1000;
        long remaining = kit.getCooldownSeconds() - elapsed;
        return remaining > 0 ? remaining : 0;
    }

    /**
     * Format seconds into a readable "1h 30m 15s" style string.
     */
    public String formatCooldown(long seconds) {
        if (seconds <= 0) return "Ready";
        long d = seconds / 86400; seconds %= 86400;
        long h = seconds / 3600;  seconds %= 3600;
        long m = seconds / 60;    seconds %= 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (seconds > 0 && d == 0) sb.append(seconds).append("s");
        return sb.toString().trim();
    }

    // Give kit to player 

    /**
     * Attempts to give a kit to a player.
     * Returns a result message (success or error).
     */
    public GiveResult giveKit(Player player, Kit kit) {
        // Check permission
        if (kit.getPermission() != null && !kit.getPermission().isEmpty()
                && !player.hasPermission(kit.getPermission())) {
            return new GiveResult(false, "You don't have permission to use this kit.");
        }

        // Check cooldown
        long remaining = getRemainingCooldown(player.getUniqueId(), kit);
        if (remaining == -1) {
            return new GiveResult(false, "You have already claimed this kit. It can only be claimed once.");
        }
        if (remaining > 0) {
            return new GiveResult(false, "This kit is on cooldown. Time remaining: " + formatCooldown(remaining));
        }

        // Give items
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(
            kit.getItems().stream()
                .filter(i -> i != null && i.getType() != Material.AIR)
                .map(ItemStack::clone)
                .toArray(ItemStack[]::new)
        );

        // Drop overflow at player's feet
        for (ItemStack leftover : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }

        // Record cooldown
        saveCooldown(player.getUniqueId(), kit.getName(), System.currentTimeMillis());

        String extra = overflow.isEmpty() ? "" : " (some items were dropped at your feet)";
        return new GiveResult(true, "Kit " + kit.getName() + " claimed!" + extra);
    }

    // Queries 

    public Kit getKit(String name) {
        return kits.get(name.toLowerCase());
    }

    public Collection<Kit> getAllKits() {
        return Collections.unmodifiableCollection(kits.values());
    }

    public Set<String> getKitNames() {
        return Collections.unmodifiableSet(kits.keySet());
    }

    /**
     * Check if a player has ever claimed a specific kit.
     */
    public boolean hasClaimedKit(UUID uuid, String kitName) {
        Map<String, Long> playerCd = cooldowns.get(uuid);
        if (playerCd == null) return false;
        return playerCd.containsKey(kitName.toLowerCase());
    }

    /**
     * Give all first-join kits to a player who has never played before.
     * Called from PlayerEventListener on first join.
     */
    public void giveFirstJoinKits(Player player) {
        for (Kit kit : kits.values()) {
            if (!kit.isGiveOnFirstJoin()) continue;

            // Skip if permission is set and player doesn't have it
            if (kit.getPermission() != null && !kit.getPermission().isEmpty()
                    && !player.hasPermission(kit.getPermission())) {
                continue;
            }

            // Skip if already claimed (safety check)
            if (hasClaimedKit(player.getUniqueId(), kit.getName())) continue;

            // Give items
            HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(
                kit.getItems().stream()
                    .filter(i -> i != null && i.getType() != Material.AIR)
                    .map(ItemStack::clone)
                    .toArray(ItemStack[]::new)
            );
            for (ItemStack leftover : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }

            // Record so it isn't given again
            saveCooldown(player.getUniqueId(), kit.getName(), System.currentTimeMillis());

            player.sendMessage(org.bukkit.ChatColor.GREEN + "You received the " + kit.getName() + " kit!");
        }
    }

    /**
     * Get kits a player has permission to use.
     */
    public List<Kit> getAvailableKits(Player player) {
        List<Kit> available = new ArrayList<>();
        for (Kit kit : kits.values()) {
            if (kit.getPermission() == null || kit.getPermission().isEmpty()
                    || player.hasPermission(kit.getPermission())) {
                available.add(kit);
            }
        }
        return available;
    }

    // Utility 

    private void saveFile() {
        try {
            kitsConfig.save(kitsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save kits.yml: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toStringMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    // Result wrapper 

    public static class GiveResult {
        private final boolean success;
        private final String message;

        public GiveResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }
}
