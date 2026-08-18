package com.kelpwing.kelpylandiaplugin.economy;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Tag;
import org.bukkit.block.ShulkerBox;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Core economy manager: balance storage, price lookups, tax calculations.
 * Supports YAML flat-file storage. SQLite can be added later.
 */
public class EconomyManager {

    private final KelpylandiaPlugin plugin;

    //  Dynamic pricing engine 
    private DynamicPricingEngine dynamicPricing;

    //  Config (loaded from economy.yml) 
    private File economyFile;
    private FileConfiguration economyConfig;

    //  Balance storage (YAML backend) 
    private File balancesFile;
    private FileConfiguration balancesConfig;
    private final Map<UUID, BigDecimal> balanceCache = new ConcurrentHashMap<>();

    //  Price caches (built from economy.yml on load) 
    /** Exact material -> price */
    private final Map<Material, BigDecimal> itemPrices = new LinkedHashMap<>();
    /** Tag-based category -> price (lower priority than item prices) */
    private final Map<Tag<Material>, BigDecimal> categoryPrices = new LinkedHashMap<>();
    /** Category display names for messages */
    private final Map<Tag<Material>, String> categoryNames = new LinkedHashMap<>();
    /** Custom categories: name -> set of materials (e.g. "minecraft_spawners" -> {ZOMBIE_SPAWNER, ...}) */
    private final Map<String, Set<Material>> customCategories = new LinkedHashMap<>();
    /** Custom category prices */
    private final Map<String, BigDecimal> customCategoryPrices = new LinkedHashMap<>();
    /** Explicit unsellable items with reason suffixes */
    private final Map<Material, String> unsellableItems = new LinkedHashMap<>();

    //  Buy price caches 
    /** Exact material -> buy price */
    private final Map<Material, BigDecimal> buyItemPrices = new LinkedHashMap<>();
    /** Tag-based category -> buy price */
    private final Map<Tag<Material>, BigDecimal> buyCategoryPrices = new LinkedHashMap<>();
    /** Custom category buy prices */
    private final Map<String, BigDecimal> buyCustomCategoryPrices = new LinkedHashMap<>();

    //  Derived settings 
    private boolean enabled;
    private boolean useVault;
    private String unit;
    private int decimals;
    private BigDecimal startingBalance;
    private boolean taxEnabled;
    private BigDecimal taxRate;          // e.g. 9.25 for 9.25%
    private boolean taxOnServerSell;
    private boolean buyingEnabled;

    public EconomyManager(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
        this.dynamicPricing = new DynamicPricingEngine(plugin);
        loadConfig();
        loadBalances();
        loadPrices();
        dynamicPricing.loadConfig(economyConfig);
    }

    
    //  Config loading
    

    public void loadConfig() {
        // Save default economy.yml if missing
        economyFile = new File(plugin.getDataFolder(), "economy.yml");
        if (!economyFile.exists()) {
            plugin.saveResource("economy.yml", false);
        }
        economyConfig = YamlConfiguration.loadConfiguration(economyFile);

        enabled = economyConfig.getBoolean("enabled", true);
        useVault = economyConfig.getBoolean("use-vault", true);
        unit = economyConfig.getString("unit", "$");
        if (unit == null || unit.isEmpty()) {
            plugin.getLogger().warning("[Economy] 'unit' is blank in economy.yml, defaulting to '$'.");
            unit = "$";
        }

        // Clamp decimals to a valid range (0-10).
        int rawDecimals = economyConfig.getInt("decimals", 2);
        if (rawDecimals < 0 || rawDecimals > 10) {
            plugin.getLogger().warning("[Economy] 'decimals' value (" + rawDecimals + ") is out of range (0-10), defaulting to 2.");
            rawDecimals = 2;
        }
        decimals = rawDecimals;

        // Starting balance must be non-negative.
        double rawStarting = economyConfig.getDouble("starting-balance", 100);
        if (Double.isNaN(rawStarting) || Double.isInfinite(rawStarting) || rawStarting < 0) {
            plugin.getLogger().warning("[Economy] 'starting-balance' value (" + rawStarting + ") is invalid, defaulting to 100.");
            rawStarting = 100;
        }
        startingBalance = BigDecimal.valueOf(rawStarting);

        taxEnabled = economyConfig.getBoolean("tax.enabled", false);

        // Tax rate must be between 0 and 100.
        double rawTaxRate = economyConfig.getDouble("tax.rate", 9.25);
        if (Double.isNaN(rawTaxRate) || Double.isInfinite(rawTaxRate) || rawTaxRate < 0 || rawTaxRate > 100) {
            plugin.getLogger().warning("[Economy] 'tax.rate' value (" + rawTaxRate + ") is out of range (0-100), defaulting to 9.25.");
            rawTaxRate = 9.25;
        }
        taxRate = BigDecimal.valueOf(rawTaxRate);

        taxOnServerSell = economyConfig.getBoolean("tax.apply-when-selling-to-server", false);
        buyingEnabled = economyConfig.getBoolean("buying.enabled", false);
    }

