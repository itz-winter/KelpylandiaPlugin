package com.kelpwing.kelpylandiaplugin.economy;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import com.kelpwing.kelpylandiaplugin.economy.commands.SellCommand;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Player-facing shop GUI for buying items from the server.
 * - Browse all buyable items with pagination
 * - Click to buy 1, shift+click to buy a stack (64)
 * - Bottom row: navigation, search, amount prompt
 */
public class ShopGUI implements Listener {

    private final KelpylandiaPlugin plugin;

    private static final String GUI_TITLE_PREFIX = ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "Server Shop";
    private static final int GUI_SIZE = 54; // 6 rows
    private static final int ITEMS_PER_PAGE = 45; // rows 1-5

    // Navigation slot indices (row 6)
    private static final int SLOT_PREV = 45;
    private static final int SLOT_SEARCH = 49;
    private static final int SLOT_NEXT = 53;

    // Per-player state
    private final Map<UUID, Integer> playerPages = new HashMap<>();
    private final Map<UUID, String> playerSearch = new HashMap<>();

    // Players waiting for chat input (search or buy amount)
    private final Map<UUID, ChatInputState> awaitingChatInput = new HashMap<>();

    public ShopGUI(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Open the shop GUI for a player.
     */
    public void openGUI(Player player) {
        openGUI(player, 0);
    }

    public void openGUI(Player player, int page) {
        EconomyManager eco = plugin.getEconomyManager();
        if (eco == null || !eco.isEnabled()) {
            player.sendMessage(ChatColor.RED + "The economy system is disabled.");
            return;
        }
        if (!eco.isBuyingEnabled()) {
            player.sendMessage(eco.getMessage("shop-buy-disabled"));
            return;
        }

        List<Map.Entry<String, Double>> prices = getFilteredPrices(player);
        int totalPages = Math.max(1, (int) Math.ceil((double) prices.size() / ITEMS_PER_PAGE));
        page = Math.max(0, Math.min(page, totalPages - 1));
        playerPages.put(player.getUniqueId(), page);

        String title = GUI_TITLE_PREFIX + " " + ChatColor.GRAY + "(" + (page + 1) + "/" + totalPages + ")";
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, title);

        // Fill items for this page
        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, prices.size());

        for (int i = startIndex; i < endIndex; i++) {
            Map.Entry<String, Double> entry = prices.get(i);
            int slot = i - startIndex;
            gui.setItem(slot, createShopItem(eco, entry.getKey(), entry.getValue(), player));
        }

        // Navigation row 
        if (page > 0) {
            gui.setItem(SLOT_PREV, createNavItem(Material.ARROW, ChatColor.YELLOW + "← Previous Page"));
        } else {
            gui.setItem(SLOT_PREV, createNavItem(Material.GRAY_STAINED_GLASS_PANE, ChatColor.GRAY + "No Previous Page"));
        }

        // Search
        String searchLabel = playerSearch.containsKey(player.getUniqueId())
                ? ChatColor.AQUA + "Search: " + ChatColor.WHITE + playerSearch.get(player.getUniqueId())
                        + ChatColor.GRAY + " (shift-click to clear)"
                : ChatColor.AQUA + "Search (click to filter)";
        gui.setItem(SLOT_SEARCH, createNavItem(Material.COMPASS, searchLabel));

        if (page < totalPages - 1) {
            gui.setItem(SLOT_NEXT, createNavItem(Material.ARROW, ChatColor.YELLOW + "Next Page →"));
        } else {
            gui.setItem(SLOT_NEXT, createNavItem(Material.GRAY_STAINED_GLASS_PANE, ChatColor.GRAY + "No Next Page"));
        }