    private void loadBalances() {
        balancesFile = new File(plugin.getDataFolder(), "balances.yml");
        if (!balancesFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                balancesFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create balances.yml!");
                e.printStackTrace();
            }
        }
        balancesConfig = YamlConfiguration.loadConfiguration(balancesFile);

        balanceCache.clear();
        for (String key : balancesConfig.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                double val = balancesConfig.getDouble(key);
                balanceCache.put(uuid, BigDecimal.valueOf(val).setScale(decimals, RoundingMode.HALF_UP));
            } catch (IllegalArgumentException ignored) {}
        }
        plugin.getLogger().info("[Economy] Loaded " + balanceCache.size() + " player balance(s).");
    }

    private void loadPrices() {
        itemPrices.clear();
        categoryPrices.clear();
        categoryNames.clear();
        customCategories.clear();
        customCategoryPrices.clear();
        unsellableItems.clear();
        buyItemPrices.clear();
        buyCategoryPrices.clear();
        buyCustomCategoryPrices.clear();

        //  Custom categories section 
        // Defines groups of materials under arbitrary names.
        // Format:  categories:
        //            minecraft_spawners:
        //              items: [ZOMBIE_SPAWNER, SKELETON_SPAWNER, ...]
        ConfigurationSection catSection = economyConfig.getConfigurationSection("categories");
        if (catSection != null) {
            for (String catName : catSection.getKeys(false)) {
                List<String> items = catSection.getStringList(catName + ".items");
                Set<Material> mats = new LinkedHashSet<>();
                for (String entry : items) {
                    Material mat = parseMaterial(entry);
                    if (mat != null) {
                        mats.add(mat);
                    } else {
                        plugin.getLogger().warning("[Economy] Unknown material in category '" + catName + "': " + entry);
                    }
                }
                if (!mats.isEmpty()) {
                    customCategories.put(catName.toLowerCase(), mats);
                }
            }
            plugin.getLogger().info("[Economy] Loaded " + customCategories.size() + " custom category/ies.");
        }

        //  Sellable section 
        ConfigurationSection sellable = economyConfig.getConfigurationSection("sellable");
        if (sellable != null) {
            for (String key : sellable.getKeys(false)) {
                double price = sellable.getDouble(key, -1);
                if (!isValidPrice(price)) {
                    plugin.getLogger().warning("[Economy] Skipping '" + key + "' in sellable: invalid or negative price (" + price + ").");
                    continue;
                }

                if (key.startsWith("#")) {
                    String catRef = key.substring(1); // e.g. "minecraft:planks" or "minecraft_spawners"

                    // Try Bukkit Tag first
                    Tag<Material> tag = resolveTag(catRef);
                    if (tag != null) {
                        categoryPrices.put(tag, BigDecimal.valueOf(price));
                        categoryNames.put(tag, catRef);
                        continue;
                    }

                    // Try custom category
                    if (customCategories.containsKey(catRef.toLowerCase())) {
                        customCategoryPrices.put(catRef.toLowerCase(), BigDecimal.valueOf(price));
                        continue;
                    }

                    plugin.getLogger().warning("[Economy] Unknown tag or category: " + catRef);
                } else {
                    // Individual item
                    Material mat = parseMaterial(key);
                    if (mat != null) {
                        itemPrices.put(mat, BigDecimal.valueOf(price));
                    } else {
                        plugin.getLogger().warning("[Economy] Unknown material: " + key);
                    }
                }
            }
        }

        //  Unsellable section 
        ConfigurationSection unsellableSection = economyConfig.getConfigurationSection("unsellable");
        if (unsellableSection != null) {
            for (String key : unsellableSection.getKeys(false)) {
                String reason = unsellableSection.getString(key, "");
                Material mat = parseMaterial(key);
                if (mat != null) {
                    unsellableItems.put(mat, reason);
                }
            }
        }

        int totalCats = categoryPrices.size() + customCategoryPrices.size();
        plugin.getLogger().info("[Economy] Loaded " + itemPrices.size() + " item price(s) and "
                + totalCats + " category price(s).");

        //  Buyable section 
        ConfigurationSection buyable = economyConfig.getConfigurationSection("buying.items");
        if (buyable != null) {
            for (String key : buyable.getKeys(false)) {
                double price = buyable.getDouble(key, -1);
                if (!isValidPrice(price)) {
                    plugin.getLogger().warning("[Economy] Skipping '" + key + "' in buying.items: invalid or negative price (" + price + ").");
                    continue;
                }

                if (key.startsWith("#")) {
                    String catRef = key.substring(1);

                    // Try Bukkit Tag first
                    Tag<Material> tag = resolveTag(catRef);
                    if (tag != null) {
                        buyCategoryPrices.put(tag, BigDecimal.valueOf(price));
                        continue;
                    }

                    // Try custom category
                    if (customCategories.containsKey(catRef.toLowerCase())) {
                        buyCustomCategoryPrices.put(catRef.toLowerCase(), BigDecimal.valueOf(price));
                        continue;
                    }

                    plugin.getLogger().warning("[Economy] Unknown buy tag or category: " + catRef);
                } else {
                    Material mat = parseMaterial(key);
                    if (mat != null) {
                        buyItemPrices.put(mat, BigDecimal.valueOf(price));
                    } else {
                        plugin.getLogger().warning("[Economy] Unknown buy material: " + key);
                    }
                }
            }
        }
        int totalBuyCats = buyCategoryPrices.size() + buyCustomCategoryPrices.size();
        plugin.getLogger().info("[Economy] Loaded " + buyItemPrices.size() + " buy price(s) and "
                + totalBuyCats + " buy category price(s)."
                + (buyingEnabled ? "" : " (buying DISABLED)"));
    }

    
    //  Balance operations
    

    public BigDecimal getBalance(UUID uuid) {
        return balanceCache.computeIfAbsent(uuid, k -> startingBalance);
    }

    public BigDecimal getBalance(OfflinePlayer player) {
        return getBalance(player.getUniqueId());
    }

    public void setBalance(UUID uuid, BigDecimal amount) {
        amount = amount.setScale(decimals, RoundingMode.HALF_UP);
        balanceCache.put(uuid, amount);
        saveBalance(uuid, amount);
    }

    public boolean hasBalance(UUID uuid, BigDecimal amount) {
        return getBalance(uuid).compareTo(amount) >= 0;
    }

    public boolean withdraw(UUID uuid, BigDecimal amount) {
        BigDecimal current = getBalance(uuid);
        if (current.compareTo(amount) < 0) return false;
        setBalance(uuid, current.subtract(amount));
        return true;
    }

    public void deposit(UUID uuid, BigDecimal amount) {
        setBalance(uuid, getBalance(uuid).add(amount));
    }

    public boolean hasAccount(UUID uuid) {
        return balanceCache.containsKey(uuid);
    }

    public void createAccount(UUID uuid) {
        if (!hasAccount(uuid)) {
            setBalance(uuid, startingBalance);
        }
    }

    private void saveBalance(UUID uuid, BigDecimal amount) {
        balancesConfig.set(uuid.toString(), amount.doubleValue());
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            synchronized (balancesFile) {
                try {
                    balancesConfig.save(balancesFile);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "Could not save balances.yml!", e);
                }
            }
        });
    }

    /** Save all cached balances to disk (used on disable). */
    public void saveAll() {
        for (Map.Entry<UUID, BigDecimal> entry : balanceCache.entrySet()) {
            balancesConfig.set(entry.getKey().toString(), entry.getValue().doubleValue());
        }
        try {
            balancesConfig.save(balancesFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save balances.yml on shutdown!", e);
        }
    }

    
    //  Baltop
    

    /**
     * Returns a sorted list of (UUID, balance) entries for baltop display.
     * Applies configured exclusions.
     */
    public List<Map.Entry<UUID, BigDecimal>> getSortedBalances() {
        Set<String> excludedPlayers = new HashSet<>(
                economyConfig.getStringList("baltop.exclude-players"));
        Set<String> excludedGroups = new HashSet<>(
                economyConfig.getStringList("baltop.exclude-groups"));
        boolean excludeOps = economyConfig.getBoolean("baltop.exclude-ops", false);
        boolean excludeNoMoney = economyConfig.getBoolean("baltop.exclude-no-money", true);

        return balanceCache.entrySet().stream()
                .filter(e -> {
                    if (excludeNoMoney && e.getValue().compareTo(BigDecimal.ZERO) <= 0) return false;
                    OfflinePlayer op = Bukkit.getOfflinePlayer(e.getKey());
                    if (excludeOps && op.isOp()) return false;
                    String name = op.getName();
                    if (name != null && excludedPlayers.contains(name)) return false;
                    // Permission-based exclusion overrides config (online players only)
                    org.bukkit.entity.Player onlineP = Bukkit.getPlayer(e.getKey());
                    if (onlineP != null && onlineP.hasPermission("qol.economy.baltop.exclude")) return false;
                    // LuckPerms group exclusion (only for online players)
                    if (!excludedGroups.isEmpty() && plugin.getLuckPermsIntegration() != null) {
                        if (onlineP != null) {
                            String group = plugin.getLuckPermsIntegration().getPrimaryGroup(onlineP);
                            if (group != null && excludedGroups.contains(group)) return false;
                        }
                    }
                    return true;
                })
                .sorted(Map.Entry.<UUID, BigDecimal>comparingByValue().reversed())
                .collect(Collectors.toList());
    }

    public int getBaltopPageSize() {
        return economyConfig.getInt("baltop.page-size", 10);
    }

    /**
     * Returns true if the given online player should be excluded from viewing
     * or appearing in baltop, based purely on the config (not on permissions).
     */
    public boolean isPlayerExcludedFromBaltop(org.bukkit.entity.Player player) {
        boolean excludeOps = economyConfig.getBoolean("baltop.exclude-ops", false);
        if (excludeOps && player.isOp()) return true;

        Set<String> excludedPlayers = new HashSet<>(economyConfig.getStringList("baltop.exclude-players"));
        if (player.getName() != null && excludedPlayers.contains(player.getName())) return true;

        // Permission-based exclusion — overrides config
        if (player.hasPermission("qol.economy.baltop.exclude")) return true;

        Set<String> excludedGroups = new HashSet<>(economyConfig.getStringList("baltop.exclude-groups"));
        if (!excludedGroups.isEmpty() && plugin.getLuckPermsIntegration() != null) {
            String group = plugin.getLuckPermsIntegration().getPrimaryGroup(player);
            if (group != null && excludedGroups.contains(group)) return true;
        }
        return false;
    }

    
    //  Price lookups
    

    /**
     * Result of a price lookup containing the price and optional category name.
     */
    public static class PriceResult {
        public final BigDecimal price;
        public final String categoryName; // null if per-item price
        public final boolean sellable;

        public PriceResult(BigDecimal price, String categoryName, boolean sellable) {
            this.price = price;
            this.categoryName = categoryName;
            this.sellable = sellable;
        }
    }

    /**
     * Look up the <b>base</b> sell price for a material (before dynamic pricing).
     * Priority: explicit unsellable &gt; per-item &gt; per-Bukkit-Tag &gt; per-custom-category &gt; unsellable.
     */
    public PriceResult getBasePrice(Material material) {
        // Check explicit unsellable first
        if (unsellableItems.containsKey(material)) {
            return new PriceResult(BigDecimal.ZERO, null, false);
        }

        // Per-item price
        BigDecimal itemPrice = itemPrices.get(material);
        if (itemPrice != null) {
            return new PriceResult(itemPrice, null, true);
        }

        // Bukkit Tag category price
        for (Map.Entry<Tag<Material>, BigDecimal> entry : categoryPrices.entrySet()) {
            if (entry.getKey().isTagged(material)) {
                String catName = categoryNames.get(entry.getKey());
                return new PriceResult(entry.getValue(), catName, true);
            }
        }

        // Custom category price
        for (Map.Entry<String, Set<Material>> entry : customCategories.entrySet()) {
            if (entry.getValue().contains(material)) {
                BigDecimal catPrice = customCategoryPrices.get(entry.getKey());
                if (catPrice != null) {
                    return new PriceResult(catPrice, entry.getKey(), true);
                }
            }
        }

        // Not listed = unsellable
        return new PriceResult(BigDecimal.ZERO, null, false);
    }

    /**
     * Look up the sell price for a material, applying dynamic pricing if enabled.
     * Priority: explicit unsellable &gt; per-item &gt; per-category &gt; unsellable.
     */
    public PriceResult getPrice(Material material) {
        return getPrice(material, null);
    }

    /**
     * Look up the sell price for a material, applying dynamic pricing for a specific player.
     */
    public PriceResult getPrice(Material material, UUID playerUuid) {
        PriceResult base = getBasePrice(material);
        if (!base.sellable || dynamicPricing == null || !dynamicPricing.isEnabled()) {
            return base;
        }
        BigDecimal dynamic = dynamicPricing.applyMultiplier(base.price, material, playerUuid);
        return new PriceResult(dynamic, base.categoryName, true);
    }

    
    //  Buy price lookups
    

    /**
     * Result of a buy price lookup.
     */
    public static class BuyPriceResult {
        public final BigDecimal price;
        public final String categoryName;
        public final boolean buyable;

        public BuyPriceResult(BigDecimal price, String categoryName, boolean buyable) {
            this.price = price;
            this.categoryName = categoryName;
            this.buyable = buyable;
        }
    }

    /**
     * Look up the <b>base</b> buy price for a material (before dynamic pricing).
     * Priority: per-item > per-Bukkit-Tag > per-custom-category > not buyable.
     */
    public BuyPriceResult getBaseBuyPrice(Material material) {
        // Per-item buy price
        BigDecimal itemPrice = buyItemPrices.get(material);
        if (itemPrice != null) {
            return new BuyPriceResult(itemPrice, null, true);
        }

        // Bukkit Tag category buy price
        for (Map.Entry<Tag<Material>, BigDecimal> entry : buyCategoryPrices.entrySet()) {
            if (entry.getKey().isTagged(material)) {
                // Reuse sell category names map for display
                String catName = categoryNames.get(entry.getKey());
                return new BuyPriceResult(entry.getValue(), catName, true);
            }
        }

        // Custom category buy price
        for (Map.Entry<String, Set<Material>> entry : customCategories.entrySet()) {
            if (entry.getValue().contains(material)) {
                BigDecimal catPrice = buyCustomCategoryPrices.get(entry.getKey());
                if (catPrice != null) {
                    return new BuyPriceResult(catPrice, entry.getKey(), true);
                }
            }
        }

        return new BuyPriceResult(BigDecimal.ZERO, null, false);
    }

    /**
     * Look up the buy price for a material, applying dynamic pricing if enabled.
     */
    public BuyPriceResult getBuyPrice(Material material) {
        return getBuyPrice(material, null);
    }

    /**
     * Look up the buy price for a material, applying dynamic pricing for a specific player.
     */
    public BuyPriceResult getBuyPrice(Material material, UUID playerUuid) {
        BuyPriceResult base = getBaseBuyPrice(material);
        if (!base.buyable || dynamicPricing == null || !dynamicPricing.isEnabled()) {
            return base;
        }
        BigDecimal dynamic = dynamicPricing.applyBuyMultiplier(base.price, material, playerUuid);
        return new BuyPriceResult(dynamic, base.categoryName, true);
    }

    /**
     * Returns a snapshot of all configured buy prices (for ShopEdit GUI and /shop).
     */
    public Map<String, Double> getAllConfiguredBuyPrices() {
        Map<String, Double> prices = new LinkedHashMap<>();
        ConfigurationSection buyable = economyConfig.getConfigurationSection("buying.items");
        if (buyable != null) {
            for (String key : buyable.getKeys(false)) {
                prices.put(key, buyable.getDouble(key));
            }
        }
        return prices;
    }

    /**
     * Get the unsellable reason for a material, or null if not explicitly listed.
     */
    public String getUnsellableReason(Material material) {
        return unsellableItems.get(material);
    }

    /**
     * Calculate the total value of an ItemStack (considers stack size).
     */
    public BigDecimal getItemValue(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return BigDecimal.ZERO;
        PriceResult result = getPrice(item.getType());
        if (!result.sellable) return BigDecimal.ZERO;
        return result.price.multiply(BigDecimal.valueOf(item.getAmount()));
    }

    /**
     * Calculate the total value of a shulker box's contents.
     */
    public BigDecimal getShulkerValue(ItemStack shulkerItem) {
        if (shulkerItem == null || !isShulkerBox(shulkerItem.getType())) return BigDecimal.ZERO;
        if (!(shulkerItem.getItemMeta() instanceof BlockStateMeta)) return BigDecimal.ZERO;

        BlockStateMeta meta = (BlockStateMeta) shulkerItem.getItemMeta();
        if (!(meta.getBlockState() instanceof ShulkerBox)) return BigDecimal.ZERO;

        ShulkerBox shulker = (ShulkerBox) meta.getBlockState();
        BigDecimal total = BigDecimal.ZERO;
        for (ItemStack content : shulker.getInventory().getContents()) {
            if (content != null && content.getType() != Material.AIR) {
                total = total.add(getItemValue(content));
            }
        }
        return total;
    }

    /**
     * Count sellable items inside a shulker box.
     */
    public int getShulkerSellableCount(ItemStack shulkerItem) {
        if (!isShulkerBox(shulkerItem.getType())) return 0;
        if (!(shulkerItem.getItemMeta() instanceof BlockStateMeta)) return 0;
        BlockStateMeta meta = (BlockStateMeta) shulkerItem.getItemMeta();
        if (!(meta.getBlockState() instanceof ShulkerBox)) return 0;

        ShulkerBox shulker = (ShulkerBox) meta.getBlockState();
        int count = 0;
        for (ItemStack content : shulker.getInventory().getContents()) {
            if (content != null && content.getType() != Material.AIR) {
                if (getPrice(content.getType()).sellable) {
                    count += content.getAmount();
                }
            }
        }
        return count;
    }

    
    //  Tax calculations
    

    public boolean isTaxEnabled() {
        return taxEnabled;
    }

    public boolean isTaxOnServerSell() {
        return taxOnServerSell;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(BigDecimal rate) {
        this.taxRate = rate;
        economyConfig.set("tax.rate", rate.doubleValue());
        saveEconomyConfig();
    }

    public void setTaxEnabled(boolean enabled) {
        this.taxEnabled = enabled;
        economyConfig.set("tax.enabled", enabled);
        saveEconomyConfig();
    }

    /**
     * Calculate tax amount for a given gross amount.
     */
    public BigDecimal calculateTax(BigDecimal amount) {
        if (!taxEnabled) return BigDecimal.ZERO;
        return amount.multiply(taxRate).divide(BigDecimal.valueOf(100), decimals, RoundingMode.HALF_UP);
    }

    /**
     * Get net amount after tax.
     */
    public BigDecimal afterTax(BigDecimal amount) {
        return amount.subtract(calculateTax(amount));
    }

    
    //  Price management (admin commands)
    

    public void setPrice(String key, double price) {
        economyConfig.set("sellable." + key, price);
        saveEconomyConfig();
        loadPrices(); // rebuild caches
    }

    public void setPrice(Material material, BigDecimal price) {
        setPrice(material.name().toLowerCase(), price.doubleValue());
    }

    public void setCategoryPrice(String tagName, BigDecimal price) {
        setPrice("#" + tagName, price.doubleValue());
    }

    public void removePrice(String key) {
        economyConfig.set("sellable." + key, null);
        saveEconomyConfig();
        loadPrices();
    }

    /** Remove a per-item price. Returns true if it existed. */
    public boolean removePrice(Material material) {
        String key = material.name().toLowerCase();
        if (economyConfig.contains("sellable." + key)) {
            removePrice(key);
            return true;
        }
        return false;
    }

    /** Remove a category price. Returns true if it existed. */
    public boolean removeCategoryPrice(String tagName) {
        String key = "#" + tagName;
        if (economyConfig.contains("sellable." + key)) {
            removePrice(key);
            return true;
        }
        return false;
    }

    //  Buy price management 

    public void setBuyPrice(String key, double price) {
        economyConfig.set("buying.items." + key, price);
        saveEconomyConfig();
        loadPrices(); // rebuild caches
    }

    public void setBuyPrice(Material material, BigDecimal price) {
        setBuyPrice(material.name().toLowerCase(), price.doubleValue());
    }

    public void removeBuyPrice(String key) {
        economyConfig.set("buying.items." + key, null);
        saveEconomyConfig();
        loadPrices();
    }

    /** Remove a per-item buy price. Returns true if it existed. */
    public boolean removeBuyPrice(Material material) {
        String key = material.name().toLowerCase();
        if (economyConfig.contains("buying.items." + key)) {
            removeBuyPrice(key);
            return true;
        }
        return false;
    }

    /** Returns a snapshot of all configured item prices (for ShopEdit GUI). */
    public Map<String, Double> getAllConfiguredPrices() {
        Map<String, Double> prices = new LinkedHashMap<>();
        ConfigurationSection sellable = economyConfig.getConfigurationSection("sellable");
        if (sellable != null) {
            for (String key : sellable.getKeys(false)) {
                prices.put(key, sellable.getDouble(key));
            }
        }
        return prices;
    }

    
    //  Reload
    

    public void reload() {
        loadConfig();
        loadBalances();
        loadPrices();
        if (dynamicPricing != null) {
            dynamicPricing.reload(economyConfig);
        }
    }

    /** Shut down the dynamic pricing engine (call on plugin disable). */
    public void shutdownDynamicPricing() {
        if (dynamicPricing != null) {
            dynamicPricing.shutdown();
        }
    }

    /**
     * Record a sale for dynamic pricing.
     * Call this after a player successfully sells items.
     */
    public void recordSale(Material material, int amount, UUID playerUuid) {
        if (dynamicPricing != null) {
            dynamicPricing.recordSale(material, amount, playerUuid);
        }
    }

    /**
     * Record a purchase for dynamic pricing.
     * Call this after a player successfully buys items from the server.
     */
    public void recordPurchase(Material material, int amount, UUID playerUuid) {
        if (dynamicPricing != null) {
            dynamicPricing.recordPurchase(material, amount, playerUuid);
        }
    }

    /** Get the dynamic pricing engine (may be null if economy is disabled). */
    public DynamicPricingEngine getDynamicPricing() {
        return dynamicPricing;
    }

    
    //  Utility
    

    private void saveEconomyConfig() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            synchronized (economyFile) {
                try {
                    economyConfig.save(economyFile);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "Could not save economy.yml!", e);
                }
            }
        });
    }

    /** Parse a material name (case-insensitive, optional minecraft: prefix). */
    public static Material parseMaterial(String name) {
        if (name == null || name.isBlank()) return null;
        name = name.trim().toUpperCase().replace("MINECRAFT:", "").replace(" ", "_");
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Returns true if {@code price} is a finite, non-negative number that is safe
     * to use as a price. NaN, Infinity, and values below zero are rejected.
     * A price of exactly 0.0 is allowed (means "sellable for free").
     */
    private static boolean isValidPrice(double price) {
        return !Double.isNaN(price) && !Double.isInfinite(price) && price >= 0;
    }

    /** Resolve a Bukkit Tag from a string like "minecraft:planks". */
    @SuppressWarnings("unchecked")
    public static Tag<Material> resolveTag(String tagName) {
        // Try blocks registry first, then items
        try {
            String[] parts = tagName.split(":");
            String ns = parts.length > 1 ? parts[0] : "minecraft";
            String key = parts.length > 1 ? parts[1] : parts[0];
            NamespacedKey nsKey = new NamespacedKey(ns, key);

            Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_BLOCKS, nsKey, Material.class);
            if (tag != null) return tag;
            tag = Bukkit.getTag(Tag.REGISTRY_ITEMS, nsKey, Material.class);
            return tag;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isShulkerBox(Material mat) {
        return mat != null && mat.name().endsWith("SHULKER_BOX");
    }

    public String formatMoney(BigDecimal amount) {
        return unit + amount.setScale(decimals, RoundingMode.HALF_UP).toPlainString();
    }

    public String formatMoney(double amount) {
        return formatMoney(BigDecimal.valueOf(amount));
    }

    /**
     * Show a transaction notification above the hotbar (action bar).
     * Gain = green "+$X.XX", loss = red "-$X.XX".
     */
    public void sendTransactionHUD(Player player, BigDecimal amount, boolean gain) {
        if (player == null || !player.isOnline()) return;
        String sign = gain ? ChatColor.GREEN + "+" : ChatColor.RED + "-";
        String text = sign + formatMoney(amount.abs());
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(text));
    }

    /** Get a message from economy.yml, applying color codes. */
    public String getMessage(String key) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&',
                economyConfig.getString("messages." + key, "&cMissing message: " + key));
    }

    //  Getters 

    public boolean isEnabled() { return enabled; }
    public boolean isUseVault() { return useVault; }
    public boolean isBuyingEnabled() { return buyingEnabled; }
    public String getUnit() { return unit; }
    public int getDecimals() { return decimals; }
    public BigDecimal getStartingBalance() { return startingBalance; }
    public FileConfiguration getEconomyConfig() { return economyConfig; }
    public String getCurrencyNameSingular() {
        return economyConfig.getString("currency-name-singular", "dollar");
    }
    public String getCurrencyNamePlural() {
        return economyConfig.getString("currency-name-plural", "dollars");
    }
    public boolean isTaxOnPay() {
        return economyConfig.getBoolean("tax.apply-when-paying-player", false);
    }
    public String getTaxRateDisplay() {
        return taxRate.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    /** Returns an unmodifiable view of all custom categories (name → materials). */
    public Map<String, Set<Material>> getCustomCategories() {
        return Collections.unmodifiableMap(customCategories);
    }
}