        // Fill empty nav slots with glass panes
        for (int slot = 45; slot < 54; slot++) {
            if (gui.getItem(slot) == null) {
                gui.setItem(slot, createNavItem(Material.BLACK_STAINED_GLASS_PANE, " "));
            }
        }

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (event.getView().getTitle() == null) return;
        if (!event.getView().getTitle().startsWith(GUI_TITLE_PREFIX)) return;

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        EconomyManager eco = plugin.getEconomyManager();
        if (eco == null || !eco.isBuyingEnabled()) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= GUI_SIZE) return;

        // Navigation clicks 
        if (slot == SLOT_PREV) {
            int page = playerPages.getOrDefault(player.getUniqueId(), 0);
            if (page > 0) openGUI(player, page - 1);
            return;
        }
        if (slot == SLOT_NEXT) {
            int page = playerPages.getOrDefault(player.getUniqueId(), 0);
            openGUI(player, page + 1);
            return;
        }
        if (slot == SLOT_SEARCH) {
            if (event.isShiftClick()) {
                playerSearch.remove(player.getUniqueId());
                openGUI(player, 0);
            } else {
                player.closeInventory();
                player.sendMessage(ChatColor.AQUA + "Type a search term in chat (or 'cancel' to cancel):");
                awaitingChatInput.put(player.getUniqueId(), new ChatInputState(ChatInputType.SEARCH, null));
            }
            return;
        }

        // Item clicks (buy) 
        if (slot < ITEMS_PER_PAGE) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            String configKey = getConfigKeyFromItem(clicked);
            if (configKey == null) return;

            Material material = resolveMaterial(configKey);
            if (material == null) return;

            if (event.isShiftClick()) {
                // Buy a full stack (64)
                executeBuy(player, eco, material, 64);
            } else if (event.isRightClick()) {
                // Prompt for custom amount
                player.closeInventory();
                player.sendMessage(ChatColor.YELLOW + "Enter amount to buy for " + ChatColor.WHITE
                        + SellCommand.formatMaterial(material) + ChatColor.YELLOW + ":");
                player.sendMessage(ChatColor.GRAY + "Type 'cancel' to cancel.");
                awaitingChatInput.put(player.getUniqueId(), new ChatInputState(ChatInputType.BUY_AMOUNT, configKey));
            } else {
                // Buy 1
                executeBuy(player, eco, material, 1);
            }
        }
    }

    private void executeBuy(Player player, EconomyManager eco, Material material, int amount) {
        EconomyManager.BuyPriceResult priceResult = eco.getBuyPrice(material, player.getUniqueId());
        if (!priceResult.buyable) {
            player.sendMessage(ChatColor.RED + "That item is no longer available for purchase.");
            return;
        }

        BigDecimal totalCost = priceResult.price.multiply(BigDecimal.valueOf(amount));

        // Check funds
        if (!eco.hasBalance(player.getUniqueId(), totalCost)) {
            player.sendMessage(eco.getMessage("shop-insufficient-funds")
                    .replace("{unit}", eco.getUnit())
                    .replace("{cost}", totalCost.setScale(eco.getDecimals(), RoundingMode.HALF_UP).toPlainString())
                    .replace("{balance}", eco.getBalance(player).setScale(eco.getDecimals(), RoundingMode.HALF_UP).toPlainString()));
            return;
        }

        // Check inventory space
        ItemStack toGive = new ItemStack(material, amount);
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(toGive);
        if (!leftover.isEmpty()) {
            // Revert: remove items we just added
            for (ItemStack item : leftover.values()) {
                player.getInventory().removeItem(item);
            }
            // Try to figure out how many actually fit
            int actualAmount = amount;
            for (ItemStack item : leftover.values()) {
                actualAmount -= item.getAmount();
            }
            if (actualAmount <= 0) {
                player.sendMessage(eco.getMessage("shop-inventory-full"));
                return;
            }
            // Give only what fits
            player.getInventory().addItem(new ItemStack(material, actualAmount));
            amount = actualAmount;
            totalCost = priceResult.price.multiply(BigDecimal.valueOf(amount));
        }

        // Withdraw funds
        eco.withdraw(player.getUniqueId(), totalCost);

        // Record purchase for dynamic pricing
        eco.recordPurchase(material, amount, player.getUniqueId());

        // Transaction HUD
        eco.sendTransactionHUD(player, totalCost, false);

        String itemName = SellCommand.formatMaterial(material);
        String totalStr = totalCost.setScale(eco.getDecimals(), RoundingMode.HALF_UP).toPlainString();
        player.sendMessage(eco.getMessage("shop-buy-success")
                .replace("{amount}", String.valueOf(amount))
                .replace("{item}", itemName)
                .replace("{unit}", eco.getUnit())
                .replace("{total}", totalStr));

        // Refresh the GUI
        int page = playerPages.getOrDefault(player.getUniqueId(), 0);
        Bukkit.getScheduler().runTask(plugin, () -> openGUI(player, page));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        if (event.getView().getTitle() == null) return;
        if (!event.getView().getTitle().startsWith(GUI_TITLE_PREFIX)) return;
        if (!awaitingChatInput.containsKey(player.getUniqueId())) {
            playerSearch.remove(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        ChatInputState state = awaitingChatInput.remove(player.getUniqueId());
        if (state == null) return;

        event.setCancelled(true);
        String input = event.getMessage().trim();

        if (input.equalsIgnoreCase("cancel")) {
            player.sendMessage(ChatColor.GRAY + "Cancelled.");
            Bukkit.getScheduler().runTask(plugin, () -> openGUI(player, playerPages.getOrDefault(player.getUniqueId(), 0)));
            return;
        }

        EconomyManager eco = plugin.getEconomyManager();
        if (eco == null) return;

        switch (state.type) {
            case SEARCH:
                playerSearch.put(player.getUniqueId(), input.toLowerCase());
                Bukkit.getScheduler().runTask(plugin, () -> openGUI(player, 0));
                break;

            case BUY_AMOUNT:
                try {
                    int amount = Integer.parseInt(input);
                    if (amount < 1) {
                        player.sendMessage(ChatColor.RED + "Amount must be at least 1.");
                        Bukkit.getScheduler().runTask(plugin, () -> openGUI(player, playerPages.getOrDefault(player.getUniqueId(), 0)));
                        return;
                    }
                    Material material = resolveMaterial(state.configKey);
                    if (material == null) {
                        player.sendMessage(ChatColor.RED + "Unknown item.");
                        Bukkit.getScheduler().runTask(plugin, () -> openGUI(player, playerPages.getOrDefault(player.getUniqueId(), 0)));
                        return;
                    }
                    final int finalAmount = amount;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        executeBuy(player, eco, material, finalAmount);
                    });
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Invalid amount: " + input);
                    Bukkit.getScheduler().runTask(plugin, () -> openGUI(player, playerPages.getOrDefault(player.getUniqueId(), 0)));
                }
                break;
        }
    }

    
    // Helpers
    

    private List<Map.Entry<String, Double>> getFilteredPrices(Player player) {
        EconomyManager eco = plugin.getEconomyManager();
        if (eco == null) return Collections.emptyList();

        Map<String, Double> all = eco.getAllConfiguredBuyPrices();
        String search = playerSearch.get(player.getUniqueId());

        List<Map.Entry<String, Double>> entries;
        if (search == null || search.isEmpty()) {
            entries = new ArrayList<>(all.entrySet());
        } else {
            entries = all.entrySet().stream()
                    .filter(e -> e.getKey().toLowerCase().contains(search)
                            || SellCommand.formatMaterial(resolveMaterialSafe(e.getKey())).toLowerCase().contains(search))
                    .collect(Collectors.toList());
        }
        return entries;
    }

    private ItemStack createShopItem(EconomyManager eco, String configKey, double basePrice, Player player) {
        Material displayMat;
        String displayName;

        if (configKey.startsWith("#")) {
            displayMat = Material.NAME_TAG;
            displayName = ChatColor.LIGHT_PURPLE + configKey;
        } else {
            Material mat = EconomyManager.parseMaterial(configKey);
            displayMat = (mat != null && mat.isItem()) ? mat : Material.BARRIER;
            displayName = ChatColor.WHITE + SellCommand.formatMaterial(displayMat);
        }

        // Get dynamic buy price if available
        Material actualMat = resolveMaterial(configKey);
        String priceDisplay;
        if (actualMat != null) {
            EconomyManager.BuyPriceResult dynamic = eco.getBuyPrice(actualMat, player.getUniqueId());
            priceDisplay = eco.getUnit() + dynamic.price.setScale(eco.getDecimals(), RoundingMode.HALF_UP).toPlainString();
        } else {
            priceDisplay = eco.getUnit() + BigDecimal.valueOf(basePrice).setScale(eco.getDecimals(), RoundingMode.HALF_UP).toPlainString();
        }

        ItemStack item = new ItemStack(displayMat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GOLD + "Buy Price: " + ChatColor.GREEN + priceDisplay);
            lore.add("");
            lore.add(ChatColor.GRAY + "Left-click: Buy 1");
            lore.add(ChatColor.GRAY + "Right-click: Custom amount");
            lore.add(ChatColor.GRAY + "Shift+click: Buy 64");
            lore.add(ChatColor.DARK_GRAY + "Key: " + configKey);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createNavItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String getConfigKeyFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.getLore() == null) return null;

        for (String line : meta.getLore()) {
            String stripped = ChatColor.stripColor(line);
            if (stripped.startsWith("Key: ")) {
                return stripped.substring(5);
            }
        }
        return null;
    }

    /**
     * Resolve a config key to a Material. Returns null for category keys.
     */
    private Material resolveMaterial(String configKey) {
        if (configKey == null || configKey.startsWith("#")) return null;
        return EconomyManager.parseMaterial(configKey);
    }

    private Material resolveMaterialSafe(String configKey) {
        Material mat = resolveMaterial(configKey);
        return mat != null ? mat : Material.BARRIER;
    }

    
    // Chat input state
    

    private enum ChatInputType {
        SEARCH, BUY_AMOUNT
    }

    private static class ChatInputState {
        final ChatInputType type;
        final String configKey;

        ChatInputState(ChatInputType type, String configKey) {
            this.type = type;
            this.configKey = configKey;
        }
    }
}
